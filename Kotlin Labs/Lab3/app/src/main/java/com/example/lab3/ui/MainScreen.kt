package com.example.lab3.ui

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lab3.CurrentLocation
import com.example.lab3.MainUiState
import com.example.lab3.MainViewModel
import com.example.lab3.R
import com.example.lab3.contacts.ContactsPickerDialog
import com.example.lab3.location.LocationContract
import com.example.lab3.location.LocationForegroundService
import com.example.lab3.model.ContactItem
import com.example.lab3.sms.SmsSender

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val snackbarHostState = remember { SnackbarHostState() }

    val hasReadContacts = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED

    val hasSendSms = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.SEND_SMS,
    ) == PackageManager.PERMISSION_GRANTED

    val hasLocation = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    val pickContactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            viewModel.onContactPicked(
                uri = uri,
                hasReadContacts = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CONTACTS,
                ) == PackageManager.PERMISSION_GRANTED,
            )
        }
    }

    val readContactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.onContactsPermissionGranted()
            viewModel.loadContactsWithPermission()
            if (viewModel.consumePendingOpenContacts()) {
                viewModel.showContactsDialog()
            }
        } else {
            val permanently = activity?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.READ_CONTACTS,
                )
            } ?: false
            viewModel.onContactsPermissionDenied(permanently)
        }
    }

    val sendSmsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.onSmsPermissionGranted()
            if (viewModel.consumePendingSend()) {
                performSend(viewModel, uiState, activity, hasSendSms = true)
            }
        } else {
            val permanently = activity?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.SEND_SMS,
                )
            } ?: false
            viewModel.onSmsPermissionDenied(permanently)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!granted) {
            val permanently = activity?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ) && !ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            } ?: false
            viewModel.onLocationPermissionDenied(permanently)
        } else {
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
                viewModel.updateCurrentLocation(
                    CurrentLocation(
                        latitude = lat,
                        longitude = lon,
                        accuracyMeters = acc,
                        timeMs = time,
                    )
                )
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

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(uiState.showContactsDialog, hasReadContacts) {
        if (uiState.showContactsDialog && hasReadContacts && uiState.allContacts.isEmpty()) {
            viewModel.loadContactsWithPermission()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.map_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            val loc = uiState.currentLocation
            OsmLocationMapView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(horizontal = 12.dp),
                latitude = loc?.latitude,
                longitude = loc?.longitude,
                accuracyMeters = loc?.accuracyMeters,
            )

            if (loc != null) {
                Text(
                    text = stringResource(
                        R.string.coordinates_format,
                        loc.latitude,
                        loc.longitude,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Text(
                    text = stringResource(R.string.accuracy_format, loc.accuracyMeters),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.location_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        if (uiState.tracking) {
                            stopTracking(context)
                            viewModel.setTracking(false)
                        } else {
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
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(if (uiState.tracking) R.string.stop_tracking else R.string.start_tracking))
                }
            }

            SmsModeSwitch(
                useDefaultApp = uiState.useDefaultSmsApp,
                onCheckedChange = viewModel::setUseDefaultSmsApp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_PICK,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        )
                        pickContactLauncher.launch(intent)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.pick_contact))
                }
                FilledTonalButton(
                    onClick = {
                        if (hasReadContacts) {
                            viewModel.loadContactsWithPermission()
                            viewModel.showContactsDialog()
                        } else {
                            viewModel.requestContactsPermissionForList()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.browse_contacts))
                }
            }

            SelectedContactsRow(
                contacts = uiState.selectedContacts,
                modifier = Modifier.padding(vertical = 8.dp),
                onContactClick = viewModel::showContactInfo,
                onContactLongPress = viewModel::requestDeleteContact,
            )

            Button(
                onClick = {
                    if (uiState.selectedContacts.isEmpty()) return@Button
                    if (uiState.currentLocation == null) return@Button
                    if (uiState.useDefaultSmsApp) {
                        sendViaDefaultApp(activity, uiState)
                    } else {
                        if (hasSendSms) {
                            performSend(viewModel, uiState, activity, hasSendSms = true)
                        } else {
                            viewModel.requestSmsPermissionBeforeSend()
                        }
                    }
                },
                enabled = uiState.selectedContacts.isNotEmpty() && uiState.currentLocation != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.send_sms))
            }
        }
    }

    if (uiState.showContactsDialog) {
        ContactsPickerDialog(
            contacts = uiState.allContacts,
            isLoading = uiState.contactsLoading,
            hasReadContactsPermission = hasReadContacts,
            onDismiss = viewModel::hideContactsDialog,
            onContactSelected = { contact ->
                viewModel.addContact(contact)
                viewModel.hideContactsDialog()
            },
            onRequestPermission = {
                readContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            },
        )
    }

    uiState.contactForInfo?.let { contact ->
        ContactInfoDialog(contact = contact, onDismiss = viewModel::dismissContactInfo)
    }

    uiState.contactToDelete?.let { contact ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDeleteContact,
            title = { Text(stringResource(R.string.contact_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.contact_delete_message,
                        contact.givenName.ifBlank { contact.displayName },
                        contact.familyName,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteContact) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDeleteContact) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (uiState.showSmsPermissionRationale) {
        PermissionRationaleDialog(
            title = stringResource(R.string.permission_sms_title),
            message = stringResource(R.string.permission_sms_rationale),
            onConfirm = {
                viewModel.hideSmsRationaleForRequest()
                sendSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            },
            onDismiss = viewModel::dismissSmsRationale,
        )
    }

    if (uiState.showSmsPermissionDenied) {
        PermissionDeniedDialog(
            title = stringResource(R.string.permission_sms_title),
            message = stringResource(R.string.permission_sms_denied),
            onOpenSettings = {
                openAppSettings(context)
                viewModel.dismissSmsDeniedDialog()
            },
            onDismiss = viewModel::dismissSmsDeniedDialog,
        )
    }

    if (uiState.showContactsPermissionRationale) {
        PermissionRationaleDialog(
            title = stringResource(R.string.permission_contacts_title),
            message = stringResource(R.string.permission_contacts_rationale),
            onConfirm = {
                viewModel.hideContactsRationaleForRequest()
                readContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            },
            onDismiss = viewModel::dismissContactsRationale,
        )
    }

    if (uiState.showContactsPermissionDenied) {
        PermissionDeniedDialog(
            title = stringResource(R.string.permission_contacts_title),
            message = stringResource(R.string.permission_contacts_denied),
            onOpenSettings = {
                openAppSettings(context)
                viewModel.dismissContactsDeniedDialog()
            },
            onDismiss = viewModel::dismissContactsDeniedDialog,
        )
    }

    if (uiState.showLocationPermissionDenied) {
        PermissionDeniedDialog(
            title = stringResource(R.string.permission_location_title),
            message = stringResource(R.string.permission_location_denied),
            onOpenSettings = {
                openAppSettings(context)
                viewModel.dismissLocationDeniedDialog()
            },
            onDismiss = viewModel::dismissLocationDeniedDialog,
        )
    }
}

@Composable
private fun SmsModeSwitch(
    useDefaultApp: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.sms_mode_label),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = if (useDefaultApp) {
                    stringResource(R.string.sms_mode_default_app)
                } else {
                    stringResource(R.string.sms_mode_programmatic)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = useDefaultApp,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ContactInfoDialog(contact: ContactItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.contact_info_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoLine(stringResource(R.string.given_name_label), contact.givenName)
                InfoLine(stringResource(R.string.family_name_label), contact.familyName)
                InfoLine(stringResource(R.string.phone_label), contact.phoneNumber)
                InfoLine(stringResource(R.string.contact_id_label), contact.contactId.toString())
                if (contact.displayName.isNotBlank()) {
                    InfoLine("Display name", contact.displayName)
                }
                InfoLine(
                    "Джерело імені",
                    if (contact.resolvedWithReadContacts) "READ_CONTACTS + StructuredName"
                    else "DISPLAY_NAME / picker",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        },
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Text(
        text = "$label: ${value.ifBlank { "—" }}",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun PermissionRationaleDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.grant_permission))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun PermissionDeniedDialog(
    title: String,
    message: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        },
    )
}

private fun performSend(
    viewModel: MainViewModel,
    uiState: MainUiState,
    activity: Activity?,
    hasSendSms: Boolean,
) {
    val loc = uiState.currentLocation ?: return
    if (activity == null || !hasSendSms) return
    val message = activity.getString(
        R.string.sms_message_template,
        loc.latitude,
        loc.longitude,
        loc.accuracyMeters,
    )
    val success = SmsSender.sendProgrammatic(activity, uiState.selectedContacts, message)
    viewModel.showSendResult(success)
}

private fun sendViaDefaultApp(activity: Activity?, uiState: MainUiState) {
    val loc = uiState.currentLocation ?: return
    if (activity == null) return
    val message = activity.getString(
        R.string.sms_message_template,
        loc.latitude,
        loc.longitude,
        loc.accuracyMeters,
    )
    SmsSender.openDefaultSmsApp(activity, uiState.selectedContacts, message)
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
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

