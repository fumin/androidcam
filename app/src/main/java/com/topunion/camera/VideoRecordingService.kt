package com.topunion.camera

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.time.Instant

class VideoRecordingService : LifecycleService() {
    inner class LocalBinder : Binder() {
        fun getService(): VideoRecordingService = this@VideoRecordingService
    }
    private val binder = LocalBinder()
    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    private lateinit var recorder: Recorder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val serviceID = 100
        val channelId = this.createNotificationChannel("my_service", "My Background Service")
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("My Awesome App")
            .build()
        var serviceType = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        ServiceCompat.startForeground(this, serviceID, notification, serviceType)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val service = this
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            service.onCameraProvider(cameraProvider)
        }, ContextCompat.getMainExecutor(this))

        return START_STICKY
    }

    private fun onCameraProvider(cameraProvider: ProcessCameraProvider){
        val csBuilder = CameraSelector.Builder()
        csBuilder.requireLensFacing(CameraSelector.LENS_FACING_BACK)
        val cameraSelector = csBuilder.build()

        val qualitySelector = QualitySelector.fromOrderedList(
            listOf(Quality.HD, Quality.SD),
            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
        this.recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
        val videoCapture = VideoCapture.withOutput(this.recorder)

        cameraProvider.bindToLifecycle(this, cameraSelector, videoCapture)

        Thread {this.runForever()}.start()
    }

    private fun runForever() {
        Log.v("TAG", "runForever")

        val wakeLock: PowerManager.WakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag").apply {
                    val minute = 60*1000L
                    acquire(365*24*60*minute)
                }
            }

        val faraway = Util.timeDate(9999, Util.December, 31, 23, 59, 59, 0, Util.UTC)
        while (Instant.now().isBefore(faraway)) {
            startVideo(this.recorder)
            Thread.sleep(10*1000L)
            stopVideo(this.recorder)
        }

        wakeLock.release()
    }

    @Suppress("SameParameterValue")
    private fun createNotificationChannel(channelId: String, channelName: String): String{
        val chan = NotificationChannel(channelId,
            channelName, NotificationManager.IMPORTANCE_NONE)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }
}

fun startVideo(recorder: Recorder) {
    val now = Instant.now()
    val tStr = Util.timeFormat20060102150405(now)
    val faraway = Util.timeDate(2073, Util.December, 31, 23, 59, 59, 0, Util.UTC)
    val farawayStr = Util.timeFormat20060102150405(faraway)
    Log.v("TAG", "%s %s".format(tStr, farawayStr))

    Log.v("TAG", recorder.toString())
}

fun stopVideo(recorder: Recorder) {
    Log.v("TAG", recorder.toString())
}