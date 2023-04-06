package com.androidxtensions.cameraxtension.gl

import android.annotation.SuppressLint
import android.media.*
import android.media.MediaCodec.BufferInfo
import android.os.Bundle
import android.util.Log
import android.view.Surface
import com.androidxtensions.cameraxtension.AudioConfiguration
import com.androidxtensions.cameraxtension.VideoConfiguration
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages the lifecycle of the video and audio recording classes.
 */
internal class VideoRecorder constructor(
    private val muxerWrapper: MuxerWrapper,
    private val videoWrapper: VideoWrapper,
    private val audioWrapper: AudioWrapper?
    ) {

    // Video input is through the surface
    val inputSurface = videoWrapper.inputSurface
    private var job: Job? = null
    private val writing = AtomicBoolean(false)
    @Volatile
    var running = false
        private set

    fun start(dispatcher: CoroutineDispatcher = Dispatchers.IO) {
        job = MainScope().launch(dispatcher) {
            writing.set(true)
            launch {
                try {
                    while (writing.get()) {
                        videoWrapper.writeVideo(writing)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            audioWrapper?.let {
                launch {
                    try {
                        while (writing.get()) {
                            it.writeAudio()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        running = true
    }

    /**
     * Releases the VideoRecorder and its underlying resources.  This is a destructive operation, the instance
     * will not be usable afterwards.
     */
    suspend fun release() {
        running = false
        writing.set(false)
        job?.join()
        videoWrapper.release()
        audioWrapper?.release()
        muxerWrapper.release()
    }

    companion object {
        fun getVideoRecorder(videoConfiguration: VideoConfiguration,
                             audioConfiguration: AudioConfiguration?,
                             output: File): VideoRecorder {
            val muxerWrapper = MuxerWrapper(output, audioConfiguration != null)
            val videoWrapper = VideoWrapper(
                videoConfiguration.width,
                videoConfiguration.height,
                videoConfiguration.bitrate,
                muxerWrapper)
            val audioWrapper = audioConfiguration?.let {
                AudioWrapper(it.sampleRate, it.channels.channelCount(), it.bitrate, muxerWrapper)
            }
            return VideoRecorder(muxerWrapper, videoWrapper, audioWrapper)
        }
    }
}

/**
 * Handles reading raw video input and encoding it into the video.
 */
internal class VideoWrapper(
    width: Int,
    height: Int,
    bitrate: Int,
    private val muxerWrapper: MuxerWrapper,
    private val encoder: MediaCodecWrapper
) {

    constructor(
        width: Int,
        height: Int,
        bitrate: Int,
        muxerWrapper: MuxerWrapper
    ) : this(width, height, bitrate, muxerWrapper, MediaCodecWrapper(MediaCodec.createEncoderByType(VIDEO_MIME_TYPE)))

    val inputSurface: Surface
    private val bufferInfo = BufferInfo()
    private var released = false

    init {
        val format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, width, height)

        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)

        encoder.also {
            it.configure(format, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = it.createInputSurface()
            it.start()
        }
    }

    /**
     * Writes a video frame if available.
     */
    suspend fun writeVideo(writing: AtomicBoolean) {
        val bufferId = encoder.dequeueOutputBuffer(bufferInfo, VIDEO_DEQUE_TIMEOUT)
        when {
            bufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                if (muxerWrapper.running) {
                    writing.set(false)
                } else {
                    muxerWrapper.addVideoTrack(encoder.outputFormat)
                }
            }

            // No data, just skip it
            bufferId < 0 -> {
                return
            }

            else -> {
                encoder.getOutputBuffer(bufferId)?.let {
                    if (muxerWrapper.running && bufferInfo.size > 0) {
                        it.position(bufferInfo.offset)
                        it.limit(bufferInfo.offset + bufferInfo.size)
                        bufferInfo.presentationTimeUs = (System.nanoTime() / 1000)

                        // If the video encoder starts before the muxer is ready
                        // the initial sync/key frames can get lost
                        // force a new sync/key frame to be generated once the muxer is ready
                        if (!encoder.hasSetInitialKeyFrame) {
                            encoder.setParameters(
                                Bundle().apply {
                                    putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                                }
                            )
                            encoder.hasSetInitialKeyFrame = true
                        }
                        muxerWrapper.writeVideo(it, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(bufferId, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        writing.set(false)
                    }
                }
            }
        }
    }

    /**
     * Releases the VideoWrapper and its resources.  This is a destructive operation, the instance
     * will not be usable afterwards.
     */
    fun release() {
        if (!released) {
            released = true

            try {
                encoder.apply {
                    stop()
                    release()
                }
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }
    }

    private companion object {
        const val VIDEO_MIME_TYPE = "video/avc"
        const val FRAME_RATE = 30
        const val IFRAME_INTERVAL = 5
        const val VIDEO_DEQUE_TIMEOUT = 10_000L
    }
}

/**
 * Handles reading raw audio input and encoding it into the video.
 */
@SuppressLint("MissingPermission")
internal class AudioWrapper(
    audioSampleRate: Int,
    audioChannelCount: Int,
    audioBitrate: Int,
    private val muxerWrapper: MuxerWrapper,
    private var recorder: AudioRecord? = null,
    private val encoder: MediaCodecWrapper
) {

    constructor(
        audioSampleRate: Int,
        audioChannelCount: Int,
        audioBitrate: Int,
        muxerWrapper: MuxerWrapper,
        recorder: AudioRecord? = null
    ) : this(audioSampleRate, audioChannelCount, audioBitrate, muxerWrapper, recorder, MediaCodecWrapper(MediaCodec.createEncoderByType(AUDIO_MIME_TYPE)))
    private val bufferInfo = BufferInfo()
    private var bufferSize = -1
    private var released = false

    init {
        val format = MediaFormat.createAudioFormat(
            AUDIO_MIME_TYPE, audioSampleRate,
            audioChannelCount
        )
        format.setInteger(
            MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, audioBitrate)
        encoder.also {
            it.configure(format, MediaCodec.CONFIGURE_FLAG_ENCODE)
            it.start()
        }



        val channelConfig = if (audioChannelCount == 1) {
            AudioFormat.CHANNEL_IN_MONO
        }  else {
            AudioFormat.CHANNEL_IN_STEREO
        }

        try {
            val bufferSize = AudioRecord.getMinBufferSize(audioSampleRate, channelConfig, AUDIO_ENCODING)

            if (recorder == null) {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    audioSampleRate,
                    channelConfig,
                    AUDIO_ENCODING,
                    bufferSize * 2
                ).also {
                    if (it.state == AudioRecord.STATE_INITIALIZED) {
                        this.bufferSize = bufferSize
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Reads from the audio buffer and writes it to the video.
     */
    suspend fun writeAudio() {
        recorder?.let { audio ->
            if (audio.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                audio.startRecording()
            }

            var audioBuffered = true

            // Read from audio buffer
            val index = encoder.dequeueInputBuffer(-1)
            if (index >= 0) {
                encoder.getInputBuffer(index)?.let { buffer ->
                    val audioLength = audio.read(buffer, bufferSize)
                    if (audioLength > 0) {
                        encoder.queueInputBuffer(index, 0, audioLength, (System.nanoTime() / 1000),0)
                        audioBuffered = true
                    }
                }
            }

            while (audioBuffered) {
                val bufferId = encoder.dequeueOutputBuffer(bufferInfo, 0)
                when {
                    bufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        muxerWrapper.addAudioTrack(encoder.outputFormat)
                        audioBuffered = false
                    }

                    // no more data
                    bufferId < 0 -> {
                        audioBuffered = false
                    }

                    else -> {
                        encoder.getOutputBuffer(bufferId)?.let {
                            if (muxerWrapper.running && bufferInfo.size > 0 && bufferInfo.presentationTimeUs > 0) {
                                it.position(bufferInfo.offset)
                                muxerWrapper.writeAudio(it, bufferInfo)
                            }

                            encoder.releaseOutputBuffer(bufferId, false)

                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0)) {
                                audioBuffered = false
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Releases the AudioWrapper and its resources.  This is a destructive operation, the instance
     * will not be usable afterwards.
     */
    fun release() {
        if (!released) {
            released = true

            try {
                encoder.apply {
                    stop()
                    release()
                }
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }

            try {
                recorder?.apply {
                    stop()
                    release()
                }
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }

        }
    }

    private companion object {
        const val AUDIO_MIME_TYPE = "audio/mp4a-latm"

        const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}

/**
 * Manages lifecycle and multithreaded access to the muxer.
 */
internal class MuxerWrapper(
    private val muxer: MediaMuxer,
    private val audioEnabled: Boolean
) {
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    var running = false
        private set
    private val mutex = Mutex()

    constructor(
        output: File,
        audioEnabled: Boolean
    ) : this(MediaMuxer(output.toString(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4), audioEnabled)

    suspend fun addVideoTrack(format: MediaFormat) =
        mutex.withLock {
            videoTrackIndex = muxer.addTrack(format)
            startMuxerIfReady()
        }

    suspend fun addAudioTrack(format: MediaFormat) =
        mutex.withLock {
            audioTrackIndex = muxer.addTrack(format)
            startMuxerIfReady()
        }

    suspend fun writeVideo(data: ByteBuffer, info: BufferInfo) =
        write(videoTrackIndex, data, info)

    suspend fun writeAudio(data: ByteBuffer, info: BufferInfo) =
        write(audioTrackIndex, data, info)

    private suspend fun write(trackIndex: Int, data: ByteBuffer, info: BufferInfo) =
        mutex.withLock {
            try {
                muxer.writeSampleData(trackIndex, data, info)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    suspend fun release() =
        mutex.withLock {
            if (running) {
                try {
                    muxer.stop()
                    muxer.release()
                    running = false
                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                }
            }
        }

    // Can only start the muxer if all tracks are set
    private fun startMuxerIfReady() {
        if (videoTrackIndex >= 0 && (!audioEnabled || audioTrackIndex >= 0)) {
            muxer.start()
            running = true
        }
    }
}

/**
 * MediaCodec exposes several native methods that can't be mocked, so we wrap it in a kotlin class.
 */
internal class MediaCodecWrapper(private val mediaCodec: MediaCodec) {

    var hasSetInitialKeyFrame = false

    val outputFormat
        get() = mediaCodec.outputFormat

    fun setParameters(params: Bundle) = mediaCodec.setParameters(params)

    fun configure(format: MediaFormat, flags: Int) = mediaCodec.configure(format, null, null, flags)

    fun createInputSurface() = mediaCodec.createInputSurface()

    fun getInputBuffer(index: Int) = mediaCodec.getInputBuffer(index)

    fun queueInputBuffer(index: Int, offset: Int, size: Int, presentationTimeUs: Long, flags: Int) = mediaCodec.queueInputBuffer(index, offset, size, presentationTimeUs, flags)

    fun dequeueInputBuffer(timeoutUs: Long) = mediaCodec.dequeueInputBuffer(timeoutUs)

    fun getOutputBuffer(index: Int) = mediaCodec.getOutputBuffer(index)

    fun dequeueOutputBuffer(info: BufferInfo, timeoutUs: Long) = mediaCodec.dequeueOutputBuffer(info, timeoutUs)

    fun releaseOutputBuffer(index: Int, render: Boolean) = mediaCodec.releaseOutputBuffer(index, render)

    fun start() = mediaCodec.start()

    fun stop() = mediaCodec.stop()

    fun release() = mediaCodec.release()
}