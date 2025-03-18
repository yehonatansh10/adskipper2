package com.example.adskipper2.util

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.example.adskipper2.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import java.security.MessageDigest

class EncryptedLogger(private val context: Context) {

    companion object {
        private const val LOG_FOLDER = "encrypted_logs"
        private const val LOG_FILE_NAME = "app_log.enc"
        private const val FALLBACK_LOG_FILE = "app_log_fallback.txt"
        private const val MAX_LOG_SIZE = 1024 * 1024 // 1MB
        private const val LOG_RETENTION_TIME = 14 * 24 * 60 * 60 * 1000L // 14 days in milliseconds
        private const val APP_ENCRYPTION_KEY = "AdSkipperSecureLoggerKey2025" // בסיסי - ניתן להחליף

        // רשימת דפוסים של מידע רגיש
        private val SENSITIVE_PATTERNS = arrayOf(
            "password=.*?[&;]",
            "token=.*?[&;]",
            "key=.*?[&;]",
            "secret=.*?[&;]",
            "\\d{16}",  // כרטיסי אשראי פוטנציאליים
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}\\b" // כתובות אימייל
        )

        @Volatile
        private var instance: EncryptedLogger? = null

        fun getInstance(context: Context): EncryptedLogger {
            return instance ?: synchronized(this) {
                instance ?: EncryptedLogger(context.applicationContext).also { instance = it }
            }
        }
    }

    private val masterKey by lazy {
        try {
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        } catch (e: Exception) {
            Logger.e("EncryptedLogger", "Failed to create MasterKey", e)
            null
        }
    }

    // יצירת כונן לאחסון
    private val logDir by lazy {
        val dir = File(context.filesDir, LOG_FOLDER)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    init {
        // הפעלת מנגנון מחיקת לוגים ישנים
        cleanupOldLogs()
    }

    private fun getEncryptedLogFile(): EncryptedFile? {
        val logFile = File(logDir, LOG_FILE_NAME)

        // בדיקת גודל וריסט אם גדול מדי
        if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
            // שמירת גיבוי לפני ריסט
            val backupFile = File(logDir, "${LOG_FILE_NAME}.bak")
            if (backupFile.exists()) {
                backupFile.delete()
            }
            logFile.renameTo(backupFile)
        }

        return try {
            if (masterKey != null) {
                EncryptedFile.Builder(
                    context,
                    logFile,
                    masterKey!!,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.e("EncryptedLogger", "Failed to create encrypted file", e)
            null
        }
    }

    // הצפנה ברמת האפליקציה (כגיבוי)
    private fun encryptWithAppKey(message: String): String {
        try {
            val key = generateKey(APP_ENCRYPTION_KEY)
            val iv = ByteArray(16) // IV בסיסי לפישוט - אפשר לייצר אקראי ולשמור
            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec)
            val encryptedBytes = cipher.doFinal(message.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            Logger.e("EncryptedLogger", "Failed to encrypt with app key", e)
            return message // במקרה של כישלון, החזר את המחרוזת המקורית
        }
    }

    private fun decryptWithAppKey(encryptedMessage: String): String {
        try {
            val key = generateKey(APP_ENCRYPTION_KEY)
            val iv = ByteArray(16) // חייב להיות זהה לזה ששימש להצפנה
            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)
            val decodedBytes = Base64.decode(encryptedMessage, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Logger.e("EncryptedLogger", "Failed to decrypt with app key", e)
            return encryptedMessage // במקרה של כישלון, החזר את המחרוזת המקורית
        }
    }

    private fun generateKey(password: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = password.toByteArray(Charsets.UTF_8)
        digest.update(bytes, 0, bytes.size)
        val key = digest.digest()
        return SecretKeySpec(key, "AES")
    }

    // סינון מידע רגיש בלוגים
    private fun sanitizeLogMessage(message: String): String {
        var sanitized = message

        // החלפת מידע רגיש ב-[REDACTED]
        for (pattern in SENSITIVE_PATTERNS) {
            sanitized = sanitized.replace(Regex(pattern), "[REDACTED]")
        }

        return sanitized
    }

    // מחיקת לוגים ישנים מעל ל-X ימים
    public fun cleanupOldLogs() {        try {
            val currentTime = System.currentTimeMillis()
            val files = logDir.listFiles()

            files?.forEach { file ->
                if (currentTime - file.lastModified() > LOG_RETENTION_TIME) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Logger.e("EncryptedLogger", "Failed to cleanup old logs", e)
        }
    }

    fun logEvent(tag: String, message: String, isError: Boolean = false) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val logLevel = if (isError) "ERROR" else "INFO"
        val sanitizedMessage = sanitizeLogMessage(message)
        val logEntry = "[$timestamp] $logLevel/$tag: $sanitizedMessage\n"

        try {
            val encryptedFile = getEncryptedLogFile()
            if (encryptedFile != null) {
                // שימוש בקובץ מוצפן מערכתי
                val existingContent = try {
                    encryptedFile.openFileInput().bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    ""
                }

                encryptedFile.openFileOutput().use { outputStream ->
                    val content = existingContent + logEntry
                    outputStream.write(content.toByteArray())
                }
            } else {
                // שימוש בהצפנה אפליקטיבית כגיבוי
                val fallbackLogFile = File(logDir, FALLBACK_LOG_FILE)
                if (fallbackLogFile.exists() && fallbackLogFile.length() > MAX_LOG_SIZE) {
                    val backupFile = File(logDir, "${FALLBACK_LOG_FILE}.bak")
                    if (backupFile.exists()) {
                        backupFile.delete()
                    }
                    fallbackLogFile.renameTo(backupFile)
                    fallbackLogFile.createNewFile()
                }

                // הוספת רשומת לוג מוצפנת
                val encryptedEntry = encryptWithAppKey(logEntry)
                FileOutputStream(fallbackLogFile, true).use { outputStream ->
                    outputStream.write((encryptedEntry + "\n").toByteArray())
                }
            }
        } catch (e: Exception) {
            // במקרה של כישלון, הסתמך על לוגים מערכתיים
            android.util.Log.e("EncryptedLogger", "Failed to write to log: ${e.message}")
        }
    }

    fun getLogContents(): String {
        return try {
            val encryptedFile = getEncryptedLogFile()
            if (encryptedFile != null) {
                // קריאת לוגים מקובץ מוצפן מערכתי
                encryptedFile.openFileInput().bufferedReader().use { it.readText() }
            } else {
                // קריאת לוגים מקובץ מוצפן אפליקטיבי
                val fallbackLogFile = File(logDir, FALLBACK_LOG_FILE)
                if (fallbackLogFile.exists()) {
                    try {
                        // פענוח שורה אחר שורה
                        val encryptedLines = fallbackLogFile.readLines()
                        encryptedLines.mapNotNull {
                            if (it.isNotBlank()) {
                                decryptWithAppKey(it)
                            } else null
                        }.joinToString("\n")
                    } catch (e: Exception) {
                        "Error reading encrypted logs: ${e.message}"
                    }
                } else {
                    "No logs available"
                }
            }
        } catch (e: Exception) {
            "Error reading logs: ${e.message}"
        }
    }

    fun clearLogs() {
        try {
            logDir.listFiles()?.forEach { it.delete() }
            Logger.d("EncryptedLogger", "All logs cleared")
        } catch (e: Exception) {
            Logger.e("EncryptedLogger", "Failed to clear logs", e)
        }
    }

    // לוגים של אבטחה ספציפיים
    fun logSecurityEvent(event: String, details: String) {
        val securityLogFile = File(logDir, "security_events.log")

        try {
            // תמיד השתמש בהצפנה אפליקטיבית לאירועי אבטחה
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val logEntry = "[$timestamp] SECURITY/$event: $details"
            val encryptedEntry = encryptWithAppKey(logEntry)

            FileOutputStream(securityLogFile, true).use { outputStream ->
                outputStream.write((encryptedEntry + "\n").toByteArray())
            }
        } catch (e: Exception) {
            android.util.Log.e("EncryptedLogger", "Failed to log security event", e)
        }
    }
}