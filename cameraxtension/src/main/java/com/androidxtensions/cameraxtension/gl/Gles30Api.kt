package com.androidxtensions.cameraxtension.gl

import android.opengl.GLES30
import java.nio.Buffer
import java.nio.IntBuffer

/**
 * GLES30 exposes many native methods that are difficult to mock.  Wrap them in a kotlin object.
 */
object Gles30Api {

    fun glGenBuffers(n: Int, buffers: IntBuffer) = GLES30.glGenBuffers(n, buffers)

    fun glBindBuffer(target: Int, buffer: Int) = GLES30.glBindBuffer(target, buffer)

    fun glBufferData(target: Int, size: Int, data: Buffer?, usage: Int) =
        GLES30.glBufferData(target, size, data, usage)

    fun glMapBufferRange(target: Int, offset: Int, length: Int, access: Int): Buffer =
        GLES30.glMapBufferRange(target, offset, length, access)

    fun glUnmapBuffer(target: Int) = GLES30.glUnmapBuffer(target)

    fun readPixelsToBuffer(x: Int, y: Int, height: Int, width: Int, format: Int, type: Int) =
        GlNativeBinding.readPixelsToBuffer(x, y, height, width, format, type)
}