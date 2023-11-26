package com.topunion.camera

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.MirrorMode.MIRROR_MODE_ON_FRONT_ONLY
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.PrintStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.Arrays
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.Future

class UploadError(var name: String, var err: String)

class VideoRecordingService : LifecycleService() {
    private val mutex = Object()
    private var serverStartTime = Util.timeDate(2006, Util.January, 2, 15, 4, 5, 0, Util.UTC)

    private var cfg = Config()
    var videoDir: String = ""

    private var db: SQLiteDatabase? = null
    val uploadErrs = Deque<UploadError>(32)

    private val videoQueue = ArrayBlockingQueue<String>(1)

    inner class LocalBinder : Binder() {
        fun getService(): VideoRecordingService = this@VideoRecordingService
    }
    private val binder = LocalBinder()
    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Make sure we are foreground.
        val serviceID = 100
        val channelId = this.createNotificationChannel("my_service", "My Background Service")
        val parentStack = TaskStackBuilder.create(this)
            .addNextIntentWithParentStack(Intent(this, MainActivity::class.java))
        val pendingIntent1 = parentStack.getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("My Awesome App")
            .setContentText("recording in background")
            .setContentIntent(pendingIntent1)
            .build()
        var serviceType = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        ServiceCompat.startForeground(this, serviceID, notification, serviceType)

        // Read config.
        this.cfg = DBHelper.readConfig(DBHelper(this).readableDatabase)
        this.db = DBHelper(this).writableDatabase
        // Prepare video directory.
        this.videoDir = File(this.getExternalFilesDir(null), "video").toString()
        File(this.videoDir).mkdirs()

        Thread{this.uploadVideosAfter(Duration.ofMinutes(60))}.start()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val service = this
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            service.onCameraProvider(cameraProvider)
        }, ContextCompat.getMainExecutor(this))

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.v("TAG", "service onDestroy")
    }

    private fun onCameraProvider(cameraProvider: ProcessCameraProvider){
        val csBuilder = CameraSelector.Builder()
        csBuilder.requireLensFacing(CameraSelector.LENS_FACING_BACK)
        val cameraSelector = csBuilder.build()

        val qualitySelector = QualitySelector.fromOrderedList(
            listOf(Quality.HD, Quality.SD),
            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
        val videoCapture = VideoCapture.Builder(recorder)
            .setMirrorMode(MIRROR_MODE_ON_FRONT_ONLY)
            .build()

        cameraProvider.bindToLifecycle(this, cameraSelector, videoCapture)

        Thread {this.recordVideo(videoCapture)}.start()
    }

    private fun recordVideo(videoCapture: VideoCapture<Recorder>) {
        Log.v("TAG", "start recording video")
        val wakeLock: PowerManager.WakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag").apply {
                    val minute = 60*1000L
                    acquire(365*24*60*minute)
                }
            }

        val faraway = Util.timeDate(9999, Util.December, 31, 23, 59, 59, 0, Util.UTC)
        while (Instant.now().isBefore(faraway)) {
            val recording = this.startVideo(videoCapture)
            Thread.sleep(3*1000L)
            recording.stop()
        }

        wakeLock.release()
    }

    private fun startVideo(videoCapture: VideoCapture<Recorder>): Recording {
        val now = Instant.now()
        val tStr = Util.timeFormatSafe(now)
        val filename = "%s.mp4".format(tStr)
        val file = File(this.videoDir, filename)

        val foOptions = FileOutputOptions.Builder(file).build()
        return videoCapture.output
            .prepareRecording(this, foOptions)
            .start(ContextCompat.getMainExecutor(this)) { ev ->
                this.onVideoRecordEvent(ev)
            }
    }
    private fun onVideoRecordEvent(ev: VideoRecordEvent) {
        if (ev is VideoRecordEvent.Finalize) {
            this.onVideoFinalize(ev)
        }
    }
    private fun onVideoFinalize(ev: VideoRecordEvent.Finalize) {
        val foOptions = ev.outputOptions as FileOutputOptions
        val fpath = foOptions.file.toString()
        if (ev.hasError()) {
            val errType = ev.error.toString()
            val buf = ByteArrayOutputStream()
            ev.cause?.printStackTrace(PrintStream(buf))
            val errCause = buf.toString()
            val err = "%s %s".format(errType, errCause)

            Log.v("TAG", "video error %s".format(err))
            this.uploadErrs.append(UploadError(fpath, err))
            return
        }

        this.videoQueue.offer(fpath)
    }

    private fun uploadVideosAfter(ago: Duration) {
        val threadPool = Executors.newFixedThreadPool(8)

        val faraway = Util.timeDate(9999, Util.December, 31, 23, 59, 59, 0, Util.UTC)
        while (Instant.now().isBefore(faraway)) {
            this.deleteOldVideos(ago)

            val last = this.videoQueue.take()
            val uploadable = this.findUploadableVideos(last)

            var tasks = emptyArray<Future<*>>()
            for (u in uploadable) {
                val fpath = File(this.videoDir, u).toString()
                val t = threadPool.submit { this.uploadVideo(fpath) }
                tasks += t
            }
            for (t in tasks) {
                t.get()
            }
        }
    }

    private fun deleteOldVideos(ago: Duration) {
        val serverStartFName = "%s.mp4".format(Util.timeFormatSafe(this.getServerStartTimeSafe()))

        val now = Instant.now()
        val cutoff = Instant.ofEpochSecond(now.epochSecond - ago.toMillis()/1000)
        val cutoffFName = "%s.mp4".format(Util.timeFormatSafe(cutoff))

        var deletables = emptyArray<String>()
        val files = File(this.videoDir).listFiles()
        for (f in files!!) {
            if (f.name < cutoffFName) {
                deletables += f.name
            }
            if (f.name < serverStartFName) {
                deletables += f.name
            }
        }

        for (fbase in deletables) {
            val f = File(this.videoDir, fbase)
            f.delete()
            Log.v("TAG", "deleted %s".format(fbase))
        }
    }
    private fun findUploadableVideos(last: String): Array<String> {
        val serverStartFName = "%s.mp4".format(Util.timeFormatSafe(this.getServerStartTimeSafe()))

        val lastBase = File(last).name
        val files = File(this.videoDir).listFiles()
        var uploadables = emptyArray<String>()
        for (f in files!!) {
            // File is before server started, ignore it.
            if (f.name < serverStartFName) {
                continue
            }
            // File is after the last finalized video, meaning it is still written by the video capturing system.
            if (f.name > lastBase) {
                continue
            }
            uploadables += f.name
        }
        Arrays.sort(uploadables)
        return uploadables
    }

    private fun uploadVideo(fpath: String) {
        val (response, err) = uploadVideo(this.cfg.uploadPath, this.cfg.cameraID, fpath)
        if (err != "") {
            Log.v("TAG", err)
            this.uploadErrs.append(UploadError(fpath, err))
            return
        }
        val resp = JSONObject(response)

        val serverStartTime = resp.getLong("T")
        val st = Instant.ofEpochSecond(serverStartTime)
        this.setServerStartTimeSafe(st)

        File(fpath).delete()
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

    private fun setServerStartTimeSafe(t: Instant) {
        synchronized(this.mutex){
            this.serverStartTime = t
        }
    }
    private fun getServerStartTimeSafe(): Instant {
       synchronized(this.mutex){
           return this.serverStartTime
       }
    }
}

