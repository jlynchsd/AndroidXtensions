package com.androidxtensions.cameraxtension.gl

import android.opengl.EGL14
import android.opengl.EGLSurface
import android.view.Surface
import io.mockk.*
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BoundEglSurfaceTest {

    @Test
    fun `when created queries dimensions from surface`() {
        val mockEglApi = mockEglApi()
        val mockSurface = mockk<Surface>()

        every {
            mockEglApi.querySurface(any(), EGL14.EGL_WIDTH)
        } returns 1920

        every {
            mockEglApi.querySurface(any(), EGL14.EGL_HEIGHT)
        } returns 1080

        val boundEglSurface = BoundEglSurface(mockEglApi, mockSurface, true)

        Assert.assertEquals(1920, boundEglSurface.width)
        Assert.assertEquals(1080, boundEglSurface.height)
    }

    @Test
    fun `when making current delegates to EglApi`() {
        val mockEglApi = mockEglApi()
        val mockSurface = mockk<Surface>()

        every { mockEglApi.makeCurrent(any()) } just runs

        val boundEglSurface = BoundEglSurface(mockEglApi, mockSurface, true)

        boundEglSurface.makeCurrent()

        verify { mockEglApi.makeCurrent(any()) }
    }

    @Test
    fun `when swapping buffers delegates to EglApi`() {
        val mockEglApi = mockEglApi()
        val mockSurface = mockk<Surface>()

        every { mockEglApi.swapBuffers(any()) } returns true

        val boundEglSurface = BoundEglSurface(mockEglApi, mockSurface, true)

        boundEglSurface.swapBuffers()

        verify { mockEglApi.swapBuffers(any()) }
    }

    @Test
    fun `when releasing only egl destroys elg surface but does not release android surface`() {
        val mockEglApi = mockEglApi()
        val mockSurface = mockk<Surface>()

        every { mockEglApi.destroySurface(any()) } just runs
        every { mockSurface.release() } just runs

        val boundEglSurface = BoundEglSurface(mockEglApi, mockSurface, false)

        boundEglSurface.release()

        verify { mockEglApi.destroySurface(any()) }
        verify(exactly = 0) { mockSurface.release() }
    }

    @Test
    fun `when releasing both egl and android surface destroys elg surface and releases android surface`() {
        val mockEglApi = mockEglApi()
        val mockSurface = mockk<Surface>()

        every { mockEglApi.destroySurface(any()) } just runs
        every { mockSurface.release() } just runs

        val boundEglSurface = BoundEglSurface(mockEglApi, mockSurface, true)

        boundEglSurface.release()

        verify { mockEglApi.destroySurface(any()) }
        verify { mockSurface.release() }
    }

    private fun mockEglApi(): EglApi {
        val mockEglApi = mockk<EglApi>()
        val mockEglSurface = mockk<EGLSurface>()

        every {
            mockEglApi.createWindowSurface(any())
        } returns mockEglSurface

        return mockEglApi
    }
}