package com.example.adskipper2

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import android.content.Context
import kotlin.math.abs
import java.util.LinkedList

class UnifiedSkipperService : AccessibilityService() {
    private var lastContentHash = 0
    private var currentContentHash = 0
    private var processingTime = 0L
    private var lastProcessedContent = ""
    private var lastActionContent = ""
    private var lastProcessedHash = 0
    private var displayWidth = 0
    private var displayHeight = 0
    private var isPerformingAction = false
    private var sponsoredSequenceCount = 0
    private var lastSponsoredTime = 0L
    private val SEQUENCE_TIMEOUT = 2000L  // 3 seconds timeout between sponsored content detections
    private val handler = Handler(Looper.getMainLooper())
    private var lastActionTime = 0L
    private var lastScrollResult = true
    private var retryCount = 0
    private var lastMainPostContent = ""
    private var currentVideoNode: AccessibilityNodeInfo? = null
    private val maxRetries = 3
    private val resetActionTimer = Handler(Looper.getMainLooper())
    private val resetTimer = Handler(Looper.getMainLooper())
    private val resetRunnable = object : Runnable {
        override fun run() {
            if (isPerformingAction) {
                Log.d(TAG, "Reset timer: forcing state reset")
                isPerformingAction = false
                lastScrollResult = true
                lastActionTime = System.currentTimeMillis() - ACTION_COOLDOWN
            }
            resetTimer.postDelayed(this, 3000)
        }
    }

    companion object {
        private const val TAG = "UnifiedSkipperService"
        private const val ACTION_COOLDOWN = 1000L
        private const val MIN_CONTENT_LENGTH = 20
        private const val PROCESSING_DELAY = 250L
    }

    override fun onCreate() {
        super.onCreate()
        resetTimer.postDelayed(resetRunnable, 3000)
        Log.d(TAG, "Service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")

        try {
            initializeDisplay()
            configureService()
            checkAndRequestPermissions()
            Log.d(TAG, "Service configured successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onServiceConnected", e)
        }
    }

