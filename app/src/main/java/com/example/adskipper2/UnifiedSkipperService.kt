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
import com.example.adskipper2.util.ErrorHandler
import com.example.adskipper2.analytics.AnalyticsManager

class UnifiedSkipperService : AccessibilityService() {
    private lateinit var errorHandler: ErrorHandler
    private lateinit var analyticsManager: AnalyticsManager
    companion object {
        private const val TAG = "UnifiedSkipperService"
        private const val ACTION_COOLDOWN = 2000L
        private const val SCROLL_COOLDOWN = 2000L
        private const val DEBUG = false
    }

    private fun logDebug(message: String) {
        if (DEBUG) {
            Log.d(TAG, message)
        }
    }

    private fun logError(message: String, e: Exception? = null) {
        if (DEBUG) {
            if (e != null) {
                Log.e(TAG, message, e)
            } else {
                Log.e(TAG, message)
            }
        }
    }

    private data class AppConfig(
        val packageName: String,
        val adKeywords: List<String>,
        val scrollConfig: ScrollConfig,
        val customActions: List<String> = emptyList()
    )

    private data class ScrollConfig(
        val startHeightRatio: Float = 0.6f,
        val endHeightRatio: Float = 0.4f,
        val duration: Long = 100,
        val cooldown: Long = SCROLL_COOLDOWN
    )

    private data class ScrollEvent(
        val timestamp: Long,
        val y: Int
    )

    private val supportedApps = mapOf(
        "com.zhiliaoapp.musically" to AppConfig(
            packageName = "com.zhiliaoapp.musically",
            adKeywords = listOf(
                "ממומן", "שותפות בתשלום", "החלק כדי לדלג",
                " החלק/החליקי למעלה למעבר לפוסט הבא",
                "לצפייה ב-stories", "תוכן פרסומי", "תוכן שיווקי",
                "Sponsored", "View Stories",
                "Swipe up for next post", "Swipe up to skip",
                "Not interested", "LIVE now", "Tap to watch LIVE",
                "Paid partnership", "Sign up",
                "Follows you", "Follow back",
                "Promotional content", "Submit",
                "How do you feel about the video you just watched?",
                "Tap an emoji to submit",
            ),
            scrollConfig = ScrollConfig()
        ),
        "com.instagram.android" to AppConfig(
            packageName = "com.instagram.android",
            adKeywords = listOf(
                "מודעה", "ממומן", "מוצע", "פוסט ממומן",
                "שותפות בתשלום", "רוצה לנסות?",
                "הצעות בשבילך", "ממומנות",
                "Sponsored", "Suggested",
                "Sponsored post", "Paid partnership",
                "Suggested threads", "Get app",
                "Turn your moments into a reel",
                "Learn more", "Sign up", "Chat on WhatsApp",
                "Get offer", "Get quote",
                "Shop now", "Install now",
            ),
            scrollConfig = ScrollConfig()  // השתמש בקונפיגורציית ברירת המחדל
        ),
        "com.facebook.katana" to AppConfig(
            packageName = "com.facebook.katana",
            adKeywords = listOf(
                "Sponsored", "ממומן",
            ),
            scrollConfig = ScrollConfig()  // השתמש בקונפיגורציית ברירת המחדל
        ),
        "com.google.android.youtube" to AppConfig(
            packageName = "com.google.android.youtube",
            adKeywords = listOf(
                "Sponsored", "ממומן", "Start now",
            ),
            scrollConfig = ScrollConfig()  // אותה קונפיגורציית גלילה בדיוק כמו בטיקטוק
        )
    )

    private var isPerformingAction = false
    private var displayWidth = 0
    private var displayHeight = 0
    private val handler = Handler(Looper.getMainLooper())
    private var lastActionTime = 0L
    private var lastScrollTime = 0L
    private var isScrolling = false
    private var currentAppConfig: AppConfig? = null
    private var lastScrollEvents = mutableListOf<ScrollEvent>()
    private val SCROLL_DIRECTION_WINDOW = 1000L

