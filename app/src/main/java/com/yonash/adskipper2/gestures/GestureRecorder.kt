package com.yonash.adskipper2.gesture

class GestureRecorder {
    var isRecording = false
        private set

    private val actions = mutableListOf<GestureAction>()
    private var startTime: Long = 0

    fun startRecording() {
        isRecording = true
        actions.clear()
        startTime = System.currentTimeMillis()
    }

    fun stopRecording() {
        isRecording = false
    }

    fun recordTap(x: Float, y: Float) {
        if (isRecording) {
            actions.add(GestureAction.Tap(x, y))
        }
    }

    fun recordDoubleTap(x: Float, y: Float) {
        if (isRecording) {
            actions.add(GestureAction.DoubleTap(x, y))
        }
    }

    fun recordScroll(startX: Float, startY: Float, endX: Float, endY: Float) {
        if (isRecording) {
            actions.add(GestureAction.Scroll(startX, startY, endX, endY))
        }
    }

    fun getRecordedActions(): List<GestureAction> = actions.toList()
}