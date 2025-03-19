package com.yonash.adskipper2.service

import android.content.Context
import android.content.Intent
import com.yonash.adskipper2.UnifiedSkipperService
import com.yonash.adskipper2.util.Logger
import com.yonash.adskipper2.storage.SecurePreferences

class ServiceManager private constructor(private val context: Context) {
    companion object {
        private const val TAG = "ServiceManager"
        private const val PREFS_NAME = "service_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_LAST_ACTIVITY = "last_activity_time"

        @Volatile
        private var instance: ServiceManager? = null

        fun getInstance(context: Context): ServiceManager {
            return instance ?: synchronized(this) {
                instance ?: ServiceManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val securePrefs = SecurePreferences(context)

    private var lastActivityTime = System.currentTimeMillis()
    private val activityLock = Any()

    fun recordActivity() {
        synchronized(activityLock) {
            lastActivityTime = System.currentTimeMillis()
        }
    }

    fun getLastActivityTime(): Long {
        synchronized(activityLock) {
            return lastActivityTime
        }
    }

    fun startService() {
        try {
            val serviceIntent = Intent(context, UnifiedSkipperService::class.java)
            context.startService(serviceIntent)
            setServiceEnabled(true)
            Logger.d(TAG, "Service started")
        } catch (e: Exception) {
            Logger.e(TAG, "Error starting service", e)
        }
    }

    fun stopService() {
        try {
            val serviceIntent = Intent(context, UnifiedSkipperService::class.java)
            context.stopService(serviceIntent)
            setServiceEnabled(false)
            Logger.d(TAG, "Service stopped")
        } catch (e: Exception) {
            Logger.e(TAG, "Error stopping service", e)
        }
    }

    fun isServiceEnabled(): Boolean {
        return securePrefs.getBoolean(KEY_SERVICE_ENABLED, false)
    }

    private fun setServiceEnabled(enabled: Boolean) {
        securePrefs.putBoolean(KEY_SERVICE_ENABLED, enabled)
    }
}