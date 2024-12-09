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
    private var sequenceCount = 0  // בחלק של המשתנים הגלובליים
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
    private var currentScrollState = 0  // 0 = לא גולל, 1 = גלילה ראשונה, 2 = גלילה אמצעית, 3 = גלילה אחרונה
    private val TOTAL_SCROLL_SEQUENCE = 3
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
        private const val ACTION_COOLDOWN = 500L
        private const val MIN_CONTENT_LENGTH = 10
        private const val PROCESSING_DELAY = 150L
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

        val centerY = rect.centerY()
        val screenCenterY = displayHeight / 2

        return abs(centerY - screenCenterY) < displayHeight / 5 && // להקטין את טווח הסטייה
                rect.height() > displayHeight / 2.2 && // להגדיל את דרישת הגודל המינימלי
                rect.width() > displayWidth * 0.85   // להגדיל את דרישת הרוחב המינימלי
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

        // בדיקה אם התוכן כבר עובד
        if (currentContentHash == lastProcessedHash) {
            Log.d(TAG, "Skipping - exact content match")
            return false
        }

        // בדיקת מילות מפתח לפרסומות
        val adKeywords = setOf(
            "sponsored",
            "promoted",
            "מודעה",
            "פרסומת",
            "campaigns",
            "promoted music"
        )

        val isAd = adKeywords.any { keyword ->
            normalizedText.contains(keyword)
        }

        if (isAd) {
            Log.d(TAG, "Found advertisement content")
            lastProcessedHash = currentContentHash
            return true  // תמיד נחזיר true כשיש פרסומת ונתן ל-executeScrollAction לטפל ברצף
        }

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
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastActionTime >= ACTION_COOLDOWN && canPerformAction()) {
                        lastActionTime = currentTime
                        executeScrollAction()
                    } else {
                        Log.d(TAG, "Skipping action - cooldown or cannot perform")
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
            if (isPerformingAction) {
                Log.d(TAG, "Already performing action")
                return
            }

            isPerformingAction = true

            // פעולה ראשונה ריקה
            if (currentScrollState == 0) {
                Log.d(TAG, "Executing empty first action")
                handler.postDelayed({
                    isPerformingAction = false
                    currentScrollState = 1  // מעבר לפעולה השניה
                    executeScrollAction()  // קריאה רקורסיבית לפעולה השניה
                }, 100)
                return
            }

            // פעולה שניה - גלילה אמיתית
            if (currentScrollState == 1) {
                Log.d(TAG, "Executing actual scroll")

                val path = Path().apply {
                    moveTo(displayWidth * 0.5f, displayHeight * 0.85f)
                    lineTo(displayWidth * 0.5f, displayHeight * 0.15f)
                }

                val gestureBuilder = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 120))
                    .build()

                val result = dispatchGesture(gestureBuilder, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        Log.d(TAG, "Scroll completed")
                        handler.postDelayed({
                            isPerformingAction = false
                            lastActionTime = System.currentTimeMillis()
                            currentScrollState = 0  // איפוס המצב לקראת הזיהוי הבא
                        }, 150)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        Log.e(TAG, "Scroll cancelled")
                        isPerformingAction = false
                        currentScrollState = 0
                    }
                }, null)

                if (!result) {
                    Log.e(TAG, "Failed to dispatch gesture")
                    isPerformingAction = false
                    currentScrollState = 0
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error executing scroll: ${e.message}")
            isPerformingAction = false
            currentScrollState = 0
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