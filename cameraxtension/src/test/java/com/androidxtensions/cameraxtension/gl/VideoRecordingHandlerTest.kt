package com.androidxtensions.cameraxtension.gl

import android.util.Size
import com.androidxtensions.cameraxtension.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowSize::class])
class VideoRecordingHandlerTest {

    @Before
    fun setup() {
        mockGles20()
    }

    @After
    fun cleanup() {
        unmockkAll()
    }

    @Test
    fun `when starting recording makes video recorder`() = runTest {
        val mockRecorder = getMockVideoRecorder()
        val mockApi = getMockEglApi()
        injectMockVideoRecorder(mockRecorder)
        val videoRecordingHandler = VideoRecordingHandler(
            mockApi, CameraConfiguration.Builder().build(), mockk(relaxed = true), 0, 0
        )
        videoRecordingHandler.startRecording(
            StandardTestDispatcher(testScheduler), RecordConfiguration.Builder(mockk()).build()
        )
        advanceUntilIdle()

        verify { mockApi.createWindowSurface(any()) }
        verify { mockRecorder.start() }
    }

    @Test
    fun `when starting recording with matching dimensions updates sizes`() = runTest {
        val mockRecorder = getMockVideoRecorder()
        val mockApi = getMockEglApi()
        val MOCK_WIDTH = 1
        val MOCK_HEIGHT = 2
        val mockSize = Size(MOCK_WIDTH, MOCK_HEIGHT)
        injectMockVideoRecorder(mockRecorder)
        val videoRecordingHandler = VideoRecordingHandler(
            mockApi,
            CameraConfiguration.Builder().build(),
            mockSize, 0, 0
        )
        val videoConfiguration = VideoConfiguration.default().copy(width = VideoConfiguration.MATCH_WIDTH, height = VideoConfiguration.MATCH_HEIGHT)
        videoRecordingHandler.startRecording(
            StandardTestDispatcher(testScheduler),
            RecordConfiguration.Builder(
                mockk(),
                videoConfiguration
            ).build()
        )
        advanceUntilIdle()

        verify { VideoRecorder.getVideoRecorder(videoConfiguration.copy(width = MOCK_WIDTH, height = MOCK_HEIGHT), any(), any()) }
        verify { mockApi.createWindowSurface(any()) }
        verify { mockRecorder.start() }
    }

    @Test
    fun `when starting recording but egl released does not start`() = runTest {
        val mockRecorder = getMockVideoRecorder()
        val mockApi = getMockEglApi(true)
        injectMockVideoRecorder(mockRecorder)
        val videoRecordingHandler = VideoRecordingHandler(
            mockApi, CameraConfiguration.Builder().build(), mockk(relaxed = true), 0, 0
        )
        videoRecordingHandler.startRecording(
            StandardTestDispatcher(testScheduler), RecordConfiguration.Builder(mockk()).build()
        )
        advanceUntilIdle()

        verify(exactly = 0) { mockApi.createWindowSurface(any()) }
        verify(exactly = 0) { mockRecorder.start() }
    }

