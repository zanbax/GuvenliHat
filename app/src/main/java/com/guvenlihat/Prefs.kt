package com.guvenlihat

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SafeContact(val name: String, val number: String)

/** TELEGRAM_SMS: önce internetten Telegram, gitmezse (internet kapalı vb.) SMS. */
enum class NotifyMethod {
    NONE, SMS, TELEGRAM, TELEGRAM_SMS;

    val usesSms get() = this == SMS || this == TELEGRAM_SMS
    val usesTelegram get() = this == TELEGRAM || this == TELEGRAM_SMS
}

/** Süre sınırının hangi aramalara uygulanacağı. */
enum class LimitMode { UNKNOWN_ONLY, ALL_EXCEPT_SAFE }

/** Tüm ayarlar ve küçük kalıcı veriler. */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("guvenli_hat", Context.MODE_PRIVATE)

    /** Bildirimlerde görünen ad, örn. "Annem". */
    var protectedName: String
        get() = sp.getString("protected_name", "") ?: ""
        set(value) = sp.edit().putString("protected_name", value.trim()).apply()

    val whoName: String get() = protectedName.ifBlank { "Korunan kişi" }

    /** 0 = kapalı */
    var limitMinutes: Int
        get() = sp.getInt("limit_minutes", 10)
        set(value) = sp.edit().putInt("limit_minutes", value).apply()

    var limitMode: LimitMode
        get() = enumOr(sp.getString("limit_mode", null), LimitMode.UNKNOWN_ONLY)
        set(value) = sp.edit().putString("limit_mode", value.name).apply()

    var redialBlockMinutes: Int
        get() = sp.getInt("redial_block_minutes", 60)
        set(value) = sp.edit().putInt("redial_block_minutes", value).apply()

    var filterUnknown: Boolean
        get() = sp.getBoolean("filter_unknown", true)
        set(value) = sp.edit().putBoolean("filter_unknown", value).apply()

    var notifyMethod: NotifyMethod
        get() = enumOr(sp.getString("notify_method", null), NotifyMethod.NONE)
        set(value) = sp.edit().putString("notify_method", value.name).apply()

    var telegramToken: String
        get() = sp.getString("tg_token", "") ?: ""
        set(value) = sp.edit().putString("tg_token", value.trim()).apply()

    var telegramChatId: String
        get() = sp.getString("tg_chat_id", "") ?: ""
        set(value) = sp.edit().putString("tg_chat_id", value.trim()).apply()

    var telegramChatTitle: String
        get() = sp.getString("tg_chat_title", "") ?: ""
        set(value) = sp.edit().putString("tg_chat_title", value).apply()

    var lastEmergencyCallAt: Long
        get() = sp.getLong("last_emergency", 0L)
        set(value) = sp.edit().putLong("last_emergency", value).apply()

    // ---------- Bakım modülü: hareketsizlik ve konum ----------

    /** 0 = kapalı */
    var inactivityHours: Int
        get() = sp.getInt("inactivity_hours", 0)
        set(value) = sp.edit().putInt("inactivity_hours", value).apply()

    var sleepStart: Int
        get() = sp.getInt("sleep_start", 22)
        set(value) = sp.edit().putInt("sleep_start", value).apply()

    var sleepEnd: Int
        get() = sp.getInt("sleep_end", 8)
        set(value) = sp.edit().putInt("sleep_end", value).apply()

    var inactivityAlerted: Boolean
        get() = sp.getBoolean("inactivity_alerted", false)
        set(value) = sp.edit().putBoolean("inactivity_alerted", value).apply()

    var lastActivityAt: Long
        get() = sp.getLong("last_activity_at", 0L)
        set(value) = sp.edit().putLong("last_activity_at", value).apply()

    /** Telefonun kullanıcı dokunmadan uyandığı anlar (gelen arama, kendi bildirimlerimiz). */
    @Synchronized
    fun addSystemWake(time: Long) {
        val arr = try { JSONArray(sp.getString("system_wakes", "[]")) } catch (e: Exception) { JSONArray() }
        val list = (0 until arr.length()).map { arr.optLong(it) }.filter { time - it < 3 * 24 * 60 * 60_000L } + time
        val out = JSONArray()
        list.takeLast(60).forEach { out.put(it) }
        sp.edit().putString("system_wakes", out.toString()).apply()
    }

    fun systemWakes(): List<Long> {
        val arr = try { JSONArray(sp.getString("system_wakes", "[]")) } catch (e: Exception) { JSONArray() }
        return (0 until arr.length()).map { arr.optLong(it) }
    }

    var locationEnabled: Boolean
        get() = sp.getBoolean("location_enabled", false)
        set(value) = sp.edit().putBoolean("location_enabled", value).apply()

    /** true: belirli saatlerde (locationTimes), false: belirli aralıklarla */
    var locationAtTimes: Boolean
        get() = sp.getBoolean("location_at_times", false)
        set(value) = sp.edit().putBoolean("location_at_times", value).apply()

    /** Konumun gönderileceği saatler (0–23), örn. [9, 14, 17] */
    var locationTimes: List<Int>
        get() = (sp.getString("location_times", "9,14,18") ?: "")
            .split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 0..23 }.distinct().sorted()
        set(value) = sp.edit().putString("location_times", value.distinct().sorted().joinToString(",")).apply()

    var locationIntervalHours: Int
        get() = sp.getInt("location_interval", 2)
        set(value) = sp.edit().putInt("location_interval", value).apply()

    /** start == end: bütün gün */
    var locationStart: Int
        get() = sp.getInt("location_start", 9)
        set(value) = sp.edit().putInt("location_start", value).apply()

    var locationEnd: Int
        get() = sp.getInt("location_end", 21)
        set(value) = sp.edit().putInt("location_end", value).apply()

    var lastLocationSentAt: Long
        get() = sp.getLong("last_location_sent", 0L)
        set(value) = sp.edit().putLong("last_location_sent", value).apply()

    /** Gelen aramada arayanın adını sesli oku */
    var announceCaller: Boolean
        get() = sp.getBoolean("announce_caller", false)
        set(value) = sp.edit().putBoolean("announce_caller", value).apply()

    /** "Arama bekliyorum": bu zamana kadar bilinmeyen aramalar engellenmez. */
    var relaxUntil: Long
        get() = sp.getLong("relax_until", 0L)
        set(value) = sp.edit().putLong("relax_until", value).apply()

    val relaxActive: Boolean get() = relaxUntil > System.currentTimeMillis()

    var missedCount: Int
        get() = sp.getInt("missed_count", 0)
        set(value) = sp.edit().putInt("missed_count", value).apply()

    var missedLastName: String
        get() = sp.getString("missed_last_name", "") ?: ""
        set(value) = sp.edit().putString("missed_last_name", value).apply()

    /** Rehberin son görülen hali (ContactsWatcher kullanır). */
    var contactSnapshot: String?
        get() = sp.getString("contact_snapshot", null)
        set(value) = sp.edit().putString("contact_snapshot", value).apply()

    var wasDefaultDialer: Boolean
        get() = sp.getBoolean("was_default_dialer", false)
        set(value) = sp.edit().putBoolean("was_default_dialer", value).apply()

    /** Uzun görüşme kesilince sırayla aranacak kişiler (en çok 3). */
    var safeContacts: List<SafeContact>
        get() {
            val raw = sp.getString("safe_contacts", null) ?: return emptyList()
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    SafeContact(o.optString("name"), o.optString("number"))
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(value) {
            val arr = JSONArray()
            value.forEach { arr.put(JSONObject().put("name", it.name).put("number", it.number)) }
            sp.edit().putString("safe_contacts", arr.toString()).apply()
        }

    // ---------- PIN ----------

    val hasPin: Boolean get() = !sp.getString("pin_hash", null).isNullOrEmpty()

    fun setPin(pin: String) = sp.edit().putString("pin_hash", hash(pin)).apply()

    fun checkPin(pin: String): Boolean = sp.getString("pin_hash", null) == hash(pin)

    private fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(("guvenlihat:$pin").toByteArray())
        return digest.joinToString("") { String.format("%02x", it) }
    }

    // ---------- Kesilen numaranın geri aranma engeli ----------

    fun blockRedial(numberKey: String, minutes: Int) {
        if (numberKey.isEmpty()) return
        val o = jsonObject("redial_blocks")
        o.put(numberKey, System.currentTimeMillis() + minutes * 60_000L)
        sp.edit().putString("redial_blocks", o.toString()).apply()
    }

    /** Engel bitiş zamanı; engel yoksa 0. */
    fun redialBlockedUntil(numberKey: String): Long {
        if (numberKey.isEmpty()) return 0L
        val until = jsonObject("redial_blocks").optLong(numberKey, 0L)
        return if (until > System.currentTimeMillis()) until else 0L
    }

    // ---------- Olay geçmişi ----------

    @Synchronized
    fun addEvent(text: String) {
        val stamp = SimpleDateFormat("dd.MM HH:mm", Locale("tr")).format(Date())
        val list = mutableListOf("$stamp  $text")
        list.addAll(events())
        val out = JSONArray()
        list.take(50).forEach { out.put(it) }
        sp.edit().putString("events", out.toString()).apply()
    }

    fun events(): List<String> {
        val arr = try {
            JSONArray(sp.getString("events", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        return (0 until arr.length()).map { arr.optString(it) }
    }

    // ---------- Bildirim sıklığı sınırı ----------

    /** Aynı anahtar için [windowMs] içinde ikinci kez true dönmez. */
    @Synchronized
    fun shouldNotify(key: String, windowMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val last = sp.getLong("thr_$key", 0L)
        if (now - last < windowMs) return false
        sp.edit().putLong("thr_$key", now).apply()
        return true
    }

    private fun jsonObject(key: String): JSONObject = try {
        JSONObject(sp.getString(key, "{}") ?: "{}")
    } catch (e: Exception) {
        JSONObject()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        if (name == null) default else runCatching { enumValueOf<T>(name) }.getOrDefault(default)
}
