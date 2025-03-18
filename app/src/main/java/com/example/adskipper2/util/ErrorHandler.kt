package com.example.adskipper2.util

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.adskipper2.BuildConfig
import com.example.adskipper2.R
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * מנהל שגיאות מרכזי לאפליקציה
 */
class ErrorHandler private constructor(private val context: Context) {

    companion object {
        private const val MAX_ERRORS_PER_TYPE = 5
        private const val ERROR_LOG_FILENAME = "error_log.txt"
        private const val ERROR_COOLDOWN_MS = 5000L  // 5 seconds

        @Volatile
        private var instance: ErrorHandler? = null

        fun getInstance(context: Context): ErrorHandler {
            return instance ?: synchronized(this) {
                instance ?: ErrorHandler(context.applicationContext).also { instance = it }
            }
        }
    }

    // מעקב אחרי תדירות השגיאות
    private val errorCounters = ConcurrentHashMap<String, Int>()
    private val lastErrorTimes = ConcurrentHashMap<String, Long>()
    private val encryptedLogger = EncryptedLogger.getInstance(context)

    /**
     * טיפול בשגיאה כללית
     */
    fun handleError(tag: String, error: Throwable, showToast: Boolean = true) {
        val errorKey = "${error.javaClass.simpleName}:${tag}"
        val currentTime = System.currentTimeMillis()
        val lastTime = lastErrorTimes[errorKey] ?: 0L

        // בדיקת קירור - לא להציף את המשתמש בהודעות על אותה שגיאה
        if (currentTime - lastTime < ERROR_COOLDOWN_MS) {
            Logger.d(tag, "Error notification suppressed (cooldown)")
            return
        }

        lastErrorTimes[errorKey] = currentTime

        // הגדלת מונה השגיאות
        val count = errorCounters.getOrPut(errorKey) { 0 } + 1
        errorCounters[errorKey] = count

        // רישום לקובץ
        logErrorToFile(tag, error)

        // רישום ליומן
        Logger.e(tag, "Error ($count): ${error.message}", error)

        // הצגה למשתמש אם נדרש
        if (showToast && count <= MAX_ERRORS_PER_TYPE) {
            showErrorToast(error)
        }

        // אם מדובר בשגיאה קריטית, הצג דיאלוג
        if (isCriticalError(error)) {
            showErrorDialog(tag, error)
        }
    }

    /**
     * טיפול בשגיאת התחברות או הרשאות
     */
    fun handlePermissionError(permissionName: String, operation: String) {
        Logger.e("Permissions", "Missing permission: $permissionName for $operation")

        Toast.makeText(
            context,
            context.getString(R.string.error_permissions),
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * בדיקה האם מדובר בשגיאה קריטית
     */
    private fun isCriticalError(error: Throwable): Boolean {
        return when (error) {
            is OutOfMemoryError,
            is StackOverflowError,
            is SecurityException -> true
            else -> false
        }
    }

    /**
     * הצגת הודעת שגיאה למשתמש
     */
    private fun showErrorToast(error: Throwable) {
        try {
            var message = error.message ?: "Unknown error"
            // קיצור הודעות ארוכות
            if (message.length > 100) {
                message = message.substring(0, 97) + "..."
            }

            Toast.makeText(
                context,
                context.getString(R.string.error_generic, message),
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            // מניעת לולאת שגיאות
            Logger.e("ErrorHandler", "Failed to show error toast", e)
        }
    }

    /**
     * הצגת דיאלוג שגיאה במקרה של שגיאות קריטיות
     */
    private fun showErrorDialog(tag: String, error: Throwable) {
        try {
            val message = if (BuildConfig.DEBUG) {
                "${error.javaClass.simpleName}: ${error.message}\n\nLocation: $tag"
            } else {
                context.getString(R.string.critical_error_message)
            }

            AlertDialog.Builder(context)
                .setTitle(R.string.critical_error_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        } catch (e: Exception) {
            // אם לא ניתן להציג דיאלוג, ננסה toast
            Logger.e("ErrorHandler", "Failed to show error dialog", e)
            showErrorToast(error)
        }
    }

    /**
     * רישום שגיאות לקובץ לוג פנימי
     */
    private fun logErrorToFile(tag: String, error: Throwable) {
        try {
            val logDir = File(context.filesDir, "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val logFile = File(logDir, ERROR_LOG_FILENAME)
            val errorMessage = "${error.javaClass.name} - ${error.message}"
            encryptedLogger.logEvent(tag, errorMessage, true)

            // מגבלת גודל - מחיקת הקובץ אם גדול מדי
            if (logFile.exists() && logFile.length() > 1024 * 1024) { // 1MB
                logFile.delete()
            }

            FileWriter(logFile, true).use { writer ->
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                writer.append("[$timestamp] $tag: ${error.javaClass.name} - ${error.message}\n")

                error.stackTrace.take(5).forEach { stackElement ->
                    encryptedLogger.logEvent(tag, "    at $stackElement", true)
                }
                writer.append("\n")
            }
        } catch (e: Exception) {
            // התעלם משגיאות בכתיבה לקובץ
            Logger.e("ErrorHandler", "Failed to write error log", e)
        }
    }

    /**
     * קבלת לוג שגיאות לצורך דיווח
     */
    fun getErrorLog(): String {
        val standardLog = try {
            val logFile = File(File(context.filesDir, "logs"), ERROR_LOG_FILENAME)
            if (logFile.exists()) {
                logFile.readText()
            } else {
                "No standard error log found"
            }
        } catch (e: Exception) {
            "Error reading standard log file: ${e.message}"
        }

        val encryptedLog = encryptedLogger.getLogContents()

        return "=== STANDARD LOG ===\n$standardLog\n\n=== ENCRYPTED LOG ===\n$encryptedLog"
    }

    /**
     * ניקוי לוג השגיאות
     */
    fun clearErrorLog() {
        val logFile = File(File(context.filesDir, "logs"), ERROR_LOG_FILENAME)
        if (logFile.exists()) {
            logFile.delete()
        }
        encryptedLogger.clearLogs()
        errorCounters.clear()
        lastErrorTimes.clear()
    }
}