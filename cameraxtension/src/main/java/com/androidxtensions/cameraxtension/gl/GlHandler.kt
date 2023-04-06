package com.androidxtensions.cameraxtension.gl

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Size
import android.view.Surface
import androidx.camera.core.Preview.SurfaceProvider
import com.androidxtensions.cameraxtension.*
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.util.concurrent.Executors

/**
 * Coordinates the data coming in from the camera to its various outputs.  Owns the thread that OpenGL
 * will be run on.
 */
internal class GlHandler(
    private val normalizePreview: Boolean,
    private val eglDispatcher: ExecutorCoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    ) {

    private val callbackHandler = CallbackHandler()
    private val tmpMatrix = FloatArray(16)
    private val previewRotationMatrix = FloatArray(16)
    private var eglApi: EglApi? = null
    private var offScreenSurface: EGLSurface? = null
    private var displaySurface: BoundEglSurface? = null
    private var previewTextureWriter: TextureWriter? = null
    private var textureId: Int = -1
    private var cameraTexture: SurfaceTexture? = null
    private var cameraOrientation = 0
    private var analysisHandler: ImageAnalysisHandler? = null
    private var videoRecordingHandler: VideoRecordingHandler? = null
    private var pendingRecordConfiguration: RecordConfiguration? = null
    private var released = false

    init {
        MainScope().launch(eglDispatcher) {
            eglApi = EglApi().also { core ->
                // make a temporary surface just so that there is a current context, otherwise OpenGL APIs aren't usable
                offScreenSurface = core.createPbufferSurface(1, 1).also {
                    core.makeCurrent(it)
                }
            }
        }
    }

    @SuppressLint("Recycle")
    suspend fun getSurfaceProvider(baseSize: Size, configuration: CameraConfiguration, rotation: Int, cameraOrientation: Int): SurfaceProvider =
        withContext(eglDispatcher) {
            // Create texture writer for writing to surfaces, get a shared texture id from it that all
            // draw calls will use
            previewTextureWriter = TextureWriter(
                rotation,
                configuration.previewTransformation
            ).also {
                textureId = it.createTexture()
            }

            // If camera is rotated +/- 90 degrees, swap the width and height
            val adjustedBase = if (rotation == 90 || rotation == 270) {
                Size(baseSize.height, baseSize.width)
            } else {
                baseSize
            }

            eglApi?.let { core ->
                if (configuration.analyzer != null) {
                    analysisHandler = ImageAnalysisHandler(
                        core,
                        configuration,
                        adjustedBase,
                        rotation,
                        cameraOrientation
                    )
                }
                videoRecordingHandler = VideoRecordingHandler(
                    core,
                    configuration,
                    adjustedBase,
                    rotation,
                    cameraOrientation
                )
            }

            this@GlHandler.cameraOrientation = cameraOrientation
            setRotation(rotation)

            val croppedDisplaySize = configuration.previewTransformation.crop.cropSize(baseSize)

            // If recording was requested before GLHandler was ready, start it now
            pendingRecordConfiguration?.let {
                startRecording(it)
                pendingRecordConfiguration = null
            }

            // Create SurfaceTexture that will be passed to camera as the input for everything
            SurfaceTexture(textureId).let {
                it.setOnFrameAvailableListener(callbackHandler)
                it.setDefaultBufferSize(croppedDisplaySize.width, croppedDisplaySize.height)
                cameraTexture = it

                SurfaceProvider { request ->
                    val stubSurface = Surface(it)
                    request.provideSurface(stubSurface, eglDispatcher.executor) {
                        stubSurface.release()
                        release()
                    }
                }
            }
        }

    /**
     * Release the GlHandler and all of its underlying resources.  This is a destructive operation,
     * the GLHandler instance will no longer be usable afterwards.
     */
    fun release() {
        MainScope().launch(eglDispatcher) {
            if (!released) {
                released = true
                offScreenSurface?.let {
                    eglApi?.destroySurface(it)
                }
                offScreenSurface = null
                analysisHandler?.release()
                analysisHandler = null
                videoRecordingHandler?.release()
                videoRecordingHandler = null
                previewTextureWriter?.release()
                previewTextureWriter = null
                cameraTexture?.release()
                cameraTexture = null
                eglApi?.release()
                eglApi = null
            }
        }
    }


    /**
     * Provide a surface for rendering the preview on.
     */
    fun bindSurface(surface: Surface) {
        MainScope().launch(eglDispatcher) {
            eglApi?.let {
                if (!it.released) {
                    displaySurface = BoundEglSurface(it, surface, false)
                }
            }
        }
    }


    /**
     * Update the rotation calculation in case that device is rotated 180 which does not trigger recreate.
     */
    fun updateRotation(rotation: Int) {
        MainScope().launch(eglDispatcher) {
            setRotation(rotation)
            videoRecordingHandler?.updateRotation(rotation)
            analysisHandler?.updateRotation(rotation)
        }
    }


    /**
     * Begin recording if configured, or sets recording pending if GlHandler is not ready yet.
     */
    fun startRecording(recordConfiguration: RecordConfiguration) {
        videoRecordingHandler?.startRecording(
            eglDispatcher,
            recordConfiguration
        ) ?: run { pendingRecordConfiguration = recordConfiguration }
    }

    /**
     * Stops any active recording and cancels any pending recordings.
     */
    suspend fun stopRecording() {
        withContext(eglDispatcher) {
            pendingRecordConfiguration = null
        }
        videoRecordingHandler?.stopRecording(eglDispatcher)
    }

    private fun setRotation(rotation: Int) {
        Matrix.setIdentityM(previewRotationMatrix, 0)
        if (normalizePreview) {
            Matrix.rotateM(previewRotationMatrix, 0, (rotation - cameraOrientation).toFloat(), 0f, 0f, 1f)
        }
    }

    private inner class CallbackHandler : SurfaceTexture.OnFrameAvailableListener {

        // Callback whenever the camera posts a new frame
        override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
            MainScope().launch(eglDispatcher) {
                // Draw the frame on the visible preview
                displaySurface?.let { previewSurface ->
                    previewSurface.makeCurrent()
                    cameraTexture?.updateTexImage()
                    cameraTexture?.getTransformMatrix(tmpMatrix)

                    Gles20Api.glViewport(0, 0, previewSurface.width, previewSurface.height)

                    previewTextureWriter?.draw(textureId, tmpMatrix, previewRotationMatrix)
                    previewSurface.swapBuffers()
                }

                // Write the frame to video
                videoRecordingHandler?.recordVideoFrame(textureId, tmpMatrix)

                // Pass the frame on for analysis
                analysisHandler?.generateAnalysisImage(textureId, tmpMatrix)
            }
        }
    }
}

