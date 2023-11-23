package com.topunion.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Point
import android.os.Bundle
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner


class MainActivity : AppCompatActivity() {
    private var permissions = arrayOf(Manifest.permission.CAMERA)
    private lateinit var layout: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var textView: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (this.hasPermissions()) {
            this.onHasPermission()
            return
        }
        val activity = this
        val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var isGranted = true
            for ((p, ok) in permissions.entries) {
                if (p in activity.permissions && !ok) {
                    isGranted = false
                }
            }

            if (isGranted) {
                this.onHasPermission()
            } else {
                Toast.makeText(activity, "Permission request denied", Toast.LENGTH_LONG).show()
            }
        }
        requestPermissionLauncher.launch(this.permissions)
    }
    @SuppressLint("SourceLockedOrientationActivity", "SetTextI18n")
    fun onHasPermission() {
        this.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        // Remove stupid bar at the top.
        this.supportActionBar?.hide()
        val clientSize = getClientSize(this)
        this.layout = FrameLayout(this)

        this.previewView = PreviewView(this)
        var params = FrameLayout.LayoutParams(clientSize.x, clientSize.y)
        params.leftMargin = 0
        params.topMargin = 0
        this.previewView.layoutParams = params
        layout.addView(this.previewView)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val activity = this
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            activity.bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this))

        this.textView = TextView(this)
        params = FrameLayout.LayoutParams(clientSize.x, clientSize.y/2)
        params.leftMargin = 0
        params.topMargin = 0
        this.textView.layoutParams = params
        this.textView.text = "hello world"
        this.textView.setBackgroundColor(Color.rgb(255, 0, 0))
        this.layout.addView(this.textView)

        val btn = Button(this)
        val btnWidth = clientSize.x/4
        params = FrameLayout.LayoutParams(btnWidth, clientSize.y/5)
        params.leftMargin = clientSize.x - btnWidth
        params.topMargin = 0
        btn.layoutParams = params
        btn.text = "show/hide"

        btn.setOnClickListener { _ -> activity.showHideClick() }
        this.layout.addView(btn)

        this.setContentView(this.layout)

        Log.v("TAG", "onCreate")
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        Log.v("TAG", "bindPreview")
        val csBuilder = CameraSelector.Builder()
        csBuilder.requireLensFacing(CameraSelector.LENS_FACING_BACK)
        val cameraSelector = csBuilder.build()

        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(this.previewView.surfaceProvider)

        val camera = cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, preview)
        Log.v("TAG", camera.toString())
    }

    private fun showHideClick() {
        Log.v("TAG", "click")
        if (this.textView.visibility == VISIBLE) {
            this.textView.visibility = GONE
        } else {
            this.textView.visibility = VISIBLE
        }
    }

    private fun hasPermissions(): Boolean {
        for (p in this.permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }
}
fun getClientSize(activity: Activity): Point {
    val displaySize = Point()
    @Suppress("DEPRECATION")
    activity.windowManager.defaultDisplay.getRealSize(displaySize)
    return displaySize
}