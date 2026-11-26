package com.example.sms

import android.content.Context
import android.provider.ContactsContract
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import com.example.domain.model.PersianTextNormalizer
import com.example.domain.model.SenderClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceContact(
    val id: Long,
    val name: String,
    val phoneNumber: String,
    val normalizedKey: String,
    val photoUri: String? = null
)

data class ResolvedContact(
    val name: String,
    val photoUri: String? = null
)

object DeviceContactsManager {

    suspend fun loadContacts(context: Context): List<DeviceContact> = withContext(Dispatchers.IO) {
        if (!DefaultSmsHelper.hasContactsPermission(context)) return@withContext emptyList()
        val results = linkedMapOf<String, DeviceContact>()
        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
            )
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getLongOrNull(idIdx) ?: 0L else 0L
                    val name = if (nameIdx >= 0) cursor.getStringOrNull(nameIdx).orEmpty().trim() else ""
                    val rawNumber = if (numIdx >= 0) cursor.getStringOrNull(numIdx).orEmpty().trim() else ""
                    val photo = if (photoIdx >= 0) cursor.getStringOrNull(photoIdx) else null
                    if (name.isBlank() || rawNumber.isBlank()) continue

                    val key = normalizePhoneLookupKey(rawNumber)
                    if (key.isBlank()) continue
                    if (!results.containsKey(key)) {
                        results[key] = DeviceContact(
                            id = id,
                            name = name,
                            phoneNumber = rawNumber,
                            normalizedKey = key,
                            photoUri = photo
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Gracefully return empty if contacts provider is unavailable
        }
        results.values.toList()
    }

    fun buildLookupMap(contacts: List<DeviceContact>): Map<String, ResolvedContact> {
        val map = HashMap<String, ResolvedContact>(contacts.size * 2)
        for (c in contacts) {
            val resolved = ResolvedContact(name = c.name, photoUri = c.photoUri)
            map[c.normalizedKey] = resolved
            val threadKey = SenderClassifier.extractThreadKey(c.phoneNumber)
            if (threadKey.isNotBlank()) {
                map.putIfAbsent(threadKey, resolved)
            }
            val digits = PersianTextNormalizer.normalizeDigits(c.phoneNumber).filter { it.isDigit() }
            if (digits.length >= 10) {
                map.putIfAbsent(digits.takeLast(10), resolved)
            }
        }
        return map
    }

    fun resolveSender(senderRaw: String, lookupMap: Map<String, ResolvedContact>): ResolvedContact? {
        if (lookupMap.isEmpty() || senderRaw.isBlank()) return null
        // Never treat alphanumeric Sender IDs (like BANK or AD-SMS) as numeric contacts
        val hasLetters = senderRaw.any { it.isLetter() }
        if (hasLetters) return null

        val threadKey = SenderClassifier.extractThreadKey(senderRaw)
        lookupMap[threadKey]?.let { return it }

        val key = normalizePhoneLookupKey(senderRaw)
        lookupMap[key]?.let { return it }

        val digits = PersianTextNormalizer.normalizeDigits(senderRaw).filter { it.isDigit() }
        if (digits.length >= 10) {
            lookupMap[digits.takeLast(10)]?.let { return it }
        }
        return null
    }

    fun normalizePhoneLookupKey(phone: String): String {
        val ascii = PersianTextNormalizer.normalizeDigits(phone).filter { it.isDigit() }
        return when {
            ascii.startsWith("0098") && ascii.length > 4 -> "0" + ascii.substring(4)
            ascii.startsWith("98") && ascii.length >= 12 -> "0" + ascii.substring(2)
            ascii.length == 10 && ascii.startsWith("9") -> "0$ascii"
            else -> ascii
        }
    }
}
