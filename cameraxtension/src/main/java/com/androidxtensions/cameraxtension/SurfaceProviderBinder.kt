package com.androidxtensions.cameraxtension

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.WindowManager
import androidx.camera.core.Preview
import androidx.camera.core.Preview.SurfaceProvider
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.SurfaceRequest.TransformationInfo
import androidx.camera.core.impl.*
import androidx.camera.core.impl.utils.futures.FutureCallback
import androidx.camera.core.impl.utils.futures.Futures
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.androidxtensions.cameraxtension.gl.GlHandler
import kotlinx.coroutines.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Intermediary class that binds the SurfaceProvider from a PreviewView with the GL SurfaceProvider
 * so that the camera writes to the GL Surface and the data gets passed on to the PreviewView's surface.
 */
internal class SurfaceProviderBinder(
    private val preview: Preview,
    private val displaySurfaceProvider: SurfaceProvider,
    activityContext: Context,
    private val configuration: CameraConfiguration,
    private val processingDispatcher: CoroutineDispatcher,
    private val updatingDispatcher: CoroutineDispatcher,
    private val executorService: ExecutorService,
    private val glHandler: GlHandler
    ) : SurfaceProvider {

    private var deviceRotationDegrees = getDeviceRotation(activityContext)
    private var cameraSensorRotation = 0

    private var displayListener: DisplayManager.DisplayListener? = null
    private var lifecycleObserver: LifecycleObserver? = null

    init {
        configuration.videoSaver?.setGlHandler(glHandler)
        displayListener = getDisplayListener(activityContext)
        lifecycleObserver = getLifecycleObserver(activityContext).also {
            activityContext.lifecycleOwner()?.lifecycle?.addObserver(it)
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onSurfaceRequested(request: SurfaceRequest) {
        MainScope().launch(processingDispatcher) {
            val croppedDisplaySize = configuration.previewTransformation.crop.cropSize(request.resolution)
            // Duplicate the original surface request from the camera, swapping in the cropped dimens
            val displaySurfaceRequest = wrapSurfaceRequest(request, croppedDisplaySize)
            // Pass the modified surface request to the PreviewView so it can create the visible surface
            val displaySurface = getDisplaySurface(displaySurfaceProvider, displaySurfaceRequest)
            // Bind the visible surface so it receives data from the gl surface
            glHandler.bindSurface(displaySurface)
            cameraSensorRotation = displaySurfaceRequest.camera.cameraInfo.sensorRotationDegrees
            // Get the surface provider that provides the gl surface
            val previewSurfaceProvider = glHandler.getSurfaceProvider(
                request.resolution,
                configuration,
                getCameraRotation(),
                displaySurfaceRequest.camera.cameraInfo.sensorRotationDegrees
            )
            // Pass the gl surface provider to the camera
            withContext(updatingDispatcher) {
                preview.setSurfaceProvider(previewSurfaceProvider)
            }
        }
    }

    /**
     * Copies the original surface request into a new one with updated dimensions
     */
    @SuppressLint("RestrictedApi")
    private suspend fun wrapSurfaceRequest(surfaceRequest: SurfaceRequest, croppedSize: Size): SurfaceRequest {
        val displaySurfaceRequest = SurfaceRequest(croppedSize, surfaceRequest.camera) {}

        // Transformation info is how CameraX communicates with PreviewView for its auto-sizing
        val transformationInfo = suspendCoroutine { continuation ->
            surfaceRequest.setTransformationInfoListener(executorService) { transformInfo ->
                continuation.resume(transformInfo)
            }
        }

        val croppedTransformationInfo = TransformationInfo.of(
            Rect(0, 0, croppedSize.width, croppedSize.height),
            transformationInfo.rotationDegrees,
            transformationInfo.targetRotation,
            transformationInfo.hasCameraTransform()
        )

        displaySurfaceRequest.updateTransformationInfo(croppedTransformationInfo)
        return displaySurfaceRequest
    }

    /**
     * Requests the surface from the visible surface provider
     */
    @SuppressLint("RestrictedApi")
    private suspend fun getDisplaySurface(displaySurfaceProvider: SurfaceProvider, displaySurfaceRequest: SurfaceRequest) =
        suspendCoroutine { continuation ->
            Futures.addCallback(displaySurfaceRequest.deferrableSurface.surface,
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
                executorService)

            displaySurfaceProvider.onSurfaceRequested(displaySurfaceRequest)
        }

    private fun getDisplayListener(activityContext: Context) =
        object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}

            override fun onDisplayRemoved(displayId: Int) {}

            override fun onDisplayChanged(displayId: Int) {
                deviceRotationDegrees = getDeviceRotation(activityContext)
                glHandler.updateRotation(getCameraRotation())
            }
        }

    private fun getLifecycleObserver(activityContext: Context) =
        object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                (activityContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).registerDisplayListener(displayListener, null)
            }

            override fun onStop(owner: LifecycleOwner) {
                super.onStop(owner)
                (activityContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).unregisterDisplayListener(displayListener)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                super.onDestroy(owner)
                displayListener = null
                lifecycleObserver = null
                activityContext.lifecycleOwner()?.lifecycle?.removeObserver(this)
            }
        }

    private fun getDeviceRotation(activityContext: Context) =
        when((activityContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            else -> 270
        }

    private fun getCameraRotation() = (cameraSensorRotation - deviceRotationDegrees * -1 + 360) % 360

    companion object {
        fun getSurfaceProviderBinder(
            preview: Preview,
            displaySurfaceProvider: SurfaceProvider,
            activityContext: Context,
            configuration: CameraConfiguration,
            normalizePreview: Boolean
        ): SurfaceProviderBinder {
            return SurfaceProviderBinder(
                preview, displaySurfaceProvider, activityContext, configuration,
                Dispatchers.IO, Dispatchers.Main, Executors.newSingleThreadExecutor(), GlHandler(normalizePreview)
            )
        }
    }
}

