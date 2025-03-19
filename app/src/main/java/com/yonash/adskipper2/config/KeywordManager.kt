package com.yonash.adskipper2.config

import android.content.Context
import com.yonash.adskipper2.storage.SecurePreferences
import com.yonash.adskipper2.util.Logger
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

/**
 * מנהל מילות מפתח לזיהוי פרסומות
 */
class KeywordManager(private val context: Context) {
    companion object {
        private const val TAG = "KeywordManager"
        private const val KEYWORDS_PREF_KEY = "app_keywords"
        private const val DEFAULT_KEYWORDS_FILE = "default_keywords.json"

        // מופע יחיד (singleton)
        @Volatile
        private var instance: KeywordManager? = null

        fun getInstance(context: Context): KeywordManager {
            return instance ?: synchronized(this) {
                instance ?: KeywordManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * תצורת אפליקציה עם מילות מפתח ותצורת גלילה
     */
    data class AppConfig(
        val packageName: String,
        val adKeywords: List<String>,
        val scrollConfig: ScrollConfig
    )

    private val securePreferences = SecurePreferences(context)
    private val gson = Gson()
    private var appConfigCache: Map<String, AppConfig>? = null

    init {
        // טעינה ראשונית של תצורות
        loadConfigurations()
    }

    /**
     * טעינת תצורות ממקור מועדף
     */
    private fun loadConfigurations() {
        try {
            // ניסיון לטעון מהעדפות מאובטחות
            val configJson = securePreferences.getString(KEYWORDS_PREF_KEY, null)
            if (!configJson.isNullOrEmpty()) {
                val type = object : TypeToken<Map<String, AppConfig>>() {}.type
                appConfigCache = gson.fromJson(configJson, type)
                Logger.d(TAG, "Loaded configurations from secure preferences")
                return
            }

            // אם אין בהעדפות, טען מקובץ ברירת המחדל
            val configsFromFile = loadDefaultConfigurations()
            if (configsFromFile.isNotEmpty()) {
                appConfigCache = configsFromFile
                // שמור לשימוש עתידי
                saveConfigurations(configsFromFile)
                return
            }

            // אם גם זה נכשל, השתמש בתצורות קשיחות
            appConfigCache = getHardcodedConfigurations()
            saveConfigurations(appConfigCache!!)

        } catch (e: Exception) {
            Logger.e(TAG, "Error loading configurations", e)
            // במקרה של שגיאה, השתמש בתצורות קשיחות
            appConfigCache = getHardcodedConfigurations()
        }
    }

    /**
     * טעינת תצורות מקובץ JSON מובנה
     */
    private fun loadDefaultConfigurations(): Map<String, AppConfig> {
        try {
            val inputStream = context.assets.open(DEFAULT_KEYWORDS_FILE)
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val json = String(buffer, Charsets.UTF_8)

            // אם יש לנו מבנה פשוט של מילות מפתח
            if (json.contains("\"com.")) {
                val type = object : TypeToken<Map<String, List<String>>>() {}.type
                val keywordsMap: Map<String, List<String>> = gson.fromJson(json, type)

                return keywordsMap.mapValues { (packageName, keywords) ->
                    AppConfig(
                        packageName = packageName,
                        adKeywords = keywords,
                        scrollConfig = ScrollConfig() // ברירת מחדל
                    )
                }
            }

            // אם יש לנו מבנה מורכב יותר עם תצורות
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val rootMap: Map<String, Any> = gson.fromJson(json, type)

            if (rootMap.containsKey("apps")) {
                // מבנה חדש עם תצורות מפורטות
                val appsMap = rootMap["apps"] as Map<String, Map<String, Any>>
                val defaultScrollConfig = parseScrollConfig(rootMap["defaultScrollConfig"] as? Map<String, Any>)

                return appsMap.mapValues { (packageName, appData) ->
                    val keywords = (appData["adKeywords"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    val scrollConfig = parseScrollConfig(appData["scrollConfig"] as? Map<String, Any>) ?: defaultScrollConfig

                    AppConfig(
                        packageName = packageName,
                        adKeywords = keywords,
                        scrollConfig = scrollConfig
                    )
                }
            }

            return emptyMap()
        } catch (e: IOException) {
            Logger.e(TAG, "Error loading default configurations file", e)
            return emptyMap()
        }
    }

    /**
     * המרת מפת תצורה לאובייקט ScrollConfig
     */
    private fun parseScrollConfig(configMap: Map<String, Any>?): ScrollConfig {
        if (configMap == null) return ScrollConfig()

        return try {
            ScrollConfig(
                startHeightRatio = (configMap["startHeightRatio"] as? Number)?.toFloat() ?: 0.6f,
                endHeightRatio = (configMap["endHeightRatio"] as? Number)?.toFloat() ?: 0.4f,
                duration = (configMap["duration"] as? Number)?.toLong() ?: 100L,
                cooldown = (configMap["cooldown"] as? Number)?.toLong() ?: 2000L
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Error parsing scroll config", e)
            ScrollConfig() // החזר ברירת מחדל במקרה של שגיאה
        }
    }

    /**
     * שמירת תצורות בהעדפות מאובטחות
     */
    private fun saveConfigurations(configs: Map<String, AppConfig>) {
        try {
            val json = gson.toJson(configs)
            securePreferences.putString(KEYWORDS_PREF_KEY, json)
            Logger.d(TAG, "Saved configurations to secure preferences")
        } catch (e: Exception) {
            Logger.e(TAG, "Error saving configurations", e)
        }
    }

    /**
     * קבלת תצורה לאפליקציה ספציפית
     */
    fun getAppConfig(packageName: String): AppConfig? {
        return getAppConfigMap()[packageName]
    }

    fun getKeywords(packageName: String): Set<String> {
        return getAppConfigMap()[packageName]?.adKeywords?.toSet() ?: emptySet()
    }

    /**
     * קבלת כל התצורות
     */
    fun getAppConfigMap(): Map<String, AppConfig> {
        if (appConfigCache == null) {
            loadConfigurations()
        }
        return appConfigCache ?: getHardcodedConfigurations()
    }

    /**
     * בדיקה אם אפליקציה נתמכת
     */
    fun isSupportedApp(packageName: String): Boolean {
        return getAppConfigMap().containsKey(packageName)
    }

    /**
     * הוספת מילת מפתח לאפליקציה ספציפית
     */
    fun addKeywordToApp(packageName: String, keyword: String): Boolean {
        val configs = appConfigCache?.toMutableMap() ?: return false
        val appConfig = configs[packageName] ?: return false

        // יצירת רשימה חדשה עם המילה הנוספת
        val updatedKeywords = appConfig.adKeywords.toMutableList()
        if (!updatedKeywords.contains(keyword)) {
            updatedKeywords.add(keyword)
        }

        // עדכון התצורה
        configs[packageName] = appConfig.copy(adKeywords = updatedKeywords)
        appConfigCache = configs

        // שמירה
        saveConfigurations(configs)
        return true
    }

    /**
     * הוספת מילת מפתח לכל האפליקציות המוגדרות
     */
    fun addKeywordToAllApps(keyword: String) {
        val configs = appConfigCache?.toMutableMap() ?: HashMap()

        configs.forEach { (packageName, appConfig) ->
            // יצירת רשימה חדשה עם המילה הנוספת
            val updatedKeywords = appConfig.adKeywords.toMutableList()
            if (!updatedKeywords.contains(keyword)) {
                updatedKeywords.add(keyword)
            }

            // עדכון התצורה
            configs[packageName] = appConfig.copy(adKeywords = updatedKeywords)
        }

        appConfigCache = configs
        saveConfigurations(configs)
    }

    /**
     * תצורות קשיחות לשימוש במקרה של כישלון
     */
    private fun getHardcodedConfigurations(): Map<String, AppConfig> {
        val map = HashMap<String, AppConfig>()

        // מילות מפתח לטיקטוק
        map["com.zhiliaoapp.musically"] = AppConfig(
            packageName = "com.zhiliaoapp.musically",
            adKeywords = listOf(
                "ממומן", "שותפות בתשלום", "החלק כדי לדלג",
                "החלק/החליקי למעלה למעבר לפוסט הבא",
                "לצפייה ב-stories", "תוכן פרסומי", "תוכן שיווקי",
                "Sponsored", "View Stories",
                "Swipe up for next post", "Swipe up to skip",
                "Not interested", "LIVE now", "Tap to watch LIVE",
                "Paid partnership", "Sign up",
                "Follows you", "Follow back",
                "Promotional content", "Submit",
                "How do you feel about the video you just watched?",
                "Tap an emoji to submit", "Shop now",
                "Is this video appropriate for TikTok?"
            ),
            scrollConfig = ScrollConfig()
        )

        // מילות מפתח לאינסטגרם
        map["com.instagram.android"] = AppConfig(
            packageName = "com.instagram.android",
            adKeywords = listOf(
                "מודעה", "ממומן", "מוצע", "פוסט ממומן",
                "שותפות בתשלום", "רוצה לנסות?",
                "הצעות בשבילך",
                "Sponsored", "Suggested",
                "Sponsored post", "Paid partnership",
                "Suggested threads", "Get app",
                "Turn your moments into a reel",
                "Learn more", "Sign up", "Chat on WhatsApp"
            ),
            scrollConfig = ScrollConfig()
        )

        // מילות מפתח לפייסבוק
        map["com.facebook.katana"] = AppConfig(
            packageName = "com.facebook.katana",
            adKeywords = listOf(
                "Sponsored", "ממומן"
            ),
            scrollConfig = ScrollConfig()
        )

        // מילות מפתח ליוטיוב
        map["com.google.android.youtube"] = AppConfig(
            packageName = "com.google.android.youtube",
            adKeywords = listOf(
                "Sponsored", "ממומן", "Start now"
            ),
            scrollConfig = ScrollConfig()
        )

        return map
    }

    /**
     * קבלת כל מילות המפתח מכל האפליקציות
     */
    fun getAllKeywords(): Set<String> {
        val allKeywords = mutableSetOf<String>()
        getAppConfigMap().values.forEach { config ->
            allKeywords.addAll(config.adKeywords)
        }
        return allKeywords
    }
}