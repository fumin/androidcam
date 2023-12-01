package com.topunion.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File


class MainActivity : AppCompatActivity() {
    private var permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.WAKE_LOCK)

    private lateinit var preview: Preview
    private lateinit var previewView: PreviewView

    private lateinit var configPage: ConfigPage
    private lateinit var statusPage: StatusPage

    // Bindings to VideoRecordingService.
    lateinit var videoRecordingService: VideoRecordingService
    private val videoRecordingServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as VideoRecordingService.LocalBinder
            videoRecordingService = binder.getService()
            onVideoRecordingServiceBound()
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            // videoRecordingService = null
        }
    }
    @SuppressLint("SourceLockedOrientationActivity", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.preview = Preview.Builder().build()
        this.previewView = PreviewView(this)

        val db = DBHelper(this).readableDatabase
        val cfg = DBHelper.readConfig(db)
        if (cfg.cameraID == "") {
            cfg.cameraID = "myCamera"
        }
        if (cfg.uploadPath == "") {
            cfg.uploadPath = "http://10.0.2.2:8080/UploadVideo"
        }

        this.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        // Remove stupid bar at the top.
        this.supportActionBar?.hide()

        this.configPage = ConfigPage.new(this, cfg)
        this.setContentView(this.configPage.contentView)

        val activity = this
        val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var isGranted = true
            for ((p, ok) in permissions.entries) {
                if (p in activity.permissions && !ok) {
                    Log.v("TAG", "%s not granted".format(p))
                    isGranted = false
                }
            }

            if (isGranted) {
                activity.onHasPermission()
            } else {
                Toast.makeText(activity, "Permission request denied", Toast.LENGTH_LONG).show()
            }
        }
        this.configPage.okBtn.setOnClickListener { _ ->
            cfg.cameraID = this.configPage.cameraIDInput.text.toString()
            cfg.uploadPath = this.configPage.serverInput.text.toString()

            // Write config.
            val dbw = DBHelper(this).writableDatabase
            var values = ContentValues().apply {
                put(DBHelper.ColConfigKey, DBHelper.ConfigCameraID)
                put(DBHelper.ColConfigValue, cfg.cameraID)
            }
            dbw.insertWithOnConflict(DBHelper.TableConfig, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            values = ContentValues().apply {
                put(DBHelper.ColConfigKey, DBHelper.ConfigUploadPath)
                put(DBHelper.ColConfigValue, cfg.uploadPath)
            }
            dbw.insertWithOnConflict(DBHelper.TableConfig, null, values, SQLiteDatabase.CONFLICT_REPLACE)

            activity.askPermission(requestPermissionLauncher)
        }
    }

    override fun onStart() {
        super.onStart()
        this.preview.setSurfaceProvider(this.previewView.surfaceProvider)
    }
    override fun onStop() {
        super.onStop()

        // It is very IMPORTANT that we set the surface to null,
        // or else stupid Android stops our video recording!
        this.preview.setSurfaceProvider(null)
    }

    private fun askPermission(requestPermissionLauncher: ActivityResultLauncher<Array<String>>) {
        if (this.hasPermissions()) {
            this.onHasPermission()
            return
        }
        requestPermissionLauncher.launch(this.permissions)
    }
    @SuppressLint("SetTextI18n", "ClickableViewAccessibility")
    fun onHasPermission() {
        val layout = FrameLayout(this)
        this.setContentView(layout)

        val clientSize = Util.getClientSize(this)
        var params = FrameLayout.LayoutParams(clientSize.x, clientSize.y)
        params.leftMargin = 0
        params.topMargin = 0
        this.previewView.layoutParams = params
        layout.addView(this.previewView)

        this.statusPage = StatusPage.new(this)
        params = FrameLayout.LayoutParams(clientSize.x, clientSize.y/2)
        params.leftMargin = 0
        params.topMargin = 0
        this.statusPage.contentView.layoutParams = params
        this.statusPage.contentView.visibility = GONE
        val activity = this
        this.statusPage.zll.setOnTouchListener { _, _ ->
            this.statusPage.zll.init(this@MainActivity)
            false
        }
        layout.addView(this.statusPage.contentView)

        val btn = Button(this)
        val btnWidth = clientSize.x/4
        params = FrameLayout.LayoutParams(btnWidth, clientSize.y/5)
        params.leftMargin = clientSize.x - btnWidth
        params.topMargin = 0
        btn.layoutParams = params
        btn.text = "show/hide"
        btn.setOnClickListener { _ -> activity.showHideClick(this.statusPage) }
        layout.addView(btn)

        this.startForegroundService(Intent(this, VideoRecordingService::class.java))
        val intent = Intent(this, VideoRecordingService::class.java)
        this.bindService(intent, this.videoRecordingServiceConnection, Context.BIND_AUTO_CREATE)
    }

    fun onVideoRecordingServiceBound() {
        val csBuilder = CameraSelector.Builder()
        csBuilder.requireLensFacing(CameraSelector.LENS_FACING_BACK)
        val cameraSelector = csBuilder.build()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val activity = this
        cameraProviderFuture.addListener({
            val camProvider =  cameraProviderFuture.get()
            camProvider.bindToLifecycle(
                activity.videoRecordingService, cameraSelector, activity.preview)
            this.videoRecordingService.onCameraProvider(camProvider)
            this.videoRecordingService.startRecord()
        }, ContextCompat.getMainExecutor(this))

        this.statusPage.cmdInput.setOnKeyListener(View.OnKeyListener { _, keyCode, event ->
            val cmd = this.statusPage.cmdInput.text.toString()
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                if (cmd == "stop") {
                    this.videoRecordingService.stopRecord()
                }
                return@OnKeyListener true
            }
            false
        })
    }

    private fun showHideClick(statusPage: StatusPage) {
        val videoFiles = File(this.videoRecordingService.videoDir).listFiles()
        val numVideos = videoFiles?.size ?: 0

        var uploadErrs = emptyArray<UploadError>()
        uploadErrs = this.videoRecordingService.uploadErrs.all(uploadErrs)

        statusPage.update(this.videoRecordingService.cfg, numVideos, uploadErrs)
        if (statusPage.contentView.visibility == VISIBLE) {
            statusPage.contentView.visibility = GONE
        } else {
            statusPage.contentView.visibility = VISIBLE
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