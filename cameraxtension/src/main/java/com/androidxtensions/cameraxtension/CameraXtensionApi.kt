package com.androidxtensions.cameraxtension

import android.graphics.Bitmap
import android.util.Size
import androidx.annotation.WorkerThread
import com.androidxtensions.cameraxtension.gl.GlNativeBinding
import com.androidxtensions.cameraxtension.gl.VideoSaver
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Provides configuration options for saving video, transforming image output, and receiving images for analysis.
 */
class CameraConfiguration private constructor(
    internal val videoSaver: VideoSaver? = null,
    internal val previewTransformation: TransformationConfiguration,
    internal val videoTransformation: TransformationConfiguration,
    internal val analysisTransformation: TransformationConfiguration,
    internal val analyzer: ((image: AnalysisImage) -> Unit)? = null
) {
    class Builder {
        private var videoSaver: VideoSaver? = null
        private var previewTransformation = TransformationConfiguration.Builder().build()
        private var videoTransformation = TransformationConfiguration.Builder().build()
        private var analysisTransformation = TransformationConfiguration.Builder().build()
        private var analyzer: ((image: AnalysisImage) -> Unit)? = null

        /**
         * Enables video recording.
         *
         * @param saver VideoSaver for controlling when to start and finish recording.
         * @param transformation Transformations to apply to the images from the camera.
         */
        fun saveVideo(saver: VideoSaver, transformation: TransformationConfiguration = TransformationConfiguration.Builder().build()): Builder {
            videoSaver = saver
            videoTransformation = transformation
            return this
        }

        /**
         * Enables image analysis from the camera's preview.
         *
         * @param transformation Transformations to apply to the images from the camera.
         * @param callback The callback to receive the image for analysis.
         */
        fun imageAnalysis(
            transformation: TransformationConfiguration = TransformationConfiguration.Builder().build(),
            callback: (image: AnalysisImage) -> Unit
        ): Builder {
            analysisTransformation = transformation
            analyzer = callback
            return this
        }

        /**
         * Apply transformations to the visible camera preview.
         *
         * @param transformation Transformations to apply to the images from the camera.
         */
        fun transformPreview(transformation: TransformationConfiguration): Builder {
            previewTransformation = transformation
            return this
        }

        fun build(): CameraConfiguration =
            CameraConfiguration(
                videoSaver,
                previewTransformation,
                videoTransformation,
                analysisTransformation,
                analyzer
            )
    }
}

/**
 * Configuration for transformations that can be applied to the camera images.
 */
class TransformationConfiguration private constructor(
    internal val crop: Crop,
    internal val mirror: MirrorAxis,
    internal val edgeDetect: Boolean
) {
    class Builder {
        private var crop = Crop(0f, 0f, 1f, 1f)
        private var mirror = MirrorAxis.NONE
        private var edgeDetect = false

        /**
         * Crop the image with the supplied values.
         */
        fun applyCrop(crop: Crop): Builder {
            this.crop = crop
            return this
        }

        /**
         * Mirror the image across the provided axis.
         */
        fun applyMirror(mirror: MirrorAxis = MirrorAxis.HORIZONTALLY): Builder {
            this.mirror = mirror
            return this
        }

        /**
         * Enable edge detection on the image.
         */
        fun applyEdgeDetection(edgeDetect: Boolean = true): Builder {
            this.edgeDetect = edgeDetect
            return this
        }

        fun build() = TransformationConfiguration(crop, mirror, edgeDetect)
    }
}

/**
 * Rectangular crop, with origin at top left.  All values are percentages rather than pixels since
 * the underlying size may vary at runtime.
 *
 * Examples:
 *
 *   val fullSize = Crop(0f, 0f, 1f, 1f)
 *
 *   val topHalf = Crop(0f, 0f, 1f, 0.5f)
 *
 *   val rightHalf = Crop(0.5f, 0f, 0.5f, 1f)
 *
 * @param x X coordinate of top left corner of the crop.
 * @param y Y coordinate of top left corner of the crop.
 * @param width Width of the crop.
 * @param height Height of the crop.
 */
data class Crop(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    internal fun cropSize(baseSize: Size) = Size(
        (baseSize.width * width).toInt(),
        (baseSize.height * height).toInt()
    )
}

/**
 * Apply a mirror transformation to the image.
 */
enum class MirrorAxis {
    /**
     * No transformation.
     */
    NONE,

    /**
     * Mirror across the horizontal axis (left <-> right).
     */
    HORIZONTALLY,

    /**
     * Mirror across the vertical axis (top <-> bottom).
     */
    VERTICALLY,

