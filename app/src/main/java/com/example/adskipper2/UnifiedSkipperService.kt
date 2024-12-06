package com.example.adskipper2

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.util.DisplayMetrics
import android.provider.Settings
import android.net.Uri

class UnifiedSkipperService : AccessibilityService() {
    private var displayWidth = 0
    private var displayHeight = 0
    private val handler = Handler(Looper.getMainLooper())
    private var lastActionTime = 0L
    private val ACTION_COOLDOWN = 2000L
    private var lastScrollResult = true
    private var retryCount = 0
    private val maxRetries = 3

    companion object {
        private const val TAG = "UnifiedSkipperService"
        private const val TARGET_TEXT = "sponsored"
    }

    override fun onCreate() {
        super.onCreate()
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
        val wm = getSystemService(WindowManager::class.java)
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        displayWidth = metrics.widthPixels
        displayHeight = metrics.heightPixels
        Log.d(TAG, "Screen size configured: ${displayWidth}x${displayHeight}")
    }

    private fun configureService() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
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

    private fun canPerformAction(): Boolean {
        return Settings.canDrawOverlays(this) &&
                displayWidth > 0 &&
                displayHeight > 0
    }

    private fun findCurrentVideoNode(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val centerY = displayHeight / 2

        try {
            for (i in 0 until rootNode.childCount) {
                val child = rootNode.getChild(i) ?: continue
                val rect = Rect()
                child.getBoundsInScreen(rect)

                if (rect.top < centerY && rect.bottom > centerY) {
                    return child
                }
                child.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding current video node", e)
        }
        return null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName == null || !canPerformAction()) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastActionTime < ACTION_COOLDOWN) {
            return
        }

        if ((event.packageName == "com.zhiliaoapp.musically" ||
                    event.packageName == "com.ss.android.ugc.trill") &&
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                try {
                    val currentVideoNode = findCurrentVideoNode(rootNode)
                    if (currentVideoNode != null) {
                        val text = getAllText(currentVideoNode)

                        if (checkContent(text)) {
                            if (lastScrollResult) {
                                Log.d(TAG, "Found sponsored content in current video - executing scroll action")
                                lastActionTime = currentTime
                                retryCount = 0
                                executeScrollAction()
                            }
                        }
                        currentVideoNode.recycle()
                    }
                } finally {
                    rootNode.recycle()
                }
            }
        }
    }

    private fun checkContent(text: String): Boolean {
        val normalizedText = text.lowercase().trim()
        return normalizedText.contains(" $TARGET_TEXT ") ||
                normalizedText.startsWith("$TARGET_TEXT ") ||
                normalizedText.endsWith(" $TARGET_TEXT") ||
                normalizedText.contains("\n$TARGET_TEXT\n")
    }

    private fun getAllText(node: AccessibilityNodeInfo): String {
        val stringBuilder = StringBuilder()
        try {
            if (node.text != null) {
                stringBuilder.append(" ${node.text} ")
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    try {
                        stringBuilder.append(getAllText(child))
                    } finally {
                        child.recycle()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting text", e)
        }
        return stringBuilder.toString()
    }

    private fun executeScrollAction() {
        if (!canPerformAction()) {
            Log.e(TAG, "Cannot perform action - missing permissions or invalid display size")
            return
        }

        try {
            val path = Path().apply {
                moveTo(displayWidth / 2f, displayHeight * 0.8f)
                lineTo(displayWidth / 2f, displayHeight * 0.2f)
            }

            val gestureBuilder = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
                .build()

            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    lastScrollResult = true
                    retryCount = 0
                    Log.d(TAG, "Scroll completed successfully")
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    if (retryCount < maxRetries) {
                        retryCount++
                        Log.d(TAG, "Scroll cancelled, retrying attempt $retryCount")
                        handler.postDelayed({ executeScrollAction() }, 500)
                    } else {
                        lastScrollResult = false
                        retryCount = 0
                        Log.e(TAG, "Scroll failed after $maxRetries attempts")
                    }
                }
            }

            dispatchGesture(gestureBuilder, callback, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing scroll", e)
            lastScrollResult = false
            retryCount = 0
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }
}