package com.example.adskipper2

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class UnifiedSkipperService : AccessibilityService() {
    companion object {
        private const val TAG = "UnifiedSkipperService"
        private const val ACTION_COOLDOWN = 2000L
    }

    private var isPerformingAction = false
    private var displayWidth = 0
    private var displayHeight = 0
    private val handler = Handler(Looper.getMainLooper())
    private var lastActionTime = 0L
    private var lastScrollTime = 0L
    private val SCROLL_COOLDOWN = 3000L // 3 שניות המתנה בין דילוגים
    private var isScrolling = false

    // המילים שאנחנו מחפשים
    private val adKeywords = listOf(
        "sponsored",
        "Sponsored"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        val display = resources.displayMetrics
        displayWidth = display.widthPixels
        displayHeight = display.heightPixels
        Log.d(TAG, "Service connected with dimensions: $displayWidth x $displayHeight")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != "com.zhiliaoapp.musically") {
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && !isPerformingAction) {
            checkContent()
        }
    }

    private fun checkContent() {
        try {
            if (isScrolling) {
                Log.d(TAG, "Still scrolling, skipping check")
                return
            }

            val windows = windows
            var sponsoredNode: AccessibilityNodeInfo? = null

            windows?.forEach { window ->
                if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    window.root?.let { node ->
                        sponsoredNode = findSponsoredNode(node)
                        if (sponsoredNode != null) return@forEach
                    }
                }
            }

            if (sponsoredNode != null) {
                val bounds = Rect()
                sponsoredNode?.getBoundsInScreen(bounds)

                val centerY = displayHeight / 2
                // הגדלת טווח הטולרנס
                val tolerance = displayHeight / 2  // שינוי מ-4 ל-2 (חצי מסך)

                // הוספת לוג לדיבוג
                Log.d(TAG, "Checking position - Bounds: ${bounds.centerY()}, " +
                        "Center: $centerY, " +
                        "Tolerance: $tolerance, " +
                        "Range: ${centerY - tolerance} to ${centerY + tolerance}")

                if (bounds.centerY() > 0 && // וידוא שהמיקום חיובי
                    bounds.centerY() < displayHeight && // וידוא שהמיקום בתוך המסך
                    bounds.centerY() in (centerY - tolerance)..(centerY + tolerance)) {

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastScrollTime > SCROLL_COOLDOWN) {
                        Log.d(TAG, "Found ad in valid position - initiating scroll")
                        lastScrollTime = currentTime
                        performScroll()
                    } else {
                        Log.d(TAG, "Found ad but in cooldown period. Waiting...")
                    }
                } else {
                    Log.d(TAG, "Found ad but not in valid position. " +
                            "Position: ${bounds.centerY()}, " +
                            "Center: $centerY, " +
                            "Valid range: ${centerY - tolerance} to ${centerY + tolerance}")
                }
            }

            sponsoredNode?.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkContent", e)
        }
    }

    private fun findSponsoredNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        try {
            // הרחבת רשימת מילות המפתח לזיהוי פרסומות
            val adKeywords = listOf(
                "sponsored",
                "Sponsored",
                "מודעָה",
                "ממומן",
                "החלק כדי לדלג",
            )

            // בדיקת ContentDescription בנוסף לטקסט
            val text = node.text?.toString() ?: ""
            val contentDesc = node.contentDescription?.toString() ?: ""

            // בדיקה אם יש התאמה באחד מהשדות
            if (adKeywords.any { keyword ->
                    text.contains(keyword) || contentDesc.contains(keyword)
                }) {
                Log.d(TAG, "Found ad indicator - Text: $text, ContentDesc: $contentDesc")
                return node
            }

            // בדיקת ילדים באופן רקורסיבי עם שיפור בניהול משאבים
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    try {
                        val result = findSponsoredNode(child)
                        if (result != null) {
                            return result
                        }
                    } finally {
                        child.recycle()  // וידוא שחרור משאבים
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in findSponsoredNode", e)
        }
        return null
    }

    private fun performScroll() {
        if (isScrolling) {
            Log.d(TAG, "Already scrolling, ignoring request")
            return
        }

        try {
            isScrolling = true
            Log.d(TAG, "Starting scroll motion")

            val path = Path().apply {
                // דילוג קצר יותר ויותר חלק
                moveTo(displayWidth / 2f, displayHeight * 0.7f)
                lineTo(displayWidth / 2f, displayHeight * 0.3f)
            }

            val gestureDescription = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 300)) // הגדלתי את זמן האנימציה ל-300ms
                .build()

            dispatchGesture(gestureDescription, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    handler.postDelayed({
                        Log.d(TAG, "Scroll completed and cooldown finished")
                        isScrolling = false
                    }, 500) // המתנה נוספת של חצי שנייה אחרי סיום הדילוג
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    handler.post {
                        Log.e(TAG, "Scroll was cancelled")
                        isScrolling = false
                    }
                }
            }, null)

        } catch (e: Exception) {
            Log.e(TAG, "Error during scroll", e)
            isScrolling = false
        }
    }


    override fun onInterrupt() {
        isPerformingAction = false
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}