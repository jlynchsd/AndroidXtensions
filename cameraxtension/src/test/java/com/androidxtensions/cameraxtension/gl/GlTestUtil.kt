package com.androidxtensions.cameraxtension.gl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.util.Size
import io.mockk.*
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

fun mockGles20(
    shaderCompiled: Boolean = true,
    programCompiled: Boolean = true,
    errors: List<Int>? = null
) {
    mockkObject(Gles20Api)
    val gles20Errors = errors?.let { glesErrors ->
        ArrayDeque<Int>().also {
            it.addAll(glesErrors)
        }
    }

    every {
        Gles20Api.glCreateShader(any())
    } returns 1

    every {
        Gles20Api.glShaderSource(any(), any())
    } just runs

    every {
        Gles20Api.glCompileShader(any())
    } just runs

    val compiledShader = slot<IntArray>()
    every {
        Gles20Api.glGetShaderiv(any(), any(), capture(compiledShader), any())
    } answers {
        compiledShader.captured[0] = if (shaderCompiled) {
            1
        } else {
            0
        }
    }

    every {
        Gles20Api.glDeleteShader(any())
    } just runs

    every {
        Gles20Api.glGetError()
    } answers {
        gles20Errors?.removeFirst() ?: GLES20.GL_NO_ERROR
    }

    every {
        Gles20Api.glCreateProgram()
    } returns 1

    every {
        Gles20Api.glAttachShader(any(), any())
    } just runs

    every {
        Gles20Api.glLinkProgram(any())
    } just runs

    val compiledProgram = slot<IntArray>()
    every {
        Gles20Api.glGetProgramiv(any(), any(), capture(compiledProgram), any())
    } answers {
        compiledProgram.captured[0] = if (programCompiled) {
            1
        } else {
            0
        }
    }

    every {
        Gles20Api.glDeleteProgram(any())
    } just runs

    every {
        Gles20Api.glGetAttribLocation(any(), any())
    } returns 1

    every {
        Gles20Api.glGetUniformLocation(any(), any())
    } returns 1
}

fun mockEgl14(
    mockEglConfig: EGLConfig? = mockk(),
    errors: List<Int> = listOf(EGL14.EGL_SUCCESS, EGL14.EGL_SUCCESS, EGL14.EGL_SUCCESS)
) {
    val eglErrors: ArrayDeque<Int> = ArrayDeque<Int>().also {
        it.addAll(errors)
    }
    mockkStatic(EGL14::class)

    every {
        EGL14.eglGetDisplay(any())
    } returns mockk()

    every {
        EGL14.eglInitialize(any(), any(), any(), any(), any())
    } returns true

    every {
        EGL14.eglCreateContext(any(), any(), any(), any(), any())
    } returns mockk()

    every {
        EGL14.eglGetError()
    } answers {
        eglErrors.removeFirst()
    }

    val configs = slot<Array<EGLConfig?>>()
    every {
        EGL14.eglChooseConfig(any(), any(), any(), capture(configs), any(), any(), any(), any())
    } answers {
        configs.captured[0] = mockEglConfig
        true
    }

    every {
        EGL14.eglCreatePbufferSurface(any(), any(), any(), any())
    } returns mockk()

    every {
        EGL14.eglCreateWindowSurface(any(), any(), any(), any(), any())
    } returns mockk()

    every {
        EGL14.eglMakeCurrent(any(), any(), any(), any())
    } returns true

    every {
        EGL14.eglDestroySurface(any(), any())
    } returns true

    every {
        EGL14.eglQueryContext(any(), any(), any(), any(), any())
    } returns true
}

internal fun injectMockVideoRecorder(mockRecorder: VideoRecorder) {
    mockkObject(VideoRecorder)
    every {
        VideoRecorder.getVideoRecorder(any(), any(), any())
    } returns mockRecorder
}

internal fun getMockVideoRecorder(running: Boolean = true): VideoRecorder {
    val mockRecorder = mockk<VideoRecorder>()

    every {
        mockRecorder.inputSurface
    } returns mockk(relaxed = true)

    every {
        mockRecorder.running
    } returns running

    every {
        mockRecorder.start()
    } just runs

    coEvery {
        mockRecorder.release()
    } just runs

    return mockRecorder
}

suspend fun waitTillIdle(executorService: ExecutorService) {
    suspendCoroutine { continuation ->
        val futures = executorService.invokeAll(listOf(Executors.callable {}))
        futures[0].get()
        continuation.resume(Unit)
    }
}

@Implements(Size::class)
class ShadowSize {

    private var width: Int = 0
    private var height: Int = 0

    @Implementation
    fun __constructor__(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    @Implementation
    fun getWidth() = width

    @Implementation
    fun getHeight() = height

    @Implementation
    override fun toString() = ""

}