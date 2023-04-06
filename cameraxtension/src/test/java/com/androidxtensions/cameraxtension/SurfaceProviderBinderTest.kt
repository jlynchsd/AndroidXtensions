package com.androidxtensions.cameraxtension

import android.content.Context
import android.content.ContextWrapper
import android.util.ArrayMap
import android.view.Surface
import android.view.SurfaceHolder
import androidx.camera.core.Preview
import androidx.camera.core.Preview.SurfaceProvider
import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.androidxtensions.cameraxtension.gl.GlHandler
import com.androidxtensions.cameraxtension.gl.ShadowSize
import com.androidxtensions.cameraxtension.gl.mockEgl14
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowArrayMap::class, ShadowSize::class])
class SurfaceProviderBinderTest {

    @Before
    fun setup() {
        mockEgl14()
    }

    @After
    fun cleanup() {
        unmockkAll()
    }


    @Test
    fun `when surface requested binds display and camera surface via GlHandler`() = runTest {
        val executorService = Executors.newSingleThreadExecutor()
        val mockGlHandler = mockGlHandler()
        val mockPreview = mockk<Preview>()
        // Waiting till idle doesn't work for suspendCoroutine, so need more aggressive blocking
        val mutex = Mutex(locked = true)
        every {
            mockPreview.setSurfaceProvider(any())
        } answers {
            mutex.unlock()
        }

        val surfaceProviderBinder = SurfaceProviderBinder(
            mockPreview, mockDisplaySurfaceProvider(executorService), mockContext(),
            CameraConfiguration.Builder().build(), executorService.asCoroutineDispatcher(),
            executorService.asCoroutineDispatcher(), Executors.newSingleThreadExecutor(), mockGlHandler
        )

        surfaceProviderBinder.onSurfaceRequested(mockSurfaceRequest())
        mutex.lock()

        verify { mockGlHandler.bindSurface(any()) }

        coVerify { mockGlHandler.getSurfaceProvider(any(), any(), any(), any()) }

        verify { mockPreview.setSurfaceProvider(any()) }
    }

    @Test
    fun `when SurfaceHolderWrapper created with SurfaceHolder produces SurfaceProvider`() = runTest {
        val mockSurfaceHolder = mockSurfaceHolder()
        val mockSurfaceRequest = mockSurfaceRequest()

        // Waiting till idle doesn't work for suspendCoroutine, so need more aggressive blocking
        val mutex = Mutex(locked = true)
        every {
            mockSurfaceRequest.provideSurface(any(), any(), any())
        } answers {
            mutex.unlock()
        }

        val surfaceHolderWrapper = SurfaceHolderWrapper(mockSurfaceHolder, StandardTestDispatcher(testScheduler))
        surfaceHolderWrapper.onSurfaceRequested(mockSurfaceRequest)

        mutex.lock()

        verify { mockSurfaceRequest.provideSurface(any(), any(), any()) }
    }

    @Test
    fun `when surface created before request does nothing`() = runTest {
        val mockSurfaceHolder = mockSurfaceHolder(false, "created")
        val mockSurfaceRequest = mockSurfaceRequest()

        val surfaceHolderWrapper = SurfaceHolderWrapper(mockSurfaceHolder, StandardTestDispatcher(testScheduler))

        verify(exactly = 0) { mockSurfaceRequest.provideSurface(any(), any(), any()) }
    }

    @Test
    fun `when surface changed before request does nothing`() = runTest {
        val mockSurfaceHolder = mockSurfaceHolder(false, "changed")
        val mockSurfaceRequest = mockSurfaceRequest()

        val surfaceHolderWrapper = SurfaceHolderWrapper(mockSurfaceHolder, StandardTestDispatcher(testScheduler))

        verify(exactly = 0) { mockSurfaceRequest.provideSurface(any(), any(), any()) }
    }

    private fun mockContext(): Context {
        val mockContext = mockk<ContextWrapper>()
        val realContext = ApplicationProvider.getApplicationContext<Context>()
        val mockLifecycleOwner = mockk<Context>(moreInterfaces = arrayOf(LifecycleOwner::class))

        every {
            (mockLifecycleOwner as LifecycleOwner).lifecycle
        } returns mockk(relaxed = true)

        every {
            mockContext.baseContext
        } returns mockLifecycleOwner

        val service = slot<String>()
        every {
            mockContext.getSystemService(capture(service))
        } answers {
            realContext.getSystemService(service.captured)
        }

        return mockContext
    }

    private fun mockGlHandler(): GlHandler {
        val mockGlHandler = mockk<GlHandler>()

        every {
            mockGlHandler.bindSurface(any())
        } just runs

        coEvery {
            mockGlHandler.getSurfaceProvider(any(), any(), any(), any())
        } returns mockk()

        every {
            mockGlHandler.updateRotation(any())
        } just  runs

        return mockGlHandler
    }

    private fun mockDisplaySurfaceProvider(executorService: ExecutorService): SurfaceProvider {
        val mockSurfaceProvider = mockk<SurfaceProvider>()

        val surfaceRequest = slot<SurfaceRequest>()
        every {
            mockSurfaceProvider.onSurfaceRequested(capture(surfaceRequest))
        } answers {
            surfaceRequest.captured.provideSurface(mockk(), executorService) {}
        }

        return mockSurfaceProvider
    }

    private fun mockSurfaceRequest(): SurfaceRequest {
        val mockSurfaceRequest = mockk<SurfaceRequest>()
        every {
            mockSurfaceRequest.resolution
        } returns mockk(relaxed = true)

        every {
            mockSurfaceRequest.camera
        } returns mockk(relaxed = true)

        val listener = slot<SurfaceRequest.TransformationInfoListener>()
        every {
            mockSurfaceRequest.setTransformationInfoListener(any(), capture(listener))
        } answers {
            listener.captured.onTransformationInfoUpdate(mockk(relaxed = true))
        }

        return mockSurfaceRequest
    }

    private fun mockSurfaceHolder(valid: Boolean = true, callbackType: String = ""): SurfaceHolder {
        val mockSurfaceHolder = mockk<SurfaceHolder>()
        val mockSurface = mockk<Surface>()

        every {
            mockSurface.isValid
        } returns valid

        val callback = slot<SurfaceHolder.Callback>()
        every {
            mockSurfaceHolder.addCallback(capture(callback))
        } answers {

            when (callbackType) {
                "created" -> callback.captured.surfaceCreated(mockSurfaceHolder)
                "changed" -> callback.captured.surfaceChanged(mockSurfaceHolder, 0, 0, 0)
            }
        }

        every {
            mockSurfaceHolder.setFixedSize(any(), any())
        } just runs

        every {
            mockSurfaceHolder.surface
        } returns mockSurface

        return mockSurfaceHolder
    }
}

// Needed to get the Preview to build
@Implements(ArrayMap::class)
class ShadowArrayMap {

    @Implementation
    fun <K, V> put(key: K, value: V): V = value

    @Implementation
    fun <K> keySet(): Set<K> = emptySet()
}