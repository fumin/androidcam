package com.topunion.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.graphics.Point
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

class MainActivity : AppCompatActivity() {
    private var permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.WAKE_LOCK)

    private lateinit var preview: Preview
    private var cameraProvider: ProcessCameraProvider? = null

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
    @SuppressLint("SourceLockedOrientationActivity", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.preview = Preview.Builder().build()

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

        val scrollView = ScrollView(this)
        this.setContentView(scrollView)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        scrollView.addView(layout)

        val cameraIDLabel = TextView(this)
        layout.addView(cameraIDLabel)
        val clientSize = getClientSize(this)
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
                this.onHasPermission(cfg)
            } else {
                Toast.makeText(activity, "Permission request denied", Toast.LENGTH_LONG).show()
            }
        }
        okBtn.setOnClickListener { _ ->
            activity.askPermission(requestPermissionLauncher, cfg)
        }
    }

    override fun onStart() {
        super.onStart()

        if (!this.hasPermissions()) {
            return
        }
        val intent = Intent(this, VideoRecordingService::class.java)
        this.bindService(intent, this.videoRecordingServiceConnection, Context.BIND_AUTO_CREATE)
    }
    override fun onStop() {
        super.onStop()

        this.cameraProvider?.unbind(this.preview)
        this.unbindService(this.videoRecordingServiceConnection)
    }

    private fun askPermission(requestPermissionLauncher: ActivityResultLauncher<Array<String>>, cfg: Config) {
        if (this.hasPermissions()) {
            this.onHasPermission(cfg)
            return
        }
        requestPermissionLauncher.launch(this.permissions)
    }
    @SuppressLint("SetTextI18n")
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

        // Launch background jobs in foreground service.
        val intent = Intent(this, VideoRecordingService::class.java)
        this.bindService(intent, this.videoRecordingServiceConnection, Context.BIND_AUTO_CREATE)

        val layout = FrameLayout(this)
        this.setContentView(layout)

        val clientSize = getClientSize(this)
        val previewView = PreviewView(this)
        var params = FrameLayout.LayoutParams(clientSize.x, clientSize.y)
        params.leftMargin = 0
        params.topMargin = 0
        previewView.layoutParams = params
        layout.addView(previewView)
        this.preview.setSurfaceProvider(previewView.surfaceProvider)

        this.textView = TextView(this)
        params = FrameLayout.LayoutParams(clientSize.x, clientSize.y/2)
        params.leftMargin = 0
        params.topMargin = 0
        this.textView.layoutParams = params
        this.textView.text = "hello world"
        this.textView.setBackgroundColor(Color.rgb(255, 0, 0))
        layout.addView(this.textView)

        val btn = Button(this)
        val btnWidth = clientSize.x/4
        params = FrameLayout.LayoutParams(btnWidth, clientSize.y/5)
        params.leftMargin = clientSize.x - btnWidth
        params.topMargin = 0
        btn.layoutParams = params
        btn.text = "show/hide"
        val activity = this
        btn.setOnClickListener { _ -> activity.showHideClick() }
        layout.addView(btn)

        this.startForegroundService(Intent(this, VideoRecordingService::class.java))
    }

    fun onVideoRecordingServiceBound() {
        val csBuilder = CameraSelector.Builder()
        csBuilder.requireLensFacing(CameraSelector.LENS_FACING_BACK)
        val cameraSelector = csBuilder.build()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val activity = this
        cameraProviderFuture.addListener({
            activity.cameraProvider = cameraProviderFuture.get()
            activity.cameraProvider?.bindToLifecycle(
                activity.videoRecordingService, cameraSelector, activity.preview)
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