package com.example.adskipper2

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.adskipper2.analytics.AnalyticsManager
import com.example.adskipper2.config.KeywordManager
import com.example.adskipper2.config.ScrollConfig
import com.example.adskipper2.service.ServiceState
import com.example.adskipper2.util.ErrorHandler
import com.example.adskipper2.util.Logger
import com.example.adskipper2.service.ServiceManager

class UnifiedSkipperService : AccessibilityService() {
    private lateinit var errorHandler: ErrorHandler
    private lateinit var analyticsManager: AnalyticsManager
    private lateinit var keywordManager: KeywordManager
    private var isServiceActive = true
    private var isActiveScanning = true // האם מבצעים סריקה מלאה
    private var currentForegroundPackage: String? = null

    companion object {
        private const val TAG = "UnifiedSkipperService"
        private const val ACTION_COOLDOWN = 2000L
        private const val SCROLL_COOLDOWN = 2000L
        private const val DEBUG = false
    }

    private fun logDebug(message: String) {
        if (DEBUG) {
            Logger.d(TAG, message)
        }
    }

    private fun logError(message: String, e: Exception? = null) {
        if (DEBUG) {
            if (e != null) {
                Logger.e(TAG, message, e)
            } else {
                Logger.e(TAG, message)
            }
        }
    }

    private data class ScrollEvent(
        val timestamp: Long,
        val y: Int
    )

    private var isPerformingAction = false
    private var displayWidth = 0
    private var displayHeight = 0
    private val handler = Handler(Looper.getMainLooper())
    private var lastActionTime = 0L
    private var lastScrollTime = 0L
    private var isScrolling = false
    private var lastScrollEvents = mutableListOf<ScrollEvent>()
    private val SCROLL_DIRECTION_WINDOW = 1000L
    private val recentPackageEvents = ArrayDeque<String>(5)

    override fun onServiceConnected() {
        try {
            super.onServiceConnected()
            errorHandler = ErrorHandler.getInstance(this)
            analyticsManager = AnalyticsManager.getInstance(this)
            keywordManager = KeywordManager.getInstance(this)
            displayWidth = resources.displayMetrics.widthPixels
            displayHeight = resources.displayMetrics.heightPixels

            // אתחול מחדש של הפרמטרים מחשש לסגירה וחזרה
            isPerformingAction = false
            isScrolling = false
            lastScrollEvents.clear()

            // הפעלת ניטור חוזר כל 30 שניות לוודא שהשירות עדיין פעיל
            setupWatchdog()

            // בדוק אם אפליקציה נוכחית היא אפליקציית מטרה
            val currentPackage = getCurrentForegroundPackage()
            isActiveScanning = currentPackage != null && keywordManager.isSupportedApp(currentPackage)

            logDebug("Service connected with dimensions: $displayWidth x $displayHeight")
            logDebug("Initial scanning state: ${if (isActiveScanning) "ACTIVE" else "SLEEP"}")
        } catch (e: Exception) {
            logError("Error in onServiceConnected", e)
        }
    }

