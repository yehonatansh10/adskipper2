package com.example.adskipper2.gesture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent

class GesturePlayer(private val context: Context) {
    private var accessibilityService: AccessibilityService? = null
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "GesturePlayer"
        private const val TAP_DURATION = 100L
        private const val SCROLL_DURATION = 300L
        private const val DOUBLE_TAP_DELAY = 200L
        private const val ACTION_DELAY = 500L
    }

    fun setAccessibilityService(service: AccessibilityService) {
        accessibilityService = service
    }

    fun playActions(actions: List<GestureAction>) {
        try {
            actions.forEachIndexed { index, action ->
                handler.postDelayed({
                    executeAction(action)
                }, index * ACTION_DELAY)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing actions: ${e.message}", e)
        }
    }

    private fun executeAction(action: GestureAction) {
        try {
            when (action) {
                is GestureAction.Tap -> performTap(action.x, action.y)
                is GestureAction.DoubleTap -> performDoubleTap(action.x, action.y)
                is GestureAction.Scroll -> performScroll(action.startX, action.startY, action.endX, action.endY)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing action: ${e.message}", e)
        }
    }

    private fun performTap(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
        }

        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION))
            .build())
    }

    private fun performDoubleTap(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
        }

        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION))
            .addStroke(GestureDescription.StrokeDescription(path, DOUBLE_TAP_DELAY, TAP_DURATION))
            .build())
    }

    private fun performScroll(startX: Float, startY: Float, endX: Float, endY: Float) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, SCROLL_DURATION))
            .build())
    }

    private fun dispatchGesture(gesture: GestureDescription) {
        accessibilityService?.let {
            it.dispatchGesture(gesture, null, null) ?:
            Log.e(TAG, "Failed to dispatch gesture")
        } ?: Log.e(TAG, "AccessibilityService not set")
    }
}