package com.androidxtensions.samples.cameraxtension

import android.content.Context
import android.view.SurfaceView
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import com.androidxtensions.cameraxtension.*
import com.androidxtensions.cameraxtension.gl.VideoSaver
import java.io.File

class CameraSamples {

    fun usePreviewView(activityContext: Context, previewView: PreviewView) {
        val preview = Preview.Builder().build()
        val configuration = CameraConfiguration.Builder().build()

        preview.setExtendedSurfaceProvider(previewView.surfaceProvider, activityContext, configuration)

        // cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
    }

    fun useSurfaceView(activityContext: Context, surfaceView: SurfaceView) {
        val preview = Preview.Builder().build()
        val configuration = CameraConfiguration.Builder().build()

        // NOTE: SurfaceViews do not support dynamic scaling if the view's size does not match the camera's preview size
        preview.setExtendedSurfaceHolder(surfaceView.holder, activityContext, configuration)

        // cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
    }

    fun recordVideo(activityContext: Context, previewView: PreviewView) {
        val preview = Preview.Builder().build()
        val videoSaver = VideoSaver()
        val configuration = CameraConfiguration.Builder().saveVideo(videoSaver).build()

        preview.setExtendedSurfaceProvider(previewView.surfaceProvider, activityContext, configuration)

        // cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)

        val videoFile = File("/path/to/your/video.mp4")

        // start video
        videoSaver.startRecording(RecordConfiguration.Builder(videoFile).withAudio().build())

        // finish video
        videoSaver.stopRecording()
    }

    fun recordVideoMatchingCameraSize(activityContext: Context, previewView: PreviewView) {
        val preview = Preview.Builder().build()
        val videoSaver = VideoSaver()
        val configuration = CameraConfiguration.Builder().saveVideo(videoSaver).build()

        preview.setExtendedSurfaceProvider(previewView.surfaceProvider, activityContext, configuration)

        // cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)

        val videoFile = File("/path/to/your/video.mp4")

        // start video
        videoSaver.startRecording(
            RecordConfiguration.Builder(
                videoFile,
                VideoConfiguration.default().copy(
                    width = VideoConfiguration.MATCH_WIDTH,
                    height = VideoConfiguration.MATCH_HEIGHT
                )
            ).withAudio().build()
        )

        // finish video
        videoSaver.stopRecording()
    }

    fun applyTransformationToPreview(activityContext: Context, previewView: PreviewView) {
        val preview = Preview.Builder().build()
        val configuration = CameraConfiguration.Builder()
            .transformPreview(
                TransformationConfiguration.Builder().applyMirror(MirrorAxis.BOTH).build()
            ).build()

        preview.setExtendedSurfaceProvider(previewView.surfaceProvider, activityContext, configuration)

        // cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
    }

    fun applyEdgeDetectionToImageAnalysis(activityContext: Context, previewView: PreviewView) {
        val preview = Preview.Builder().build()
        val configuration = CameraConfiguration.Builder()
            .imageAnalysis(TransformationConfiguration.Builder().applyEdgeDetection().build()) { analysisImage ->
                analysisImage.use {
                    // run on background thread
                }
            }.build()

        preview.setExtendedSurfaceProvider(previewView.surfaceProvider, activityContext, configuration)

        // cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
    }
}