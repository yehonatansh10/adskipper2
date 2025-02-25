package com.example.adskipper2.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.adskipper2.AppInfo
import com.example.adskipper2.util.Logger

class SecurePreferences(private val context: Context) {
    companion object {
        private const val TAG = "SecurePreferences"
    }

    private val masterKey by lazy {
        try {
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        } catch (e: Exception) {
            Logger.e(TAG, "Error creating MasterKey, falling back to default", e)
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        }
    }

    private val prefs: SharedPreferences by lazy {
        try {
            EncryptedSharedPreferences.create(
                context,
                "secure_targets",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create EncryptedSharedPreferences, using fallback", e)
            // נפילה חזרה ל-SharedPreferences רגיל במקרה של שגיאה
            context.getSharedPreferences("secure_targets_fallback", Context.MODE_PRIVATE)
        }
    }

    fun saveSelectedApps(apps: Set<AppInfo>) {
        prefs.edit().apply {
            putStringSet("selected_apps", apps.map { it.packageName }.toSet())
            apply()
        }
    }

    fun getSelectedAppPackages(): Set<String> {
        return prefs.getStringSet("selected_apps", emptySet()) ?: emptySet()
    }

    fun saveTargetText(packageName: String, text: String) {
        prefs.edit().apply {
            putString("${packageName}_target_${text.hashCode()}", text)
            apply()
        }
    }

    fun getTargetTexts(packageName: String): List<String> {
        return prefs.all.filter {
            it.key.startsWith("${packageName}_target_")
        }.mapNotNull {
            it.value as? String
        }
    }

    fun saveServiceRunning(isRunning: Boolean) {
        prefs.edit().apply {
            putBoolean("service_running", isRunning)
            apply()
        }
    }

    fun isServiceRunning(): Boolean {
        return prefs.getBoolean("service_running", false)
    }

    fun saveRecordedActions(actionsJson: String) {
        prefs.edit().apply {
            putString("recorded_actions", actionsJson)
            apply()
        }
    }

    fun getRecordedActions(): String? {
        return prefs.getString("recorded_actions", null)
    }
}