package com.example.lab1.model

import java.util.UUID

data class ContactItem(
    val id: String = UUID.randomUUID().toString(),
    val contactId: Long,
    val phoneNumber: String,
    val givenName: String,
    val familyName: String,
    val displayName: String,
    val resolvedWithReadContacts: Boolean,
) {
    val initials: String
        get() {
            val first = givenName.firstOrNull()?.uppercaseChar()
            val last = familyName.firstOrNull()?.uppercaseChar()
            return when {
                first != null && last != null -> "$first$last"
                first != null -> first.toString()
                last != null -> last.toString()
                else -> displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            }
        }

    val fullName: String
        get() = listOf(givenName, familyName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { displayName }
}
