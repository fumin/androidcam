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
import android.util.TypedValue
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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

    private var cfg = Config()

    // Bindings to VideoRecordingService.
    private lateinit var videoRecordingService: VideoRecordingService
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
        this.cfg = DBHelper.readConfig(db)
        if (this.cfg.cameraID == "") {
            this.cfg.cameraID = "myCamera"
        }
        if (this.cfg.uploadPath == "") {
            this.cfg.uploadPath = "http://10.0.2.2:8080/UploadVideo"
        }

        this.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        // Remove stupid bar at the top.
        this.supportActionBar?.hide()

        val scrollView = ScrollView(this)
        this.setContentView(scrollView)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        scrollView.addView(layout)

        val cameraIDLabel = TextView(this)
        layout.addView(cameraIDLabel)
        val clientSize = Util.getClientSize(this)
        cameraIDLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, (clientSize.y/ 10).toFloat())
        cameraIDLabel.text = "Camera name"

        val cameraIDInput = EditText(this)
        layout.addView(cameraIDInput)
        cameraIDInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, (clientSize.y/ 32).toFloat())
        cameraIDInput.setText(cfg.cameraID)

        val serverLabel = TextView(this)
        layout.addView(serverLabel)
        serverLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, (clientSize.y/ 10).toFloat())
        serverLabel.text = "Server"

        val serverInput = EditText(this)
        layout.addView(serverInput)
        serverInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, (clientSize.y/ 32).toFloat())
        serverInput.setText(cfg.uploadPath)

        val blank = TextView(this)
        layout.addView(blank)
        blank.setTextSize(TypedValue.COMPLEX_UNIT_PX, (clientSize.y/ 10).toFloat())

        val okBtn = Button(this)
        layout.addView(okBtn)
        okBtn.setTextSize(TypedValue.COMPLEX_UNIT_PX, (clientSize.y/ 10).toFloat())
        okBtn.text = "OK"
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
                this.onHasPermission(this.cfg)
            } else {
                Toast.makeText(activity, "Permission request denied", Toast.LENGTH_LONG).show()
            }
        }
        okBtn.setOnClickListener { _ ->
            this.cfg.cameraID = cameraIDInput.text.toString()
            this.cfg.uploadPath = serverInput.text.toString()
            activity.askPermission(requestPermissionLauncher, this.cfg)
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

    private fun askPermission(requestPermissionLauncher: ActivityResultLauncher<Array<String>>, cfg: Config) {
        if (this.hasPermissions()) {
            this.onHasPermission(cfg)
            return
        }
        requestPermissionLauncher.launch(this.permissions)
    }
    @SuppressLint("SetTextI18n", "ClickableViewAccessibility")
    fun onHasPermission(cfg: Config) {
        // Write config.
        val db = DBHelper(this).writableDatabase
        var values = ContentValues().apply {
            put(DBHelper.ColConfigKey, DBHelper.ConfigCameraID)
            put(DBHelper.ColConfigValue, cfg.cameraID)
        }
        db.insertWithOnConflict(DBHelper.TableConfig, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        values = ContentValues().apply {
            put(DBHelper.ColConfigKey, DBHelper.ConfigUploadPath)
            put(DBHelper.ColConfigValue, cfg.uploadPath)
        }
        db.insertWithOnConflict(DBHelper.TableConfig, null, values, SQLiteDatabase.CONFLICT_REPLACE)

        val layout = FrameLayout(this)
        this.setContentView(layout)

        val clientSize = Util.getClientSize(this)
        var params = FrameLayout.LayoutParams(clientSize.x, clientSize.y)
        params.leftMargin = 0
        params.topMargin = 0
        this.previewView.layoutParams = params
        layout.addView(this.previewView)

        val statusPage = StatusPage.new(this)
        params = FrameLayout.LayoutParams(clientSize.x, clientSize.y/2)
        params.leftMargin = 0
        params.topMargin = 0
        statusPage.contentView.layoutParams = params
        statusPage.contentView.visibility = GONE
        val activity = this
        statusPage.zll.setOnTouchListener { _, _ ->
            statusPage.zll.init(this@MainActivity)
            false
        }
        layout.addView(statusPage.contentView)

        val btn = Button(this)
        val btnWidth = clientSize.x/4
        params = FrameLayout.LayoutParams(btnWidth, clientSize.y/5)
        params.leftMargin = clientSize.x - btnWidth
        params.topMargin = 0
        btn.layoutParams = params
        btn.text = "show/hide"
        btn.setOnClickListener { _ -> activity.showHideClick(statusPage) }
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
            cameraProviderFuture.get().bindToLifecycle(activity.videoRecordingService, cameraSelector, activity.preview)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showHideClick(statusPage: StatusPage) {
        val videoFiles = File(this.videoRecordingService.videoDir).listFiles()
        val numVideos = videoFiles?.size ?: 0

        var uploadErrs = emptyArray<UploadError>()
        uploadErrs = this.videoRecordingService.uploadErrs.all(uploadErrs)

        statusPage.update(this.cfg, numVideos, uploadErrs)
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