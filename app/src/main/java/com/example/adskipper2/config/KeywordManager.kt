// קובץ חדש: app/src/main/java/com/example/adskipper2/config/KeywordManager.kt

package com.example.adskipper2.config

import android.content.Context
import com.example.adskipper2.storage.SecurePreferences
import com.example.adskipper2.util.Logger
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

class KeywordManager(private val context: Context) {
    companion object {
        private const val TAG = "KeywordManager"
        private const val KEYWORDS_PREF_KEY = "app_keywords"
        private const val DEFAULT_KEYWORDS_FILE = "default_keywords.json"
    }

    private val securePreferences = SecurePreferences(context)
    private val gson = Gson()
    private var keywordsCache: Map<String, Set<String>>? = null

    init {
        // טעינה ראשונית של מילות מפתח
        loadKeywords()
    }

    // טעינת מילות מפתח ממקור מועדף
    private fun loadKeywords() {
        try {
            // ניסיון לטעון מהעדפות מאובטחות
            val keywordsJson = securePreferences.getString(KEYWORDS_PREF_KEY, null)
            if (!keywordsJson.isNullOrEmpty()) {
                val type = object : TypeToken<Map<String, Set<String>>>() {}.type
                keywordsCache = gson.fromJson(keywordsJson, type)
                Logger.d(TAG, "Loaded keywords from secure preferences")
                return
            }

            // אם אין בהעדפות, טען מקובץ ברירת המחדל
            val keywordsFromFile = loadDefaultKeywords()
            if (keywordsFromFile.isNotEmpty()) {
                keywordsCache = keywordsFromFile
                // שמור לשימוש עתידי
                saveKeywords(keywordsFromFile)
                return
            }

            // אם גם זה נכשל, השתמש במילות מפתח קשיחות
            keywordsCache = getHardcodedKeywords()
            saveKeywords(keywordsCache!!)

        } catch (e: Exception) {
            Logger.e(TAG, "Error loading keywords", e)
            // במקרה של שגיאה, השתמש במילות מפתח קשיחות
            keywordsCache = getHardcodedKeywords()
        }
    }

    // טעינת מילות מפתח מקובץ JSON מובנה
    private fun loadDefaultKeywords(): Map<String, Set<String>> {
        try {
            val inputStream = context.assets.open(DEFAULT_KEYWORDS_FILE)
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val json = String(buffer, Charsets.UTF_8)

            val type = object : TypeToken<Map<String, Set<String>>>() {}.type
            return gson.fromJson(json, type)
        } catch (e: IOException) {
            Logger.e(TAG, "Error loading default keywords file", e)
            return emptyMap()
        }
    }

    // שמירת מילות מפתח בהעדפות מאובטחות
    private fun saveKeywords(keywords: Map<String, Set<String>>) {
        try {
            val json = gson.toJson(keywords)
            securePreferences.putString(KEYWORDS_PREF_KEY, json)
            Logger.d(TAG, "Saved keywords to secure preferences")
        } catch (e: Exception) {
            Logger.e(TAG, "Error saving keywords", e)
        }
    }

    // הוספת מילת מפתח חדשה לאפליקציה ספציפית
    fun addKeyword(packageName: String, keyword: String): Boolean {
        try {
            val currentKeywords = getKeywordsMap().toMutableMap()
            val appKeywords = currentKeywords[packageName]?.toMutableSet() ?: mutableSetOf()
            appKeywords.add(keyword)
            currentKeywords[packageName] = appKeywords

            keywordsCache = currentKeywords
            saveKeywords(currentKeywords)
            return true
        } catch (e: Exception) {
            Logger.e(TAG, "Error adding keyword", e)
            return false
        }
    }

    // הסרת מילת מפתח
    fun removeKeyword(packageName: String, keyword: String): Boolean {
        try {
            val currentKeywords = getKeywordsMap().toMutableMap()
            val appKeywords = currentKeywords[packageName]?.toMutableSet()

            if (appKeywords != null && appKeywords.remove(keyword)) {
                currentKeywords[packageName] = appKeywords
                keywordsCache = currentKeywords
                saveKeywords(currentKeywords)
                return true
            }
            return false
        } catch (e: Exception) {
            Logger.e(TAG, "Error removing keyword", e)
            return false
        }
    }

    // קבלת מילות מפתח לאפליקציה ספציפית
    fun getKeywords(packageName: String): Set<String> {
        return getKeywordsMap()[packageName] ?: emptySet()
    }

    // קבלת כל מילות המפתח
    fun getKeywordsMap(): Map<String, Set<String>> {
        if (keywordsCache == null) {
            loadKeywords()
        }
        return keywordsCache ?: getHardcodedKeywords()
    }

    // מילות מפתח קשיחות לשימוש במקרה של כישלון
    private fun getHardcodedKeywords(): Map<String, Set<String>> {
        val map = HashMap<String, Set<String>>()

        // מילות מפתח לטיקטוק
        map["com.zhiliaoapp.musically"] = setOf(
            "ממומן", "שותפות בתשלום", "החלק כדי לדלג",
            " החלק/החליקי למעלה למעבר לפוסט הבא",
            "לצפייה ב-stories", "תוכן פרסומי", "תוכן שיווקי",
            "Sponsored", "View Stories",
            "Swipe up for next post", "Swipe up to skip",
            "Not interested", "LIVE now", "Tap to watch LIVE",
            "Paid partnership", "Sign up",
            "Follows you", "Follow back",
            "Promotional content", "Submit",
            "How do you feel about the video you just watched?",
            "Tap an emoji to submit",
        )

        // מילות מפתח לאינסטגרם
        map["com.instagram.android"] = setOf(
            "מודעה", "ממומן", "מוצע", "פוסט ממומן",
            "שותפות בתשלום", "רוצה לנסות?",
            "הצעות בשבילך",
            "Sponsored", "Suggested",
            "Sponsored post", "Paid partnership",
            "Suggested threads", "Get app",
            "Turn your moments into a reel",
            "Learn more", "Sign up", "Chat on WhatsApp",
        )

        // מילות מפתח לפייסבוק
        map["com.facebook.katana"] = setOf(
            "Sponsored", "ממומן",
        )

        // מילות מפתח ליוטיוב
        map["com.google.android.youtube"] = setOf(
            "Sponsored", "ממומן", "Start now",
        )

        return map
    }
}