package com.example.lab2.sms

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import com.example.lab2.model.ContactItem

object SmsSender {

    fun sendProgrammatic(
        context: Context,
        recipients: List<ContactItem>,
        message: String,
    ): Boolean {
        if (recipients.isEmpty()) return false
        val smsManager = context.getSystemService(SmsManager::class.java)
            ?: @Suppress("DEPRECATION") SmsManager.getDefault()
        var allSucceeded = true
        recipients.forEach { contact ->
            try {
                smsManager.sendTextMessage(contact.phoneNumber, null, message, null, null)
            } catch (_: Exception) {
                allSucceeded = false
            }
        }
        return allSucceeded
    }

    fun openDefaultSmsApp(activity: Activity, recipients: List<ContactItem>, message: String) {
        if (recipients.isEmpty()) return
        val numbers = recipients.joinToString(";") { it.phoneNumber }
        val uri = Uri.parse("smsto:$numbers")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(activity.packageManager) != null) {
            activity.startActivity(intent)
        }
    }
}