/**
 * Reads image from OpenGL to android and controls its lifecycle.
 */
internal class ImageAnalysisHandler(
    private val eglApi: EglApi,
    configuration: CameraConfiguration,
    adjustedBase: Size,
    rotation: Int,
    private val cameraOrientation: Int
    ) {

    private val analysisRotationMatrix = FloatArray(16)
    private var analysisTextureWriter: TextureWriter
    private var offScreenAnalysisSurface: EGLSurface
    private val analysisSize: Size
    private var analyzer: ((image: AnalysisImage) -> Unit)? = null
    private var analysisRunning = false
    private val pixelReader: GlPixelReader

    init {
        analysisTextureWriter = TextureWriter(
            rotation,
            configuration.analysisTransformation
        )

        val croppedAnalysisSize = configuration.analysisTransformation.crop.cropSize(adjustedBase)
        analysisSize = croppedAnalysisSize
        offScreenAnalysisSurface =
            eglApi.createPbufferSurface(croppedAnalysisSize.width, croppedAnalysisSize.height)
        analyzer = configuration.analyzer
        // If GL3 available use faster double buffer method, otherwise slow direct reads
        pixelReader = if (eglApi.glVersion == 3) {
            PboPixelReader(analysisSize)
        } else {
            DirectPixelReader(analysisSize)
        }

        updateRotation(rotation)
    }

    /**
     * Gets the RGBA pixels from OpenGL and provides them for analysis.
     */
    fun generateAnalysisImage(textureId: Int, tmpMatrix: FloatArray) {
        // Only provide new image when old one is done
        if (!analysisRunning) {
            analyzer?.let { analysis ->
                analysisRunning = true
                eglApi.makeCurrent(offScreenAnalysisSurface)
                Gles20Api.glViewport(0, 0, analysisSize.width, analysisSize.height)
                analysisTextureWriter.draw(textureId, tmpMatrix, analysisRotationMatrix)
                pixelReader.getPixelData(textureId, tmpMatrix)?.let { pixelData ->
                    analysis(
                        AnalysisImage(analysisSize.width, analysisSize.height, pixelData.array()) {
                            analysisRunning = false
                            pixelData.clear()
                        }
                    )
                } ?: run { analysisRunning = false }
            }
        }
    }

    /**
     * Update rotation calculations in case of 180 device rotation.
     */
    fun updateRotation(rotation: Int) {
        Matrix.setIdentityM(analysisRotationMatrix, 0)
        Matrix.rotateM(analysisRotationMatrix, 0, (cameraOrientation - rotation).toFloat(), 0f, 0f, 1f)
        Matrix.scaleM(analysisRotationMatrix, 0, 1f, -1f, 1f)
    }

    /**
     * Destroy the surface and shader program.  This is a destructive operation, and the instance
     * will no longer be usable afterwards.
     */
    fun release() {
        eglApi.destroySurface(offScreenAnalysisSurface)
        analysisTextureWriter.release()
    }
}

