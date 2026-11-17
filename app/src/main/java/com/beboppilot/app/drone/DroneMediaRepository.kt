package com.beboppilot.app.drone

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.beboppilot.app.sdk.DroneMediaItem
import com.beboppilot.app.sdk.MediaGalleryState
import com.parrot.arsdk.ardatatransfer.ARDATATRANSFER_ERROR_ENUM
import com.parrot.arsdk.ardatatransfer.ARDataTransferManager
import com.parrot.arsdk.ardatatransfer.ARDataTransferMedia
import com.parrot.arsdk.ardatatransfer.ARDataTransferMediasDownloader
import com.parrot.arsdk.ardatatransfer.ARDataTransferMediasDownloaderCompletionListener
import com.parrot.arsdk.ardatatransfer.ARDataTransferMediasDownloaderProgressListener
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService
import com.parrot.arsdk.arutils.ARUTILS_DESTINATION_ENUM
import com.parrot.arsdk.arutils.ARUTILS_ERROR_ENUM
import com.parrot.arsdk.arutils.ARUTILS_FTP_TYPE_ENUM
import com.parrot.arsdk.arutils.ARUtilsManager
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Gestiona listado y descarga FTP (puerto 21, anonymous, "internal_000") del Bebop 2
 * usando ARUtilsManager + ARDataTransferMediasDownloader a través de USB MUX ("drone").
 * No borra automáticamente los archivos originales en el dron.
 */
