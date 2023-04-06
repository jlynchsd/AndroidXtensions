package com.androidxtensions.cameraxtension.gl

import android.opengl.EGL14
import android.util.Size
import android.view.Surface
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.impl.utils.futures.FutureCallback
import com.androidxtensions.cameraxtension.CameraConfiguration
import com.androidxtensions.cameraxtension.RecordConfiguration
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowSize::class])
class GlHandlerTest {

    @Before
    fun setup() {
        mockEgl14()
    }

    @After
    fun cleanup() {
        unmockkAll()
    }

    @Test
    fun `when created makes default pbuffer surface`() = runTest {
        val executorService = Executors.newSingleThreadExecutor()
        val glHandler = GlHandler(false, executorService.asCoroutineDispatcher())
        waitTillIdle(executorService)

        verify { EGL14.eglCreatePbufferSurface(any(), any(), any(), any()) }
    }

    @Test
    fun `when created surface provider can provide a valid surface`() = runTest {
        val executorService = Executors.newSingleThreadExecutor()
        val dispatcher = executorService.asCoroutineDispatcher()
        val glHandler = GlHandler(false, dispatcher)
        waitTillIdle(executorService)
        val surfaceProvider = glHandler.getSurfaceProvider(
            Size(0, 0),
            CameraConfiguration.Builder().build(),
            0, 0
        )
        waitTillIdle(executorService)

        val surface = getSurface(surfaceProvider, SurfaceRequest(Size(0, 0), mockk()) {}, dispatcher.executor)
        waitTillIdle(executorService)

        Assert.assertTrue(surface.isValid)
    }

    @Test
    fun `when created with analysis surface provider can provide a valid surface`() = runTest {
        val executorService = Executors.newSingleThreadExecutor()
        val dispatcher = executorService.asCoroutineDispatcher()
        val glHandler = GlHandler(true, dispatcher)
        waitTillIdle(executorService)
        val surfaceProvider = glHandler.getSurfaceProvider(
            Size(0, 0),
            CameraConfiguration.Builder().imageAnalysis {  }.build(),
            90, 0
        )
        waitTillIdle(executorService)

        val surface = getSurface(surfaceProvider, SurfaceRequest(Size(0, 0), mockk()) {}, dispatcher.executor)
        waitTillIdle(executorService)

        Assert.assertTrue(surface.isValid)
    }

