package com.example.adskipper2.util

import android.util.Log
import com.example.adskipper2.BuildConfig
import java.util.regex.Pattern

object Logger {
    private const val TAG_PREFIX = "AdSkipper_"
    private const val MAX_LOG_LENGTH = 1000 // הגבלת אורך הודעות היומן

    private val SENSITIVE_PATTERNS = arrayOf(
        Pattern.compile("password|pwd|token|key", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}"), // כרטיסי אשראי
        Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}"), // אימיילים
        Pattern.compile("/.*/.*/") // נתיבי קבצים
    )

    private var logLevel = if (BuildConfig.DEBUG) Log.VERBOSE else Log.ERROR

    // טיפול בהודעות ארוכות ורגישות
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            val safeMessage = truncateAndSanitize(message)
            Log.d("$TAG_PREFIX$tag", safeMessage)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val safeMessage = truncateAndSanitize(message)

        if (throwable != null) {
            Log.e("$TAG_PREFIX$tag", safeMessage, throwable)
        } else {
            Log.e("$TAG_PREFIX$tag", safeMessage)
        }

        // בגרסת שחרור, שמור שגיאות קריטיות במסד נתונים מקומי ללא מידע רגיש
        if (!BuildConfig.DEBUG) {
            // כאן ניתן להוסיף לוגיקה לשמירת שגיאות לשימוש בעתיד
        }
    }

    fun sanitizeMessage(message: String): String {
        var sanitized = message

        // בדיקה אם יש מידע רגיש על פי דפוסים ידועים
        for (pattern in SENSITIVE_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll("[REDACTED]")
        }

        return sanitized
    }

    private fun truncateAndSanitize(message: String): String {
        // קיצור הודעות ארוכות
        val truncated = if (message.length > MAX_LOG_LENGTH) {
            message.substring(0, MAX_LOG_LENGTH) + "... [truncated]"
        } else {
            message
        }

        return sanitizeMessage(truncated)
    }
}