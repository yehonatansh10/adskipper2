package com.example.adskipper2

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.SharedPreferences
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.adskipper2.gesture.GesturePlayer
import com.example.adskipper2.gesture.GestureAction

class SkipperService : AccessibilityService() {
    private lateinit var prefs: SharedPreferences
    private lateinit var gesturePrefs: SharedPreferences
    private lateinit var gesturePlayer: GesturePlayer
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "SkipperService"
    }

    override fun onCreate() {
        super.onCreate()
        initializeServices()
    }

    private fun initializeServices() {
        prefs = getSharedPreferences("targets", MODE_PRIVATE)
        gesturePrefs = getSharedPreferences("gestures", MODE_PRIVATE)
        gesturePlayer = GesturePlayer(this)
        gesturePlayer.setAccessibilityService(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val currentPackage = event.packageName?.toString() ?: return
        val targetApps = prefs.getStringSet("selected_apps", setOf()) ?: return

        if (currentPackage in targetApps) {
            checkAndPerformAction(event, currentPackage)
        }
    }

    private fun checkAndPerformAction(event: AccessibilityEvent, currentPackage: String) {
        val targetText = prefs.getString("${currentPackage}_text", "") ?: return
        val sourceText = event.text?.joinToString(" ") ?: return

        Log.d(TAG, "Checking text: $sourceText")
        Log.d(TAG, "Target text: $targetText")

        if (sourceText.contains(targetText, ignoreCase = true)) {
            Log.d(TAG, "Found matching text!")
            performAction()
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service Connected")
        showToast("שירות הדילוג הופעל")
    }

    private fun performAction() {
        val recordedActionsString = gesturePrefs.getString("recorded_actions", null)

        if (!recordedActionsString.isNullOrEmpty()) {
            executeRecordedActions(recordedActionsString)
            return
        }

        performDefaultAction()
    }

    private fun executeRecordedActions(actionsString: String) {
        val actions = parseRecordedActions(actionsString)
        if (actions.isNotEmpty()) {
            gesturePlayer.playActions(actions)
            showToast("מבצע פעולות מוקלטות")
        }
    }

    private fun performDefaultAction() {
        rootInActiveWindow?.let {
            it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            showToast("בוצעה פעולת דילוג")
        }
    }

    private fun showToast(message: String) {
        handler.post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseRecordedActions(actionsString: String): List<GestureAction> {
        return try {
            actionsString.split(";").mapNotNull { actionStr ->
                when {
                    actionStr.startsWith("tap:") -> createTapAction(actionStr, false)
                    actionStr.startsWith("doubletap:") -> createTapAction(actionStr, true)
                    actionStr.startsWith("scroll:") -> createScrollAction(actionStr)
                    else -> null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing actions: ${e.message}")
            emptyList()
        }
    }

    private fun createTapAction(actionStr: String, isDouble: Boolean): GestureAction? {
        val prefix = if (isDouble) "doubletap:" else "tap:"
        val (x, y) = actionStr.substringAfter(prefix).split(",").map { it.toFloat() }
        return if (isDouble) GestureAction.DoubleTap(x, y) else GestureAction.Tap(x, y)
    }

    private fun createScrollAction(actionStr: String): GestureAction? {
        val (startX, startY, endX, endY) = actionStr.substringAfter("scroll:")
            .split(",").map { it.toFloat() }
        return GestureAction.Scroll(startX, startY, endX, endY)
    }
}