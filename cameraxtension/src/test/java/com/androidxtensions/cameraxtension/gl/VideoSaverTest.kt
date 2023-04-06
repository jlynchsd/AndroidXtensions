package com.androidxtensions.cameraxtension.gl

import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VideoSaverTest {

    @Test
    fun `when GlHandler set then started starts recording`() {
        val mockHandler = mockGlHandler()
        val videoSaver = VideoSaver()
        videoSaver.setGlHandler(mockHandler)
        videoSaver.startRecording(mockk())

        verify { mockHandler.startRecording(any()) }
    }

    @Test
    fun `when started before GlHandler set starts recording after handler set`() {
        val mockHandler = mockGlHandler()
        val videoSaver = VideoSaver()
        videoSaver.startRecording(mockk())

        verify(exactly = 0) { mockHandler.startRecording(any()) }

        videoSaver.setGlHandler(mockHandler)

        verify { mockHandler.startRecording(any()) }
    }

    @Test
    fun `when start recording called multiple times starts then does nothing`() {
        val mockHandler = mockGlHandler()
        val videoSaver = VideoSaver()
        videoSaver.setGlHandler(mockHandler)
        videoSaver.startRecording(mockk())
        videoSaver.startRecording(mockk())

        verify(exactly = 1) { mockHandler.startRecording(any()) }
    }

    @Test
    fun `when stop recording is called before starting does nothing`() = runTest {
        val mockHandler = mockGlHandler()
        val videoSaver = VideoSaver()
        videoSaver.setGlHandler(mockHandler)
        videoSaver.stopRecording(StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        coVerify(exactly = 0) { mockHandler.stopRecording() }
    }

    @Test
    fun `when stop recording is called before GlHandler is set cancels pending start`() = runTest {
        val mockHandler = mockGlHandler()
        val videoSaver = VideoSaver()
        videoSaver.startRecording(mockk())
        videoSaver.stopRecording(StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        videoSaver.setGlHandler(mockHandler)

        verify(exactly = 0) { mockHandler.startRecording(any()) }
    }

    @Test
    fun `when running and then stopped stops recording`() = runTest {
        val mockHandler = mockGlHandler()
        val videoSaver = VideoSaver()
        videoSaver.setGlHandler(mockHandler)
        videoSaver.startRecording(mockk())
        videoSaver.stopRecording(StandardTestDispatcher(testScheduler))
        advanceUntilIdle()


        coVerify { mockHandler.stopRecording() }
    }

    @Test
    fun `when stopping with pending start begins new recording`() = runTest {
        val mockHandler = mockGlHandler()
        val videoSaver = VideoSaver()
        videoSaver.setGlHandler(mockHandler)
        videoSaver.startRecording(mockk())
        videoSaver.stopRecording(StandardTestDispatcher(testScheduler))
        videoSaver.startRecording(mockk())
        advanceUntilIdle()


        coVerify { mockHandler.stopRecording() }
        verify(exactly = 2) { mockHandler.startRecording(any()) }
    }

    private fun mockGlHandler(): GlHandler {
        val mockGlHandler = mockk<GlHandler>()

        every {
            mockGlHandler.startRecording(any())
        } just runs

        coEvery {
            mockGlHandler.stopRecording()
        } just runs

        return mockGlHandler
    }
}