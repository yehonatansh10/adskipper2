package com.example.adskipper2

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class UnifiedSkipperService : AccessibilityService() {
    private var isPerformingAction = false
    private var lastProcessedHash = ""
    private var lastActionTime = 0L
    private var processingTime = 0L
    private var isActivelyScanning = true
    private val handler = Handler(Looper.getMainLooper())
    private var displayWidth = 0
    private var displayHeight = 0

    companion object {
        private const val TAG = "UnifiedSkipperService"
        private const val PROCESSING_DELAY = 1000L
        private const val ACTION_COOLDOWN = 2000L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val display = this.resources.displayMetrics
        displayWidth = display.widthPixels
        displayHeight = display.heightPixels
        isActivelyScanning = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isActivelyScanning) return
        if (event?.packageName == null) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - processingTime < PROCESSING_DELAY) {
            return
        }
        processingTime = currentTime

        try {
            val rootNode = rootInActiveWindow ?: return
            val text = getAllText(rootNode)

            if (checkContent(text)) {
                if (canPerformAction()) {
                    lastActionTime = System.currentTimeMillis()
                    executeScrollAction()
                    isActivelyScanning = false
                }
            } else {
                isActivelyScanning = false
            }

            rootNode.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onAccessibilityEvent", e)
        }
    }

    private fun getAllText(node: AccessibilityNodeInfo): String {
        val text = StringBuilder()
        try {
            if (node.text != null) {
                text.append(node.text)
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                text.append(getAllText(child))
                child.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting text from node", e)
        }
        return text.toString()
    }

    private fun checkContent(text: String): Boolean {
        // כאן תוסיף את הלוגיקה לבדיקת הטקסט הרצוי
        // לדוגמה:
        return text.contains("מודעה", ignoreCase = true) ||
                text.contains("sponsored", ignoreCase = true)
    }

    private fun canPerformAction(): Boolean {
        val currentTime = System.currentTimeMillis()
        return !isPerformingAction &&
                (currentTime - lastActionTime > ACTION_COOLDOWN)
    }

    fun resumeScanning() {
        isActivelyScanning = true
        processingTime = 0L
    }

    private fun executeScrollAction() {
        try {
            isPerformingAction = true

            val path = Path().apply {
                moveTo(displayWidth * 0.5f, displayHeight * 0.8f)
                lineTo(displayWidth * 0.5f, displayHeight * 0.2f)
            }

            val gestureBuilder = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 200))
                .build()

            dispatchGesture(gestureBuilder, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    handler.postDelayed({
                        isPerformingAction = false
                    }, 250)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    isPerformingAction = false
                }
            }, null)
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
        Log.d(TAG, "Service destroyed")
    }
}