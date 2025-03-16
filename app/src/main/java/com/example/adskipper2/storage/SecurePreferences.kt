package com.example.adskipper2.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.adskipper2.AppInfo
import com.example.adskipper2.util.Logger
import java.security.GeneralSecurityException
import java.io.IOException

class SecurePreferences(private val context: Context) {
    companion object {
        private const val TAG = "SecurePreferences"
        private const val PREF_NAME = "secure_targets"
        private const val FALLBACK_PREF_NAME = "secure_targets_fallback"
        private const val ENCRYPTED_INDICATOR = "is_encrypted"
        private const val KEY_SHOW_ENCRYPTION_WARNING = "show_encryption_warning"
    }

    // יצירת מפתח מאסטר
    private val masterKey by lazy {
        try {
            // ניסיון ראשון עם הגדרות מומלצות
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .setUserAuthenticationRequired(false)
                .build()
        } catch (e: Exception) {
            Logger.e(TAG, "Error creating MasterKey with primary scheme, trying fallback", e)
            try {
                // ניסיון שני עם הגדרות יותר בסיסיות
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to create any MasterKey, using null with fallback storage", e)
                null
            }
        }
    }

    private val prefs: SharedPreferences by lazy {
        initializePreferences()
    }

    private fun initializePreferences(): SharedPreferences {
        // נסה ליצור העדפות מוצפנות
        return try {
            if (masterKey == null) {
                throw GeneralSecurityException("MasterKey is null")
            }

            val encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                masterKey!!,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            // סמן שאלו העדפות מוצפנות
            encryptedPrefs.edit().putBoolean(ENCRYPTED_INDICATOR, true).apply()

            // אפס את דגל האזהרה - ההצפנה עובדת תקין
            getEncryptionWarningPrefs().edit().putBoolean(KEY_SHOW_ENCRYPTION_WARNING, false).apply()

            encryptedPrefs
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create EncryptedSharedPreferences: ${Logger.sanitizeMessage(e.message ?: "Unknown error")}", e)

            // סמן שיש להציג אזהרה למשתמש בהפעלה הבאה
            getEncryptionWarningPrefs().edit().putBoolean(KEY_SHOW_ENCRYPTION_WARNING, true).apply()

            // יצירת העדפות רגילות, אבל עם הגבלת מידע רגיש
            val fallbackPrefs = context.getSharedPreferences(FALLBACK_PREF_NAME, Context.MODE_PRIVATE)
            fallbackPrefs.edit().putBoolean(ENCRYPTED_INDICATOR, false).apply()

            fallbackPrefs
        }
    }

    // הוסף מתודה זו לקבלת ההעדפות של אזהרות ההצפנה
    private fun getEncryptionWarningPrefs(): SharedPreferences {
        return context.getSharedPreferences("encryption_warning_prefs", Context.MODE_PRIVATE)
    }

    // הודעה למשתמש שההצפנה נכשלה ויוצבו הגבלות
    private fun showEncryptionFailureNotification() {
        // כאן תוכל להוסיף קוד להצגת הודעה למשתמש
        Logger.e(TAG, "Using unencrypted fallback preferences with limited functionality")
    }

    fun saveSelectedApps(apps: Set<AppInfo>) {
        prefs.edit().apply {
            // בדוק אם אלו העדפות מוצפנות
            if (prefs.getBoolean(ENCRYPTED_INDICATOR, false)) {
                putStringSet("selected_apps", apps.map { it.packageName }.toSet())
            } else {
                // אם לא, שמור רק מידע לא רגיש
                putStringSet("selected_apps", apps.take(3).map { it.packageName }.toSet()) // הגבל את כמות האפליקציות
            }
            apply()
        }
    }

    /**
     * בודק אם יש צורך להציג אזהרת הצפנה למשתמש
     */
    fun shouldShowEncryptionWarning(): Boolean {
        return getEncryptionWarningPrefs().getBoolean(KEY_SHOW_ENCRYPTION_WARNING, false)
    }

    /**
     * מסמן שהאזהרה הוצגה למשתמש
     */
    fun markEncryptionWarningShown() {
        getEncryptionWarningPrefs().edit().putBoolean(KEY_SHOW_ENCRYPTION_WARNING, false).apply()
    }

    fun getSelectedAppPackages(): Set<String> {
        return prefs.getStringSet("selected_apps", emptySet()) ?: emptySet()
    }

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String, defaultValue: String?): String? {
        return prefs.getString(key, defaultValue)
    }

    // שיטה לבדיקה אם אנחנו משתמשים בהעדפות מוצפנות
    fun isUsingEncryptedStorage(): Boolean {
        return prefs.getBoolean(ENCRYPTED_INDICATOR, false)
    }

    // במקרה שצריך לאפס את כל ההעדפות
    fun clearAllPreferences() {
        prefs.edit().clear().apply()

        // ניסיון לאתחול מחדש של ההעדפות המוצפנות
        if (!prefs.getBoolean(ENCRYPTED_INDICATOR, false)) {
            // נסה ליצור מחדש את ההעדפות המוצפנות
            try {
                if (masterKey != null) {
                    val encryptedPrefs = EncryptedSharedPreferences.create(
                        context,
                        PREF_NAME,
                        masterKey!!,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                    encryptedPrefs.edit().putBoolean(ENCRYPTED_INDICATOR, true).apply()
                    Logger.d(TAG, "Successfully recreated encrypted preferences")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to recreate encrypted preferences", e)
            }
        }
    }
}