    private fun initializeDisplay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            val bounds = metrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            Point().apply { wm.defaultDisplay.getRealSize(this) }
        }

        displayWidth = display.x
        displayHeight = display.y
        Log.d(TAG, "Screen size configured: ${displayWidth}x${displayHeight}")
    }

    private fun configureService() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 50
        }
        serviceInfo = info
    }

    private fun checkAndRequestPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    private fun findCurrentVideoNode(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        try {
            fun findNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                if (isNodeVisible(node)) {
                    currentVideoNode = AccessibilityNodeInfo.obtain(node)
                    return currentVideoNode
                }

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    val result = findNode(child)
                    if (result != null) {
                        child.recycle()
                        return result
                    }
                    child.recycle()
                }
                return null
            }

            val result = findNode(rootNode)
            if (result == null) {
                currentVideoNode = null
            }
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error in findCurrentVideoNode: ${e.message}")
            currentVideoNode = null
            return null
        }
    }

    private fun isNodeVisible(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        // בדיקה האם ה-node נמצא במרכז המסך
        val centerY = rect.centerY()
        val screenCenterY = displayHeight / 2

        return abs(centerY - screenCenterY) < displayHeight / 4 && // רבע מגובה המסך סטייה מקסימלית
                rect.height() > displayHeight / 2 && // גודל מינימלי
                rect.width() > displayWidth * 0.8   // רוחב מינימלי
    }

    private fun getAllText(node: AccessibilityNodeInfo): String {
        val text = StringBuilder()

        fun extractText(n: AccessibilityNodeInfo) {
            if (!n.text.isNullOrEmpty()) {
                text.append(n.text).append(" ")
            }

            // בדיקת contentDescription
            if (!n.contentDescription.isNullOrEmpty()) {
                text.append(n.contentDescription).append(" ")
            }

            // חיפוש בילדים
            for (i in 0 until n.childCount) {
                val child = n.getChild(i) ?: continue
                extractText(child)
                child.recycle()
            }
        }

        try {
            extractText(node)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text: ${e.message}")
        }

        val result = text.toString().trim()
        if (result.isNotEmpty()) {
            Log.d(TAG, "Extracted text: $result")
        }
        return result
    }

    private fun canPerformAction(): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastAction = currentTime - lastActionTime

        // בדיקה מחמירה יותר של מצב הפעולה
        if (isPerformingAction) {
            if (timeSinceLastAction > 3000) {
                Log.d(TAG, "Resetting stuck performing state")
                isPerformingAction = false
            } else {
                return false
            }
        }

        if (timeSinceLastAction < ACTION_COOLDOWN) {
            Log.d(TAG, "Action cooldown in effect: ${timeSinceLastAction}ms")
            return false
        }

        val canDraw = Settings.canDrawOverlays(this)
        val validDisplay = displayWidth > 0 && displayHeight > 0

        return canDraw && validDisplay
    }


    private fun checkContent(text: String): Boolean {
        if (text.isEmpty()) {
            Log.d(TAG, "Empty text content")
            return false
        }

        val normalizedText = text.lowercase().trim()
        currentContentHash = normalizedText.hashCode()

        // בדיקה האם התוכן זהה לקודם
        if (currentContentHash == lastContentHash ||
            currentContentHash == lastProcessedHash ||
            normalizedText == lastProcessedContent) {
            Log.d(TAG, "Skipping - content already processed")
            return false
        }

        // בדיקת תקופת המתנה
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastActionTime < ACTION_COOLDOWN) {
            Log.d(TAG, "Skipping - cooldown period")
            return false
        }

        // מעקב אחר הפוסט הנוכחי
        val isCurrentPost = normalizedText.length > MIN_CONTENT_LENGTH &&
                !normalizedText.contains("sponsored", ignoreCase = true) &&
                currentVideoNode?.let { isNodeVisible(it) } == true

        if (isCurrentPost) {
            // שמירת המצב הנוכחי לשימוש בהמשך
            lastMainPostContent = normalizedText
            Log.d(TAG, "Current main post content updated")
        }

        // קבלת המילים השמורות מה-SharedPreferences
        val prefs = getSharedPreferences("targets", Context.MODE_PRIVATE)
        val targetWords = prefs.all.values
            .filterIsInstance<String>()
            .filter { it.isNotEmpty() }
            .map { it.lowercase().trim() }

        // בדיקה האם הטקסט מכיל אחת מהמילים המוגדרות
        val foundTarget = targetWords.any { target ->
            normalizedText.contains(target)
        }

        if (foundTarget) {
            Log.d(TAG, "Found target word in content")

            // וידוא שהמילה לא נמצאת בפוסט הראשי
            if (lastMainPostContent.isNotEmpty() &&
                targetWords.any { lastMainPostContent.contains(it) }) {
                Log.d(TAG, "Target word found in main post - skipping action")
                lastContentHash = currentContentHash
                return false
            }

            // Reset sequence if too much time has passed
            if (currentTime - lastSponsoredTime > SEQUENCE_TIMEOUT) {
                sponsoredSequenceCount = 0
            }

            sponsoredSequenceCount++
            lastSponsoredTime = currentTime

            // Only execute action on the second detection
            val shouldExecuteAction = sponsoredSequenceCount == 2

            // Reset sequence after third detection
            if (sponsoredSequenceCount >= 3) {
                sponsoredSequenceCount = 0
            }

            lastContentHash = currentContentHash
            lastProcessedContent = normalizedText
            lastProcessedHash = currentContentHash

            return shouldExecuteAction
        }

        lastContentHash = currentContentHash
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName == null) return

        if ((event.packageName != "com.zhiliaoapp.musically" &&
                    event.packageName != "com.ss.android.ugc.trill")) {
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - processingTime < PROCESSING_DELAY) {
            return
        }
        processingTime = currentTime

        try {
            val rootNode = rootInActiveWindow ?: return
            val currentVideoNode = findCurrentVideoNode(rootNode)

            if (currentVideoNode != null) {
                val text = getAllText(currentVideoNode)

                // שינוי: בדיקה והפעלת פעולה בנפרד
                if (checkContent(text)) {
                    if (canPerformAction()) {
                        lastActionTime = System.currentTimeMillis()
                        executeScrollAction()
                    } else {
                        Log.d(TAG, "Cannot perform action at this time")
                    }
                }

                currentVideoNode.recycle()
            }

            rootNode.recycle()

        } catch (e: Exception) {
            Log.e(TAG, "Error in onAccessibilityEvent", e)
        }
    }

    private fun executeScrollAction() {
        try {
            isPerformingAction = true
            Log.d(TAG, "Creating scroll gesture for sequence ${sponsoredSequenceCount}")

            // Only perform the actual scroll for sequence count 2
            if (sponsoredSequenceCount != 2) {
                Log.d(TAG, "Skipping scroll action for sequence ${sponsoredSequenceCount}")
                isPerformingAction = false
                return
            }

            val path = Path().apply {
                moveTo(displayWidth * 0.5f, displayHeight * 0.8f)
                lineTo(displayWidth * 0.5f, displayHeight * 0.2f)
            }

            val gestureBuilder = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 200))
                .build()

            val result = dispatchGesture(gestureBuilder, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Scroll completed for sequence ${sponsoredSequenceCount}")
                    handler.postDelayed({
                        isPerformingAction = false
                        lastProcessedHash = currentContentHash
                    }, 250)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.e(TAG, "Scroll cancelled")
                    isPerformingAction = false
                }
            }, null)

            if (!result) {
                Log.e(TAG, "Failed to dispatch scroll gesture")
                isPerformingAction = false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error executing scroll: ${e.message}")
            isPerformingAction = false
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        resetTimer.removeCallbacks(resetRunnable)
        Log.d(TAG, "Service destroyed")
    }
}