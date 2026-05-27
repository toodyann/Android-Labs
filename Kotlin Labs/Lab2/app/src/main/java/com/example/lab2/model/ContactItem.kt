package com.example.lab2.model

data class ContactItem(
    val contactId: Long,
    val phoneNumber: String,
    val givenName: String,
    val familyName: String,
    val displayName: String,
    val resolvedWithReadContacts: Boolean,
) {
    val id: String get() = "$contactId:$phoneNumber"

    val fullName: String
        get() = listOf(givenName, familyName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { displayName }

    val initials: String
        get() {
            val a = (givenName.ifBlank { displayName }).trim().firstOrNull()?.uppercaseChar()
            val b = familyName.trim().firstOrNull()?.uppercaseChar()
            return listOfNotNull(a, b).joinToString("")
                .ifBlank { "?" }
        }
}

