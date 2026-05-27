package com.example.lab4

import android.app.Application
import androidx.preference.PreferenceManager
import com.google.firebase.FirebaseApp
import org.osmdroid.config.Configuration

class LabApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().load(
            this,
            PreferenceManager.getDefaultSharedPreferences(this),
        )

        // Prevent crash when google-services.json is missing.
        try {
            FirebaseApp.initializeApp(this)
        } catch (_: Exception) {
            // Firebase is optional for build/run; chat can fall back to local mode.
        }
    }
}

