package com.guvenlihat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils

object PhoneUtils {

    /** Giden aramada asla kesilmeyecek numaralar. */
    private val EMERGENCY = setOf("112", "110", "155", "156", "177", "122", "911", "999")

    fun numberOf(call: Call): String? = numberOf(call.details)

    fun numberOf(details: Call.Details?): String? {
        val n = details?.handle?.schemeSpecificPart
        return if (n.isNullOrBlank()) null else n
    }

    /** Karşılaştırma anahtarı: son 10 hane (0532..., +90532..., 90532... aynı olur). */
    fun key(number: String?): String {
        val digits = number?.filter { it.isDigit() } ?: return ""
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    @Suppress("DEPRECATION")
    fun isEmergency(number: String?): Boolean {
        if (number.isNullOrBlank()) return false
        val d = number.filter { it.isDigit() }
        if (d in EMERGENCY) return true
        return try {
            PhoneNumberUtils.isEmergencyNumber(d)
        } catch (e: Exception) {
            false
        }
    }

    fun has(ctx: Context, permission: String): Boolean =
        ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun hasContactsPermission(ctx: Context) = has(ctx, Manifest.permission.READ_CONTACTS)

    /** Rehberde bulunursa adı ("" olabilir), bulunmazsa null. Hata olursa istisna fırlatır. */
    private fun lookupRow(ctx: Context, number: String): String? {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.DISPLAY_NAME)
        ctx.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(1) ?: ""
        }
        return null
    }

    fun contactName(ctx: Context, number: String?): String? {
        if (number.isNullOrBlank() || !hasContactsPermission(ctx)) return null
        return try {
            // İsimsiz kayıtlarda isim alanı numaranın kendisidir; onu isim sayma
            lookupRow(ctx, number)?.takeIf { n -> n.any { it.isLetter() } }
        } catch (e: Exception) {
            null
        }
    }

    /** Rehberdeki küçük fotoğraf (varsa). Ek izin gerekmez, rehber izniyle gelir. */
    fun photoUri(ctx: Context, number: String?): Uri? {
        if (number.isNullOrBlank() || !hasContactsPermission(ctx)) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            ctx.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI), null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { Uri.parse(it) } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * true: rehberde kayıtlı, false: kayıtlı değil (gizli numara dahil),
     * null: bilinemiyor (rehber izni yok). Bilinmiyorsa kimse engellenmez.
     */
    fun isKnown(ctx: Context, number: String?): Boolean? {
        if (!hasContactsPermission(ctx)) return null
        if (number.isNullOrBlank()) return false
        return try {
            lookupRow(ctx, number) != null
        } catch (e: Exception) {
            null
        }
    }

    fun isSafe(prefs: Prefs, number: String?): Boolean = safeName(prefs, number) != null

    fun safeName(prefs: Prefs, number: String?): String? {
        val k = key(number)
        if (k.isEmpty()) return null
        return prefs.safeContacts.firstOrNull { key(it.number) == k }?.name
    }

    /** Ekranda gösterilecek ad. */
    fun label(ctx: Context, number: String?): String {
        if (number.isNullOrBlank()) return "Gizli numara"
        return contactName(ctx, number) ?: safeName(Prefs(ctx), number) ?: number
    }

    // ---------- Arama başlatma ----------

    fun placeCall(ctx: Context, number: String, autoSim: Boolean = false) {
        val uri = Uri.fromParts("tel", number, null)
        try {
            val tm = ctx.getSystemService(TelecomManager::class.java)
            if (tm != null && has(ctx, Manifest.permission.CALL_PHONE)) {
                val extras = Bundle()
                if (autoSim) defaultAccount(ctx)?.let {
                    extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it)
                }
                tm.placeCall(uri, extras)
                return
            }
        } catch (e: Exception) {
            // aşağıdaki yedek yollara düş
        }
        try {
            ctx.startActivity(Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                ctx.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (ignored: Exception) {
            }
        }
    }

    fun phoneAccounts(ctx: Context): List<PhoneAccountHandle> {
        if (!has(ctx, Manifest.permission.READ_PHONE_STATE)) return emptyList()
        return try {
            ctx.getSystemService(TelecomManager::class.java)?.callCapablePhoneAccounts ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun defaultAccount(ctx: Context): PhoneAccountHandle? {
        if (!has(ctx, Manifest.permission.READ_PHONE_STATE)) return null
        val def = try {
            ctx.getSystemService(TelecomManager::class.java)
                ?.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
        } catch (e: Exception) {
            null
        }
        return def ?: phoneAccounts(ctx).firstOrNull()
    }

    fun accountLabel(ctx: Context, handle: PhoneAccountHandle, index: Int): String {
        val label = try {
            ctx.getSystemService(TelecomManager::class.java)?.getPhoneAccount(handle)?.label?.toString()
        } catch (e: Exception) {
            null
        }
        return if (label.isNullOrBlank()) "Hat ${index + 1}" else label
    }
}