    private fun setupWatchdog() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (isServiceActive) {
                    // בדיקת מצב נוכחי ורענון אם צריך
                    val currentPackage = getCurrentForegroundPackage()
                    if (currentPackage != null && keywordManager.isSupportedApp(currentPackage)) {
                        if (!isActiveScanning) {
                            activateFullService()
                        }
                        // בדיקת תוכן מחדש למקרה שפספסנו משהו
                        checkContent()
                    }
                    // המשך ניטור
                    handler.postDelayed(this, 30000)
                }
            }
        }, 30000)
    }

    private fun setupPeriodicCheck() {
        handler.postDelayed({
            if (isServiceActive) {
                // בדוק רק אם נמצאים באפליקציית מטרה
                val currentPackage = getCurrentForegroundPackage()
                if (currentPackage != null && keywordManager.isSupportedApp(currentPackage)) {
                    checkContent()
                }
                setupPeriodicCheck()
            }
        }, 2 * 60 * 1000)
    }

    // הוסף מתודה חדשה זו אחרי onServiceConnected
    private fun getCurrentForegroundPackage(): String? {
        try {
            // קבלת חלון שירות הנגישות הנוכחי
            val rootNode = rootInActiveWindow
            val result = rootNode?.packageName?.toString()
            rootNode?.recycle()

            // אם לא הצלחנו לקבל את החבילה, ננסה דרך אלטרנטיבית
            if (result == null) {
                // ללא גישה ישירה למערכת ניהול החבילות, ננסה לבדוק לפי האירועים האחרונים
                val events = getRecentPackageEvents()
                if (events.isNotEmpty()) {
                    return events.first()
                }
            }

            return result
        } catch (e: Exception) {
            logError("Error getting current package", e)
            return null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!ServiceState.getInstance(this).isEnabled()) {
            return  // אם השירות מושבת, צא מהמתודה מיד
        }

        // בדיקת שינוי חלון כדי לזהות מעבר בין אפליקציות
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()

            if (packageName != currentForegroundPackage) {
                currentForegroundPackage = packageName
                ServiceManager.getInstance(this).recordActivity()

                // בדיקה אם האפליקציה הנוכחית היא אחת מאפליקציות היעד
                val isTargetApp = packageName != null && keywordManager.isSupportedApp(packageName)

                if (isTargetApp && !isActiveScanning) {
                    // התעוררות כשנמצאים באפליקציית מטרה
                    activateFullService()
                    // התחלת הבדיקות התקופתיות
                    setupPeriodicCheck()
                } else if (!isTargetApp && isActiveScanning) {
                    // כניסה למצב שינה - הפסקת כל הבדיקות
                    enterSleepMode()
                    // הפסקת הבדיקות התקופתיות
                    handler.removeCallbacksAndMessages(null)
                }

            }
        }

        event.packageName?.toString()?.let { packageName ->
            if (packageName.isNotEmpty()) {
                synchronized(recentPackageEvents) {
                    if (!recentPackageEvents.contains(packageName)) {
                        recentPackageEvents.add(packageName)
                        if (recentPackageEvents.size > 5) {
                            recentPackageEvents.removeFirst()
                        }
                    }
                }
            }
        }

        // המשך הלוגיקה הקיימת רק אם במצב סריקה פעיל
        if (isActiveScanning) {
            // בדיקה שלא מדובר באפליקציה רגישה
            val packageName = event.packageName?.toString()

            // שימוש ברשימה לבנה - נעבד רק אפליקציות שמוגדרות מראש
            if (packageName != null && keywordManager.isSupportedApp(packageName)) {
                val appConfig = keywordManager.getAppConfig(packageName)
                if (appConfig != null && !isPerformingAction) {
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
    }

    private fun getRecentPackageEvents(): List<String> {
        synchronized(recentPackageEvents) {
            return recentPackageEvents.toList()
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
        val nodesToRecycle = mutableListOf<AccessibilityNodeInfo>()

        try {
            rootNode = rootInActiveWindow ?: return
            nodesToRecycle.add(rootNode)
            val packageName = rootNode.packageName?.toString() ?: return
            val appConfig = keywordManager.getAppConfig(packageName) ?: return

            // ולידציה וקיצור דרך מוקדם בקוד
            if (!keywordManager.isSupportedApp(packageName)) {
                return
            }

            // המשך הלוגיקה המקורית עם טיפול מאובטח יותר בצומת
            when (packageName) {
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
                        ServiceManager.getInstance(this).recordActivity()
                        performScroll(appConfig.scrollConfig)
                        lastScrollTime = currentTime
                    }
                }
                node.recycle()
            }

            rootNode.recycle()
        } catch (e: Exception) {
            logError("Error in checkContent", e)
            errorHandler.handleError(TAG, e, false)
        } finally {
            // וידוא שחרור כל המשאבים גם במקרה של שגיאה
            nodesToRecycle.forEach { node ->
                try {
                    if (node != sponsoredNode && node != rootNode) {
                        node.recycle()
                    }
                } catch (e: Exception) {
                    // התעלם משגיאות בשחרור
                }
            }

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
            currentForegroundPackage?.let { packageName ->
                analyticsManager.trackAdDetection(packageName, true)
            }

            ServiceManager.getInstance(this).recordActivity()

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
                        currentForegroundPackage?.let { packageName ->
                            analyticsManager.trackError("scroll_cancelled", "Scroll gesture cancelled for $packageName")
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
        isServiceActive = false
        isPerformingAction = false
        isScrolling = false
        handler.removeCallbacksAndMessages(null)
        logDebug("Service destroyed")
    }

    private fun activateFullService() {
        if (!isActiveScanning) {
            isActiveScanning = true
            // רישום ליומן
            logDebug("Activating full scanning for package: $currentForegroundPackage")

            // כאן אפשר להפעיל מחדש מנגנונים נוספים אם נדרש
        }
    }

    private fun enterSleepMode() {
        if (isActiveScanning) {
            isActiveScanning = false
            // רישום ליומן
            logDebug("Entering sleep mode, current package: $currentForegroundPackage")

            // ניקוי משאבים כדי לחסוך סוללה
            isPerformingAction = false
            isScrolling = false
            lastScrollEvents.clear()
            handler.removeCallbacksAndMessages(null)
        }
    }
}