package com.example.lab2.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.lab2.MainActivity
import com.example.lab2.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationForegroundService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private var callback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            LocationContract.ACTION_STOP -> {
                stopLocationUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            LocationContract.ACTION_START, null -> {
                startForeground(
                    LocationContract.NOTIFICATION_ID,
                    buildNotification(),
                )
                startLocationUpdates()
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        stopLocationUpdates()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationUpdates() {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return

        fusedClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) broadcastLocation(location.latitude, location.longitude, location.accuracy, location.time)
            }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                broadcastLocation(location.latitude, location.longitude, location.accuracy, location.time)
            }
        }
        callback = cb
        fusedClient.requestLocationUpdates(request, cb, Looper.getMainLooper())
    }

    private fun stopLocationUpdates() {
        val cb = callback ?: return
        fusedClient.removeLocationUpdates(cb)
        callback = null
    }

    private fun broadcastLocation(lat: Double, lon: Double, acc: Float, timeMs: Long) {
        val intent = Intent(LocationContract.ACTION_LOCATION).apply {
            putExtra(LocationContract.EXTRA_LAT, lat)
            putExtra(LocationContract.EXTRA_LON, lon)
            putExtra(LocationContract.EXTRA_ACC, acc)
            putExtra(LocationContract.EXTRA_TIME, timeMs)
        }
        sendBroadcast(intent)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(this, 0, openIntent, flags)

        val stopIntent = Intent(this, LocationForegroundService::class.java).apply {
            action = LocationContract.ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(this, 1, stopIntent, flags)

        return NotificationCompat.Builder(this, LocationContract.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.location_notification_title))
            .setContentText(getString(R.string.location_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_delete,
                getString(R.string.stop_tracking),
                pendingStop,
            )
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(LocationContract.NOTIFICATION_CHANNEL_ID)
        if (existing != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                LocationContract.NOTIFICATION_CHANNEL_ID,
                getString(R.string.location_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }
}

