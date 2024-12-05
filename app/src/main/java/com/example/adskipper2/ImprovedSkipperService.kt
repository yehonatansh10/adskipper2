package com.example.adskipper2

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import java.util.concurrent.atomic.AtomicBoolean

class ImprovedSkipperService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val isRunning = AtomicBoolean(false)
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private val scanInterval = 500L // חצי שנייה בין סריקות

    companion object {
        private const val TAG = "ImprovedSkipper"
        private val TARGET_PACKAGES = setOf(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill"
        )
        private val TARGET_TEXTS = setOf(
            "דלג על מודעה",
            "דלגו על המודעה",
            "Skip Ad",
            "Skip ad",
            "Skip Ads"
        )
    }

    private val screenScanner = object : Runnable {
        override fun run() {
            if (isRunning.get()) {
                scanScreen()
                handler.postDelayed(this, scanInterval)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service Connected")
        isRunning.set(true)
        handler.post(screenScanner)
        setupOverlay()
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = FrameLayout(this)

        val params = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        try {
            windowManager?.addView(overlayView, params)
            Log.d(TAG, "Overlay setup successful")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup overlay: ${e.message}")
        }
    }

    private fun scanScreen() {
        if (!isRunning.get()) return

        val rootNode = rootInActiveWindow ?: return
        try {
            val currentPackage = rootNode.packageName?.toString() ?: return
            if (currentPackage !in TARGET_PACKAGES) {
                return
            }

            Log.d(TAG, "Scanning package: $currentPackage")
            findAndClickSkipButton(rootNode)
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning screen: ${e.message}")
        } finally {
            rootNode.recycle()
        }
    }

    private fun findAndClickSkipButton(rootNode: AccessibilityNodeInfo) {
        val nodesToProcess = ArrayList<AccessibilityNodeInfo>()
        findAllNodes(rootNode, nodesToProcess)

        for (node in nodesToProcess) {
            try {
                val nodeText = node.text?.toString()?.lowercase() ?: ""
                val nodeDesc = node.contentDescription?.toString()?.lowercase() ?: ""

                if (TARGET_TEXTS.any { text ->
                        nodeText.contains(text.lowercase()) ||
                                nodeDesc.contains(text.lowercase())
                    }) {
                    Log.d(TAG, "Found skip button: $nodeText")
                    performSkipAction(node)
                    break
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing node: ${e.message}")
            }
        }

        nodesToProcess.forEach { it.recycle() }
    }

    private fun findAllNodes(node: AccessibilityNodeInfo?, nodes: ArrayList<AccessibilityNodeInfo>) {
        if (node == null) return
        nodes.add(node)
        for (i in 0 until node.childCount) {
            findAllNodes(node.getChild(i), nodes)
        }
    }

    private fun performSkipAction(node: AccessibilityNodeInfo) {
        try {
            // נסה קודם ללחוץ ישירות על הכפתור
            if (node.isClickable) {
                val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    Log.d(TAG, "Successfully clicked skip button")
                    return
                }
            }

            // אם הלחיצה הישירה לא עבדה, נסה להשתמש במחוות
            val rect = Rect()
            node.getBoundsInScreen(rect)

            val path = Path()
            path.moveTo(rect.centerX().toFloat(), rect.centerY().toFloat())

            val gestureBuilder = GestureDescription.Builder()
            val gesture = gestureBuilder
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                .build()

            dispatchGesture(gesture, null, null)
            Log.d(TAG, "Performed gesture click at: ${rect.centerX()}, ${rect.centerY()}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform skip action: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // אנחנו משתמשים בסורק תקופתי במקום להסתמך על אירועים
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
        isRunning.set(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning.set(false)
        handler.removeCallbacks(screenScanner)

        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay: ${e.message}")
        }
    }
}