package com.example.lab2

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lab2.contacts.ContactsHelper
import com.example.lab2.model.ContactItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CurrentLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timeMs: Long,
)

data class MainUiState(
    val currentLocation: CurrentLocation? = null,
    val tracking: Boolean = false,
    val useDefaultSmsApp: Boolean = true,
    val selectedContacts: List<ContactItem> = emptyList(),
    val allContacts: List<ContactItem> = emptyList(),
    val contactsLoading: Boolean = false,
    val showContactsDialog: Boolean = false,
    val contactForInfo: ContactItem? = null,
    val contactToDelete: ContactItem? = null,
    val snackbarMessage: String? = null,
    val showSmsPermissionRationale: Boolean = false,
    val showSmsPermissionDenied: Boolean = false,
    val showContactsPermissionRationale: Boolean = false,
    val showContactsPermissionDenied: Boolean = false,
    val showLocationPermissionDenied: Boolean = false,
    val pendingSendAfterSmsPermission: Boolean = false,
    val pendingOpenContactsAfterPermission: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun setTracking(tracking: Boolean) {
        _uiState.update { it.copy(tracking = tracking) }
    }

    fun updateCurrentLocation(location: CurrentLocation) {
        _uiState.update { it.copy(currentLocation = location) }
    }

    fun setUseDefaultSmsApp(useDefault: Boolean) {
        _uiState.update { it.copy(useDefaultSmsApp = useDefault) }
    }

    fun showContactsDialog() {
        _uiState.update { it.copy(showContactsDialog = true) }
    }

    fun hideContactsDialog() {
        _uiState.update { it.copy(showContactsDialog = false) }
    }

    fun loadContactsWithPermission() {
        viewModelScope.launch {
            _uiState.update { it.copy(contactsLoading = true) }
            val contacts = withContext(Dispatchers.IO) {
                ContactsHelper.loadAllContacts(getApplication<Application>().contentResolver)
            }
            _uiState.update {
                it.copy(allContacts = contacts, contactsLoading = false)
            }
        }
    }

    fun addContact(contact: ContactItem) {
        _uiState.update { state ->
            val exists = state.selectedContacts.any {
                it.contactId == contact.contactId && it.phoneNumber == contact.phoneNumber
            }
            if (exists) {
                state.copy(snackbarMessage = "Контакт уже додано")
            } else {
                state.copy(
                    selectedContacts = state.selectedContacts + contact,
                    snackbarMessage = null,
                )
            }
        }
    }

    fun onContactPicked(uri: Uri, hasReadContacts: Boolean) {
        val contact = ContactsHelper.fromPickerUri(
            getApplication(),
            uri,
            hasReadContacts,
        )
        if (contact != null) {
            addContact(contact)
        } else {
            _uiState.update { it.copy(snackbarMessage = "Не вдалося прочитати контакт") }
        }
    }

    fun showContactInfo(contact: ContactItem) {
        _uiState.update { it.copy(contactForInfo = contact) }
    }

    fun dismissContactInfo() {
        _uiState.update { it.copy(contactForInfo = null) }
    }

    fun requestDeleteContact(contact: ContactItem) {
        _uiState.update { it.copy(contactToDelete = contact) }
    }

    fun confirmDeleteContact() {
        _uiState.update { state ->
            val toRemove = state.contactToDelete ?: return@update state
            state.copy(
                selectedContacts = state.selectedContacts.filter { it.id != toRemove.id },
                contactToDelete = null,
            )
        }
    }

    fun cancelDeleteContact() {
        _uiState.update { it.copy(contactToDelete = null) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun onSmsPermissionDenied(permanently: Boolean) {
        _uiState.update {
            it.copy(
                showSmsPermissionRationale = false,
                showSmsPermissionDenied = permanently,
                pendingSendAfterSmsPermission = false,
            )
        }
    }

    fun onSmsPermissionGranted() {
        _uiState.update {
            it.copy(
                showSmsPermissionRationale = false,
                showSmsPermissionDenied = false,
            )
        }
    }

    fun requestSmsPermissionBeforeSend() {
        _uiState.update {
            it.copy(
                showSmsPermissionRationale = true,
                pendingSendAfterSmsPermission = true,
            )
        }
    }

    fun consumePendingSend(): Boolean {
        val pending = _uiState.value.pendingSendAfterSmsPermission
        if (pending) {
            _uiState.update { it.copy(pendingSendAfterSmsPermission = false) }
        }
        return pending
    }

    fun hideSmsRationaleForRequest() {
        _uiState.update { it.copy(showSmsPermissionRationale = false) }
    }

    fun dismissSmsRationale() {
        _uiState.update {
            it.copy(showSmsPermissionRationale = false, pendingSendAfterSmsPermission = false)
        }
    }

    fun dismissSmsDeniedDialog() {
        _uiState.update { it.copy(showSmsPermissionDenied = false) }
    }

    fun onContactsPermissionDenied(permanently: Boolean) {
        _uiState.update {
            it.copy(
                showContactsPermissionRationale = false,
                showContactsPermissionDenied = permanently,
                pendingOpenContactsAfterPermission = false,
            )
        }
    }

    fun onContactsPermissionGranted() {
        _uiState.update {
            it.copy(
                showContactsPermissionRationale = false,
                showContactsPermissionDenied = false,
            )
        }
    }

    fun requestContactsPermissionForList() {
        _uiState.update {
            it.copy(
                showContactsPermissionRationale = true,
                pendingOpenContactsAfterPermission = true,
            )
        }
    }

    fun consumePendingOpenContacts(): Boolean {
        val pending = _uiState.value.pendingOpenContactsAfterPermission
        if (pending) {
            _uiState.update { it.copy(pendingOpenContactsAfterPermission = false) }
        }
        return pending
    }

    fun hideContactsRationaleForRequest() {
        _uiState.update { it.copy(showContactsPermissionRationale = false) }
    }

    fun dismissContactsRationale() {
        _uiState.update {
            it.copy(
                showContactsPermissionRationale = false,
                pendingOpenContactsAfterPermission = false,
            )
        }
    }

    fun dismissContactsDeniedDialog() {
        _uiState.update { it.copy(showContactsPermissionDenied = false) }
    }

    fun onLocationPermissionDenied(permanently: Boolean) {
        _uiState.update { it.copy(showLocationPermissionDenied = permanently) }
    }

    fun dismissLocationDeniedDialog() {
        _uiState.update { it.copy(showLocationPermissionDenied = false) }
    }

    fun showSendResult(success: Boolean) {
        _uiState.update {
            it.copy(
                snackbarMessage = if (success) {
                    getApplication<Application>().getString(R.string.sms_sent)
                } else {
                    getApplication<Application>().getString(R.string.sms_partial_failure)
                },
            )
        }
    }
}

