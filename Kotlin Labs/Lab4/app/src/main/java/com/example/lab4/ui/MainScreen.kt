package com.example.lab4.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lab4.R
import com.example.lab4.chat.ChatMessage
import com.example.lab4.firebase.FirebaseAvailability
import com.example.lab4.location.LocationContract
import com.example.lab4.location.LocationForegroundService
import com.google.firebase.inappmessaging.FirebaseInAppMessaging

@Composable
fun MainScreen(
    userId: String,
    userName: String,
    viewModel: MainViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (FirebaseAvailability.isConfigured(context)) {
            try {
                FirebaseInAppMessaging.getInstance().triggerEvent("chat_opened")
            } catch (_: Exception) {
                // ignore: Firebase not configured or runtime issue
            }
        }
    }

    val hasLocation = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    val hasPostNotifications = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED

    val postNotificationsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            // user may press start again
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            startTracking(context)
            viewModel.setTracking(true)
        }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != LocationContract.ACTION_LOCATION) return
                val lat = intent.getDoubleExtra(LocationContract.EXTRA_LAT, Double.NaN)
                val lon = intent.getDoubleExtra(LocationContract.EXTRA_LON, Double.NaN)
                val acc = intent.getFloatExtra(LocationContract.EXTRA_ACC, Float.NaN)
                val time = intent.getLongExtra(LocationContract.EXTRA_TIME, 0L)
                if (lat.isNaN() || lon.isNaN() || acc.isNaN()) return
                viewModel.updateCurrentLocation(lat, lon, acc, time)
            }
        }
        val filter = IntentFilter(LocationContract.ACTION_LOCATION)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.map_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )

        OsmLocationMapView(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            latitude = uiState.mapLat,
            longitude = uiState.mapLon,
            accuracyMeters = uiState.mapAccuracyM,
        )

        Text(
            text = "debug: current=${uiState.currentLocation?.lat},${uiState.currentLocation?.lon}  map=${uiState.mapLat},${uiState.mapLon}  follow=${uiState.followMyLocation}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (uiState.tracking) {
                            stopTracking(context)
                            viewModel.setTracking(false)
                        } else {
                            if (!hasPostNotifications && Build.VERSION.SDK_INT >= 33) {
                                postNotificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                return@FilledTonalButton
                            }
                            if (hasLocation) {
                                startTracking(context)
                                viewModel.setTracking(true)
                            } else {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    )
                                )
                            }
                        }
                    },
                ) {
                    Text(stringResource(if (uiState.tracking) R.string.stop_tracking else R.string.start_tracking))
                }

                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.centerOnMe() },
                    enabled = uiState.currentLocation != null,
                ) {
                    Text(stringResource(R.string.center_on_me))
                }
            }

            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val loc = uiState.currentLocation
                    if (loc != null) {
                        viewModel.sendLocation(
                            userId = userId,
                            userName = userName,
                        )
                    }
                },
                enabled = uiState.currentLocation != null,
            ) {
                Text(stringResource(R.string.share_location))
            }
        }

        Text(text = stringResource(R.string.chat_title), style = MaterialTheme.typography.titleMedium)

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.messages, key = { it.id }) { msg ->
                ChatMessageCard(
                    msg = msg,
                    isMine = msg.userId == userId,
                    onClick = {
                        if (msg.lat != null && msg.lon != null) {
                            viewModel.focusMap(msg.lat, msg.lon, msg.accuracyM)
                        }
                    },
                )
            }
        }

        var input by remember { mutableStateOf("") }
        Row(
            modifier = Modifier.padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(stringResource(R.string.message_hint)) },
            )
            Button(
                onClick = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        viewModel.sendText(userId, userName, text)
                        input = ""
                    }
                },
            ) {
                Text(stringResource(R.string.send))
            }
        }
    }
}

@Composable
private fun ChatMessageCard(
    msg: ChatMessage,
    isMine: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = msg.userName + if (isMine) " (you)" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = msg.text, style = MaterialTheme.typography.bodyMedium)
            if (msg.lat != null && msg.lon != null) {
                Text(
                    text = "📍 ${"%.5f".format(msg.lat)}, ${"%.5f".format(msg.lon)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun startTracking(context: Context) {
    val intent = Intent(context, LocationForegroundService::class.java).apply {
        action = LocationContract.ACTION_START
    }
    ContextCompat.startForegroundService(context, intent)
}

private fun stopTracking(context: Context) {
    val intent = Intent(context, LocationForegroundService::class.java).apply {
        action = LocationContract.ACTION_STOP
    }
    context.startService(intent)
}

