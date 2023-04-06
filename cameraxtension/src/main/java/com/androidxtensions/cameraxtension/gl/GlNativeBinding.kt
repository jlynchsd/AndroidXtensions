package com.androidxtensions.cameraxtension.gl

internal object GlNativeBinding {
    init {
        System.loadLibrary("cameraxtension")
    }

    external fun readPixelsToBuffer(x: Int, y: Int, height: Int, width: Int, format: Int, type: Int)

    external fun rgbaToPackedArgb(width: Int, height: Int, input: ByteArray, output: IntArray)
}