fun uploadVideo(urlStr: String, cameraID: String, fpath: String): Pair<String, String> {
    try {
        val charset = "UTF-8"
        val uploadFile = File(fpath)

        val boundary = java.lang.Long.toHexString(System.currentTimeMillis())
        val cRLF = "\r\n"

        val connection = URL(urlStr).openConnection() as HttpURLConnection
        connection.doOutput = true
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

        val output = DataOutputStream(connection.outputStream)

        // Camera ID.
        output.writeBytes("--$boundary$cRLF")
        output.writeBytes("Content-Disposition: form-data; name=\"c\"$cRLF")
        output.writeBytes(cRLF)
        output.writeBytes(cameraID)
        output.writeBytes(cRLF)

        // Upload file.
        output.writeBytes("--$boundary$cRLF")
        output.writeBytes("Content-Disposition: form-data; name=\"f\"; filename=\"" + uploadFile.name + "\"" + cRLF)
        output.writeBytes("Content-Type: " + HttpURLConnection.guessContentTypeFromName(uploadFile.name) + cRLF)
        output.writeBytes(cRLF)
        val inputFile = FileInputStream(uploadFile)
        val buffer = ByteArray(4096)
        while (true) {
            val bytesRead = inputFile.read(buffer)
            if (bytesRead == -1) {
                break
            }
            output.write(buffer, 0, bytesRead)
        }
        output.writeBytes(cRLF)

        // End of multipart.
        output.writeBytes("--$boundary--$cRLF")

        val input = BufferedReader(InputStreamReader(connection.inputStream, charset))
        val responseBuilder = StringBuilder()
        val faraway = Util.timeDate(9999, Util.December, 31, 23, 59, 59, 0, Util.UTC)
        while (true) {
            val responseLine = input.readLine()
            if (responseLine == null && Instant.now().isBefore(faraway)) {
                break
            }
            responseBuilder.append(responseLine)
        }
        return Pair(responseBuilder.toString(), "")
    } catch (e: Throwable) {
        val buf = ByteArrayOutputStream()
        e.printStackTrace(PrintStream(buf))
        val err = buf.toString()
        return Pair("", err)
    }
}