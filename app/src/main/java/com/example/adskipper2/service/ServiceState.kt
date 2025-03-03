package com.example.adskipper2.service

import android.content.Context

interface ServiceState {
    fun setEnabled(enabled: Boolean)
    fun isEnabled(): Boolean

    companion object {
        private var instance: ServiceState? = null

        fun getInstance(context: Context): ServiceState {
            if (instance == null) {
                instance = ServiceStateImpl(context)
            }
            return instance!!
        }
    }

    private class ServiceStateImpl(context: Context) : ServiceState {
        private val prefs = context.applicationContext.getSharedPreferences("service_state", Context.MODE_PRIVATE)

        override fun setEnabled(enabled: Boolean) {
            prefs.edit().putBoolean("is_enabled", enabled).apply()
        }

        override fun isEnabled(): Boolean {
            return prefs.getBoolean("is_enabled", false)
        }
    }
}