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

class UnifiedSkipperService : AccessibilityService() {
    companion object {
        private const val TAG = "UnifiedSkipperService"
        private const val ACTION_COOLDOWN = 2000L
        private const val SCROLL_COOLDOWN = 3000L
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
        val duration: Long = 150,
        val cooldown: Long = SCROLL_COOLDOWN
    )

    private val supportedApps = mapOf(
        "com.zhiliaoapp.musically" to AppConfig(
            packageName = "com.zhiliaoapp.musically",
            adKeywords = listOf(
                "sponsored", "Sponsored", "ממומן",
                "החלק כדי לדלג", "view stories", "View stories", "שותפות בתשלום",
                " החלק/החליקי למעלה למעבר לפוסט הבא",
                "החלק כדי לדלג על", "Swipe up for next post", "swipe up for next post",
                "Swipe up to skip", "swipe up to skip",
                "Not interested", "not interested",
                "Live now", "live now", "Tap to watch live", "tap to watch live",
                "paid partnership", "Paid partnership",
            ),
            scrollConfig = ScrollConfig()
        ),
        "com.instagram.android" to AppConfig(
            packageName = "com.instagram.android",
            adKeywords = listOf(
                "Sponsored", "sponsored", "מודעה", "ממומן",
                "Suggested", "suggested", "מוצע", "פוסט ממומן",
                "Sponsored post", "Paid partnership", "הצעות בשבילך", "רוצה לנסות?"
            ),
            scrollConfig = ScrollConfig(
                startHeightRatio = 0.7f,
                endHeightRatio = 0.3f,
                duration = 200
            )
        ),
        "com.facebook.katana" to AppConfig(
            packageName = "com.facebook.katana",
            adKeywords = listOf(
                "Sponsored", "sponsored", "ממומן",
            ),
            scrollConfig = ScrollConfig(
                startHeightRatio = 0.65f,
                endHeightRatio = 0.35f,
                duration = 250
            )
        ),
        "com.google.android.youtube" to AppConfig(
            packageName = "com.google.android.youtube",
            adKeywords = listOf(
                "Sponsored", "sponsored", "ממומן",
            ),
            scrollConfig = ScrollConfig(
                startHeightRatio = 0.7f,
                endHeightRatio = 0.3f,
                duration = 200
            ),
            customActions = listOf("דלג על מודעה", "Skip Ad", "Skip")
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        displayWidth = resources.displayMetrics.widthPixels
        displayHeight = resources.displayMetrics.heightPixels
        Log.d(TAG, "Service connected with dimensions: $displayWidth x $displayHeight")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        event.packageName?.toString()?.let { packageName ->
            currentAppConfig = supportedApps[packageName]
            if (currentAppConfig != null && !isPerformingAction) {
                when (event.eventType) {
                    AccessibilityEvent.TYPE_VIEW_SCROLLED,
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                        if (packageName == "com.google.android.youtube") {
                            val rootNode = rootInActiveWindow ?: return
                            // בודק אם אנחנו במסך שורטס
                            val isShorts = findNodeByText(rootNode, "Shorts")?.let { shortsNode ->
                                shortsNode.recycle()
                                true
                            } ?: findNodeByText(rootNode, "שורטס")?.let { shortsNode ->
                                shortsNode.recycle()
                                true
                            } ?: false

                            if (isShorts) {
                                checkContent() // מטפל בשורטס כמו בפייסבוק/טיקטוק
                            } else {
                                checkForYoutubeAds() // מטפל ביוטיוב רגיל
                            }
                            rootNode.recycle()
                        } else {
                            checkContent()
                        }
                    }
                }
            }
        }
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

    private fun checkForYoutubeAds() {
        try {
            val rootNode = rootInActiveWindow ?: return
            currentAppConfig?.customActions?.forEach { actionText ->
                findNodeByText(rootNode, actionText)?.let { skipButton ->
                    if (skipButton.isClickable) {
                        skipButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d(TAG, "Clicked YouTube skip button: $actionText")
                    }
                    skipButton.recycle()
                }
            }
            rootNode.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking YouTube ads", e)
        }
    }

    private fun checkContent() {
        if (isScrolling) return

        try {
            val rootNode = rootInActiveWindow ?: return
            val appConfig = currentAppConfig ?: return
            var sponsoredNode: AccessibilityNodeInfo? = null

            // בדיקה מיוחדת לפייסבוק
            if (appConfig.packageName == "com.facebook.katana") {
                var hasReels = false
                var hasSponsored = false

                // בדיקת Reels
                findNodeByText(rootNode, "Reels")?.let { reelsNode ->
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
            } else {
                // הלוגיקה המקורית לשאר האפליקציות
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

            sponsoredNode?.let { node ->
                val bounds = Rect()
                node.getBoundsInScreen(bounds)

                val centerY = displayHeight / 2
                val tolerance = displayHeight / 2

                if (bounds.centerY() in 1 until displayHeight &&
                    bounds.centerY() in (centerY - tolerance)..(centerY + tolerance)) {

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastScrollTime > appConfig.scrollConfig.cooldown) {
                        Log.d(TAG, "Found ad in ${appConfig.packageName} with bounds: ${bounds.centerY()}")
                        performScroll(appConfig.scrollConfig)
                        lastScrollTime = currentTime
                    }
                }
                node.recycle()
            }

            rootNode.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkContent", e)
        }
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

            dispatchGesture(gestureDescription, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    handler.postDelayed({
                        isScrolling = false
                    }, 250)
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    handler.post {
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