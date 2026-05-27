package com.example.lab2.contacts

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import com.example.lab2.model.ContactItem

object ContactsHelper {

    fun splitDisplayName(displayName: String): Pair<String, String> {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty()) return "" to ""
        val parts = trimmed.split(Regex("\\s+"), limit = 2)
        return when (parts.size) {
            1 -> parts[0] to ""
            else -> parts[0] to parts[1]
        }
    }

    fun loadAllContacts(contentResolver: ContentResolver): List<ContactItem> {
        val byContactId = linkedMapOf<Long, MutableContactBuilder>()

        contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(
                Phone.CONTACT_ID,
                Phone.NUMBER,
                Phone.DISPLAY_NAME,
            ),
            null,
            null,
            Phone.DISPLAY_NAME + " ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(Phone.CONTACT_ID)
            val numberIdx = cursor.getColumnIndexOrThrow(Phone.NUMBER)
            val nameIdx = cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(idIdx)
                val number = cursor.getString(numberIdx)?.trim().orEmpty()
                if (number.isEmpty()) continue
                val displayName = cursor.getString(nameIdx).orEmpty()
                val builder = byContactId.getOrPut(contactId) {
                    MutableContactBuilder(contactId, displayName)
                }
                builder.addPhone(number)
            }
        }

        byContactId.values.forEach { builder ->
            val structured = loadStructuredName(contentResolver, builder.contactId)
            if (structured != null) {
                builder.givenName = structured.first
                builder.familyName = structured.second
            } else {
                val (given, family) = splitDisplayName(builder.displayName)
                builder.givenName = given
                builder.familyName = family
            }
        }

        return byContactId.values
            .flatMap { it.toContactItems(resolvedWithReadContacts = true) }
            .distinctBy { "${it.contactId}:${it.phoneNumber}" }
    }

    fun fromPickerUri(
        context: Context,
        uri: Uri,
        hasReadContactsPermission: Boolean,
    ): ContactItem? {
        val resolver = context.contentResolver
        val contactId = resolveContactId(resolver, uri) ?: return null
        val displayName = resolveDisplayName(resolver, contactId, uri).orEmpty()
        val phone = resolvePrimaryPhone(resolver, contactId, uri) ?: return null

        val (given, family) = if (hasReadContactsPermission) {
            loadStructuredName(resolver, contactId)
                ?: splitDisplayName(displayName)
        } else {
            splitDisplayName(displayName)
        }

        return ContactItem(
            contactId = contactId,
            phoneNumber = phone,
            givenName = given,
            familyName = family,
            displayName = displayName.ifBlank {
                listOf(given, family).filter { it.isNotBlank() }.joinToString(" ")
            },
            resolvedWithReadContacts = hasReadContactsPermission &&
                loadStructuredName(resolver, contactId) != null,
        )
    }

    private fun resolveContactId(resolver: ContentResolver, uri: Uri): Long? {
        resolver.query(
            uri,
            arrayOf(ContactsContract.Contacts._ID),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
            }
        }
        val lastSegment = uri.lastPathSegment
        return lastSegment?.toLongOrNull()
    }

    private fun resolveDisplayName(
        resolver: ContentResolver,
        contactId: Long,
        uri: Uri,
    ): String? {
        resolver.query(
            uri,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
            }
        }
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
            "${ContactsContract.Contacts._ID} = ?",
            arrayOf(contactId.toString()),
            null
        )?.use { cursor ->
            if (cursor.moveToNext()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
            }
        }
        return null
    }

    private fun resolvePrimaryPhone(
        resolver: ContentResolver,
        contactId: Long,
        uri: Uri,
    ): String? {
        if (uri.toString().contains("phone", ignoreCase = true)) {
            resolver.query(uri, arrayOf(Phone.NUMBER), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(Phone.NUMBER))?.trim()
                }
            }
        }
        resolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.NUMBER),
            "${Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            "${Phone.IS_PRIMARY} DESC"
        )?.use { cursor ->
            if (cursor.moveToNext()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(Phone.NUMBER))?.trim()
            }
        }
        return null
    }

    private fun loadStructuredName(
        resolver: ContentResolver,
        contactId: Long,
    ): Pair<String, String>? {
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(StructuredName.GIVEN_NAME, StructuredName.FAMILY_NAME, StructuredName.DISPLAY_NAME),
            "${Data.CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
            arrayOf(contactId.toString(), StructuredName.CONTENT_ITEM_TYPE),
            null
        )?.use { cursor ->
            if (cursor.moveToNext()) {
                val given = cursor.getString(cursor.getColumnIndexOrThrow(StructuredName.GIVEN_NAME)).orEmpty()
                val family = cursor.getString(cursor.getColumnIndexOrThrow(StructuredName.FAMILY_NAME)).orEmpty()
                if (given.isNotBlank() || family.isNotBlank()) {
                    return given to family
                }
                val display = cursor.getString(cursor.getColumnIndexOrThrow(StructuredName.DISPLAY_NAME)).orEmpty()
                if (display.isNotBlank()) return splitDisplayName(display)
            }
        }
        return null
    }

    private class MutableContactBuilder(
        val contactId: Long,
        var displayName: String,
    ) {
        var givenName: String = ""
        var familyName: String = ""
        private val phones = linkedSetOf<String>()

        fun addPhone(phone: String) {
            phones.add(phone)
        }

        fun toContactItems(resolvedWithReadContacts: Boolean): List<ContactItem> =
            phones.map { phone ->
                ContactItem(
                    contactId = contactId,
                    phoneNumber = phone,
                    givenName = givenName,
                    familyName = familyName,
                    displayName = displayName.ifBlank {
                        listOf(givenName, familyName).filter { it.isNotBlank() }.joinToString(" ")
                    },
                    resolvedWithReadContacts = resolvedWithReadContacts,
                )
            }
    }
}

