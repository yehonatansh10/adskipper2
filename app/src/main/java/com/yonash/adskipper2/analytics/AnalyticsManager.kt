package com.yonash.adskipper2.analytics

import android.content.Context
import android.os.Build
import com.yonash.adskipper2.util.Logger
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.HashMap
import com.yonash.adskipper2.storage.SecurePreferences

/**
 * מנהל אנליטיקה פנימי שומר נתונים מקומית בלבד, אינו שולח מידע לשרת חיצוני
 */
class AnalyticsManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AnalyticsManager"
        private const val PREFS_NAME = "analytics_data"
        private const val KEY_EVENTS = "tracked_events"
        private const val KEY_FIRST_LAUNCH = "first_launch_time"
        private const val KEY_LAST_LAUNCH = "last_launch_time"
        private const val KEY_LAUNCH_COUNT = "launch_count"
        private const val KEY_APP_VERSION = "app_version"
        private const val MAX_EVENTS = 500  // הגבלת כמות האירועים הנשמרים

        @Volatile
        private var instance: AnalyticsManager? = null

        fun getInstance(context: Context): AnalyticsManager {
            return instance ?: synchronized(this) {
                instance ?: AnalyticsManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private data class EventData(
        val name: String,
        val timestamp: Long,
        val properties: Map<String, String>,
        val count: Int = 1
    )

    private val gson = Gson()
    private val securePrefs = SecurePreferences(context)
    private val eventsMap = loadEvents()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    init {
        trackAppLaunch()
    }

    /**
     * מעקב אחרי אירוע באפליקציה
     */
    fun trackEvent(eventName: String, properties: Map<String, String> = emptyMap()) {
        try {
            val today = dateFormat.format(Date())
            val key = "$eventName:$today"

            synchronized(eventsMap) {
                val existingEvent = eventsMap[key]
                if (existingEvent != null) {
                    // עדכון מונה האירוע הקיים
                    eventsMap[key] = existingEvent.copy(
                        count = existingEvent.count + 1,
                        timestamp = System.currentTimeMillis()
                    )
                } else {
                    // יצירת אירוע חדש
                    eventsMap[key] = EventData(
                        name = eventName,
                        timestamp = System.currentTimeMillis(),
                        properties = properties
                    )

                    // הגבלת גודל המפה
                    if (eventsMap.size > MAX_EVENTS) {
                        val oldest = eventsMap.minByOrNull { it.value.timestamp }?.key
                        oldest?.let { eventsMap.remove(it) }
                    }
                }

                // שמירת השינויים
                saveEvents()
            }

        } catch (e: Exception) {
            Logger.e(TAG, "Error tracking event: $eventName", e)
        }
    }

    /**
     * מעקב אחרי הפעלת האפליקציה
     */
    private fun trackAppLaunch() {
        val currentTime = System.currentTimeMillis()
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "unknown"
        }

        // בדיקה אם זו הפעם הראשונה
        if (!securePrefs.contains(KEY_FIRST_LAUNCH)) {
            securePrefs.putLong(KEY_FIRST_LAUNCH, currentTime)
        }

        // עדכון מידע הפעלה
        val launchCount = securePrefs.getInt(KEY_LAUNCH_COUNT, 0) + 1

        securePrefs.putLong(KEY_LAST_LAUNCH, currentTime)
        securePrefs.putInt(KEY_LAUNCH_COUNT, launchCount)
        securePrefs.putSensitiveString(KEY_APP_VERSION, appVersion)

        // תיעוד הפעלה עם מידע על המכשיר (מידע לא מזהה)
        val deviceInfo = mapOf(
            "android_version" to Build.VERSION.RELEASE,
            "device_type" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "app_version" to appVersion,
            "launch_count" to launchCount.toString()
        )

        trackEvent("app_launch", deviceInfo)
    }

    /**
     * מעקב אחרי זיהוי פרסומת
     */
    fun trackAdDetection(packageName: String, wasSkipped: Boolean) {
        val properties = mapOf(
            "app_package" to packageName,
            "skipped" to wasSkipped.toString()
        )

        trackEvent("ad_detected", properties)
    }

    /**
     * מעקב אחרי שגיאות
     */
    fun trackError(errorType: String, message: String) {
        val properties = mapOf(
            "error_type" to errorType,
            "error_message" to message.take(100) // הגבלת אורך
        )

        trackEvent("app_error", properties)
    }

    /**
     * קבלת פרופיל שימוש
     */
    fun getUsageStats(): Map<String, Any> {
        val currentTime = System.currentTimeMillis()
        val firstLaunchTime = securePrefs.getLong(KEY_FIRST_LAUNCH, currentTime)
        val daysSinceFirstLaunch = TimeUnit.MILLISECONDS.toDays(currentTime - firstLaunchTime)

        val appLaunches = eventsMap.filter { it.key.startsWith("app_launch:") }
            .map { it.value.count }.sum()

        val adDetections = eventsMap.filter { it.key.startsWith("ad_detected:") }
            .map { it.value.count }.sum()

        val errorEvents = eventsMap.filter { it.key.startsWith("app_error:") }
            .map { it.value.count }.sum()

        val topApps = eventsMap.filter { it.key.startsWith("ad_detected:") }
            .flatMap { event ->
                event.value.properties.filter { it.key == "app_package" }.map { it.value }
            }
            .groupBy { it }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
            .toMap()

        return mapOf(
            "days_active" to daysSinceFirstLaunch,
            "total_launches" to appLaunches,
            "ads_detected" to adDetections,
            "errors_count" to errorEvents,
            "top_apps" to topApps
        )
    }

    /**
     * טעינת אירועים מהעדפות משותפות
     */
    private fun loadEvents(): HashMap<String, EventData> {
        val eventsJson = securePrefs.getString(KEY_EVENTS, null)
        return if (eventsJson != null) {
            try {
                val type = object : TypeToken<HashMap<String, EventData>>() {}.type
                gson.fromJson(eventsJson, type)
            } catch (e: Exception) {
                Logger.e(TAG, "Error loading events", e)
                HashMap()
            }
        } else {
            HashMap()
        }
    }

    /**
     * שמירת אירועים להעדפות משותפות
     */
    private fun saveEvents() {
        try {
            val eventsJson = gson.toJson(eventsMap)
            securePrefs.putSensitiveString(KEY_EVENTS, eventsJson)
        } catch (e: Exception) {
            Logger.e(TAG, "Error saving events", e)
        }
    }

    /**
     * ניקוי נתוני אנליטיקה ישנים (מעל 90 יום)
     */
    fun cleanupOldData() {
        val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90)

        synchronized(eventsMap) {
            val keysToRemove = eventsMap.entries
                .filter { it.value.timestamp < cutoffTime }
                .map { it.key }

            keysToRemove.forEach { eventsMap.remove(it) }

            if (keysToRemove.isNotEmpty()) {
                saveEvents()
                Logger.d(TAG, "Cleaned up ${keysToRemove.size} old analytic events")
            }
        }
    }

    /**
     * איפוס כל הנתונים האנליטיים
     */
    fun resetAllData() {
        synchronized(eventsMap) {
            eventsMap.clear()
            saveEvents()

            // Replace the edit() chain with individual method calls
            securePrefs.remove(KEY_FIRST_LAUNCH)
            securePrefs.remove(KEY_LAST_LAUNCH)
            securePrefs.putInt(KEY_LAUNCH_COUNT, 0)
        }
    }
}