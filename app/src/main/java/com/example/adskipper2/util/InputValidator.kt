package com.example.adskipper2.util

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.MimeTypeMap
import java.util.regex.Pattern

object InputValidator {
    private const val TAG = "InputValidator"

    // דפוסים לזיהוי תוכן זדוני
    private val MALICIOUS_PATTERNS = arrayOf(
        Pattern.compile("<script\\b[^>]*>(.*?)</script>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("data:text/html", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(exec|system|eval)\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(DROP|DELETE|UPDATE|INSERT)\\s+\\w+", Pattern.CASE_INSENSITIVE)
    )

    // בדיקת קלט טקסט
    fun validateText(text: String): String {
        // קיצור טקסט ארוך מדי
        val truncated = if (text.length > 1000) {
            text.substring(0, 1000)
        } else {
            text
        }

        // סינון תווים מסוכנים
        var sanitized = truncated.replace("[<>&'\"\\\\/]".toRegex(), "")

        // הסרת תגיות HTML
        sanitized = sanitized.replace("<[^>]*>".toRegex(), "")

        // בדיקת תבניות זדוניות
        for (pattern in MALICIOUS_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll("")
        }

        return sanitized.trim()
    }

    // בדיקת תקינות של נתיב קובץ
    fun validateFilePath(path: String): Boolean {
        // בדיקה אם הנתיב מכיל קטעים חשודים
        val suspiciousPatterns = arrayOf(
            "../", "..\\", // ניסיון לצאת מתיקייה
            "/etc/", "/var/", // גישה לתיקיות מערכת
            "/proc/", "/sys/", // גישה למידע רגיש
            "//", "\\\\" // ניסיון להשתמש בנתיבי רשת
        )

        for (pattern in suspiciousPatterns) {
            if (path.contains(pattern)) {
                Logger.d(TAG, "Suspicious file path detected: $path")
                return false
            }
        }

        return true
    }

    // אימות URI של תוכן
    fun validateContentUri(context: Context, uri: Uri): Boolean {
        try {
            // בדיקה אם URI הוא במתכונת תקינה
            val scheme = uri.scheme
            if (scheme == null || !(scheme == "content" || scheme == "file")) {
                Logger.d(TAG, "Invalid URI scheme: $scheme")
                return false
            }

            // בדיקה שניתן לגשת ל-URI
            context.contentResolver.openInputStream(uri)?.use {
                // הקובץ קיים וניתן לגשת אליו
            }

            return true
        } catch (e: Exception) {
            Logger.e(TAG, "Error validating URI: $uri", e)
            return false
        }
    }

    // בדיקת חוקיות חבילה
    fun validatePackageName(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false

        // בדיקת אורך סביר
        if (packageName.length > 255) return false

        // בדיקה שחבילה בפורמט תקין יותר
        if (!packageName.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$"))) {
            Logger.d(TAG, "Invalid package name format: $packageName")
            return false
        }

        // רשימה שחורה של חבילות שאסור להשתמש בהן
        val blacklistedPackages = setOf(
            "com.android.settings",
            "com.android.systemui",
            "android",
            "com.android.phone"
            // הוסף חבילות רגישות נוספות
        )

        if (blacklistedPackages.contains(packageName)) {
            Logger.d(TAG, "Package is blacklisted: $packageName")
            return false
        }

        // וידוא שהחבילה מותקנת במכשיר
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            return true
        } catch (e: PackageManager.NameNotFoundException) {
            Logger.d(TAG, "Package not found: $packageName")
            return false
        }
    }

    // בדיקת סוג קובץ תמונה
    fun validateImageFile(context: Context, uri: Uri): Boolean {
        try {
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType == null || !mimeType.startsWith("image/")) {
                Logger.d(TAG, "Not an image file, mime type: $mimeType")
                return false
            }

            // בדיקת גודל מקסימלי (5MB)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val fileSize = inputStream.available()
                if (fileSize > 5 * 1024 * 1024) {
                    Logger.d(TAG, "Image too large: $fileSize bytes")
                    return false
                }
            }

            return true
        } catch (e: Exception) {
            Logger.e(TAG, "Error validating image file", e)
            return false
        }
    }
}