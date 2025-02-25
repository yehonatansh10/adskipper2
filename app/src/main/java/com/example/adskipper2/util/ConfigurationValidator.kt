package com.example.adskipper2.util

import android.content.Context
import com.example.adskipper2.AppInfo

object ConfigurationValidator {
    private const val TAG = "ConfigValidator"

    // בדיקת הגדרות כלליות
    fun validateAppConfiguration(context: Context, apps: Set<AppInfo>, targetTexts: Set<String>): Boolean {
        // וידוא שיש לפחות אפליקציה אחת מוגדרת
        if (apps.isEmpty()) {
            Logger.d(TAG, "No apps configured")
            return false
        }

        // וידוא שהאפליקציות המוגדרות תקפות
        val validApps = apps.filter { app ->
            InputValidator.validatePackageName(context, app.packageName)
        }

        if (validApps.isEmpty()) {
            Logger.d(TAG, "No valid apps configured")
            return false
        }

        // וידוא שיש לפחות מילת מפתח אחת מוגדרת
        if (targetTexts.isEmpty()) {
            Logger.d(TAG, "No target texts configured")
            return false
        }

        // וידוא שמילות המפתח תקפות
        val validTexts = targetTexts.filter { text ->
            text.isNotBlank() && text.length <= 100
        }

        if (validTexts.isEmpty()) {
            Logger.d(TAG, "No valid target texts configured")
            return false
        }

        return true
    }

    // בדיקת הגדרות מתקדמות (אם יש)
    fun validateAdvancedSettings(scrollSettings: Map<String, Any>): Boolean {
        // וידוא שמהירות הגלילה הגיונית
        val scrollDuration = scrollSettings["duration"] as? Long ?: 0L
        if (scrollDuration < 50 || scrollDuration > 1000) {
            Logger.d(TAG, "Invalid scroll duration: $scrollDuration")
            return false
        }

        // וידוא שמיקום הגלילה הגיוני
        val startRatio = scrollSettings["startHeightRatio"] as? Float ?: 0f
        val endRatio = scrollSettings["endHeightRatio"] as? Float ?: 0f

        if (startRatio < 0 || startRatio > 1 || endRatio < 0 || endRatio > 1) {
            Logger.d(TAG, "Invalid scroll ratios: start=$startRatio, end=$endRatio")
            return false
        }

        return true
    }
}