    /**
     * Mirror across both axis.
     */
    BOTH,
}

/**
 * Provides access to the image from the camera preview.
 *
 * NOTE:
 * * Only one AnalysisImage can be open at a time.  To receive a new image for analysis, the old
 * AnalysisImage must be closed.  User either `analysisImage.use { ... }` or explicitly call
 * `analysisImage.close()`.  Once closed, `data` will be overwritten.  If you need to persist the data,
 * make sure to do a deep copy.
 *
 * * Image processing is very resource expensive, and AnalysisImage does not provide any concurrency.
 * Make sure to move all image processing to the background.
 *
 * @property width The width of the image in pixels.
 * @property height The height of the image in pixels.
 * @property data The interleaved RGBA formatted image.
 */
class AnalysisImage internal constructor(
    val width: Int,
    val height: Int,
    val data: ByteArray,
    internal val closedCallback: () -> Unit
) : AutoCloseable {

    /**
     * Close the AnalysisImage, allowing for new images to be produced.  Will overwrite `data`.
     */
    override fun close() {
        closedCallback()
    }

    /**
     * Converts the RGBA data into a Bitmap. NOTE: This is an expensive operation and should only be
     * done in the background.
     */
    @WorkerThread
    fun toBitmap(): Bitmap {
        val pixels = IntArray(data.size / 4)
        GlNativeBinding.rgbaToPackedArgb(width, height, data, pixels)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    /**
     * Converts the RGBA data into an encoded JPG. NOTE: This is an expensive operation and should only be
     * done in the background.
     */
    @WorkerThread
    fun toJpg(quality: Int = 100): ByteArray {
        val outputStream = ByteArrayOutputStream()
        toBitmap().compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return outputStream.toByteArray()
    }
}

/**
 * Configuration for recording video.
 */
class RecordConfiguration private constructor(
    internal val outputFile: File,
    internal val videoConfiguration: VideoConfiguration,
    internal val audioConfiguration: AudioConfiguration? = null
) {

    /**
     * @param outputFile The file to save the video into.
     * @param videoConfiguration The [VideoConfiguration] to use when recording.
     */
    class Builder(
        private val outputFile: File,
        private val videoConfiguration: VideoConfiguration = VideoConfiguration.default()
    ) {
        private var audioConfiguration: AudioConfiguration? = null

        /**
         * Enables audio for the video.  NOTE: Audio permission must be requested independently.
         *
         * @param configuration The [AudioConfiguration] to use when recording.
         */
        fun withAudio(configuration: AudioConfiguration? = AudioConfiguration.default()): Builder {
            audioConfiguration = configuration
            return this
        }

        fun build() = RecordConfiguration(outputFile, videoConfiguration, audioConfiguration)
    }
}

/**
 * Configuration for recording video.
 *
 * @param width The width of the video in pixels, or [MATCH_WIDTH] to match the camera's width.
 * @param height The height of the video in pixels, or [MATCH_HEIGHT] to match the camera's height.
 * @param bitrate The bitrate of the video in bits/sec.
 */
data class VideoConfiguration(
    val width: Int,
    val height: Int,
    val bitrate: Int
) {
    companion object {
        /**
         * Match the width of the video.
         */
        const val MATCH_WIDTH = -1

        /**
         * Match the height of the video.
         */
        const val MATCH_HEIGHT = -1

        /**
         * Get the default SD video recording configuration.
         */
        // see https://developer.android.com/guide/topics/media/media-formats#video-encoding
        fun default() = VideoConfiguration(480, 360, 500_000)
    }
}

/**
 * Configuration for recording audio.
 *
 * @param sampleRate The audio sample rate in kHz.
 * @param channels The number of audio channels.
 * @param bitrate The audio bitrate in bits/sec.
 */
data class AudioConfiguration(
    val sampleRate: Int,
    val channels: AudioChannels,
    val bitrate: Int
) {
    companion object {

        /**
         * Get the default audio configuration.
         */
        // see https://developer.android.com/ndk/guides/audio/sampling-audio
        // see https://developer.android.com/guide/topics/media/media-formats#video-encoding
        fun default() = AudioConfiguration(44_100, AudioChannels.STEREO, 128_000)
    }
}

/**
 * Determines how many audio channels to use.
 */
enum class AudioChannels {

    /**
     * One audio channel.
     */
    MONO,

    /**
     * Two audio channels.
     */
    STEREO;

    internal fun channelCount(): Int =
        when (this) {
            MONO -> 1
            STEREO -> 2
        }
}