package com.example.adskipper2.util

import android.util.Log
import com.example.adskipper2.BuildConfig
import java.util.regex.Pattern

object Logger {
    private const val TAG_PREFIX = "AdSkipper_"

    private val SENSITIVE_PATTERNS = arrayOf(
        Pattern.compile("password|pwd", Pattern.CASE_INSENSITIVE),
        Pattern.compile("token|api[_-]?key", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}"), // כרטיסי אשראי
        Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}") // אימיילים
    )

    private var logLevel = if (BuildConfig.DEBUG) Log.VERBOSE else Log.ERROR

    // הגדרת רמת לוג
    fun setLogLevel(level: Int) {
        logLevel = level
    }

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d("$TAG_PREFIX$tag", message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$TAG_PREFIX$tag", message, throwable)
        } else {
            Log.e("$TAG_PREFIX$tag", message)
        }

        // בגרסת שחרור, שמור שגיאות קריטיות במסד נתונים מקומי ללא מידע רגיש
        if (!BuildConfig.DEBUG) {
            // כאן ניתן להוסיף לוגיקה לשמירת שגיאות לשימוש בעתיד
            // saveErrorToLocalStorage(tag, message)
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

    // במקרה שצריך לרשום מידע רגיש במפורש (רק למקרים קריטיים)
    fun logSensitive(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d("$TAG_PREFIX$tag [SENSITIVE]", message)
        }
    }
}