package com.example.adskipper2

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.SharedPreferences
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.adskipper2.gesture.GesturePlayer
import com.example.adskipper2.gesture.GestureAction

class SkipperService : AccessibilityService() {
    private lateinit var prefs: SharedPreferences
    private lateinit var gesturePrefs: SharedPreferences
    private lateinit var gesturePlayer: GesturePlayer
    private val handler = Handler(Looper.getMainLooper())
    private val scanInterval = 1000L
    private val scanHandler = Handler(Looper.getMainLooper())
    private val scanRunnable = object : Runnable {
        override fun run() {
            checkCurrentScreen()
            scanHandler.postDelayed(this, scanInterval)
        }
    }

    companion object {
        private const val TAG = "SkipperService"
    }

    override fun onCreate() {
        super.onCreate()
        initializeServices()
    }

    private fun initializeServices() {
        prefs = getSharedPreferences("targets", MODE_PRIVATE)
        gesturePrefs = getSharedPreferences("gestures", MODE_PRIVATE)
        gesturePlayer = GesturePlayer(this)
        gesturePlayer.setAccessibilityService(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        scanHandler.post(scanRunnable)
        Log.d(TAG, "Service Connected")
        showToast("שירות הדילוג הופעל")
    }

    private fun checkCurrentScreen() {
        val currentPackage = rootInActiveWindow?.packageName?.toString() ?: return
        val targetApps = prefs.getStringSet("selected_apps", setOf()) ?: return

        if (currentPackage in targetApps) {
            performScreenCheck(currentPackage)
        }
    }

    private fun performScreenCheck(currentPackage: String) {
        val targetText = prefs.getString("${currentPackage}_text", "")?.lowercase() ?: return

        try {
            val rootNode = rootInActiveWindow ?: return
            val allNodes = ArrayList<AccessibilityNodeInfo>()
            findAllNodes(rootNode, allNodes)

            Log.d(TAG, "Scanning ${allNodes.size} nodes for text: $targetText")

            for (node in allNodes) {
                val nodeText = node.text?.toString()?.lowercase() ?: ""
                val nodeDesc = node.contentDescription?.toString()?.lowercase() ?: ""

                Log.d(TAG, "Node text: $nodeText, desc: $nodeDesc")

                if (nodeText.contains(targetText) || nodeDesc.contains(targetText)) {
                    performActionOnNode(node)
                    break
                }
            }

            allNodes.forEach { it.recycle() }
            rootNode.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Error in performScreenCheck", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val currentPackage = event.packageName?.toString() ?: return
        val targetApps = prefs.getStringSet("selected_apps", setOf()) ?: return

        Log.d(TAG, "Event type: ${event.eventType}")
        Log.d(TAG, "Event text: ${event.text}")
        Log.d(TAG, "Event description: ${event.contentDescription}")
        Log.d(TAG, "Window Changes: ${event.windowChanges}")
        Log.d(TAG, "Content Change Types: ${event.contentChangeTypes}")

        if (currentPackage in targetApps) {
            Log.d(TAG, "Package matches target apps")
            performScreenCheck(currentPackage)
        }
    }

    private fun findAllNodes(node: AccessibilityNodeInfo?, nodes: ArrayList<AccessibilityNodeInfo>) {
        if (node == null) return
        nodes.add(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                findAllNodes(child, nodes)
            }
        }
    }

    private fun performActionOnNode(node: AccessibilityNodeInfo) {
        if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            showToast("לחיצה על כפתור")
            return
        }

        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                showToast("לחיצה על אזור ההורה")
                return
            }
            val newParent = parent.parent
            parent.recycle()
            parent = newParent
        }

        performAction()
    }

    private fun performAction() {
        val recordedActionsString = gesturePrefs.getString("recorded_actions", null)

        if (!recordedActionsString.isNullOrEmpty()) {
            executeRecordedActions(recordedActionsString)
            return
        }

        performDefaultAction()
    }

    private fun executeRecordedActions(actionsString: String) {
        val actions = parseRecordedActions(actionsString)
        if (actions.isNotEmpty()) {
            gesturePlayer.playActions(actions)
            showToast("מבצע פעולות מוקלטות")
        }
    }

    private fun performDefaultAction() {
        rootInActiveWindow?.let {
            it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            showToast("בוצעה פעולת דילוג")
        }
    }

    private fun showToast(message: String) {
        handler.post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseRecordedActions(actionsString: String): List<GestureAction> {
        return try {
            actionsString.split(";").mapNotNull { actionStr ->
                when {
                    actionStr.startsWith("tap:") -> createTapAction(actionStr, false)
                    actionStr.startsWith("doubletap:") -> createTapAction(actionStr, true)
                    actionStr.startsWith("scroll:") -> createScrollAction(actionStr)
                    else -> null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing actions: ${e.message}")
            emptyList()
        }
    }

    private fun createTapAction(actionStr: String, isDouble: Boolean): GestureAction? {
        val prefix = if (isDouble) "doubletap:" else "tap:"
        val (x, y) = actionStr.substringAfter(prefix).split(",").map { it.toFloat() }
        return if (isDouble) GestureAction.DoubleTap(x, y) else GestureAction.Tap(x, y)
    }

    private fun createScrollAction(actionStr: String): GestureAction? {
        val (startX, startY, endX, endY) = actionStr.substringAfter("scroll:")
            .split(",").map { it.toFloat() }
        return GestureAction.Scroll(startX, startY, endX, endY)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
        scanHandler.removeCallbacks(scanRunnable)
    }
}