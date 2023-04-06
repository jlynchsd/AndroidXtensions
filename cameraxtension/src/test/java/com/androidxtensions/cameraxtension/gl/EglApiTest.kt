package com.androidxtensions.cameraxtension.gl

import android.opengl.EGL14
import android.opengl.EGLConfig
import io.mockk.*
import org.junit.After
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EglApiTest {

    @After
    fun cleanup() {
        unmockkAll()
    }

    @Test(expected = RuntimeException::class)
    fun `when created with no default display throws runtime exception`() {
        mockkStatic(EGL14::class)
        every {
            EGL14.eglGetDisplay(any())
        } returns EGL14.EGL_NO_DISPLAY

        val eglApi = EglApi()
    }

    @Test(expected = RuntimeException::class)
    fun `when created and unable to initialize egl display throws runtime exception`() {
        mockEgl14()
        every {
            EGL14.eglInitialize(any(), any(), any(), any(), any())
        } returns false

        val eglApi = EglApi()
    }

    @Test(expected = RuntimeException::class)
    fun `when unable to create egl config does not try and create context and throws runtime exception`() {
        mockEgl14()
        every {
            EGL14.eglChooseConfig(any(), any(), any(), any(), any(), any(), any(), any())
        } returns false

        val eglApi = EglApi()

        verify(exactly = 0) { EGL14.eglCreateContext(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `when created creates egl context`() {
        mockEgl14()
        val eglApi = EglApi()

        verify { EGL14.eglCreateContext(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `when unable to create egl3 context falls back to egl2`() {
        mockEgl14(errors = listOf(EGL14.EGL_BAD_CONTEXT, EGL14.EGL_SUCCESS))
        val eglApi = EglApi()

        Assert.assertEquals(2, eglApi.glVersion)
    }

    @Test(expected = RuntimeException::class)
    fun `when unable to create context falls throws runtime exception`() {
        mockEgl14(errors = listOf(EGL14.EGL_BAD_CONTEXT, EGL14.EGL_BAD_CONTEXT))
        val eglApi = EglApi()
    }

    @Test
    fun `when released cleans up resources and updates status`() {
        mockEgl14()
        every {
            EGL14.eglMakeCurrent(any(), any(), any(), any())
        } returns true
        every {
            EGL14.eglDestroyContext(any(), any())
        } returns true
        every {
            EGL14.eglReleaseThread()
        } returns true
        every {
            EGL14.eglTerminate(any())
        } returns true

        val eglApi = EglApi()
        Assert.assertFalse(eglApi.released)

        eglApi.release()

        Assert.assertTrue(eglApi.released)
        verify { EGL14.eglMakeCurrent(any(), EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT) }
        verify { EGL14.eglDestroyContext(any(), any()) }
        verify { EGL14.eglReleaseThread() }
        verify { EGL14.eglTerminate(any()) }
    }

    @Test
    fun `when destroying surface delegates to Egl14`() {
        mockEgl14()
        every {
            EGL14.eglDestroySurface(any(), any())
        } returns true

        val eglApi = EglApi()

        eglApi.destroySurface(mockk())

        verify { EGL14.eglDestroySurface(any(), any()) }
    }

    @Test
    fun `when creating window surface delegates to Egl14`() {
        mockEgl14()
        every {
            EGL14.eglCreateWindowSurface(any(), any(), any(), any(), any())
        } returns mockk()

        val eglApi = EglApi()

        eglApi.createWindowSurface(mockk())

        verify { EGL14.eglCreateWindowSurface(any(), any(), any(), any(), any()) }
    }

    @Test(expected = RuntimeException::class)
    fun `when creating window surface raises error throws runtime exception`() {
        mockEgl14(errors = listOf(EGL14.EGL_SUCCESS, EGL14.EGL_BAD_SURFACE))
        every {
            EGL14.eglCreateWindowSurface(any(), any(), any(), any(), any())
        } returns mockk()

        val eglApi = EglApi()

        eglApi.createWindowSurface(mockk())
    }

    @Test(expected = RuntimeException::class)
    fun `when creating window surface and surface is null throws runtime exception`() {
        mockEgl14()
        every {
            EGL14.eglCreateWindowSurface(any(), any(), any(), any(), any())
        } returns null

        val eglApi = EglApi()

        eglApi.createWindowSurface(mockk())
    }

    @Test
    fun `when creating pbuffer surface delegates to Egl14`() {
        mockEgl14()
        every {
            EGL14.eglCreatePbufferSurface(any(), any(), any(), any())
        } returns mockk()

        val eglApi = EglApi()

        eglApi.createPbufferSurface(0, 0)

        verify { EGL14.eglCreatePbufferSurface(any(), any(), any(), any()) }
    }

    @Test(expected = RuntimeException::class)
    fun `when creating pbuffer surface raises error throws runtime exception`() {
        mockEgl14(errors = listOf(EGL14.EGL_SUCCESS, EGL14.EGL_BAD_SURFACE))
        every {
            EGL14.eglCreatePbufferSurface(any(), any(), any(), any())
        } returns mockk()

        val eglApi = EglApi()

        eglApi.createPbufferSurface(0, 0)
    }

    @Test(expected = RuntimeException::class)
    fun `when creating pbuffer surface and surface is null throws runtime exception`() {
        mockEgl14()
        every {
            EGL14.eglCreatePbufferSurface(any(), any(), any(), any())
        } returns null

        val eglApi = EglApi()

        eglApi.createPbufferSurface(0, 0)
    }

    @Test
    fun `when making current delegates to Egl14`() {
        mockEgl14()
        every {
            EGL14.eglMakeCurrent(any(), any(), any(), any())
        } returns true

        val eglApi = EglApi()

        eglApi.makeCurrent(mockk())

        verify { EGL14.eglMakeCurrent(any(), any(), any(), any()) }
    }

    @Test(expected = RuntimeException::class)
    fun `when making current fails throws runtime exception`() {
        mockEgl14()
        every {
            EGL14.eglMakeCurrent(any(), any(), any(), any())
        } returns false

        val eglApi = EglApi()

        eglApi.makeCurrent(mockk())
    }

    @Test
    fun `when swapping buffers delegates to Egl14`() {
        mockEgl14()
        every {
            EGL14.eglSwapBuffers(any(), any())
        } returns true

        val eglApi = EglApi()

        eglApi.swapBuffers(mockk())

        verify { EGL14.eglSwapBuffers(any(), any()) }
    }

    @Test
    fun `when querying surface delegates to Egl14`() {
        mockEgl14()
        every {
            EGL14.eglQuerySurface(any(), any(), any(), any(), any())
        } returns true

        val eglApi = EglApi()

        eglApi.querySurface(mockk(), 0)

        verify { EGL14.eglQuerySurface(any(), any(), any(), any(), any()) }
    }
}