private interface GlPixelReader {

    fun getPixelData(textureId: Int, tmpMatrix: FloatArray): ByteBuffer?
}

/**
 * Double buffer implementation for reading images.
 */
private class PboPixelReader(
    private val size: Size
) : GlPixelReader {

    private val bufferIds = IntBuffer.allocate(2)
    private val bufferSize = size.width * size.height * 4
    private var currentBufferIndex = 0
    private var bufferReady = false
    private var analysisBuffer: ByteBuffer =
        BufferFactory.allocateBuffer(bufferSize).also { buffer ->
            buffer.order(ByteOrder.nativeOrder())
        }

    init {
        Gles30Api.glGenBuffers(bufferIds.capacity(), bufferIds)

        for (i in 0 until bufferIds.capacity()) {
            Gles30Api.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, bufferIds[i])
            Gles30Api.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER, bufferSize, null, GLES30.GL_STATIC_READ)
        }

        Gles30Api.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, GLES30.GL_NONE)
    }

    /**
     * Gets the image data from OpenGL.  Uses two buffers to asynchronously read the data, every call
     * starts loading data into one buffer while returning the data from the previous asynchronous read.
     * As such, the first call returns null since there is no pre-read buffer to fall back on.
     */
    override fun getPixelData(textureId: Int, tmpMatrix: FloatArray): ByteBuffer? {
        Gles30Api.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, bufferIds[currentBufferIndex])
        Gles30Api.readPixelsToBuffer(0, 0, size.width, size.height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE)
        currentBufferIndex = nextIndex()

        return if (bufferReady) {
            Gles30Api.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, bufferIds[currentBufferIndex])
            val imageBuffer = (Gles30Api.glMapBufferRange(GLES30.GL_PIXEL_PACK_BUFFER, 0, bufferSize, GLES30.GL_MAP_READ_BIT) as ByteBuffer)
            imageBuffer.order(ByteOrder.nativeOrder())
            analysisBuffer.put(imageBuffer)
            cleanupBuffers()
            return analysisBuffer
        } else {
            cleanupBuffers()
            bufferReady = true
            null
        }
    }

    private fun nextIndex() = (currentBufferIndex + 1) % bufferIds.capacity()

    private fun cleanupBuffers() {
        Gles30Api.glUnmapBuffer(GLES30.GL_PIXEL_PACK_BUFFER)
        Gles30Api.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, GLES30.GL_NONE)
    }
}

/**
 * Direct implementation for reading images.
 */
private class DirectPixelReader(
    private val size: Size
) : GlPixelReader {

    private var analysisBuffer: ByteBuffer =
        BufferFactory.allocateBuffer(4 * size.width * size.height).also { buffer ->
            buffer.order(ByteOrder.nativeOrder())
        }

    /**
     * Directly read the image data in a slow blocking call.
     */
    override fun getPixelData(textureId: Int, tmpMatrix: FloatArray): ByteBuffer {
        Gles20Api.glPixelStorei(GLES20.GL_PACK_ALIGNMENT, 1)
        Gles20Api.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        Gles20Api.glReadPixels(0, 0, size.width, size.height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, analysisBuffer)
        return analysisBuffer
    }
}

/**
 * ByteBuffer instantiation is difficult to mock, so wrap it in a kotlin object.
 */
internal object BufferFactory {

    fun allocateBuffer(bufferSize: Int): ByteBuffer = ByteBuffer.allocateDirect(bufferSize)
}

/**
 * Owns lifecycle and state for writing to video.
 */
