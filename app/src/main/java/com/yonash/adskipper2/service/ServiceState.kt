package com.yonash.adskipper2.service

import android.content.Context
import com.yonash.adskipper2.storage.SecurePreferences

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
        private val securePrefs = SecurePreferences(context.applicationContext)

        override fun setEnabled(enabled: Boolean) {
            securePrefs.putBoolean("is_enabled", enabled)
        }

        override fun isEnabled(): Boolean {
            return securePrefs.getBoolean("is_enabled", false)
        }
    }
}