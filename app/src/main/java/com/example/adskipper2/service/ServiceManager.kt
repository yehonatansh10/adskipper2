package com.example.adskipper2.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.example.adskipper2.UnifiedSkipperService
import com.example.adskipper2.util.Logger

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

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
        return prefs.getBoolean(KEY_SERVICE_ENABLED, false)
    }

    private fun setServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }
}