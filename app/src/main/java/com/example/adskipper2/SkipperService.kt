package com.example.adskipper2

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.SharedPreferences
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.widget.Toast

class SkipperService : AccessibilityService() {
    private lateinit var prefs: SharedPreferences
    private lateinit var gesturePrefs: SharedPreferences
    private lateinit var gesturePlayer: GesturePlayer
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("targets", MODE_PRIVATE)
        gesturePrefs = getSharedPreferences("gestures", MODE_PRIVATE)
        gesturePlayer = GesturePlayer(this)
        gesturePlayer.setAccessibilityService(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            val currentPackage = it.packageName?.toString()
            val targetApps = prefs.getStringSet("selected_apps", setOf()) ?: setOf()

            if (currentPackage in targetApps) {
                val targetText = prefs.getString("${currentPackage}_text", "") ?: ""
                val sourceText = event.text?.joinToString(" ") ?: ""

                Log.d("SkipperService", "Checking text: $sourceText")
                Log.d("SkipperService", "Target text: $targetText")

                if (sourceText.contains(targetText, ignoreCase = true)) {
                    Log.d("SkipperService", "Found matching text!")
                    performAction()
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d("SkipperService", "Service Interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("SkipperService", "Service Connected")
        handler.post {
            Toast.makeText(this, "שירות הדילוג הופעל", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performAction() {
        // בדוק אם יש פעולות מוקלטות
        val recordedActionsString = gesturePrefs.getString("recorded_actions", null)
        if (recordedActionsString != null) {
            // המר את המחרוזת לרשימת פעולות
            val actions = parseRecordedActions(recordedActionsString)
            if (actions.isNotEmpty()) {
                // הפעל את הפעולות המוקלטות
                gesturePlayer.playActions(actions)
                handler.post {
                    Toast.makeText(this, "מבצע פעולות מוקלטות", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }

        // אם אין פעולות מוקלטות, בצע את פעולת ברירת המחדל (גלילה)
        val root = rootInActiveWindow
        root?.let {
            it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            handler.post {
                Toast.makeText(this, "בוצעה פעולת דילוג", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun parseRecordedActions(actionsString: String): List<GestureAction> {
        return try {
            // המרת המחרוזת לרשימת פעולות
            // הפורמט תלוי באופן השמירה ב-SharedPreferences
            actionsString.split(";").mapNotNull { actionStr ->
                when {
                    actionStr.startsWith("tap:") -> {
                        val (x, y) = actionStr.substringAfter("tap:").split(",").map { it.toFloat() }
                        GestureAction.Tap(x, y)
                    }
                    actionStr.startsWith("doubletap:") -> {
                        val (x, y) = actionStr.substringAfter("doubletap:").split(",").map { it.toFloat() }
                        GestureAction.DoubleTap(x, y)
                    }
                    actionStr.startsWith("scroll:") -> {
                        val (startX, startY, endX, endY) = actionStr.substringAfter("scroll:")
                            .split(",").map { it.toFloat() }
                        GestureAction.Scroll(startX, startY, endX, endY)
                    }
                    else -> null
                }
            }
        } catch (e: Exception) {
            Log.e("SkipperService", "Error parsing actions: ${e.message}")
            emptyList()
        }
    }
}