    @Test
    fun `when stopping recording releases video recorder`() = runTest {
        val mockRecorder = getMockVideoRecorder()
        val mockApi = getMockEglApi()
        injectMockVideoRecorder(mockRecorder)
        val videoRecordingHandler = VideoRecordingHandler(
            mockApi, CameraConfiguration.Builder().build(), mockk(relaxed = true), 0, 0
        )
        videoRecordingHandler.startRecording(
            StandardTestDispatcher(testScheduler), RecordConfiguration.Builder(mockk()).build()
        )
        advanceUntilIdle()

        videoRecordingHandler.stopRecording(StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        coVerify { mockRecorder.release() }
    }

    @Test
    fun `when stopping recording before starting does not release video recorder`() = runTest {
        val mockRecorder = getMockVideoRecorder()
        val mockApi = getMockEglApi()
        injectMockVideoRecorder(mockRecorder)
        val videoRecordingHandler = VideoRecordingHandler(
            mockApi, CameraConfiguration.Builder().build(), mockk(relaxed = true), 0, 0
        )

        videoRecordingHandler.stopRecording(StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        coVerify(exactly = 0) { mockRecorder.release() }
    }

    @Test
    fun `when recording a video frame but recorder is not started does nothing`() = runTest {
        val mockRecorder = getMockVideoRecorder()
        val mockApi = getMockEglApi()
        injectMockVideoRecorder(mockRecorder)
        val videoRecordingHandler = VideoRecordingHandler(
            mockApi, CameraConfiguration.Builder().build(), mockk(relaxed = true), 0, 0
        )

        videoRecordingHandler.recordVideoFrame(0, floatArrayOf())

        verify(exactly = 0) { Gles20Api.glDrawArrays(any(), any(), any()) }
    }

    @Test
    fun `when recording a video frame but recorder is not running does nothing`() = runTest {
        val mockRecorder = getMockVideoRecorder(false)
        val mockApi = getMockEglApi()
        injectMockVideoRecorder(mockRecorder)
        val videoRecordingHandler = VideoRecordingHandler(
            mockApi, CameraConfiguration.Builder().build(), mockk(relaxed = true), 0, 0
        )
        videoRecordingHandler.startRecording(
            StandardTestDispatcher(testScheduler), RecordConfiguration.Builder(mockk()).build()
        )
        advanceUntilIdle()

        videoRecordingHandler.recordVideoFrame(0, floatArrayOf())

        verify(exactly = 0) { Gles20Api.glDrawArrays(any(), any(), any()) }
    }

    @Test
    fun `when recording a video frame but surface is not available does nothing`() = runTest {
        val mockRecorder = getMockVideoRecorder()
        val mockApi = getMockEglApi(true)
        injectMockVideoRecorder(mockRecorder)
        val videoRecordingHandler = VideoRecordingHandler(
            mockApi, CameraConfiguration.Builder().build(), mockk(relaxed = true), 0, 0
        )
        videoRecordingHandler.startRecording(
            StandardTestDispatcher(testScheduler), RecordConfiguration.Builder(mockk()).build()
        )
        advanceUntilIdle()

        videoRecordingHandler.recordVideoFrame(0, floatArrayOf())

        verify(exactly = 0) { Gles20Api.glDrawArrays(any(), any(), any()) }
    }

    @Test
    fun `when recording a video frame draws to surface`() = runTest {
        val mockRecorder = getMockVideoRecorder()
        val mockApi = getMockEglApi()
        injectMockVideoRecorder(mockRecorder)
        val videoRecordingHandler = VideoRecordingHandler(
            mockApi, CameraConfiguration.Builder().build(), mockk(relaxed = true), 0, 0
        )
        videoRecordingHandler.startRecording(
            StandardTestDispatcher(testScheduler), RecordConfiguration.Builder(mockk()).build()
        )
        advanceUntilIdle()

        videoRecordingHandler.recordVideoFrame(0, floatArrayOf())

        verify { Gles20Api.glDrawArrays(any(), any(), any()) }
    }

    @Test
    fun `when releasing releases recorder and texture`() = runTest {
        val mockRecorder = getMockVideoRecorder()
        val mockApi = getMockEglApi()
        injectMockVideoRecorder(mockRecorder)
        val videoRecordingHandler = VideoRecordingHandler(
            mockApi, CameraConfiguration.Builder().build(), mockk(relaxed = true), 0, 0
        )
        videoRecordingHandler.startRecording(
            StandardTestDispatcher(testScheduler), RecordConfiguration.Builder(mockk()).build()
        )
        advanceUntilIdle()

        videoRecordingHandler.release(StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        coVerify { mockRecorder.release() }
        verify { Gles20Api.glDeleteProgram(any()) }
    }

    @Test
    fun `when releasing before starting only releases texture`() = runTest {
        val mockRecorder = getMockVideoRecorder()
        val mockApi = getMockEglApi()
        injectMockVideoRecorder(mockRecorder)
        val videoRecordingHandler = VideoRecordingHandler(
            mockApi, CameraConfiguration.Builder().build(), mockk(relaxed = true), 0, 0
        )

        videoRecordingHandler.release(StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        coVerify(exactly = 0) { mockRecorder.release() }
        verify { Gles20Api.glDeleteProgram(any()) }
    }

    private fun getMockEglApi(released: Boolean = false): EglApi {
        val mockEglApi = mockk<EglApi>()
        every {
            mockEglApi.released
        } returns released

        every {
            mockEglApi.createWindowSurface(any())
        } returns mockk()

        every {
            mockEglApi.makeCurrent(any())
        } just runs

        every {
            mockEglApi.swapBuffers(any())
        } returns true

        every {
            mockEglApi.destroySurface(any())
        } just runs

        return mockEglApi
    }
}