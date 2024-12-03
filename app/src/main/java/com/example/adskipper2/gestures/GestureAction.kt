package com.example.adskipper2.gesture

sealed class GestureAction {
    data class Tap(val x: Float, val y: Float) : GestureAction()
    data class DoubleTap(val x: Float, val y: Float) : GestureAction()
    data class Scroll(val startX: Float, val startY: Float, val endX: Float, val endY: Float) : GestureAction()
}