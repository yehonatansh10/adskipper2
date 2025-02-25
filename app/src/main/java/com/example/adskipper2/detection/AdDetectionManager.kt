package com.example.adskipper2.detection

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.adskipper2.util.Logger

class AdDetectionManager {
    companion object {
        private const val TAG = "AdDetectionManager"
    }

    // מילות מפתח לפי האפליקציה
    private val adKeywordsMap = buildKeywordsMap()

    private var lastDetectedBounds: Rect? = null

    private fun storeLastDetectedBounds(bounds: Rect) {
        lastDetectedBounds = Rect(bounds)
    }

    fun getLastDetectedBounds(): Rect? {
        return lastDetectedBounds?.let { Rect(it) }
    }

    private fun buildKeywordsMap(): Map<String, Set<String>> {
        val map = HashMap<String, Set<String>>()

        // מילות מפתח לטיקטוק
        map["com.zhiliaoapp.musically"] = setOf(
            "ממומן", "שותפות בתשלום", "החלק כדי לדלג",
            " החלק/החליקי למעלה למעבר לפוסט הבא",
            "לצפייה ב-stories", "תוכן פרסומי", "תוכן שיווקי",
            "Sponsored", "View Stories",
            "Swipe up for next post", "Swipe up to skip",
            "LIVE now", "Tap to watch LIVE",
            "Paid partnership", "Sign up",
            "Follows you", "Follow back",
            "Promotional content", "Submit",
            "How do you feel about the video you just watched?",
            "Tap an emoji to submit"
        )

        // מילות מפתח לאינסטגרם
        map["com.instagram.android"] = setOf(
            "מודעה", "ממומן", "מוצע", "פוסט ממומן",
            "שותפות בתשלום", "רוצה לנסות?",
            "הצעות בשבילך",
            "Sponsored", "Suggested",
            "Sponsored post", "Paid partnership",
            "Suggested threads", "Get app",
            "Turn your moments into a reel",
            "Learn more", "Sign up", "Chat on WhatsApp"
        )

        // מילות מפתח לפייסבוק
        map["com.facebook.katana"] = setOf(
            "Sponsored", "ממומן"
        )

        // מילות מפתח ליוטיוב
        map["com.google.android.youtube"] = setOf(
            "Sponsored", "ממומן", "Start now"
        )

        return map
    }

    fun detectAd(rootNode: AccessibilityNodeInfo?, packageName: String): Pair<Boolean, Rect?> {
        if (rootNode == null) return Pair(false, null)

        try {
            // קבלת מילות מפתח לאפליקציה
            val keywords = adKeywordsMap[packageName] ?: return Pair(false, null)

            // לוגיקה ספציפית לאפליקציות מסוימות
            val (found, bounds) = when (packageName) {
                "com.facebook.katana", "com.instagram.android" -> {
                    detectSocialMediaAd(rootNode, keywords)
                }
                "com.google.android.youtube" -> {
                    detectYoutubeAd(rootNode, keywords)
                }
                else -> {
                    detectGenericAd(rootNode, keywords)
                }
            }

            if (found && bounds != null) {
                // כאן אנחנו מעבירים את המידע על הגבולות לפונקציה אחרת במקום להחזיר אותו
                storeLastDetectedBounds(bounds)
                return Pair(true, null)
            }

            return Pair(false, null)

        } catch (e: Exception) {
            Logger.e(TAG, "Error during ad detection", e)
            return Pair(false, null)
        }
    }

    private fun detectSocialMediaAd(rootNode: AccessibilityNodeInfo, keywords: Set<String>): Pair<Boolean, Rect?> {
        var hasReels = false
        var sponsoredNode: AccessibilityNodeInfo? = null
        var reelsNode: AccessibilityNodeInfo? = null

        try {
            // נסה למצוא Reels
            reelsNode = findNodeByText(rootNode, "Reels")
            if (reelsNode != null) {
                hasReels = true
            } else {
                reelsNode = findNodeByText(rootNode, "ריל")
                if (reelsNode != null) {
                    hasReels = true
                }
            }

            // בדיקת ממומן/Sponsored
            for (keyword in keywords) {
                val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
                if (nodes != null) {
                    for (node in nodes) {
                        try {
                            if (node.text?.toString()?.contains(keyword, ignoreCase = true) == true ||
                                node.contentDescription?.toString()?.contains(keyword, ignoreCase = true) == true) {

                                val bounds = Rect()
                                node.getBoundsInScreen(bounds)

                                // שמור reference לשחרור מאוחר יותר
                                sponsoredNode = node

                                return Pair(true, bounds)
                            }
                        } finally {
                            // שחרר nodes שאינם sponsoredNode
                            if (node != sponsoredNode) {
                                node.recycle()
                            }
                        }
                    }
                }
            }

            try {
                return Pair(false, null)
            } finally {
                safeRecycle(sponsoredNode)
            }

        } finally {
            // שחרר את כל ה-nodes
            reelsNode?.recycle()
            sponsoredNode?.recycle()
        }
    }

    private fun detectYoutubeAd(rootNode: AccessibilityNodeInfo, keywords: Set<String>): Pair<Boolean, Rect?> {
        var hasDislike = false
        var sponsoredNode: AccessibilityNodeInfo? = null

        // בדיקת Dislike
        findNodeByText(rootNode, "Dislike")?.let { dislikeNode ->
            hasDislike = true
            dislikeNode.recycle()
        } ?: findNodeByText(rootNode, "דיסלייק")?.let { dislikeNode ->
            hasDislike = true
            dislikeNode.recycle()
        }

        // בדיקת ממומן/Sponsored
        for (keyword in keywords) {
            rootNode.findAccessibilityNodeInfosByText(keyword)?.forEach { node ->
                if (node.text?.toString()?.contains(keyword, ignoreCase = true) == true ||
                    node.contentDescription?.toString()?.contains(keyword, ignoreCase = true) == true) {

                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    sponsoredNode = node
                    return Pair(true, bounds)
                }
                node.recycle()
            }
        }

        // אם לא מצאנו צומת ספציפי או אין Dislike, אין פרסומת
        sponsoredNode?.recycle()
        return Pair(false, null)
    }

    private fun safeRecycle(node: AccessibilityNodeInfo?) {
        try {
            node?.recycle()
        } catch (e: Exception) {
            Logger.e(TAG, "Error recycling node", e)
        }
    }

    private fun detectGenericAd(rootNode: AccessibilityNodeInfo, keywords: Set<String>): Pair<Boolean, Rect?> {
        // חיפוש כללי עבור פרסומות
        for (keyword in keywords) {
            rootNode.findAccessibilityNodeInfosByText(keyword)?.forEach { node ->
                if (node.text?.toString()?.contains(keyword, ignoreCase = true) == true ||
                    node.contentDescription?.toString()?.contains(keyword, ignoreCase = true) == true) {

                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    return Pair(true, bounds)
                }
                node.recycle()
            }
        }

        return Pair(false, null)
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true) {
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
}