package com.example.lab4.firebase

import android.content.Context
import com.google.firebase.FirebaseApp

object FirebaseAvailability {
    fun isConfigured(context: Context): Boolean =
        try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }
}

