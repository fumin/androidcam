package com.topunion.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Point
import android.os.Bundle
import android.os.IBinder
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

class MainActivity : AppCompatActivity() {
    private var permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WAKE_LOCK)
    private lateinit var layout: FrameLayout

    private lateinit var previewView: PreviewView
    private lateinit var preview: Preview
    private lateinit var cameraSelector: CameraSelector
    private lateinit var cameraProvider: ProcessCameraProvider

    private lateinit var textView: TextView

    // Bindings to VideoRecordingService.
    private lateinit var videoRecordingService: VideoRecordingService
    private var videoRecordingServiceBound: Boolean = false
    private val videoRecordingServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as VideoRecordingService.LocalBinder
            videoRecordingService = binder.getService()
            videoRecordingServiceBound = true
            onVideoRecordingServiceBound()
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            videoRecordingServiceBound = false
        }
    }
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

    override fun onStart() {
        super.onStart()

        Intent(this, VideoRecordingService::class.java).also { intent ->
            bindService(intent, this.videoRecordingServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }
    override fun onStop() {
        super.onStop()

        this.cameraProvider.unbind(this.preview)

        unbindService(this.videoRecordingServiceConnection)
        this.videoRecordingServiceBound = false
    }
    @SuppressLint("SourceLockedOrientationActivity", "SetTextI18n")
    fun onHasPermission() {
        this.startForegroundService(Intent(this, VideoRecordingService::class.java))

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

        val csBuilder = CameraSelector.Builder()
        csBuilder.requireLensFacing(CameraSelector.LENS_FACING_BACK)
        this.cameraSelector = csBuilder.build()
        this.preview = Preview.Builder().build()
        this.preview.setSurfaceProvider(this.previewView.surfaceProvider)

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

        val activity = this
        btn.setOnClickListener { _ -> activity.showHideClick() }
        this.layout.addView(btn)

        this.setContentView(this.layout)

        Log.v("TAG", "onCreate")
    }

    fun onVideoRecordingServiceBound() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            this.cameraProvider = cameraProviderFuture.get()
            this.cameraProvider.bindToLifecycle(this.videoRecordingService, this.cameraSelector, this.preview)
        }, ContextCompat.getMainExecutor(this))
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