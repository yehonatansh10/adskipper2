package com.example.adskipper2.util

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.example.adskipper2.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class EncryptedLogger(private val context: Context) {

    companion object {
        private const val LOG_FOLDER = "encrypted_logs"
        private const val LOG_FILE_NAME = "app_log.enc"
        private const val MAX_LOG_SIZE = 1024 * 1024 // 1MB

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
            null
        }
    }

    private fun getEncryptedLogFile(): EncryptedFile? {
        val logDir = File(context.filesDir, LOG_FOLDER)
        if (!logDir.exists()) {
            logDir.mkdirs()
        }

        val logFile = File(logDir, LOG_FILE_NAME)

        // Check file size and reset if too large
        if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
            logFile.delete()
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

    fun logEvent(tag: String, message: String, isError: Boolean = false) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val logLevel = if (isError) "ERROR" else "INFO"
        val logEntry = "[$timestamp] $logLevel/$tag: ${Logger.sanitizeMessage(message)}\n"

        try {
            val encryptedFile = getEncryptedLogFile()
            if (encryptedFile != null) {
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
                // Fallback to regular logging if encryption is not available
                Logger.d("EncryptedLogger", "Using fallback non-encrypted logging")
                val logDir = File(context.filesDir, LOG_FOLDER)
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }

                val regularLogFile = File(logDir, "fallback_log.txt")
                if (regularLogFile.exists() && regularLogFile.length() > MAX_LOG_SIZE) {
                    regularLogFile.delete()
                }

                FileOutputStream(regularLogFile, true).use { outputStream ->
                    outputStream.write(logEntry.toByteArray())
                }
            }
        } catch (e: Exception) {
            // Use Android's built-in logging as last resort
            android.util.Log.e("EncryptedLogger", "Failed to write to encrypted log: ${e.message}")
        }
    }

    fun getLogContents(): String {
        return try {
            val encryptedFile = getEncryptedLogFile()
            if (encryptedFile != null) {
                encryptedFile.openFileInput().bufferedReader().use { it.readText() }
            } else {
                val logDir = File(context.filesDir, LOG_FOLDER)
                val regularLogFile = File(logDir, "fallback_log.txt")
                if (regularLogFile.exists()) {
                    regularLogFile.readText()
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
            val logDir = File(context.filesDir, LOG_FOLDER)
            logDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            Logger.e("EncryptedLogger", "Failed to clear logs", e)
        }
    }
}