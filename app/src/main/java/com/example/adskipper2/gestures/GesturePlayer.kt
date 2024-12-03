package com.example.adskipper2.gesture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Handler
import android.os.Looper

class GesturePlayer(private val context: Context) {
    private val gestureBuilder = GestureDescription.Builder()
    private var accessibilityService: AccessibilityService? = null
    private val handler = Handler(Looper.getMainLooper())

    fun setAccessibilityService(service: AccessibilityService) {
        accessibilityService = service
    }

    fun playActions(actions: List<GestureAction>) {
        actions.forEachIndexed { index, action ->
            handler.postDelayed({
                when (action) {
                    is GestureAction.Tap -> performTap(action.x, action.y)
                    is GestureAction.DoubleTap -> performDoubleTap(action.x, action.y)
                    is GestureAction.Scroll -> performScroll(action.startX, action.startY, action.endX, action.endY)
                }
            }, index * 500L)
        }
    }

    private fun performTap(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        accessibilityService?.dispatchGesture(gesture, null, null)
    }

    private fun performDoubleTap(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .addStroke(GestureDescription.StrokeDescription(path, 200, 100))
            .build()

        accessibilityService?.dispatchGesture(gesture, null, null)
    }

    private fun performScroll(startX: Float, startY: Float, endX: Float, endY: Float) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        accessibilityService?.dispatchGesture(gesture, null, null)
    }
}