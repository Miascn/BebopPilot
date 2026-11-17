package com.beboppilot.app.video

import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/**
 * Transcodificador ligero por hardware (Decoder -> EGL SurfaceTexture -> Encoder H.264 -> MediaMuxer)
 * que convierte el stream PIR/intra-refresh del Bebop 2 en un MP4 estándar con keyframes IDR
 * reales cada 1 segundo, logrando apertura instantánea y reproducción fluida en cualquier visor.
 */
internal class HardwareMp4Transcoder(
    private val sourceWidth: Int,
    private val sourceHeight: Int,
    private val spsAnnexB: ByteArray,
    private val ppsAnnexB: ByteArray,
    private val muxer: MediaMuxer,
) {
    companion object {
        private const val TAG = "HwMp4Transcoder"
        private const val MIME_AVC = "video/avc"
        private const val EGL_RECORDABLE_ANDROID = 0x3142
        private const val CODEC_TIMEOUT_US = 5_000L
        private const val FRAME_WAIT_TIMEOUT_MS = 60L
        private const val FINISH_DRAIN_DEADLINE_MS = 900L

        private const val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uSTMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTextureCoord = (uSTMatrix * aTextureCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """

        private val QUAD_VERTICES = floatArrayOf(
            // X, Y, Z, U, V
            -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,
            1.0f, -1.0f, 0.0f, 1.0f, 0.0f,
            -1.0f, 1.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 0.0f, 1.0f, 1.0f,
        )
    }

    var encodedWidth: Int = sourceWidth
        private set
    var encodedHeight: Int = sourceHeight
        private set
    var encodedFramesCount: Long = 0L
        private set
    val isMuxerStarted: Boolean
        get() = muxerStarted

    private var encoder: MediaCodec? = null
    private var decoder: MediaCodec? = null
    private var encoderInputSurface: Surface? = null
    private var decoderSurface: Surface? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var frameThread: HandlerThread? = null

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var glProgram = 0
    private var oesTextureId = 0
    private var uMvpHandle = -1
    private var uStHandle = -1
    private var aPosHandle = -1
    private var aTexHandle = -1
    private val mvpMatrix = FloatArray(16)
    private val stMatrix = FloatArray(16)
    private lateinit var vertexBuffer: FloatBuffer

    private val frameSync = Object()
    @Volatile
    private var frameAvailable = false

    private var trackIndex = -1
    private var muxerStarted = false

    fun prepare(): Boolean {
        return try {
            val (encW, encH) = resolveAlignedEncoderSize(sourceWidth, sourceHeight)
            encodedWidth = encW
            encodedHeight = encH

            val bitrate = if (encW >= 1200) 4_800_000 else 2_400_000
            val encFormat = MediaFormat.createVideoFormat(MIME_AVC, encW, encH).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val enc = MediaCodec.createEncoderByType(MIME_AVC)
            enc.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurf = enc.createInputSurface()
            enc.start()
            encoder = enc
            encoderInputSurface = inputSurf

            setupEgl(inputSurf)
            setupGlRenderer()

            val thread = HandlerThread("BebopTranscodeFrame").apply { start() }
            frameThread = thread
            val tex = SurfaceTexture(oesTextureId).apply {
                setDefaultBufferSize(sourceWidth, sourceHeight)
                setOnFrameAvailableListener({
                    synchronized(frameSync) {
                        frameAvailable = true
                        frameSync.notifyAll()
                    }
                }, Handler(thread.looper))
            }
            surfaceTexture = tex
            val decSurf = Surface(tex)
            decoderSurface = decSurf

            val decFormat = MediaFormat.createVideoFormat(MIME_AVC, sourceWidth, sourceHeight).apply {
                setByteBuffer("csd-0", ByteBuffer.wrap(spsAnnexB))
                setByteBuffer("csd-1", ByteBuffer.wrap(ppsAnnexB))
            }
            val dec = MediaCodec.createDecoderByType(MIME_AVC)
            dec.configure(decFormat, decSurf, null, 0)
            dec.start()
            decoder = dec

            Log.i(TAG, "Transcodificador hardware listo (${sourceWidth}x${sourceHeight} -> ${encW}x${encH}, IDR cada 1s)")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "No se pudo inicializar transcodificador hardware; se usará remux directo", t)
            release()
            false
        }
    }

    fun feedFrame(annexB: ByteArray, size: Int, isKeyHint: Boolean, ptsUs: Long): Boolean {
        val dec = decoder ?: return false
        return try {
            val inIdx = dec.dequeueInputBuffer(CODEC_TIMEOUT_US)
            if (inIdx >= 0) {
                val buf = dec.getInputBuffer(inIdx)
                if (buf != null && buf.capacity() >= size) {
                    buf.clear()
                    buf.put(annexB, 0, size)
                    val flags = if (isKeyHint) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    dec.queueInputBuffer(inIdx, 0, size, ptsUs, flags)
                } else {
                    dec.queueInputBuffer(inIdx, 0, 0, ptsUs, 0)
                }
            }
            drainDecoderAndEncode(maxDrainMs = 80L)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Error durante transcodificación de frame", t)
            false
        }
    }

    fun finish(): Long {
        val dec = decoder
        val enc = encoder
        if (dec != null) {
            runCatching {
                val inIdx = dec.dequeueInputBuffer(20_000L)
                if (inIdx >= 0) {
                    dec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
                drainDecoderAndEncode(maxDrainMs = 300L)
            }
        }
        if (enc != null) {
            runCatching {
                enc.signalEndOfInputStream()
                drainEncoder(endOfStream = true, deadlineMs = SystemClock.elapsedRealtime() + FINISH_DRAIN_DEADLINE_MS)
            }
        }
        return encodedFramesCount
    }

    fun release() {
        runCatching { decoder?.stop() }
        runCatching { decoder?.release() }
        decoder = null

        runCatching { encoder?.stop() }
        runCatching { encoder?.release() }
        encoder = null

        runCatching { decoderSurface?.release() }
        decoderSurface = null

        runCatching { surfaceTexture?.setOnFrameAvailableListener(null) }
        runCatching { surfaceTexture?.release() }
        surfaceTexture = null

        runCatching { frameThread?.quitSafely() }
        frameThread = null

        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            runCatching {
                EGL14.eglMakeCurrent(
                    eglDisplay,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT,
                )
            }
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                runCatching { EGL14.eglDestroySurface(eglDisplay, eglSurface) }
                eglSurface = EGL14.EGL_NO_SURFACE
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                runCatching { EGL14.eglDestroyContext(eglDisplay, eglContext) }
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            runCatching { EGL14.eglReleaseThread() }
            runCatching { EGL14.eglTerminate(eglDisplay) }
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }

        runCatching { encoderInputSurface?.release() }
        encoderInputSurface = null
    }

    private fun drainDecoderAndEncode(maxDrainMs: Long) {
        val dec = decoder ?: return
        val info = MediaCodec.BufferInfo()
        val deadline = SystemClock.elapsedRealtime() + maxDrainMs

        while (SystemClock.elapsedRealtime() < deadline) {
            val outIdx = dec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)
            when {
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                outIdx < 0 -> Unit
                else -> {
                    val doRender = info.size > 0
                    val ptsUs = info.presentationTimeUs
                    val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

                    if (doRender) {
                        synchronized(frameSync) { frameAvailable = false }
                    }
                    dec.releaseOutputBuffer(outIdx, doRender)

                    if (doRender && awaitNewImage()) {
                        surfaceTexture?.updateTexImage()
                        surfaceTexture?.getTransformMatrix(stMatrix)
                        drawOesQuad()
                        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsUs * 1000L)
                        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                        drainEncoder(endOfStream = false, deadlineMs = SystemClock.elapsedRealtime() + 80L)
                    }
                    if (eos) break
                }
            }
        }
    }

    private fun drainEncoder(endOfStream: Boolean, deadlineMs: Long) {
        val enc = encoder ?: return
        val info = MediaCodec.BufferInfo()

        while (true) {
            val outIdx = enc.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)
            when {
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream || SystemClock.elapsedRealtime() >= deadlineMs) break
                }
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(enc.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }
                outIdx < 0 -> Unit
                else -> {
                    val encodedBuf = enc.getOutputBuffer(outIdx)
                    if (encodedBuf != null && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0 && muxerStarted) {
                        encodedBuf.position(info.offset)
                        encodedBuf.limit(info.offset + info.size)
                        muxer.writeSampleData(trackIndex, encodedBuf, info)
                        encodedFramesCount++
                    }
                    val isEos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    enc.releaseOutputBuffer(outIdx, false)
                    if (isEos) break
                }
            }
        }
    }

    private fun awaitNewImage(): Boolean {
        val deadline = SystemClock.elapsedRealtime() + FRAME_WAIT_TIMEOUT_MS
        synchronized(frameSync) {
            while (!frameAvailable) {
                val left = deadline - SystemClock.elapsedRealtime()
                if (left <= 0L) return false
                try {
                    frameSync.wait(left)
                } catch (_: InterruptedException) {
                    return false
                }
            }
            frameAvailable = false
            return true
        }
    }

    private fun setupEgl(inputSurface: Surface) {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        require(display != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay falló" }
        val version = IntArray(2)
        require(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize falló" }
        eglDisplay = display

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        require(EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfigs, 0) && numConfigs[0] > 0) {
            "eglChooseConfig falló"
        }
        val config = configs[0] ?: error("EGLConfig null")

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        val ctx = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        require(ctx != EGL14.EGL_NO_CONTEXT) { "eglCreateContext falló" }
        eglContext = ctx

        val surfAttribs = intArrayOf(EGL14.EGL_NONE)
        val surf = EGL14.eglCreateWindowSurface(display, config, inputSurface, surfAttribs, 0)
        require(surf != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface falló" }
        eglSurface = surf

        require(EGL14.eglMakeCurrent(display, surf, surf, ctx)) { "eglMakeCurrent falló" }
    }

    private fun setupGlRenderer() {
        vertexBuffer = ByteBuffer.allocateDirect(QUAD_VERTICES.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(QUAD_VERTICES)
                position(0)
            }

        val vShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vShader)
        GLES20.glAttachShader(prog, fShader)
        GLES20.glLinkProgram(prog)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
        require(linkStatus[0] == GLES20.GL_TRUE) { "Error enlazando shader GL" }
        glProgram = prog

        aPosHandle = GLES20.glGetAttribLocation(prog, "aPosition")
        aTexHandle = GLES20.glGetAttribLocation(prog, "aTextureCoord")
        uMvpHandle = GLES20.glGetUniformLocation(prog, "uMVPMatrix")
        uStHandle = GLES20.glGetUniformLocation(prog, "uSTMatrix")

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        oesTextureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        Matrix.setIdentityM(mvpMatrix, 0)
        Matrix.setIdentityM(stMatrix, 0)
    }

    private fun drawOesQuad() {
        GLES20.glViewport(0, 0, encodedWidth, encodedHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(glProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(aPosHandle, 3, GLES20.GL_FLOAT, false, 20, vertexBuffer)
        GLES20.glEnableVertexAttribArray(aPosHandle)

        vertexBuffer.position(3)
        GLES20.glVertexAttribPointer(aTexHandle, 2, GLES20.GL_FLOAT, false, 20, vertexBuffer)
        GLES20.glEnableVertexAttribArray(aTexHandle)

        GLES20.glUniformMatrix4fv(uMvpHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uStHandle, 1, false, stMatrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glFinish()
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        require(status[0] == GLES20.GL_TRUE) { "Error compilando shader GL: ${GLES20.glGetShaderInfoLog(shader)}" }
        return shader
    }

    private fun resolveAlignedEncoderSize(rawW: Int, rawH: Int): Pair<Int, Int> {
        val fallbackW = ((rawW + 15) / 16) * 16
        val fallbackH = ((rawH + 15) / 16) * 16
        return runCatching {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val caps = list.codecInfos
                .firstOrNull { it.isEncoder && it.supportedTypes.any { t -> t.equals(MIME_AVC, ignoreCase = true) } }
                ?.getCapabilitiesForType(MIME_AVC)
                ?.videoCapabilities
                ?: return fallbackW to fallbackH

            if (caps.isSizeSupported(rawW, rawH)) {
                return rawW to rawH
            }
            val alignW = caps.widthAlignment.coerceAtLeast(2)
            val alignH = caps.heightAlignment.coerceAtLeast(2)
            val alignedW = (((rawW.toFloat() / alignW).roundToInt()) * alignW)
                .coerceIn(caps.supportedWidths.lower, caps.supportedWidths.upper)
            val alignedH = (((rawH.toFloat() / alignH).roundToInt()) * alignH)
                .coerceIn(caps.supportedHeights.lower, caps.supportedHeights.upper)
            if (caps.isSizeSupported(alignedW, alignedH)) alignedW to alignedH else fallbackW to fallbackH
        }.getOrDefault(fallbackW to fallbackH)
    }
}
