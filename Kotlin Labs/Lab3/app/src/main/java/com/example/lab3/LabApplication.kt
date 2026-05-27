package com.example.lab3

import android.app.Application
import androidx.preference.PreferenceManager
import org.osmdroid.config.Configuration

class LabApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().load(
            this,
            PreferenceManager.getDefaultSharedPreferences(this),
        )
    }
}

