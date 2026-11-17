package com.beboppilot.app.drone

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.beboppilot.app.sdk.LocalMediaItem

/**
 * Consulta en MediaStore únicamente las fotos y videos guardados por BebopPilot
 * (Movies/BebopPilot y Pictures/BebopPilot), sin mezclar otros archivos del teléfono.
 */
object LocalMediaStoreLoader {

    private const val TAG = "LocalMediaStoreLoader"

    fun queryAppMedia(context: Context): List<LocalMediaItem> {
        val resolver = context.contentResolver
        val items = ArrayList<LocalMediaItem>()

        queryVideos(resolver, items)
        queryImages(resolver, items)

        items.sortByDescending { it.dateAddedSec }
        return items
    }

    private fun queryVideos(
        resolver: android.content.ContentResolver,
        out: MutableList<LocalMediaItem>,
    ) {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.DATE_ADDED)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.DURATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        val selection: String
        val selectionArgs: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection =
                "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0"
            selectionArgs = arrayOf("%BebopPilot%")
        } else {
            @Suppress("DEPRECATION")
            selection = "${MediaStore.Video.Media.DATA} LIKE ?"
            selectionArgs = arrayOf("%/BebopPilot/%")
        }

        runCatching {
            resolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Video.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val relCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                } else {
                    -1
                }

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val size = cursor.getLong(sizeCol).coerceAtLeast(0L)
                    if (size <= 0L) continue
                    val dateSec = cursor.getLong(dateCol)
                    val durMs = if (durCol >= 0 && !cursor.isNull(durCol)) cursor.getLong(durCol) else null
                    val relPath = if (relCol >= 0) {
                        cursor.getString(relCol) ?: "${Environment.DIRECTORY_MOVIES}/BebopPilot/"
                    } else {
                        "${Environment.DIRECTORY_MOVIES}/BebopPilot/"
                    }
                    val uri = ContentUris.withAppendedId(collection, id)
                    out.add(
                        LocalMediaItem(
                            id = id,
                            uriString = uri.toString(),
                            name = name,
                            dateAddedSec = dateSec,
                            sizeBytes = size,
                            durationMs = durMs,
                            isVideo = true,
                            relativePath = relPath,
                        )
                    )
                }
            }
        }.onFailure { t ->
            Log.w(TAG, "Error consultando videos locales de BebopPilot", t)
        }
    }

    private fun queryImages(
        resolver: android.content.ContentResolver,
        out: MutableList<LocalMediaItem>,
    ) {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.DATE_ADDED)
            add(MediaStore.Images.Media.SIZE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        val selection: String
        val selectionArgs: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection =
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0"
            selectionArgs = arrayOf("%BebopPilot%")
        } else {
            @Suppress("DEPRECATION")
            selection = "${MediaStore.Images.Media.DATA} LIKE ?"
            selectionArgs = arrayOf("%/BebopPilot/%")
        }

        runCatching {
            resolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val relCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                } else {
                    -1
                }

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val size = cursor.getLong(sizeCol).coerceAtLeast(0L)
                    if (size <= 0L) continue
                    val dateSec = cursor.getLong(dateCol)
                    val relPath = if (relCol >= 0) {
                        cursor.getString(relCol) ?: "${Environment.DIRECTORY_PICTURES}/BebopPilot/"
                    } else {
                        "${Environment.DIRECTORY_PICTURES}/BebopPilot/"
                    }
                    val uri = ContentUris.withAppendedId(collection, id)
                    out.add(
                        LocalMediaItem(
                            id = id,
                            uriString = uri.toString(),
                            name = name,
                            dateAddedSec = dateSec,
                            sizeBytes = size,
                            durationMs = null,
                            isVideo = false,
                            relativePath = relPath,
                        )
                    )
                }
            }
        }.onFailure { t ->
            Log.w(TAG, "Error consultando imágenes locales de BebopPilot", t)
        }
    }
}
