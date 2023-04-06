package com.androidxtensions.cameraxtension.gl

import android.opengl.EGL14
import android.view.Surface

/**
 * Defines mapping between EGL surfaces and Android window surfaces.
 */
internal class BoundEglSurface(
    private val eglApi: EglApi,
    private val nativeSurface: Surface,
    private val releaseNativeSurface: Boolean) {

    private val eglSurface = eglApi.createWindowSurface(nativeSurface)

    val width: Int
        get() = eglApi.querySurface(eglSurface, EGL14.EGL_WIDTH)

    val height: Int
        get() = eglApi.querySurface(eglSurface, EGL14.EGL_HEIGHT)

    fun makeCurrent() {
        eglApi.makeCurrent(eglSurface)
    }

    fun swapBuffers(): Boolean {
        return eglApi.swapBuffers(eglSurface)
    }

    fun release() {
        eglApi.destroySurface(eglSurface)
        if (releaseNativeSurface) {
            nativeSurface.release()
        }
    }
}