internal class VideoRecordingHandler(
    private val eglApi: EglApi,
    configuration: CameraConfiguration,
    adjustedBase: Size,
    rotation: Int,
    private val cameraOrientation: Int
) {
    private val videoRotationMatrix = FloatArray(16)
    private var videoTextureWriter: TextureWriter
    private var videoSize: Size

    private var videoConfiguration: VideoConfiguration? = null
    private var videoRecordSurface: BoundEglSurface? = null
    private var videoRecorder: VideoRecorder? = null

    init {
        videoTextureWriter = TextureWriter(rotation, configuration.videoTransformation)

        updateRotation(rotation)
        videoSize = configuration.videoTransformation.crop.cropSize(adjustedBase)
    }

    /**
     * Begins recording a new video file with the specified configuration.
     */
    fun startRecording(eglDispatcher: CoroutineDispatcher, recordConfiguration: RecordConfiguration) =
        MainScope().launch(eglDispatcher) {
            // If the video size should match the camera output size, update its dimensions
            var videoConfiguration = recordConfiguration.videoConfiguration
            if (videoConfiguration.width == VideoConfiguration.MATCH_WIDTH) {
                videoConfiguration = videoConfiguration.copy(width = videoSize.width)
            }
            if (videoConfiguration.height == VideoConfiguration.MATCH_HEIGHT) {
                videoConfiguration = videoConfiguration.copy(height = videoSize.height)
            }
            this@VideoRecordingHandler.videoConfiguration = videoConfiguration
            videoRecorder = VideoRecorder.getVideoRecorder(
                videoConfiguration,
                recordConfiguration.audioConfiguration,
                recordConfiguration.outputFile
            ).also {
                if (!eglApi.released) {
                    videoRecordSurface = BoundEglSurface(eglApi, it.inputSurface, true)
                    it.start()
                }
            }
        }

    /**
     * Stops recording the video.
     */
    suspend fun stopRecording(eglDispatcher: CoroutineDispatcher) {
        withContext(eglDispatcher) {
            videoConfiguration = null
        }
        videoRecorder?.release()
        withContext(eglDispatcher) {
            videoRecordSurface?.release()
            videoRecordSurface = null
            videoRecorder = null
        }
    }

    /**
     * Writes a camera frame to the video.
     */
    fun recordVideoFrame(textureId: Int, tmpMatrix: FloatArray) {
        if (videoRecorder?.running == true) {
            videoRecordSurface?.let { videoSurface ->
                videoConfiguration?.let { configuration ->
                    videoSurface.makeCurrent()
                    Gles20Api.glViewport(0, 0, configuration.width, configuration.height)
                    videoTextureWriter.draw(textureId, tmpMatrix, videoRotationMatrix)
                    videoSurface.swapBuffers()
                }

            }
        }
    }

    /**
     * Updates the rotation calculations in case of 180 degree device rotation.
     */
    fun updateRotation(rotation: Int) {
        Matrix.setIdentityM(videoRotationMatrix, 0)
        Matrix.rotateM(videoRotationMatrix, 0, (rotation - cameraOrientation).toFloat(), 0f, 0f, 1f)
    }

    /**
     * Releases the VideoRecordingHandler and its underlying resources.  This is a destructive operation,
     * the instance will not be usable afterwards.
     */
    fun release(dispatcher: CoroutineDispatcher = Dispatchers.IO) {
        MainScope().launch(dispatcher) {
            videoRecorder?.release()
        }
        videoTextureWriter.release()
    }
}

/**
 * Provides control for starting and finishing video recording.
 *
 * @sample com.androidxtensions.samples.cameraxtension.CameraSamples.recordVideo
 */
class VideoSaver {
    private var glHandler: GlHandler? = null
    private var pendingConfiguration: RecordConfiguration? = null
    @Volatile
    private var state = State.STOPPED

    internal fun setGlHandler(handler: GlHandler) {
        glHandler = handler
        // If startRecording was called before GlHandler was set, start now
        if (state == State.STARTING) {
            pendingConfiguration?.let {
                startRecording(it)
                pendingConfiguration = null
            }
        }
    }

    /**
     * Starts recording a video based on the provided configuration.
     */
    fun startRecording(recordConfiguration: RecordConfiguration) {

        when (state) {
            State.STOPPED, State.STARTING -> {
                // Either start recording now, or set recording pending
                glHandler?.let {
                    state = State.RUNNING
                    it.startRecording(recordConfiguration)
                } ?: run {
                    state = State.STARTING
                    pendingConfiguration = recordConfiguration
                }
            }

            // If already stopping, let it finish writing the old video before starting a new one
            State.STOPPING -> {
                pendingConfiguration = recordConfiguration
            }

            // Already running, do nothing
            State.RUNNING -> {}
        }
    }

    /**
     * Stops recording and finishes the video file.
     */
    fun stopRecording(dispatcher: CoroutineDispatcher = Dispatchers.IO) {

        when (state) {
            // Already stopping, do nothing
            State.STOPPED, State.STOPPING -> {}

            // Cancel pending recording
            State.STARTING -> {
                pendingConfiguration = null
                state = State.STOPPED
            }

            State.RUNNING -> {
                state = State.STOPPING
                // Finishing the video file takes some time, need to run in a coroutine
                MainScope().launch(dispatcher) {
                    glHandler?.stopRecording()
                    state = State.STOPPED
                    // If there is a pending recording start that one
                    pendingConfiguration?.let {
                        startRecording(it)
                        pendingConfiguration = null
                    }
                }
            }
        }
    }

    private enum class State {
        STOPPED,
        STARTING,
        RUNNING,
        STOPPING
    }
}