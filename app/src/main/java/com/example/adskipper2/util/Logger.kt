package com.example.adskipper2.util

import android.util.Log
import com.example.adskipper2.BuildConfig

object Logger {
    private const val TAG_PREFIX = "AdSkipper_"

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d("$TAG_PREFIX$tag", message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.e("$TAG_PREFIX$tag", message, throwable)
            } else {
                Log.e("$TAG_PREFIX$tag", message)
            }
        }

        // בגרסת שחרור, שמור שגיאות קריטיות במסד נתונים מקומי ללא מידע רגיש
        if (!BuildConfig.DEBUG) {
            // כאן ניתן להוסיף לוגיקה לשמירת שגיאות לשימוש בעתיד
            // saveErrorToLocalStorage(tag, message)
        }
    }
}