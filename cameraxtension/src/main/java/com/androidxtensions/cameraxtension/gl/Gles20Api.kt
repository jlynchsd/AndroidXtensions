package com.androidxtensions.cameraxtension.gl

import android.opengl.GLES20
import java.nio.Buffer

/**
 * GLES20 exposes many native methods that are difficult to mock.  Wrap them in a kotlin object.
 */
object Gles20Api {

    fun glGetAttribLocation(program: Int, name: String) = GLES20.glGetAttribLocation(program, name)

    fun glGetUniformLocation(program: Int, name: String) = GLES20.glGetUniformLocation(program, name)


    fun glUniformMatrix4fv(location: Int, count: Int, transpose: Boolean, value: FloatArray, offset: Int) =
        GLES20.glUniformMatrix4fv(location, count, transpose, value, offset)


    fun glEnableVertexAttribArray(index: Int) = GLES20.glEnableVertexAttribArray(index)

    fun glDisableVertexAttribArray(index: Int) = GLES20.glDisableVertexAttribArray(index)

    fun glVertexAttribPointer(indx: Int, size: Int, type: Int, normalized: Boolean, stride: Int, ptr: Buffer) =
        GLES20.glVertexAttribPointer(indx, size, type, normalized, stride, ptr)

    fun glDrawArrays(mode: Int, first: Int, count: Int) = GLES20.glDrawArrays(mode, first, count)


    fun glActiveTexture(texture: Int) = GLES20.glActiveTexture(texture)

    fun glGenTextures(n: Int, textures: IntArray, offset: Int) = GLES20.glGenTextures(n, textures, offset)

    fun glBindTexture(target: Int, texture: Int) = GLES20.glBindTexture(target, texture)

    fun glTexParameterf(target: Int, pname: Int, param: Float) = GLES20.glTexParameterf(target, pname, param)

    fun glTexParameteri(target: Int, pname: Int, param: Int) = GLES20.glTexParameteri(target, pname, param)


    fun glCreateProgram() = GLES20.glCreateProgram()

    fun glUseProgram(program: Int) = GLES20.glUseProgram(program)

    fun glLinkProgram(program: Int) = GLES20.glLinkProgram(program)

    fun glGetProgramiv(program: Int, pname: Int, params: IntArray, offset: Int) =
        GLES20.glGetProgramiv(program, pname, params, offset)

    fun glDeleteProgram(program: Int) = GLES20.glDeleteProgram(program)


    fun glCreateShader(type: Int) = GLES20.glCreateShader(type)

    fun glShaderSource(shader: Int, string: String) = GLES20.glShaderSource(shader, string)

    fun glCompileShader(shader: Int) = GLES20.glCompileShader(shader)

    fun glGetShaderiv(shader: Int, pname: Int, params: IntArray, offset: Int) =
        GLES20.glGetShaderiv(shader, pname, params, offset)

    fun glAttachShader(program: Int, shader: Int) = GLES20.glAttachShader(program, shader)

    fun glDeleteShader(shader: Int) = GLES20.glDeleteShader(shader)


    fun glViewport(x: Int, y: Int, width: Int, height: Int) = GLES20.glViewport(x, y, width, height)

    fun glPixelStorei(pname: Int, param: Int) = GLES20.glPixelStorei(pname, param)

    fun glReadPixels(x: Int, y: Int, width: Int, height: Int, format: Int, type: Int, pixels: Buffer) =
        GLES20.glReadPixels(x, y, width, height, format, type, pixels)

    fun glGetError() = GLES20.glGetError()
}