class DroneMediaRepository(
    context: Context,
    private val onStateChanged: (MediaGalleryState) -> Unit,
) {

    private val appContext = context.applicationContext
    private val ftpExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "BebopPilot-FtpMux")
    }
    private val localMediaExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "BebopPilot-LocalMedia")
    }
    private val stateLock = Any()

    private val cancelRequested = AtomicBoolean(false)
    private var currentState = MediaGalleryState()

    // Accedidos dentro de ftpExecutor o cancelados de forma segura
    private var ftpListManager: ARUtilsManager? = null
    private var ftpQueueManager: ARUtilsManager? = null
    private var dataTransferManager: ARDataTransferManager? = null
    private var boundService: ARDiscoveryDeviceService? = null
    private val mediaByKey = LinkedHashMap<String, ARDataTransferMedia>()

    fun attachDeviceService(service: ARDiscoveryDeviceService?) {
        if (service == null) {
            cancelActiveOperation()
        }
        ftpExecutor.execute {
            if (boundService == service) return@execute
            closeSessionLocked()
            boundService = service
            mediaByKey.clear()
            updateState { prev ->
                MediaGalleryState(
                    isSupported = true,
                    downloadedNames = prev.downloadedNames,
                    localItems = prev.localItems,
                    localLoading = prev.localLoading,
                )
            }
        }
    }

    fun refreshLocalMedia() {
        updateState { it.copy(localLoading = true) }
        localMediaExecutor.execute {
            val found = LocalMediaStoreLoader.queryAppMedia(appContext)
            val savedNames = found.map { it.name }.toSet()
            updateState { prev ->
                prev.copy(
                    localItems = found,
                    localLoading = false,
                    downloadedNames = prev.downloadedNames + savedNames,
                )
            }
        }
    }

    fun refreshMediaList() {
        ftpExecutor.execute {
            val service = boundService
            if (service == null) {
                updateState {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Skycontroller 2 no enlazado por USB MUX",
                    )
                }
                return@execute
            }

            cancelRequested.set(false)
            updateState {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    statusMessage = "Consultando memoria del Bebop 2 vía FTP/MUX...",
                )
            }

            try {
                val downloader = ensureSessionLocked(service)
                val count = downloader.getAvailableMediasSync(false)
                val list = ArrayList<DroneMediaItem>(count.coerceAtLeast(0))
                mediaByKey.clear()
                for (i in 0 until count) {
                    if (cancelRequested.get()) break
                    val media = downloader.getAvailableMediaAtIndex(i) ?: continue
                    val name = media.name ?: "media_$i"
                    // media.uuid en Parrot es el run_id del vuelo (compartido por foto y video);
                    // usamos remotePath (o nombre único) para no mezclar ni sobrescribir archivos.
                    val uniqueKey = media.remotePath?.takeIf { it.isNotBlank() }
                        ?: "${name}_${media.uuid.orEmpty()}_$i"
                    mediaByKey[uniqueKey] = media
                    val lower = name.lowercase()
                    val isVideo = lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".mkv")
                    list.add(
                        DroneMediaItem(
                            uuid = uniqueKey,
                            name = name,
                            date = media.date ?: "",
                            sizeBytes = media.size.toLong().coerceAtLeast(0L),
                            isVideo = isVideo,
                            remotePath = media.remotePath,
                        )
                    )
                }
                list.sortByDescending { it.date }
                updateState {
                    it.copy(
                        isLoading = false,
                        items = list,
                        statusMessage = if (list.isEmpty()) {
                            "No se encontraron archivos en internal_000"
                        } else {
                            "${list.size} archivo(s) encontrado(s) en Bebop 2"
                        },
                        errorMessage = null,
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Error listando medios FTP/MUX", t)
                closeSessionLocked()
                updateState {
                    it.copy(
                        isLoading = false,
                        errorMessage = "No se pudo listar FTP/MUX (${t.message ?: t::class.java.simpleName}). " +
                            "Comprueba que el Bebop 2 esté enlazado con el Skycontroller 2.",
                    )
                }
            }
        }
    }

    fun downloadMedia(item: DroneMediaItem) {
        ftpExecutor.execute {
            val service = boundService
            if (service == null) {
                updateState { it.copy(errorMessage = "Skycontroller 2 desconectado") }
                return@execute
            }
            val media = mediaByKey[item.uuid]
            if (media == null) {
                updateState { it.copy(errorMessage = "Elemento no disponible; actualiza la galería") }
                return@execute
            }

            cancelRequested.set(false)
            updateState {
                it.copy(
                    isDownloading = true,
                    activeDownloadName = item.name,
                    downloadProgressPercent = 0,
                    errorMessage = null,
                    statusMessage = "Descargando ${item.name}...",
                )
            }

            try {
                val downloader = ensureSessionLocked(service)
                val stagingDir = stagingDirectory()
                val stagedFile = media.filePath?.let { File(it) } ?: File(stagingDir, item.name)
                if (stagedFile.exists()) {
                    stagedFile.delete()
                }
                val downloadingPartial = File(stagingDir, "downloading_${item.name}")
                if (downloadingPartial.exists()) {
                    downloadingPartial.delete()
                }

                var completionError: ARDATATRANSFER_ERROR_ENUM? = null
                val progressListener = ARDataTransferMediasDownloaderProgressListener { _, _, percent ->
                    val clamped = percent.roundToInt().coerceIn(0, 100)
                    updateState { it.copy(downloadProgressPercent = clamped) }
                }
                val completionListener = ARDataTransferMediasDownloaderCompletionListener { _, _, err ->
                    completionError = err
                    Log.i(TAG, "Descarga FTP finalizada para ${item.name} ($err); cerrando ciclo de cola")
                    if (!cancelRequested.get() && err == ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_OK) {
                        updateState {
                            it.copy(
                                downloadProgressPercent = 100,
                                statusMessage = "Guardando ${item.name} en el celular...",
                            )
                        }
                    }
                    // ARDATATRANSFER_MediasDownloader_QueueThreadRun es un bucle infinito esperando
                    // queueSem hasta recibir cancelQueueThread(); sin esto se queda bloqueado en 100%.
                    runCatching { downloader.cancelQueueThread() }
                }

                downloader.addMediaToQueue(media, progressListener, null, completionListener, null)
                downloader.downloaderQueueRunnable.run()

                if (cancelRequested.get()) {
                    stagedFile.delete()
                    updateState {
                        it.copy(
                            isDownloading = false,
                            activeDownloadName = null,
                            downloadProgressPercent = 0,
                            statusMessage = "Descarga cancelada",
                        )
                    }
                    return@execute
                }

                if (completionError != null && completionError != ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_OK) {
                    stagedFile.delete()
                    updateState {
                        it.copy(
                            isDownloading = false,
                            activeDownloadName = null,
                            errorMessage = "Error descargando ${item.name}: $completionError",
                        )
                    }
                    return@execute
                }

                val savedLocation = if (stagedFile.exists()) {
                    exportStagedFileToMediaStore(stagedFile, item)
                } else {
                    null
                }
                stagedFile.delete()

                if (savedLocation != null) {
                    updateState {
                        it.copy(
                            isDownloading = false,
                            activeDownloadName = null,
                            downloadProgressPercent = 100,
                            downloadedNames = it.downloadedNames + item.name,
                            statusMessage = "Guardado en $savedLocation (original conservado en Bebop 2)",
                            errorMessage = null,
                        )
                    }
                    refreshLocalMedia()
                } else {
                    updateState {
                        it.copy(
                            isDownloading = false,
                            activeDownloadName = null,
                            errorMessage = "No se encontró el archivo descargado para exportar a galería",
                        )
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Excepción descargando medio FTP/MUX", t)
                closeSessionLocked()
                updateState {
                    it.copy(
                        isDownloading = false,
                        activeDownloadName = null,
                        errorMessage = "Fallo de transferencia FTP/MUX: ${t.message ?: t::class.java.simpleName}",
                    )
                }
            }
        }
    }

    fun deleteMedia(item: DroneMediaItem) {
        ftpExecutor.execute {
            val service = boundService
            if (service == null) {
                updateState { it.copy(errorMessage = "Skycontroller 2 desconectado") }
                return@execute
            }
            val media = mediaByKey[item.uuid]
            if (media == null) {
                updateState { it.copy(errorMessage = "Archivo no encontrado; actualiza la memoria del dron") }
                return@execute
            }

            updateState {
                it.copy(
                    isDeleting = true,
                    activeDeleteName = item.name,
                    errorMessage = null,
                    statusMessage = "Borrando ${item.name} del Bebop 2...",
                )
            }

            try {
                val downloader = ensureSessionLocked(service)
                val err = downloader.deleteMedia(media)
                if (err == ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_OK) {
                    mediaByKey.remove(item.uuid)
                    Log.i(TAG, "Archivo eliminado del Bebop 2 vía FTP/MUX: ${item.name}")
                    updateState {
                        it.copy(
                            isDeleting = false,
                            activeDeleteName = null,
                            items = it.items.filterNot { entry -> entry.uuid == item.uuid },
                            statusMessage = "Eliminado del dron: ${item.name}",
                            errorMessage = null,
                        )
                    }
                } else {
                    Log.w(TAG, "deleteMedia(${item.name}) falló -> $err")
                    updateState {
                        it.copy(
                            isDeleting = false,
                            activeDeleteName = null,
                            errorMessage = "No se pudo borrar ${item.name} del dron ($err)",
                        )
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Excepción borrando medio FTP/MUX", t)
                closeSessionLocked()
                updateState {
                    it.copy(
                        isDeleting = false,
                        activeDeleteName = null,
                        errorMessage = "Error borrando en Bebop 2: ${t.message ?: t::class.java.simpleName}",
                    )
                }
            }
        }
    }

    fun cancelActiveOperation() {
        cancelRequested.set(true)
        val downloader = dataTransferManager?.arDataTransferMediasDownloader
        runCatching { downloader?.cancelGetAvailableMedias() }
        runCatching { downloader?.cancelQueueThread() }
    }

    fun release() {
        cancelActiveOperation()
        ftpExecutor.execute {
            closeSessionLocked()
            boundService = null
        }
    }

    private fun ensureSessionLocked(service: ARDiscoveryDeviceService): ARDataTransferMediasDownloader {
        dataTransferManager?.arDataTransferMediasDownloader?.let { existing ->
            return existing
        }

        val listMgr = ARUtilsManager()
        val queueMgr = ARUtilsManager()

        val listErr = listMgr.initFtp(
            appContext,
            service,
            ARUTILS_DESTINATION_ENUM.ARUTILS_DESTINATION_DRONE,
            ARUTILS_FTP_TYPE_ENUM.ARUTILS_FTP_TYPE_GENERIC,
        )
        if (listErr != ARUTILS_ERROR_ENUM.ARUTILS_OK) {
            listMgr.dispose()
            queueMgr.dispose()
            throw IllegalStateException("initFtp(list) falló: $listErr")
        }

        val queueErr = queueMgr.initFtp(
            appContext,
            service,
            ARUTILS_DESTINATION_ENUM.ARUTILS_DESTINATION_DRONE,
            ARUTILS_FTP_TYPE_ENUM.ARUTILS_FTP_TYPE_GENERIC,
        )
        if (queueErr != ARUTILS_ERROR_ENUM.ARUTILS_OK) {
            runCatching { listMgr.closeFtp(appContext, service) }
            listMgr.dispose()
            queueMgr.dispose()
            throw IllegalStateException("initFtp(queue) falló: $queueErr")
        }

        val transferMgr = ARDataTransferManager()
        transferMgr.arDataTransferMediasDownloader.createMediasDownloader(
            listMgr,
            queueMgr,
            DRONE_MEDIA_FOLDER,
            stagingDirectory().absolutePath + File.separator,
        )

        ftpListManager = listMgr
        ftpQueueManager = queueMgr
        dataTransferManager = transferMgr
        return transferMgr.arDataTransferMediasDownloader
    }

    private fun closeSessionLocked() {
        val service = boundService
        runCatching { dataTransferManager?.dispose() }
        dataTransferManager = null

        if (service != null) {
            runCatching { ftpListManager?.closeFtp(appContext, service) }
            runCatching { ftpQueueManager?.closeFtp(appContext, service) }
        }
        runCatching { ftpListManager?.dispose() }
        runCatching { ftpQueueManager?.dispose() }
        ftpListManager = null
        ftpQueueManager = null
    }

    private fun stagingDirectory(): File {
        return File(appContext.cacheDir, "drone_ftp_staging").apply {
            if (!exists()) mkdirs()
        }
    }

    private fun exportStagedFileToMediaStore(source: File, item: DroneMediaItem): String? {
        val resolver = appContext.contentResolver
        val isVideo = item.isVideo
        val mimeType = when {
            item.name.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            item.name.endsWith(".mov", ignoreCase = true) -> "video/quicktime"
            item.name.endsWith(".dng", ignoreCase = true) -> "image/x-adobe-dng"
            else -> "image/jpeg"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val relDir = if (isVideo) {
                "${Environment.DIRECTORY_MOVIES}/BebopPilot"
            } else {
                "${Environment.DIRECTORY_PICTURES}/BebopPilot"
            }
            val values = ContentValues().apply {
                if (isVideo) {
                    put(MediaStore.Video.Media.DISPLAY_NAME, item.name)
                    put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Video.Media.RELATIVE_PATH, relDir)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                } else {
                    put(MediaStore.Images.Media.DISPLAY_NAME, item.name)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, relDir)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(collection, values) ?: return null
            return try {
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(source).use { input ->
                        input.copyTo(out)
                    }
                } ?: return null
                val publish = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, publish, null, null)
                "$relDir/${item.name}"
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
        } else {
            val baseDir = Environment.getExternalStoragePublicDirectory(
                if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            )
            val targetDir = File(baseDir, "BebopPilot").apply { mkdirs() }
            val dest = File(targetDir, item.name)
            FileInputStream(source).use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            }
            return dest.absolutePath
        }
    }

    private fun updateState(transform: (MediaGalleryState) -> MediaGalleryState) {
        val next = synchronized(stateLock) {
            val updated = transform(currentState)
            currentState = updated
            updated
        }
        onStateChanged(next)
    }

    companion object {
        private const val TAG = "DroneMediaRepository"
        private const val DRONE_MEDIA_FOLDER = "internal_000"
    }
}
