package com.example.adskipper2.storage

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.adskipper2.AppInfo
import com.example.adskipper2.util.Logger
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import android.provider.Settings

class SecurePreferences(private val context: Context) {
    companion object {
        private const val TAG = "SecurePreferences"
        private const val PREF_NAME = "secure_targets"
        private const val FALLBACK_PREF_NAME = "secure_targets_fallback"
        private const val ENCRYPTED_INDICATOR = "is_encrypted"
        private const val KEY_SHOW_ENCRYPTION_WARNING = "show_encryption_warning"

        // חשיבות הנתונים
        enum class DataSensitivity {
            HIGH,   // נתונים רגישים מאוד - זהות, סיסמאות, מפתחות
            MEDIUM, // נתונים רגישים - הגדרות אישיות, תצורת אפליקציה
            LOW     // נתונים לא רגישים - העדפות ממשק משתמש, סטטיסטיקות שימוש
        }
    }

    // יצירת מפתח מאסטר
    private val masterKey by lazy {
        try {
            // ניסיון ראשון עם הגדרות מומלצות ומאובטחות יותר
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .setKeyGenParameterSpec(
                    KeyGenParameterSpec.Builder(
                        MasterKey.DEFAULT_MASTER_KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setUserAuthenticationRequired(false) // אפשר לשנות ל-true עם timeout
                        .build()
                )
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

    private fun getOrCreateEncryptionKey(): SecretKey {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)

            // Check if key exists
            if (keyStore.containsAlias("AdSkipperSecretKey")) {
                return (keyStore.getEntry("AdSkipperSecretKey", null) as KeyStore.SecretKeyEntry).secretKey
            }

            // Generate a new key if it doesn't exist
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            keyGenerator.init(
                KeyGenParameterSpec.Builder("AdSkipperSecretKey",
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            return keyGenerator.generateKey()
        } catch (e: Exception) {
            // Fallback to a derived key if keystore fails
            Logger.e("SecurePrefs", "Keystore failed, using fallback", e)
            val digest = MessageDigest.getInstance("SHA-256")
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val salt = context.packageName
            val bytes = (deviceId + salt).toByteArray(Charsets.UTF_8)
            digest.update(bytes, 0, bytes.size)
            val key = digest.digest()
            return SecretKeySpec(key, "AES")
        }
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

    // מימוש הצפנה פנימית (אפליקטיבית) לשימוש כגיבוי כשאין EncryptedSharedPreferences
    private fun encryptString(input: String): String {
        try {
            // Use the secure key instead of the hardcoded backup key
            val key = generateKey()
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encryptedBytes = cipher.doFinal(input.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            Logger.e(TAG, "Error during app-level encryption", e)
            return input // במקרה של כישלון, החזר את המחרוזת המקורית
        }
    }

    private fun decryptString(input: String): String {
        try {
            // Use the secure key instead of the hardcoded backup key
            val key = generateKey()
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key)
            val decodedBytes = Base64.decode(input, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Logger.e(TAG, "Error during app-level decryption", e)
            return input // במקרה של כישלון, החזר את המחרוזת המקורית
        }
    }

    private fun generateKey(): SecretKeySpec {
        try {
            // Try to use the secure key from Android Keystore
            val secretKey = getOrCreateEncryptionKey()
            if (secretKey is SecretKeySpec) {
                return secretKey
            }

            // If it's not a SecretKeySpec, convert it
            val keyBytes = secretKey.encoded
            return SecretKeySpec(keyBytes, "AES")
        } catch (e: Exception) {
            // Fallback to a derived key
            Logger.e(TAG, "Error generating key, using device-specific fallback", e)
            val digest = MessageDigest.getInstance("SHA-256")
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val salt = context.packageName
            val bytes = (deviceId + salt).toByteArray(Charsets.UTF_8)
            digest.update(bytes, 0, bytes.size)
            val key = digest.digest()
            return SecretKeySpec(key, "AES")
        }
    }

    // פונקציה חדשה: שמירת ערך רגיש עם התחשבות ברמת הרגישות
    fun putSensitiveString(key: String, value: String, sensitivity: DataSensitivity = DataSensitivity.LOW) {
        // בדיקה אם להשתמש בהצפנה מערכתית או אפליקטיבית
        val isUsingSystemEncryption = prefs.getBoolean(ENCRYPTED_INDICATOR, false)

        when {
            // במקרה שיש הצפנה מערכתית - השתמש בה לכל הנתונים
            isUsingSystemEncryption -> {
                prefs.edit().putString(key, value).apply()
            }

            // כאשר אין הצפנה מערכתית, טפל בנתונים לפי רמת הרגישות
            sensitivity == DataSensitivity.HIGH -> {
                // עבור נתונים רגישים מאוד - השתמש בהצפנה אפליקטיבית
                val encryptedValue = encryptString(value)
                prefs.edit().putString("${key}_enc", encryptedValue).apply()
            }

            sensitivity == DataSensitivity.MEDIUM -> {
                // עבור נתונים רגישים בינוניים - השתמש בהצפנה אפליקטיבית
                val encryptedValue = encryptString(value)
                prefs.edit().putString("${key}_enc", encryptedValue).apply()
            }

            else -> {
                // עבור נתונים לא רגישים - שמור כרגיל
                prefs.edit().putString(key, value).apply()
            }
        }
    }

    // פונקציה חדשה: קבלת ערך רגיש
    fun getSensitiveString(key: String, defaultValue: String?, sensitivity: DataSensitivity = DataSensitivity.LOW): String? {
        val isUsingSystemEncryption = prefs.getBoolean(ENCRYPTED_INDICATOR, false)

        // אם יש הצפנה מערכתית, השתמש בה ישירות
        if (isUsingSystemEncryption) {
            return prefs.getString(key, defaultValue)
        }

        // אם אין הצפנה מערכתית, בדוק אם זהו נתון רגיש
        return if (sensitivity == DataSensitivity.HIGH || sensitivity == DataSensitivity.MEDIUM) {
            // נסה לקבל גרסה מוצפנת של הנתון
            val encryptedValue = prefs.getString("${key}_enc", null)
            if (encryptedValue != null) {
                decryptString(encryptedValue)
            } else {
                defaultValue
            }
        } else {
            // נתון לא רגיש - קבל ישירות
            prefs.getString(key, defaultValue)
        }
    }

    // שיטות קיימות עם תוספות

    fun saveSelectedApps(apps: Set<AppInfo>) {
        if (prefs.getBoolean(ENCRYPTED_INDICATOR, false)) {
            // אם יש הצפנה מערכתית - שמור את כל האפליקציות
            prefs.edit().putStringSet("selected_apps", apps.map { it.packageName }.toSet()).apply()
        } else {
            // אם אין הצפנה מערכתית - שמור רק 3 אפליקציות והצפן בהצפנה אפליקטיבית
            val limitedApps = apps.take(3).map { it.packageName }.toSet()
            val encryptedApps = encryptString(limitedApps.joinToString(","))
            prefs.edit().putString("selected_apps_enc", encryptedApps).apply()
        }
    }

    fun getSelectedAppPackages(): Set<String> {
        if (prefs.getBoolean(ENCRYPTED_INDICATOR, false)) {
            return prefs.getStringSet("selected_apps", emptySet()) ?: emptySet()
        } else {
            // נסה לקבל את רשימת האפליקציות המוצפנת
            val encryptedApps = prefs.getString("selected_apps_enc", "")
            return if (encryptedApps.isNullOrEmpty()) {
                emptySet()
            } else {
                try {
                    decryptString(encryptedApps).split(",").toSet()
                } catch (e: Exception) {
                    emptySet()
                }
            }
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

    fun putString(key: String, value: String) {
        putSensitiveString(key, value, DataSensitivity.LOW)
    }

    fun getString(key: String, defaultValue: String?): String? {
        return getSensitiveString(key, defaultValue, DataSensitivity.LOW)
    }

    // שיטה לבדיקה אם אנחנו משתמשים בהעדפות מוצפנות
    fun isUsingEncryptedStorage(): Boolean {
        return prefs.getBoolean(ENCRYPTED_INDICATOR, false)
    }

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return prefs.getInt(key, defaultValue)
    }

    fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    fun getLong(key: String, defaultValue: Long): Long {
        return prefs.getLong(key, defaultValue)
    }

    fun putStringSet(key: String, values: Set<String>) {
        // בדיקה אם מדובר בנתונים רגישים לפי שם המפתח
        val sensitive = key.contains("password") || key.contains("key") || key.contains("token")

        if (prefs.getBoolean(ENCRYPTED_INDICATOR, false) || !sensitive) {
            prefs.edit().putStringSet(key, values).apply()
        } else {
            // אם מדובר בנתונים רגישים והאחסון לא מוצפן, השתמש בהצפנה אפליקטיבית
            val encryptedValue = encryptString(values.joinToString(","))
            prefs.edit().putString("${key}_enc", encryptedValue).apply()
        }
    }

    fun getStringSet(key: String, defaultValues: Set<String>): Set<String> {
        val sensitive = key.contains("password") || key.contains("key") || key.contains("token")

        if (prefs.getBoolean(ENCRYPTED_INDICATOR, false) || !sensitive) {
            return prefs.getStringSet(key, defaultValues) ?: defaultValues
        } else {
            // אם מדובר בנתונים רגישים, נסה לפענח
            val encryptedValue = prefs.getString("${key}_enc", null)
            return if (encryptedValue != null) {
                try {
                    decryptString(encryptedValue).split(",").toSet()
                } catch (e: Exception) {
                    defaultValues
                }
            } else {
                defaultValues
            }
        }
    }

    fun contains(key: String): Boolean {
        return prefs.contains(key) || prefs.contains("${key}_enc")
    }

    fun remove(key: String) {
        prefs.edit().remove(key).remove("${key}_enc").apply()
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