package com.example.adskipper2.util

import android.util.Log
import com.example.adskipper2.BuildConfig
import java.util.regex.Pattern
import android.content.Context

object Logger {
    private const val TAG_PREFIX = "AdSkipper_"
    private const val MAX_LOG_LENGTH = 1000 // הגבלת אורך הודעות היומן

    private val SENSITIVE_PATTERNS = arrayOf(
        Pattern.compile("password[=:].*?[&;\\s]", Pattern.CASE_INSENSITIVE),
        Pattern.compile("pwd[=:].*?[&;\\s]", Pattern.CASE_INSENSITIVE),
        Pattern.compile("token[=:].*?[&;\\s]", Pattern.CASE_INSENSITIVE),
        Pattern.compile("key[=:].*?[&;\\s]", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}"), // כרטיסי אשראי
        Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}"), // אימיילים
        Pattern.compile("/.*/.*/"), // נתיבי קבצים
        Pattern.compile("Bearer\\s+[A-Za-z0-9\\-\\._~\\+/]+=*"), // JWT tokens
        Pattern.compile("api_key[=:]\\s*['\"]?[\\w\\-]+['\"]?", Pattern.CASE_INSENSITIVE)
    )

    private var logLevel = if (BuildConfig.DEBUG) Log.VERBOSE else Log.ERROR
    private var encryptedLogger: EncryptedLogger? = null
    private var securityLogs: Boolean = true // אם להפעיל לוגים מאובטחים

    fun initialize(context: Context) {
        encryptedLogger = EncryptedLogger.getInstance(context)

        // רוטציית לוגים ישנים בהתחלה - קריאה למתודה של EncryptedLogger
        encryptedLogger?.cleanupOldLogs()
    }

    // הסרנו את הפונקציה cleanupOldLogs ונשתמש ישירות בקריאה למתודה של encryptedLogger

    // טיפול בהודעות ארוכות ורגישות
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            // Only log in debug builds
            val safeMessage = truncateAndSanitize(message)
            Log.d("$TAG_PREFIX$tag", safeMessage)
        }

        // But always log to secure storage if enabled
        if (securityLogs) {
            encryptedLogger?.logEvent(tag, truncateAndSanitize(message), false)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val safeMessage = truncateAndSanitize(message)

        if (throwable != null) {
            Log.e("$TAG_PREFIX$tag", safeMessage, throwable)
        } else {
            Log.e("$TAG_PREFIX$tag", safeMessage)
        }

        // שמירת לוג שגיאה במאגר מאובטח
        encryptedLogger?.logEvent(tag, safeMessage + (throwable?.let { " Exception: ${it.message}" } ?: ""), true)

        // לוג אירוע אבטחה במקרה של שגיאות אבטחה
        if (tag.contains("Security") ||
            tag.contains("Crypto") ||
            message.contains("security") ||
            message.contains("encryp") ||
            message.contains("auth")) {
            encryptedLogger?.logSecurityEvent("SECURITY_ERROR", safeMessage + (throwable?.let { " ${it.message}" } ?: ""))
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

    // מאפשר לכבות/להפעיל לוגים מאובטחים
    fun setSecurityLogging(enabled: Boolean) {
        securityLogs = enabled
    }

    // יומן אירועי אבטחה ספציפיים
    fun logSecurityEvent(event: String, details: String) {
        encryptedLogger?.logSecurityEvent(event, details)
    }

    // מחזיר את כל הלוגים המאובטחים - מוגן בסיסמה
    fun getSecureLogs(password: String): String {
        // יישום פשוט למטרות הדגמה - בפועל יש להוסיף אימות משתמש חזק יותר
        return if (password == "AdSkipperAdmin") { // סיסמה בסיסית להדגמה, יש להחליף בייצור
            encryptedLogger?.getLogContents() ?: "No logs available"
        } else {
            "Access denied"
        }
    }
}