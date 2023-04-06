package com.androidxtensions.cameraxtension.gl

import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaMuxer
import com.androidxtensions.cameraxtension.VideoConfiguration
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VideoRecorderTest {

    @After
    fun cleanup() {
        clearAllMocks()
        unmockkAll()
    }

    // region VideoRecorder

    @Test
    fun `when VideoRecorder started with video only begins writing to video`() = runTest {
        val mockVideoWrapper = mockk<VideoWrapper>()
        every {
            mockVideoWrapper.inputSurface
        } returns mockk()
        val writing = slot<AtomicBoolean>()
        coEvery {
            mockVideoWrapper.writeVideo(capture(writing))
        } answers {
            writing.captured.set(false)
        }
        val videoRecorder = VideoRecorder(mockk(), mockVideoWrapper, null)
        videoRecorder.start(StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        Assert.assertTrue(videoRecorder.running)
        coVerify { mockVideoWrapper.writeVideo(any()) }
    }

    @Test
    fun `when VideoRecorder started and writing video throws an exception catches it`() = runTest {
        val mockVideoWrapper = mockk<VideoWrapper>()
        every {
            mockVideoWrapper.inputSurface
        } returns mockk()
        coEvery {
            mockVideoWrapper.writeVideo(any())
        } throws IllegalStateException()
        val videoRecorder = VideoRecorder(mockk(), mockVideoWrapper, null)
        videoRecorder.start(StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        Assert.assertTrue(videoRecorder.running)
        coVerify { mockVideoWrapper.writeVideo(any()) }
    }

    @Test
    fun `when VideoRecorder started with video and audio begins writing both`() = runTest {
        val mockVideoWrapper = mockk<VideoWrapper>()
        every {
            mockVideoWrapper.inputSurface
        } returns mockk()
        val writing = slot<AtomicBoolean>()
        coEvery {
            mockVideoWrapper.writeVideo(capture(writing))
        } throws IllegalStateException() //cheat to get past video recording

        val mockAudioWrapper = mockk<AudioWrapper>()
        coEvery {
            mockAudioWrapper.writeAudio()
        } answers {
            writing.captured.set(false)
        }

        val videoRecorder = VideoRecorder(mockk(), mockVideoWrapper, mockAudioWrapper)
        videoRecorder.start(StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        Assert.assertTrue(videoRecorder.running)
        coVerify { mockVideoWrapper.writeVideo(any()) }
        coVerify { mockAudioWrapper.writeAudio() }
    }

    @Test
    fun `when VideoRecorder started with video and audio then audio throws an exception catches it`() = runTest {
        val mockVideoWrapper = mockk<VideoWrapper>()
        every {
            mockVideoWrapper.inputSurface
        } returns mockk()
        val writing = slot<AtomicBoolean>()
        coEvery {
            mockVideoWrapper.writeVideo(capture(writing))
        } throws IllegalStateException() //cheat to get past video recording

        val mockAudioWrapper = mockk<AudioWrapper>()
        coEvery {
            mockAudioWrapper.writeAudio()
        } throws IllegalStateException()

        val videoRecorder = VideoRecorder(mockk(), mockVideoWrapper, mockAudioWrapper)
        videoRecorder.start(StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        Assert.assertTrue(videoRecorder.running)
        coVerify { mockVideoWrapper.writeVideo(any()) }
        coVerify { mockAudioWrapper.writeAudio() }
    }

    @Test
    fun `when releasing VideoWrapper releases its resources`() = runTest {
        val mockVideoWrapper = mockk<VideoWrapper>()
        every {
            mockVideoWrapper.inputSurface
        } returns mockk()
        every {
            mockVideoWrapper.release()
        } just runs

        val mockAudioWrapper = mockk<AudioWrapper>()
        every {
            mockAudioWrapper.release()
        } just runs

        val mockMuxerWrapper = mockk<MuxerWrapper>()
        coEvery {
            mockMuxerWrapper.release()
        } just runs

        val videoRecorder = VideoRecorder(mockMuxerWrapper, mockVideoWrapper, mockAudioWrapper)
        videoRecorder.release()

        verify { mockVideoWrapper.release() }
        verify { mockAudioWrapper.release() }
        coVerify { mockMuxerWrapper.release() }
    }

    // endregion

    // region VideoWrapper

    @Test
    fun `when writing video and format changes while muxer is running stops writing`() = runTest {
        val mockWrapper = mockMediaCodecWrapper()
        every {
            mockWrapper.dequeueOutputBuffer(any(), any())
        } returns MediaCodec.INFO_OUTPUT_FORMAT_CHANGED

        val videoWrapper = VideoWrapper(0, 0, 0, mockMuxerWrapper(), mockWrapper)
        val writing = AtomicBoolean(true)
        videoWrapper.writeVideo(writing)

        Assert.assertFalse(writing.get())
    }

    @Test
    fun `when writing video and format changes while muxer is not running updates muxer`() = runTest {
        val mockWrapper = mockMediaCodecWrapper()
        every {
            mockWrapper.dequeueOutputBuffer(any(), any())
        } returns MediaCodec.INFO_OUTPUT_FORMAT_CHANGED

        val mockMuxerWrapper = mockk<MuxerWrapper>()
        every {
            mockMuxerWrapper.running
        } returns false
        coEvery {
            mockMuxerWrapper.addVideoTrack(any())
        } just runs

        val videoWrapper = VideoWrapper(0, 0, 0, mockMuxerWrapper, mockWrapper)
        val writing = AtomicBoolean(true)
        videoWrapper.writeVideo(writing)

        Assert.assertTrue(writing.get())
        coVerify { mockMuxerWrapper.addVideoTrack(any()) }
    }

    @Test
    fun `when writing video and codec is not ready skips read`() = runTest {
        val mockWrapper = mockMediaCodecWrapper()
        every {
            mockWrapper.dequeueOutputBuffer(any(), any())
        } returns MediaCodec.INFO_TRY_AGAIN_LATER

        val videoWrapper = VideoWrapper(0, 0, 0, mockk(), mockWrapper)
        val writing = AtomicBoolean(true)
        videoWrapper.writeVideo(writing)

        Assert.assertTrue(writing.get())
    }

    @Test
    fun `when reading video and data is available but muxer is not ready releases data`() = runTest {
        val mockWrapper = mockMediaCodecWrapper()
        val mockMuxerWrapper = mockk<MuxerWrapper>()
        every {
            mockMuxerWrapper.running
        } returns false

        val videoWrapper = VideoWrapper(0, 0, 0, mockMuxerWrapper, mockWrapper)
        val writing = AtomicBoolean(true)
        videoWrapper.writeVideo(writing)
        verify { mockWrapper.releaseOutputBuffer(any(), any()) }
    }

    @Test
    fun `when reading video and muxer is ready but data is empty releases data`() = runTest {
        val mockWrapper = mockMediaCodecWrapper(getDefaultBuffer(size = 0))
        val videoWrapper = VideoWrapper(0, 0, 0, mockMuxerWrapper(), mockWrapper)
        val writing = AtomicBoolean(true)
        videoWrapper.writeVideo(writing)
        verify { mockWrapper.releaseOutputBuffer(any(), any()) }
    }

    @Test
    fun `when reading video and initial keyframe not set, sets it and writes data`() = runTest {
        val mockWrapper = mockMediaCodecWrapper()

        every {
            mockWrapper.hasSetInitialKeyFrame
        } returns false

        every {
            mockWrapper setProperty "hasSetInitialKeyFrame" value any<Boolean>()
        } propertyType Boolean::class answers { fieldValue = value }

        every {
            mockWrapper.setParameters(any())
        } just runs

        val mockMuxerWrapper = mockMuxerWrapper()

        val videoWrapper = VideoWrapper(0, 0, 0, mockMuxerWrapper, mockWrapper)
        val writing = AtomicBoolean(true)
        videoWrapper.writeVideo(writing)
        verify { mockWrapper.setParameters(any()) }
        coVerify { mockMuxerWrapper.writeVideo(any(), any()) }
    }

    @Test
    fun `when reading video and initial keyframe already set just writes data`() = runTest {
        val mockWrapper = mockMediaCodecWrapper()

        every {
            mockWrapper.hasSetInitialKeyFrame
        } returns true

        val mockMuxerWrapper = mockMuxerWrapper()

        val videoWrapper = VideoWrapper(0, 0, 0, mockMuxerWrapper, mockWrapper)
        val writing = AtomicBoolean(true)
        videoWrapper.writeVideo(writing)
        verify(exactly = 0) { mockWrapper.setParameters(any()) }
        coVerify { mockMuxerWrapper.writeVideo(any(), any()) }
    }

    @Test
    fun `when reading video and end of stream flag present stops writing`() = runTest {
        val mockWrapper = mockMediaCodecWrapper(getDefaultBuffer(flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM))

        every {
            mockWrapper.hasSetInitialKeyFrame
        } returns true

        val mockMuxerWrapper = mockMuxerWrapper()

        val videoWrapper = VideoWrapper(0, 0, 0, mockMuxerWrapper, mockWrapper)
        val writing = AtomicBoolean(true)
        videoWrapper.writeVideo(writing)
        coVerify { mockMuxerWrapper.writeVideo(any(), any()) }
        Assert.assertFalse(writing.get())
    }

    @Test
    fun `when VideoWrapper releasing shuts down encoder`() {
        val mockWrapper = mockMediaCodecWrapper()

        val videoWrapper = VideoWrapper(0, 0, 0, mockk(), mockWrapper)

        videoWrapper.release()

        verify { mockWrapper.stop() }
        verify { mockWrapper.release() }
    }

    @Test
    fun `when VideoWrapper releasing multiple times shuts down encoder just once`() {
        val mockWrapper = mockMediaCodecWrapper()

        val videoWrapper = VideoWrapper(0, 0, 0, mockk(), mockWrapper)

        videoWrapper.release()
        videoWrapper.release()

        verify(exactly = 1) { mockWrapper.stop() }
        verify(exactly = 1) { mockWrapper.release() }
    }

    @Test
    fun `when VideoWrapper releasing and encoder throws exception catches it`() {
        val mockWrapper = mockMediaCodecWrapper()

        every {
            mockWrapper.release()
        } throws IllegalStateException()

        val videoWrapper = VideoWrapper(0, 0, 0, mockk(), mockWrapper)

        videoWrapper.release()

        verify { mockWrapper.stop() }
        verify { mockWrapper.release() }
    }

    // endregion

    // region AudioWrapper

    @Test
    fun `when started and microphone not recording starts microphone`() = runTest {
        val mockAudioRecord = mockAudioRecord()
        every {
            mockAudioRecord.recordingState
        } returns AudioRecord.RECORDSTATE_STOPPED

        val audioWrapper = AudioWrapper(
            44_100, 2, 128_000, mockMuxerWrapper(), mockAudioRecord,
            mockMediaCodecWrapper(getDefaultBuffer(flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM))
        )

        audioWrapper.writeAudio()

        verify { mockAudioRecord.startRecording() }
    }

    @Test
    fun `when started input buffer is available encodes microphone data into it`() = runTest {
        val mockWrapper = mockMediaCodecWrapper(getDefaultBuffer(flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM))
        every {
            mockWrapper.dequeueInputBuffer(any())
        } returns 1

        every {
            mockWrapper.getInputBuffer(any())
        } returns mockk()

        val mockAudioRecord = mockAudioRecord()

        every {
            mockAudioRecord.read(any(), any())
        } returns 1

        every {
            mockWrapper.queueInputBuffer(any(), any(), any(), any(), any())
        } just runs

        val audioWrapper = AudioWrapper(
            44_100, 2, 128_000,
            mockMuxerWrapper(), mockAudioRecord, mockWrapper
        )

        audioWrapper.writeAudio()

        verify { mockAudioRecord.read(any(), any()) }
        verify { mockWrapper.queueInputBuffer(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `when started input buffer is available but microphone data is empty skips encoding`() = runTest {
        val mockWrapper = mockMediaCodecWrapper(getDefaultBuffer(flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM))
        every {
            mockWrapper.dequeueInputBuffer(any())
        } returns 1

        every {
            mockWrapper.getInputBuffer(any())
        } returns mockk()

        val mockAudioRecord = mockAudioRecord()

        every {
            mockAudioRecord.read(any(), any())
        } returns 0

        every {
            mockWrapper.queueInputBuffer(any(), any(), any(), any(), any())
        } just runs

        val audioWrapper = AudioWrapper(
            44_100, 2, 128_000,
            mockMuxerWrapper(), mockAudioRecord, mockWrapper
        )

        audioWrapper.writeAudio()

        verify { mockAudioRecord.read(any(), any()) }
        verify(exactly = 0) { mockWrapper.queueInputBuffer(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `when started input buffer is available but cant load skips microphone read`() = runTest {
        val mockWrapper = mockMediaCodecWrapper(getDefaultBuffer(flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM))
        every {
            mockWrapper.dequeueInputBuffer(any())
        } returns 1

        every {
            mockWrapper.getInputBuffer(any())
        } returns null

        val mockAudioRecord = mockAudioRecord()

        val audioWrapper = AudioWrapper(
            44_100, 2, 128_000,
            mockMuxerWrapper(), mockAudioRecord, mockWrapper
        )

        audioWrapper.writeAudio()

        verify(exactly = 0) { mockAudioRecord.read(any(), any()) }
        verify(exactly = 0) { mockWrapper.queueInputBuffer(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `when writing audio and format changes updates muxer`() = runTest {
        val mockWrapper = mockMediaCodecWrapper(getDefaultBuffer(flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM))
        every {
            mockWrapper.dequeueOutputBuffer(any(), any())
        } returns MediaCodec.INFO_OUTPUT_FORMAT_CHANGED

        val mockMuxerWrapper = mockMuxerWrapper()

        val audioWrapper = AudioWrapper(
            44_100, 2, 128_000,
            mockMuxerWrapper, mockAudioRecord(), mockWrapper
        )

        audioWrapper.writeAudio()

        coVerify { mockMuxerWrapper.addAudioTrack(any()) }
    }

    @Test
    fun `when writing audio and encoder is not ready skips encoding`() = runTest {
        val mockWrapper = mockMediaCodecWrapper(getDefaultBuffer(flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM))
        every {
            mockWrapper.dequeueOutputBuffer(any(), any())
        } returns MediaCodec.INFO_TRY_AGAIN_LATER

        val audioWrapper = AudioWrapper(
            44_100, 2, 128_000,
            mockMuxerWrapper(), mockAudioRecord(), mockWrapper
        )

        audioWrapper.writeAudio()

        verify(exactly = 0) { mockWrapper.getOutputBuffer(any()) }
    }

    @Test
    fun `when writing audio and muxer is not ready skips writing to muxer`() = runTest {
        val mockMuxerWrapper = mockk<MuxerWrapper>()
        every {
            mockMuxerWrapper.running
        } returns false

        val audioWrapper = AudioWrapper(
            44_100, 2, 128_000,
            mockMuxerWrapper, mockAudioRecord(), mockMediaCodecWrapper(getDefaultBuffer(flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM))
        )

        audioWrapper.writeAudio()

        coVerify(exactly = 0) { mockMuxerWrapper.writeAudio(any(), any()) }
    }

    @Test
    fun `when writing audio and buffer is empty skips writing to muxer`() = runTest {
        val mockMuxerWrapper = mockMuxerWrapper()

        val audioWrapper = AudioWrapper(
            44_100, 2, 128_000,
            mockMuxerWrapper, mockAudioRecord(), mockMediaCodecWrapper(getDefaultBuffer(size = 0, flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM))
        )

        audioWrapper.writeAudio()

        coVerify(exactly = 0) { mockMuxerWrapper.writeAudio(any(), any()) }
    }

    @Test
    fun `when writing audio and timestamp is invalid skips writing to muxer`() = runTest {
        val mockMuxerWrapper = mockMuxerWrapper()

        val audioWrapper = AudioWrapper(
            44_100, 2, 128_000,
            mockMuxerWrapper, mockAudioRecord(), mockMediaCodecWrapper(getDefaultBuffer(presentationTime = -1, flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM))
        )

        audioWrapper.writeAudio()

        coVerify(exactly = 0) { mockMuxerWrapper.writeAudio(any(), any()) }
    }

    @Test
    fun `when AudioWrapper releasing shuts down encoder`() {
        val mockWrapper = mockMediaCodecWrapper()

        val mockAudioRecord = mockAudioRecord()

        val audioWrapper = AudioWrapper(
            44_100, 2, 128_000,
            mockk(), mockAudioRecord, mockWrapper
        )

        audioWrapper.release()

        verify { mockWrapper.stop() }
        verify { mockWrapper.release() }

        verify { mockAudioRecord.stop() }
        verify { mockAudioRecord.release() }
    }

    @Test
    fun `when AudioWrapper releasing multiple times shuts down encoder just once`() {
        val mockWrapper = mockMediaCodecWrapper()
        val mockAudioRecord = mockAudioRecord()

        val audioWrapper = AudioWrapper(
            44_100, 2, 128_000,
            mockk(), mockAudioRecord, mockWrapper
        )

        audioWrapper.release()
        audioWrapper.release()

        verify(exactly = 1) { mockWrapper.stop() }
        verify(exactly = 1) { mockWrapper.release() }

        verify(exactly = 1) { mockAudioRecord.stop() }
        verify(exactly = 1) { mockAudioRecord.release() }
    }

    @Test
    fun `when AudioWrapper releasing and encoder throws exception catches it`() {
        val mockWrapper = mockMediaCodecWrapper()

        every {
            mockWrapper.release()
        } throws IllegalStateException()

        val mockAudioRecord = mockAudioRecord()

        val audioWrapper = AudioWrapper(
            44_100, 2, 128_000,
            mockk(), mockAudioRecord, mockWrapper
        )

        audioWrapper.release()

        verify { mockWrapper.stop() }
        verify { mockWrapper.release() }

        verify { mockAudioRecord.stop() }
        verify { mockAudioRecord.release() }
    }

    @Test
    fun `when AudioWrapper releasing and microphone throws exception catches it`() {
        val mockWrapper = mockMediaCodecWrapper()

        val mockAudioRecord = mockAudioRecord()

        every {
            mockAudioRecord.stop()
        } throws IllegalStateException()

        val audioWrapper = AudioWrapper(
            44_100, 2, 128_000,
            mockk(), mockAudioRecord, mockWrapper
        )
        
        audioWrapper.release()

        verify { mockWrapper.stop() }
        verify { mockWrapper.release() }

        verify { mockAudioRecord.stop() }
    }

    // endregion

    @Test
    fun `when started with video only is ready after adding video track`() = runTest {
        val muxerWrapper = MuxerWrapper(mockMediaMuxer(), false)
        muxerWrapper.addVideoTrack(mockk())

        Assert.assertTrue(muxerWrapper.running)
    }

    @Test
    fun `when started with video only but audio is added is ready after adding video track`() = runTest {
        val muxerWrapper = MuxerWrapper(mockMediaMuxer(), false)
        muxerWrapper.addAudioTrack(mockk())
        muxerWrapper.addVideoTrack(mockk())

        Assert.assertTrue(muxerWrapper.running)
    }

    @Test
    fun `when started with video and audio is not ready after adding video track`() = runTest {
        val muxerWrapper = MuxerWrapper(mockMediaMuxer(), true)
        muxerWrapper.addVideoTrack(mockk())

        Assert.assertFalse(muxerWrapper.running)
    }

    @Test
    fun `when started with video and audio is ready after adding video and audio tracks`() = runTest {
        val muxerWrapper = MuxerWrapper(mockMediaMuxer(), true)
        muxerWrapper.addVideoTrack(mockk())

        Assert.assertFalse(muxerWrapper.running)

        muxerWrapper.addAudioTrack(mockk())

        Assert.assertTrue(muxerWrapper.running)
    }

    @Test
    fun `when running and writing video data writes to file`() = runTest {
        val mockMediaMuxer = mockMediaMuxer()
        val muxerWrapper = MuxerWrapper(mockMediaMuxer, true)
        muxerWrapper.addVideoTrack(mockk())
        muxerWrapper.addAudioTrack(mockk())
        muxerWrapper.writeVideo(mockk(), getDefaultBuffer())

        verify { mockMediaMuxer.writeSampleData(0, any(), any()) }
    }

    @Test
    fun `when running and writing audio data writes to file`() = runTest {
        val mockMediaMuxer = mockMediaMuxer()
        val muxerWrapper = MuxerWrapper(mockMediaMuxer, true)
        muxerWrapper.addVideoTrack(mockk())
        muxerWrapper.addAudioTrack(mockk())
        muxerWrapper.writeAudio(mockk(), getDefaultBuffer())

        verify { mockMediaMuxer.writeSampleData(1, any(), any()) }
    }

    @Test
    fun `when running and writing data throws an exception catches error`() = runTest {
        val mockMediaMuxer = mockMediaMuxer()
        val muxerWrapper = MuxerWrapper(mockMediaMuxer, true)
        muxerWrapper.addVideoTrack(mockk())
        muxerWrapper.addAudioTrack(mockk())

        every {
            mockMediaMuxer.writeSampleData(any(), any(), any())
        } throws IllegalStateException()

        muxerWrapper.writeAudio(mockk(), getDefaultBuffer())
    }

    @Test
    fun `when releasing shuts down media muxer`() = runTest {
        val mockMediaMuxer = mockMediaMuxer()
        val muxerWrapper = MuxerWrapper(mockMediaMuxer, true)
        muxerWrapper.addVideoTrack(mockk())
        muxerWrapper.addAudioTrack(mockk())
        muxerWrapper.release()

        verify { mockMediaMuxer.stop() }
        verify { mockMediaMuxer.release() }
    }

    @Test
    fun `when release called multiple times shuts down media muxer just once`() = runTest {
        val mockMediaMuxer = mockMediaMuxer()
        val muxerWrapper = MuxerWrapper(mockMediaMuxer, true)
        muxerWrapper.addVideoTrack(mockk())
        muxerWrapper.addAudioTrack(mockk())
        muxerWrapper.release()
        muxerWrapper.release()

        verify(exactly = 1) { mockMediaMuxer.stop() }
        verify(exactly = 1) { mockMediaMuxer.release() }
    }

    @Test
    fun `when releasing before starting does nothing`() = runTest {
        val mockMediaMuxer = mockMediaMuxer()
        val muxerWrapper = MuxerWrapper(mockMediaMuxer, true)
        muxerWrapper.release()

        verify(exactly = 0) { mockMediaMuxer.stop() }
        verify(exactly = 0) { mockMediaMuxer.release() }
    }

    @Test
    fun `when releasing and muxer throws exception catches error`() = runTest {
        val mockMediaMuxer = mockMediaMuxer()
        val muxerWrapper = MuxerWrapper(mockMediaMuxer, true)
        muxerWrapper.addVideoTrack(mockk())
        muxerWrapper.addAudioTrack(mockk())
        every {
            mockMediaMuxer.stop()
        } throws IllegalStateException()

        muxerWrapper.release()
    }

    // region

    private fun mockMediaCodecWrapper(bufferInfo: BufferInfo = getDefaultBuffer()): MediaCodecWrapper {
        val mockMediaCodecWrapper = mockk<MediaCodecWrapper>()

        every {
            mockMediaCodecWrapper.createInputSurface()
        } returns mockk()

        every {
            mockMediaCodecWrapper.configure(any(), any())
        } just runs

        every {
            mockMediaCodecWrapper.start()
        } just runs

        every {
            mockMediaCodecWrapper.stop()
        } just runs

        every {
            mockMediaCodecWrapper.release()
        } just runs

        every {
            mockMediaCodecWrapper.releaseOutputBuffer(any(), any())
        } just runs

        every {
            mockMediaCodecWrapper.outputFormat
        } returns mockk()

        every {
            mockMediaCodecWrapper.getOutputBuffer(any())
        } returns mockk(relaxed = true)

        val buffer = slot<BufferInfo>()
        every {
            mockMediaCodecWrapper.dequeueOutputBuffer(capture(buffer), any())
        } answers {
            buffer.captured.apply {
                size = bufferInfo.size
                presentationTimeUs = bufferInfo.presentationTimeUs
                flags = bufferInfo.flags
            }
            1
        }

        every {
            mockMediaCodecWrapper.dequeueInputBuffer(any())
        } returns -1

        return mockMediaCodecWrapper
    }

    private fun mockMuxerWrapper(): MuxerWrapper {
        val mockMuxerWrapper = mockk<MuxerWrapper>()

        every {
            mockMuxerWrapper.running
        } returns true

        coEvery {
            mockMuxerWrapper.addVideoTrack(any())
        } just runs

        coEvery {
            mockMuxerWrapper.addAudioTrack(any())
        } just runs

        coEvery {
            mockMuxerWrapper.writeVideo(any(), any())
        } just runs

        coEvery {
            mockMuxerWrapper.writeAudio(any(), any())
        } just runs

        return mockMuxerWrapper
    }

    private fun mockAudioRecord(): AudioRecord {
        val mockAudioRecord = mockk<AudioRecord>()

        every {
            mockAudioRecord.recordingState
        } returns AudioRecord.RECORDSTATE_RECORDING

        every {
            mockAudioRecord.startRecording()
        } just runs

        every {
            mockAudioRecord.stop()
        } just runs

        every {
            mockAudioRecord.release()
        } just runs

        return mockAudioRecord
    }

    private fun mockMediaMuxer(): MediaMuxer {
        val mockMediaMuxer = mockk<MediaMuxer>()

        var trackIndex = 0
        every {
            mockMediaMuxer.addTrack(any())
        } answers { trackIndex++ }

        every {
            mockMediaMuxer.writeSampleData(any(), any(), any())
        } just runs

        every {
            mockMediaMuxer.start()
        } just runs

        every {
            mockMediaMuxer.stop()
        } just runs

        every {
            mockMediaMuxer.release()
        } just runs

        return mockMediaMuxer
    }

    private fun getDefaultBuffer(size: Int = 1, presentationTime: Long = 1, flags: Int = 0) =
        BufferInfo().also {
            it.size = size
            it.presentationTimeUs = presentationTime
            it.flags = flags
        }
}