    @Test
    fun `when binding surface makes EGL surface`() = runTest {
        val executorService = Executors.newSingleThreadExecutor()
        val dispatcher = executorService.asCoroutineDispatcher()
        val glHandler = GlHandler(false, dispatcher)
        waitTillIdle(executorService)

        glHandler.bindSurface(mockk())
        waitTillIdle(executorService)

        verify { EGL14.eglCreateWindowSurface(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `when binding surface after releasing does not makes EGL surface`() = runTest {
        val executorService = Executors.newSingleThreadExecutor()
        val dispatcher = executorService.asCoroutineDispatcher()
        val glHandler = GlHandler(false, dispatcher)
        waitTillIdle(executorService)

        glHandler.release()
        waitTillIdle(executorService)

        glHandler.bindSurface(mockk())
        waitTillIdle(executorService)

        verify(exactly = 0) { EGL14.eglCreateWindowSurface(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `when releasing multiple times only releases resources once`() = runTest {
        val executorService = Executors.newSingleThreadExecutor()
        val dispatcher = executorService.asCoroutineDispatcher()
        val glHandler = GlHandler(false, dispatcher)
        waitTillIdle(executorService)

        glHandler.release()
        waitTillIdle(executorService)

        glHandler.release()
        waitTillIdle(executorService)

        verify(exactly = 1) { EGL14.eglDestroySurface(any(), any()) }
    }

    @Test
    fun `when starting recording called starts recording video`() = runTest {
        val mockVideoRecorder = getMockVideoRecorder()
        injectMockVideoRecorder(mockVideoRecorder)
        val executorService = Executors.newSingleThreadExecutor()
        val dispatcher = executorService.asCoroutineDispatcher()
        val glHandler = GlHandler(false, dispatcher)
        waitTillIdle(executorService)

        glHandler.getSurfaceProvider(
            Size(0, 0),
            CameraConfiguration.Builder().build(),
            0, 0
        )
        waitTillIdle(executorService)

        glHandler.startRecording(RecordConfiguration.Builder(mockk()).build())
        waitTillIdle(executorService)

        verify { mockVideoRecorder.start(any()) }
    }

    @Test
    fun `when starting recording before input is ready called starts recording video when surface provided`() = runTest {
        val mockVideoRecorder = getMockVideoRecorder()
        injectMockVideoRecorder(mockVideoRecorder)
        val executorService = Executors.newSingleThreadExecutor()
        val dispatcher = executorService.asCoroutineDispatcher()
        val glHandler = GlHandler(false, dispatcher)
        waitTillIdle(executorService)

        glHandler.startRecording(RecordConfiguration.Builder(mockk()).build())
        waitTillIdle(executorService)

        glHandler.getSurfaceProvider(
            Size(0, 0),
            CameraConfiguration.Builder().build(),
            0, 0
        )
        waitTillIdle(executorService)

        verify { mockVideoRecorder.start(any()) }
    }

    @Test
    fun `when starting recording without video recorder does nothing`() = runTest {
        val mockVideoRecorder = getMockVideoRecorder()
        injectMockVideoRecorder(mockVideoRecorder)
        val executorService = Executors.newSingleThreadExecutor()
        val dispatcher = executorService.asCoroutineDispatcher()
        val glHandler = GlHandler(false, dispatcher)
        waitTillIdle(executorService)

        glHandler.startRecording(mockk())
        waitTillIdle(executorService)

        verify(exactly = 0) { mockVideoRecorder.start(any()) }
    }

    @Test
    fun `when stopping recording stops recording`() = runTest {
        val mockVideoRecorder = getMockVideoRecorder()
        injectMockVideoRecorder(mockVideoRecorder)
        val executorService = Executors.newSingleThreadExecutor()
        val dispatcher = executorService.asCoroutineDispatcher()
        val glHandler = GlHandler(false, dispatcher)
        waitTillIdle(executorService)

        glHandler.getSurfaceProvider(
            Size(0, 0),
            CameraConfiguration.Builder().build(),
            0, 0
        )
        waitTillIdle(executorService)

        glHandler.startRecording(RecordConfiguration.Builder(mockk()).build())
        waitTillIdle(executorService)

        glHandler.stopRecording()

        coVerify { mockVideoRecorder.release() }
    }

    @Test
    fun `when stopping recording without video recorder does nothing`() = runTest {
        val mockVideoRecorder = getMockVideoRecorder()
        injectMockVideoRecorder(mockVideoRecorder)
        val executorService = Executors.newSingleThreadExecutor()
        val dispatcher = executorService.asCoroutineDispatcher()
        val glHandler = GlHandler(false, dispatcher)
        waitTillIdle(executorService)

        glHandler.stopRecording()

        coVerify(exactly = 0) { mockVideoRecorder.release() }
    }

    @Test
    fun `when stopping recording without running video recorder does nothing`() = runTest {
        val mockVideoRecorder = getMockVideoRecorder()
        injectMockVideoRecorder(mockVideoRecorder)
        val executorService = Executors.newSingleThreadExecutor()
        val dispatcher = executorService.asCoroutineDispatcher()
        val glHandler = GlHandler(false, dispatcher)
        waitTillIdle(executorService)

        glHandler.getSurfaceProvider(
            Size(0, 0),
            CameraConfiguration.Builder().build(),
            0, 0
        )
        waitTillIdle(executorService)

        glHandler.stopRecording()

        coVerify(exactly = 0) { mockVideoRecorder.release() }
    }

    private suspend fun getSurface(displaySurfaceProvider: Preview.SurfaceProvider, displaySurfaceRequest: SurfaceRequest, executor: Executor) =
        suspendCoroutine { continuation ->
            androidx.camera.core.impl.utils.futures.Futures.addCallback(displaySurfaceRequest.deferrableSurface.surface,
                object : FutureCallback<Surface> {
                    override fun onSuccess(result: Surface?) {
                        if (result != null) {
                            continuation.resume(result)
                        } else {
                            continuation.resumeWithException(NullPointerException())
                        }
                    }

                    override fun onFailure(t: Throwable) {
                        continuation.resumeWithException(t)
                    }
                },
                executor)

            displaySurfaceProvider.onSurfaceRequested(displaySurfaceRequest)
        }
}