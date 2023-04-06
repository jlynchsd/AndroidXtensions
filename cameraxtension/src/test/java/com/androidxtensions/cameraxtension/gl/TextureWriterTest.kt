package com.androidxtensions.cameraxtension.gl

import android.opengl.GLES20
import com.androidxtensions.cameraxtension.TransformationConfiguration
import io.mockk.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TextureWriterTest {

    @After
    fun cleanup() {
        unmockkAll()
    }

    @Test
    fun `when created with default shader compiles shader and program`() {
        mockGles20()
        val textureWriter = TextureWriter(0, TransformationConfiguration.Builder().build())

        verify { Gles20Api.glCompileShader(any()) }
        verify { Gles20Api.glCreateProgram() }
    }

    @Test
    fun `when created with edge detection shader compiles shader and program`() {
        mockGles20()
        val textureWriter = TextureWriter(0, TransformationConfiguration.Builder().applyEdgeDetection().build())

        verify { Gles20Api.glCompileShader(any()) }
        verify { Gles20Api.glCreateProgram() }
    }

    @Test(expected = RuntimeException::class)
    fun `when unable to create shader throws exception`() {
        mockGles20()
        every {
            Gles20Api.glCreateShader(any())
        } returns 0

        val textureWriter = TextureWriter(0, TransformationConfiguration.Builder().build())
    }

    @Test(expected = RuntimeException::class)
    fun `when unable to get shader deletes shader and throws exception`() {
        mockGles20(shaderCompiled = false)

        val textureWriter = TextureWriter(0, TransformationConfiguration.Builder().build())

        verify { Gles20Api.glDeleteShader(any()) }
    }

    @Test(expected = RuntimeException::class)
    fun `when unable to get program deletes program and throws exception`() {
        mockGles20(programCompiled = false)

        val textureWriter = TextureWriter(0, TransformationConfiguration.Builder().build())

        verify { Gles20Api.glDeleteProgram(any()) }
    }

    @Test(expected = RuntimeException::class)
    fun `when unable to create program throws exception`() {
        mockGles20()
        every {
            Gles20Api.glCreateProgram()
        } returns 0

        val textureWriter = TextureWriter(0, TransformationConfiguration.Builder().build())
    }

    @Test(expected = RuntimeException::class)
    fun `when unable to get attribute location throws exception`() {
        mockGles20()
        every {
            Gles20Api.glGetAttribLocation(any(), any())
        } returns -1

        val textureWriter = TextureWriter(0, TransformationConfiguration.Builder().build())
    }

    @Test(expected = RuntimeException::class)
    fun `when unable to get uniform location throws exception`() {
        mockGles20()
        every {
            Gles20Api.glGetUniformLocation(any(), any())
        } returns -1

        val textureWriter = TextureWriter(0, TransformationConfiguration.Builder().build())
    }

    @Test(expected = RuntimeException::class)
    fun `when gles error is raised throws exception`() {
        mockGles20()
        every {
            Gles20Api.glGetError()
        } returns GLES20.GL_INVALID_OPERATION

        val textureWriter = TextureWriter(0, TransformationConfiguration.Builder().build())
    }

    @Test
    fun `when creating texture object binds texture and sets parameters`() {
        mockGles20()

        val textureWriter = TextureWriter(0, TransformationConfiguration.Builder().build())
        textureWriter.createTexture()

        verify { Gles20Api.glBindTexture(any(), any()) }
        verify(exactly = 2) { Gles20Api.glTexParameterf(any(), any(), any()) }
        verify(exactly = 2) { Gles20Api.glTexParameteri(any(), any(), any()) }
    }

    @Test
    fun `when drawing sets up the texture, draws, then releases the texture`() {
        mockGles20()

        val textureWriter = TextureWriter(0, TransformationConfiguration.Builder().build())
        textureWriter.draw(3, floatArrayOf(), floatArrayOf())

        verify { Gles20Api.glActiveTexture(any()) }
        verify { Gles20Api.glBindTexture(any(), 3) }

        verify { Gles20Api.glDrawArrays(any(), any(), any()) }

        verify { Gles20Api.glBindTexture(any(), GLES20.GL_NONE) }
        verify { Gles20Api.glUseProgram(GLES20.GL_NONE) }
    }

    @Test
    fun `when released deletes program`() {
        mockGles20()
        val textureWriter = TextureWriter(0, TransformationConfiguration.Builder().build())
        textureWriter.release()

        verify { Gles20Api.glDeleteProgram(any()) }
    }
}