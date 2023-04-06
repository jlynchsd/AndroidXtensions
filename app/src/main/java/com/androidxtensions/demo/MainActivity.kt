package com.androidxtensions.demo

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.androidxtensions.cameraxtension.CameraConfiguration
import com.androidxtensions.cameraxtension.MirrorAxis
import com.androidxtensions.cameraxtension.TransformationConfiguration
import com.androidxtensions.cameraxtension.setExtendedSurfaceProvider

class MainActivity : AppCompatActivity() {
    private lateinit var cameraPreview: PreviewView
    private lateinit var switchCameraButton: ImageButton
    private lateinit var mirrorCameraButton: ImageButton
    private lateinit var edgeDetectionCameraButton: ImageButton

    private var useBackCamera = true
    private var mirrorCamera = false
    private var edgeDetection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraPreview = findViewById(R.id.cameraPreview)
        switchCameraButton = findViewById(R.id.switchCamera)
        mirrorCameraButton = findViewById(R.id.mirrorCamera)
        edgeDetectionCameraButton = findViewById(R.id.edgeDetectCamera)

        switchCameraButton.setOnClickListener {
            useBackCamera = !useBackCamera
            startCamera()
        }

        mirrorCameraButton.setOnClickListener {
            mirrorCamera = !mirrorCamera
            startCamera()
        }

        edgeDetectionCameraButton.setOnClickListener {
            edgeDetection = !edgeDetection
            startCamera()
        }
    }

    override fun onResume() {
        super.onResume()

        startCamera()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (allPermissionsGranted()) {
            launchCamera()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        // Request camera permissions
        if (allPermissionsGranted()) {
            launchCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun launchCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val cameraSelector = if (useBackCamera) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }

            val preview = Preview.Builder().build()

            val mirror = if (mirrorCamera) {
                MirrorAxis.HORIZONTALLY
            } else {
                MirrorAxis.NONE
            }
            val configuration = CameraConfiguration.Builder()
                .transformPreview(
                    TransformationConfiguration.Builder().
                            applyMirror(mirror)
                        .applyEdgeDetection(edgeDetection)
                        .build()
                )
                .build()
            preview.setExtendedSurfaceProvider(cameraPreview.surfaceProvider, this, configuration)

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview)
            } catch(exc: Exception) {
                Log.e("LoadCamera", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    companion object {

        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).toTypedArray()
    }

}