package com.example.adskipper2

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

class TextDetectionService : Service() {
    private lateinit var imageReader: ImageReader
    private lateinit var mediaProjection: MediaProjection
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var targetText = ""
    private var targetPackage = ""
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                targetText = intent.getStringExtra(EXTRA_TARGET_TEXT) ?: ""
                targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: ""
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)

                if (!isRunning && data != null) {
                    startScreenCapture(resultCode, data)
                }
            }
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Text Detection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used for text detection service"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Text Detection Service")
            .setContentText("מזהה טקסט...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startScreenCapture(resultCode: Int, data: Intent) {
        try {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)

            val metrics = resources.displayMetrics
            imageReader = ImageReader.newInstance(
                metrics.widthPixels,
                metrics.heightPixels,
                PixelFormat.RGBA_8888,
                2
            )

            mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                WindowManager.LayoutParams.FLAG_SECURE,
                imageReader.surface,
                null,
                null
            )

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                image?.let {
                    if (isCurrentPackageTarget()) {
                        val inputImage = InputImage.fromMediaImage(it, 0)
                        detectText(inputImage)
                    }
                    it.close()
                }
            }, null)

            isRunning = true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting screen capture: ${e.message}", e)
            stopSelf()
        }
    }

    private fun isCurrentPackageTarget(): Boolean {
        val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val runningTasks = am.getRunningTasks(1)
        return runningTasks[0].topActivity?.packageName == targetPackage
    }

    private fun detectText(image: InputImage) {
        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                if (visionText.text.contains(targetText, ignoreCase = true)) {
                    performAction()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Text detection failed: ${e.message}")
            }
    }

    private fun performAction() {
        // כאן יש להוסיף את הפעולה שתרצה לבצע כשהטקסט מזוהה
        // לדוגמה: הפעלת gestures שהוקלטו
        val intent = Intent("TEXT_DETECTED_ACTION")
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRunning) {
            imageReader.close()
            mediaProjection.stop()
            isRunning = false
        }
    }

    companion object {
        private const val TAG = "TextDetectionService"
        private const val CHANNEL_ID = "text_detection_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.example.adskipper2.ACTION_START"
        const val ACTION_STOP = "com.example.adskipper2.ACTION_STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val EXTRA_TARGET_TEXT = "target_text"
        const val EXTRA_TARGET_PACKAGE = "target_package"
    }
}