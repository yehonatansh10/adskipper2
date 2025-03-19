package com.yonash.adskipper2.detection

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.yonash.adskipper2.config.KeywordManager
import com.yonash.adskipper2.util.Logger

class AdDetectionManager(private val context: Context) {
    companion object {
        private const val TAG = "AdDetectionManager"
    }

    // מילות מפתח לפי האפליקציה
    private val keywordManager = KeywordManager(context)

    private var lastDetectedBounds: Rect? = null

    private fun storeLastDetectedBounds(bounds: Rect) {
        lastDetectedBounds = Rect(bounds)
    }

    fun getLastDetectedBounds(): Rect? {
        return lastDetectedBounds?.let { Rect(it) }
    }

    fun detectAd(rootNode: AccessibilityNodeInfo?, packageName: String): Pair<Boolean, Rect?> {
        if (rootNode == null) return Pair(false, null)

        try {
            // קבלת מילות מפתח לאפליקציה
            val keywords = keywordManager.getKeywords(packageName)
            if (keywords.isEmpty()) {
                Logger.d(TAG, "No keywords defined for package: $packageName")
                return Pair(false, null)
            }

            Logger.d(TAG, "Scanning for ads in $packageName with ${keywords.size} keywords")

            // לוגיקה ספציפית לאפליקציות מסוימות
            val result = when (packageName) {
                "com.facebook.katana", "com.instagram.android" -> {
                    Logger.d(TAG, "Using social media detection for $packageName")
                    detectSocialMediaAd(rootNode, keywords)
                }
                "com.google.android.youtube" -> {
                    Logger.d(TAG, "Using YouTube detection for $packageName")
                    detectYoutubeAd(rootNode, keywords)
                }
                else -> {
                    Logger.d(TAG, "Using generic detection for $packageName")
                    detectGenericAd(rootNode, keywords)
                }
            }

            if (result.first) {
                Logger.d(TAG, "Ad detection successful in $packageName")
            }

            return result
        } catch (e: Exception) {
            Logger.e(TAG, "Error during ad detection", e)
            return Pair(false, null)
        }
    }

    private fun detectSocialMediaAd(rootNode: AccessibilityNodeInfo, keywords: Set<String>): Pair<Boolean, Rect?> {
        var hasReels = false
        var sponsoredNode: AccessibilityNodeInfo? = null

        findNodeByText(rootNode, "Reels")?.let { reelsNode ->
            hasReels = true
            Logger.d(TAG, "Found 'Reels' indicator in social media")
            reelsNode.recycle()
        } ?: findNodeByText(rootNode, "ריל")?.let { reelsNode ->
            hasReels = true
            Logger.d(TAG, "Found 'ריל' indicator in social media")
            reelsNode.recycle()
        }

        // בדיקת ממומן/Sponsored
        for (keyword in keywords) {
            rootNode.findAccessibilityNodeInfosByText(keyword)?.forEach { node ->
                if (node.text?.toString()?.contains(keyword, ignoreCase = true) == true ||
                    node.contentDescription?.toString()?.contains(keyword, ignoreCase = true) == true) {

                    val detectedText = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
                    Logger.d(TAG, "SOCIAL MEDIA AD DETECTED! Keyword: '$keyword' found in text: '$detectedText'")

                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    sponsoredNode = node
                    return Pair(true, bounds)
                }
                node.recycle()
            }
        }

        // אם לא מצאנו צומת ספציפי או אין Reels, אין פרסומת
        sponsoredNode?.recycle()
        return Pair(false, null)
    }

    private fun detectYoutubeAd(rootNode: AccessibilityNodeInfo, keywords: Set<String>): Pair<Boolean, Rect?> {
        var hasDislike = false
        var sponsoredNode: AccessibilityNodeInfo? = null

        // בדיקת Dislike
        findNodeByText(rootNode, "Dislike")?.let { dislikeNode ->
            hasDislike = true
            Logger.d(TAG, "Found 'Dislike' indicator in YouTube")
            dislikeNode.recycle()
        } ?: findNodeByText(rootNode, "דיסלייק")?.let { dislikeNode ->
            hasDislike = true
            Logger.d(TAG, "Found 'דיסלייק' indicator in YouTube")
            dislikeNode.recycle()
        }

        // בדיקת ממומן/Sponsored
        for (keyword in keywords) {
            rootNode.findAccessibilityNodeInfosByText(keyword)?.forEach { node ->
                if (node.text?.toString()?.contains(keyword, ignoreCase = true) == true ||
                    node.contentDescription?.toString()?.contains(keyword, ignoreCase = true) == true) {

                    val detectedText = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
                    Logger.d(TAG, "YOUTUBE AD DETECTED! Keyword: '$keyword' found in text: '$detectedText'")

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

                    val detectedText = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
                    Logger.d(TAG, "GENERIC AD DETECTED! Keyword: '$keyword' found in text: '$detectedText'")

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
            Logger.d(TAG, "Found node with text: '$text'")
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