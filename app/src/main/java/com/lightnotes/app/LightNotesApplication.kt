package com.lightnotes.app

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.lightnotes.app.data.AppPreferences

class LightNotesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (AppPreferences.isDynamicColors(this)) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }
    }
}
