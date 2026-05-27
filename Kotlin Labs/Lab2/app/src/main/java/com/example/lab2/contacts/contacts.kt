package com.example.lab2.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.lab2.R
import com.example.lab2.model.ContactItem

@Composable
fun ContactsPickerDialog(
    contacts: List<ContactItem>,
    isLoading: Boolean,
    hasReadContactsPermission: Boolean,
    onDismiss: () -> Unit,
    onContactSelected: (ContactItem) -> Unit,
    onRequestPermission: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) contacts
        else {
            val q = searchQuery.lowercase()
            contacts.filter {
                it.displayName.lowercase().contains(q) ||
                    it.givenName.lowercase().contains(q) ||
                    it.familyName.lowercase().contains(q) ||
                    it.phoneNumber.contains(q)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.contacts_dialog_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!hasReadContactsPermission) {
                    Text(
                        text = stringResource(R.string.contacts_permission_required),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    TextButton(onClick = onRequestPermission) {
                        Text(stringResource(R.string.grant_permission))
                    }
                } else {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.contacts_search_hint)) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        filtered.isEmpty() -> {
                            Text(
                                text = stringResource(R.string.contacts_empty),
                                modifier = Modifier.padding(16.dp),
                            )
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp),
                            ) {
                                items(filtered, key = { "${it.contactId}:${it.phoneNumber}" }) { contact ->
                                    ContactListRow(
                                        contact = contact,
                                        onClick = { onContactSelected(contact) },
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ContactListRow(
    contact: ContactItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.givenName.ifBlank { contact.displayName },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (contact.familyName.isNotBlank()) {
                Text(
                    text = contact.familyName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = contact.phoneNumber,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

