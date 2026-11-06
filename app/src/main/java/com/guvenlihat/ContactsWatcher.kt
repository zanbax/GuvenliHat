package com.guvenlihat

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import org.json.JSONObject

/**
 * Rehberdeki değişiklikleri (hangi uygulamadan yapılırsa yapılsın) yakalar ve aileye bildirir.
 * Her numaranın rehberde ilk görüldüğü zamanı saklar: son 24 saatte eklenen numaralara
 * tam güvenilmez (dolandırıcının "numaramı kaydet" taktiğine karşı).
 */
object ContactsWatcher {

    private const val NEW_WINDOW_MS = 24 * 60 * 60_000L

    /** Bu sayıdan fazla değişiklik bir anda olursa (ör. Google eşitlemesi) tek özet mesaj atılır. */
    private const val BULK_LIMIT = 5

    private val main = Handler(Looper.getMainLooper())
    private val pending = Runnable { checkInBackground(appCtx ?: return@Runnable) }
    private var appCtx: Context? = null

    /** Uygulama süreci açık olduğu sürece rehber değişikliklerini dinler. */
    fun register(ctx: Context) {
        appCtx = ctx.applicationContext
        try {
            ctx.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI, true,
                object : ContentObserver(main) {
                    override fun onChange(selfChange: Boolean) {
                        main.removeCallbacks(pending)
                        main.postDelayed(pending, 3000)
                    }
                },
            )
        } catch (e: Exception) {
        }
    }

    fun checkInBackground(ctx: Context) {
        val app = ctx.applicationContext
        Thread { check(app) }.start()
    }

    /** Rehberi son kayıtla karşılaştırır; farkları aileye bildirir. */
    @Synchronized
    fun check(ctx: Context) {
        if (!PhoneUtils.hasContactsPermission(ctx)) return
        val current = loadPhones(ctx) ?: return
        val prefs = Prefs(ctx)
        val now = System.currentTimeMillis()
        val old = readSnapshot(prefs)

        if (old == null) {
            // İlk kurulum: mevcut rehberin tamamı "eski" sayılır, bildirim gönderilmez.
            writeSnapshot(prefs, current.mapValues { it.value to 0L })
            return
        }

        val added = current.keys - old.keys
        val removed = old.keys - current.keys
        if (added.isEmpty() && removed.isEmpty()) {
            // Sadece isim değişmiş olabilir; kaydı güncelle
            if (current.any { (k, n) -> old[k]?.first != n }) {
                writeSnapshot(prefs, current.mapValues { (k, n) -> n to (old[k]?.second ?: 0L) })
            }
            return
        }

        val bulk = added.size + removed.size > BULK_LIMIT
        writeSnapshot(
            prefs,
            current.mapValues { (k, n) -> n to (old[k]?.second ?: if (bulk) 0L else now) },
        )

        if (bulk) {
            Notifier.send(
                ctx,
                "Rehberde bir anda ${added.size} ekleme ve ${removed.size} silme oldu " +
                    "(hesap eşitlemesi olabilir). Lütfen kontrol edin.",
            )
            return
        }

        // Aynı isim farklı numarayla geri geldiyse "numara değiştirildi" de.
        val removedByName = removed.groupBy { old[it]?.first ?: "" }
        val handledRemoved = HashSet<String>()
        for (k in added) {
            val name = current[k] ?: ""
            val oldKey = removedByName[name]?.firstOrNull { it !in handledRemoved }
            if (name.isNotEmpty() && oldKey != null) {
                handledRemoved.add(oldKey)
                Notifier.send(
                    ctx,
                    "Rehberde $name kişisinin numarası DEĞİŞTİRİLDİ: 0$oldKey → 0$k. " +
                        "Bu numara 24 saat boyunca yeni kişi gibi takip edilecek.",
                )
            } else {
                Notifier.send(
                    ctx,
                    "Rehbere yeni kişi eklendi: $name (0$k). " +
                        "Bu numara 24 saat boyunca yeni kişi gibi takip edilecek.",
                )
            }
        }
        for (k in removed) {
            if (k in handledRemoved) continue
            Notifier.send(ctx, "Rehberden kişi silindi: ${old[k]?.first ?: ""} (0$k).")
        }
    }

    /** Numara rehbere son 24 saat içinde mi girdi? */
    fun isRecentlyAdded(ctx: Context, number: String?): Boolean {
        val k = PhoneUtils.key(number)
        if (k.isEmpty()) return false
        val seen = readSnapshot(Prefs(ctx))?.get(k)?.second ?: return false
        return seen > 0 && System.currentTimeMillis() - seen < NEW_WINDOW_MS
    }

    /** anahtar (son 10 hane) → isim; hata olursa null */
    private fun loadPhones(ctx: Context): Map<String, String>? {
        val out = HashMap<String, String>()
        return try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val k = PhoneUtils.key(c.getString(0))
                    if (k.length >= 7) out[k] = c.getString(1) ?: ""
                }
            } ?: return null
            out
        } catch (e: Exception) {
            null
        }
    }

    private fun readSnapshot(prefs: Prefs): Map<String, Pair<String, Long>>? {
        val raw = prefs.contactSnapshot ?: return null
        return try {
            val o = JSONObject(raw)
            val m = HashMap<String, Pair<String, Long>>()
            for (k in o.keys()) {
                val e = o.getJSONObject(k)
                m[k] = e.optString("n") to e.optLong("t")
            }
            m
        } catch (e: Exception) {
            null
        }
    }

    private fun writeSnapshot(prefs: Prefs, map: Map<String, Pair<String, Long>>) {
        val o = JSONObject()
        for ((k, v) in map) o.put(k, JSONObject().put("n", v.first).put("t", v.second))
        prefs.contactSnapshot = o.toString()
    }
}