    override fun onServiceConnected() {
        try {
            super.onServiceConnected()
            errorHandler = ErrorHandler.getInstance(this)
            analyticsManager = AnalyticsManager.getInstance(this)
            displayWidth = resources.displayMetrics.widthPixels
            displayHeight = resources.displayMetrics.heightPixels
            logDebug("Service connected with dimensions: $displayWidth x $displayHeight")
        } catch (e: Exception) {
            logError("Error in onServiceConnected", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // בדיקה שלא מדובר באפליקציה רגישה
        val packageName = event.packageName?.toString()

        // שימוש בשימת רשימה לבנה - נעבד רק אפליקציות שמוגדרות מראש
        if (packageName != null && supportedApps.containsKey(packageName)) {
            currentAppConfig = supportedApps[packageName]
            if (currentAppConfig != null && !isPerformingAction) {
                when (event.eventType) {
                    AccessibilityEvent.TYPE_VIEW_SCROLLED,
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                        checkContent()
                    }
                }
            }
        }
    }

    private fun isScrollingForward(): Boolean? {
        val currentTime = System.currentTimeMillis()
        lastScrollEvents.removeAll { currentTime - it.timestamp > SCROLL_DIRECTION_WINDOW }
        if (lastScrollEvents.size < 2) return null
        val sortedEvents = lastScrollEvents.sortedBy { it.timestamp }
        return sortedEvents.first().y > sortedEvents.last().y
    }

    private fun updateScrollDirection(bounds: Rect) {
        lastScrollEvents.add(ScrollEvent(
            timestamp = System.currentTimeMillis(),
            y = bounds.centerY()
        ))
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(text) == true) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findNodeByText(child, text)?.let { result ->
                    child.recycle()
                    return result
                }
                child.recycle()
            }
        }
        return null
    }

    private fun checkContent() {
        if (isScrolling) return

        var rootNode: AccessibilityNodeInfo? = null
        var sponsoredNode: AccessibilityNodeInfo? = null

        try {
            rootNode = rootInActiveWindow ?: return
            val appConfig = currentAppConfig ?: return

            // ולידציה וקיצור דרך מוקדם בקוד
            if (!supportedApps.containsKey(appConfig.packageName)) {
                return
            }

            // המשך הלוגיקה המקורית עם טיפול מאובטח יותר בצומת
            when (appConfig.packageName) {
                "com.facebook.katana", "com.instagram.android" -> {
                    var hasReels = false
                    var hasSponsored = false

                    // בדיקת Reels
                    findNodeByText(rootNode, "Reels")?.let { reelsNode ->
                        hasReels = true
                        reelsNode.recycle()
                    } ?: findNodeByText(rootNode, "ריל")?.let { reelsNode ->
                        hasReels = true
                        reelsNode.recycle()
                    }

                    // בדיקת ממומן/Sponsored
                    for (keyword in appConfig.adKeywords) {
                        rootNode.findAccessibilityNodeInfosByText(keyword)?.forEach { node ->
                            if (node.text?.toString()?.contains(keyword, ignoreCase = true) == true ||
                                node.contentDescription?.toString()?.contains(keyword, ignoreCase = true) == true) {
                                hasSponsored = true
                                sponsoredNode = AccessibilityNodeInfo.obtain(node)
                                return@forEach
                            }
                            node.recycle()
                        }
                        if (sponsoredNode != null) break
                    }

                    // רק אם יש גם Reels וגם ממומן נמשיך
                    if (!hasReels || !hasSponsored) {
                        sponsoredNode?.recycle()
                        rootNode.recycle()
                        return
                    }
                }

                "com.google.android.youtube" -> {
                    var hasDislike = false
                    var hasSponsored = false

                    // בדיקת Dislike
                    findNodeByText(rootNode, "Dislike")?.let { dislikeNode ->
                        hasDislike = true
                        dislikeNode.recycle()
                    } ?: findNodeByText(rootNode, "דיסלייק")?.let { dislikeNode ->
                        hasDislike = true
                        dislikeNode.recycle()
                    }

                    // בדיקת ממומן/Sponsored
                    for (keyword in appConfig.adKeywords) {
                        rootNode.findAccessibilityNodeInfosByText(keyword)?.forEach { node ->
                            if (node.text?.toString()?.contains(keyword, ignoreCase = true) == true ||
                                node.contentDescription?.toString()?.contains(keyword, ignoreCase = true) == true) {
                                hasSponsored = true
                                sponsoredNode = AccessibilityNodeInfo.obtain(node)
                                return@forEach
                            }
                            node.recycle()
                        }
                        if (sponsoredNode != null) break
                    }

                    // רק אם יש גם Dislike וגם ממומן נמשיך
                    if (!hasDislike || !hasSponsored) {
                        sponsoredNode?.recycle()
                        rootNode.recycle()
                        return
                    }
                }

                else -> {
                    // הלוגיקה המקורית לשאר האפליקציות (טיקטוק)
                    for (keyword in appConfig.adKeywords) {
                        rootNode.findAccessibilityNodeInfosByText(keyword)?.forEach { node ->
                            if (node.text?.toString()?.contains(keyword, ignoreCase = true) == true ||
                                node.contentDescription?.toString()?.contains(keyword, ignoreCase = true) == true) {
                                sponsoredNode = AccessibilityNodeInfo.obtain(node)
                                return@forEach
                            }
                            node.recycle()
                        }
                        if (sponsoredNode != null) break
                    }
                }
            }

            sponsoredNode?.let { node ->
                val bounds = Rect()
                node.getBoundsInScreen(bounds)

                val centerY = displayHeight / 2
                val tolerance = displayHeight / 2

                if (bounds.centerY() in 1 until displayHeight &&
                    bounds.centerY() in (centerY - tolerance)..(centerY + tolerance)) {

                    updateScrollDirection(bounds)
                    val scrollForward = isScrollingForward()
                    val currentTime = System.currentTimeMillis()
                    if ((scrollForward == true || scrollForward == null) &&
                        currentTime - lastScrollTime > appConfig.scrollConfig.cooldown) {
                        logDebug("Found ad in ${appConfig.packageName} with bounds: ${bounds.centerY()}")
                        performScroll(appConfig.scrollConfig)
                        lastScrollTime = currentTime
                    }
                }
                node.recycle()
            }

            sponsoredNode?.recycle()
            rootNode.recycle()
        } catch (e: Exception) {
            logError("Error in checkContent", e)
            errorHandler.handleError(TAG, e, false)
        } finally {
            // וידוא שחרור משאבים גם במקרה של שגיאה
            safeRecycle(sponsoredNode)
            safeRecycle(rootNode)
        }
    }

    private fun safeRecycle(node: AccessibilityNodeInfo?) {
        try {
            node?.recycle()
        } catch (e: Exception) {
            // לכידת שגיאות שעלולות להיגרם בשחרור הצומת
            logError("Error recycling node", e)
        }
    }

    private fun isValidContent(node: AccessibilityNodeInfo?, keyword: String): Boolean {
        if (node == null) return false

        // בדיקת תוכן טקסט הצומת
        val nodeText = node.text?.toString() ?: ""
        val nodeContentDesc = node.contentDescription?.toString() ?: ""

        // בדיקה בסיסית
        if (nodeText.contains(keyword, ignoreCase = true) ||
            nodeContentDesc.contains(keyword, ignoreCase = true)) {

            // בדיקות נוספות - למנוע זיהוי שגוי

            // אם הצומת קטן מדי - ייתכן שזה UI אחר ולא פרסומת
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() < 50 || bounds.height() < 20) {
                return false
            }

            // בדיקת אורך טקסט (על פי רוב טקסט פרסומת קצר)
            if (nodeText.length > 100 && !nodeText.contains("sponsored", ignoreCase = true)) {
                return false
            }

            return true
        }

        return false
    }

    private fun performScroll(scrollConfig: ScrollConfig) {
        if (isScrolling) return

        try {
            isScrolling = true
            val path = Path().apply {
                moveTo(displayWidth / 2f, displayHeight * scrollConfig.startHeightRatio)
                lineTo(displayWidth / 2f, displayHeight * scrollConfig.endHeightRatio)
            }

            val gestureDescription = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, scrollConfig.duration))
                .build()

            // תיעוד דילוג על פרסומת
            currentAppConfig?.let { config ->
                analyticsManager.trackAdDetection(config.packageName, true)
            }

            dispatchGesture(gestureDescription, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    handler.postDelayed({
                        isScrolling = false
                    }, 250)
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    handler.post {
                        isScrolling = false

                        // תיעוד כישלון בדילוג
                        currentAppConfig?.let { config ->
                            analyticsManager.trackError("scroll_cancelled", "Scroll gesture cancelled for ${config.packageName}")
                        }
                    }
                }
            }, null)
        } catch (e: Exception) {
            logError("Error during scroll", e)
            errorHandler.handleError(TAG, e, false)
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