package com.example.adskipper2

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.media.Image
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
import android.hardware.display.DisplayManager
import android.util.Log
import android.widget.Toast
import android.app.ActivityManager

class TextDetectionService : Service() {
    private lateinit var imageReader: ImageReader
    private lateinit var mediaProjection: MediaProjection
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var targetText: String = ""
    private var targetPackage: String = ""
    private var isRunning: Boolean = false
    private var lastImage: Image? = null
    private var lastDetectionTime: Long = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            Log.e(TAG, "Service started with null intent")
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                val targetText = intent.getStringExtra(EXTRA_TARGET_TEXT)
                val targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE)

                if (targetText.isNullOrEmpty() || targetPackage.isNullOrEmpty()) {
                    Log.e(TAG, "Invalid target text or package")
                    stopSelf()
                    return START_NOT_STICKY
                }

                this.targetText = targetText
                this.targetPackage = targetPackage
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)

                if (resultCode == -1 || data == null) {
                    Log.e(TAG, "Invalid result code or data")
                    stopSelf()
                    return START_NOT_STICKY
                }

                if (!isRunning) {
                    startScreenCapture(resultCode, data)
                }
            }
            ACTION_STOP -> stopSelf()
            else -> {
                Log.e(TAG, "Unknown action: ${intent.action}")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
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
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data) ?:
                    throw IllegalStateException("Failed to get media projection")

            val metrics = resources.displayMetrics

            // הגדרות מותאמות לזיהוי טוב יותר
            imageReader = ImageReader.newInstance(
                metrics.widthPixels,  // רוחב מלא
                metrics.heightPixels, // גובה מלא
                PixelFormat.RGBA_8888,
                2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    try {
                        lastImage?.close()
                        val newImage = reader.acquireLatestImage()
                        lastImage = newImage

                        if (newImage != null) {
                            if (isCurrentPackageTarget()) {
                                val inputImage = InputImage.fromMediaImage(newImage, 0)
                                detectText(inputImage)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing image: ${e.message}", e)
                    }
                }, null)
            }

            val virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                null
            )

            Log.d(TAG, "Virtual display created: ${virtualDisplay != null}")
            isRunning = true

        } catch (e: Exception) {
            Log.e(TAG, "Error in startScreenCapture: ${e.message}", e)
            stopSelf()
        }
    }

    private fun isCurrentPackageTarget(): Boolean {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val tasks = am.getRunningTasks(1)
            val currentPackage = tasks[0].topActivity?.packageName

            Log.d(TAG, "Current package: $currentPackage")
            Log.d(TAG, "Target package: $targetPackage")

            return currentPackage == targetPackage ||
                    currentPackage == "com.zhiliaoapp.musically" ||
                    currentPackage == "com.ss.android.ugc.trill"
        } catch (e: Exception) {
            Log.e(TAG, "Error checking current package: ${e.message}", e)
            return false
        }
    }

    private fun detectText(image: InputImage) {
        // מפחית את המרווח בין בדיקות ל-500 מילישניות
        if (System.currentTimeMillis() - lastDetectionTime < 500) return
        lastDetectionTime = System.currentTimeMillis()

        try {
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    // מנקה את הטקסט שזוהה ומדפיס אותו ללוג
                    val detectedText = visionText.text.trim()
                        .replace("\n", " ")
                        .replace("\\s+".toRegex(), " ")
                        .lowercase()

                    Log.d(TAG, "Detected text: $detectedText")
                    Log.d(TAG, "Looking for target text: $targetText")

                    // בדיקה גמישה יותר של הטקסט
                    val isTextFound = detectedText.contains(targetText.lowercase()) ||
                            targetText.lowercase().split(" ").all { word ->
                                detectedText.contains(word)
                            }

                    if (isTextFound) {
                        Log.d(TAG, "Target text found!")
                        performAction()
                    }
                }
            .addOnFailureListener { e ->
                Log.e(TAG, "Text detection failed: ${e.message}", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in detectText: ${e.message}", e)
        }
    }

    private fun performAction() {
        val intent = Intent("TEXT_DETECTED_ACTION")
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service being destroyed")
        if (isRunning) {
            try {
                lastImage?.close()
                imageReader.close()
                mediaProjection.stop()
                isRunning = false
                Log.d(TAG, "Service cleanup completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error during service cleanup: ${e.message}", e)
            }
        }
    }

    companion object {
        private const val TAG = "TextDetectionService"
        const val ACTION_START = "com.example.adskipper2.ACTION_START"
        const val ACTION_STOP = "com.example.adskipper2.ACTION_STOP"
        const val EXTRA_TARGET_TEXT = "target_text"
        const val EXTRA_TARGET_PACKAGE = "target_package"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL_ID = "text_detection_channel"
        private const val NOTIFICATION_ID = 1
    }
}