/**
 * Wraps a SurfaceHolder in a SurfaceProvider.
 */
internal class SurfaceHolderWrapper(
    private val surfaceHolder: SurfaceHolder,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : SurfaceProvider {
    private var surfaceRequest: SurfaceRequest? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val surfaceHolderCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            surfaceRequest?.let {
                processSurfaceRequest(it)
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            surfaceRequest?.let {
                processSurfaceRequest(it)
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            surfaceRequest?.invalidate()
        }

    }

    init {
        surfaceHolder.addCallback(surfaceHolderCallback)
    }

    override fun onSurfaceRequested(request: SurfaceRequest) {
        surfaceRequest = request
        if (surfaceHolder.surface.isValid) {
            processSurfaceRequest(request)
        }
    }

    private fun processSurfaceRequest(surfaceRequest: SurfaceRequest) {
        MainScope().launch(dispatcher) {
            val transformationInfo = suspendCoroutine { continuation ->
                surfaceRequest.setTransformationInfoListener(executor) { transformInfo ->
                    continuation.resume(transformInfo)
                }
            }
            if (transformationInfo.rotationDegrees == 0 || transformationInfo.rotationDegrees == 180) {
                surfaceHolder.setFixedSize(surfaceRequest.resolution.width, surfaceRequest.resolution.height)
            } else {
                surfaceHolder.setFixedSize(surfaceRequest.resolution.height, surfaceRequest.resolution.width)
            }
            surfaceRequest.provideSurface(surfaceHolder.surface, executor) {}
        }
    }
}

/**
 * Bind the SurfaceProvider and configurations to the preview.
 *
 * @sample com.androidxtensions.samples.cameraxtension.CameraSamples.usePreviewView
 */
fun Preview.setExtendedSurfaceProvider(surfaceProvider: SurfaceProvider, activityContext: Context, configuration: CameraConfiguration) {
    this.setSurfaceProvider(SurfaceProviderBinder.getSurfaceProviderBinder(this, surfaceProvider, activityContext, configuration, false))
}

/**
 * Bind the SurfaceHolder and configurations to the preview.  NOTE: SurfaceHolder's can not scale automatically like
 * SurfaceProviders, and so if the camera's size is different than the SurfaceHolder it will stretch.
 *
 * @sample com.androidxtensions.samples.cameraxtension.CameraSamples.useSurfaceView
 */
fun Preview.setExtendedSurfaceHolder(surfaceHolder: SurfaceHolder, activityContext: Context, configuration: CameraConfiguration) {
    this.setSurfaceProvider(SurfaceProviderBinder.getSurfaceProviderBinder(this, SurfaceHolderWrapper(surfaceHolder), activityContext, configuration,true))
}

private fun Context.lifecycleOwner(): LifecycleOwner? {
    var curContext = this
    var maxDepth = 20
    while (maxDepth-- > 0 && curContext !is LifecycleOwner) {
        curContext = (curContext as ContextWrapper).baseContext
    }
    return if (curContext is LifecycleOwner) {
        curContext
    } else {
        null
    }
}