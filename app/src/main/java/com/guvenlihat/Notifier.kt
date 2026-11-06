package com.guvenlihat

import android.Manifest
import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Aileye SMS veya Telegram ile haber verir; her olayı geçmişe de yazar. */
object Notifier {

    private val executor = Executors.newSingleThreadExecutor()

    /**
     * @param throttleKey aynı anahtarla [throttleMs] içinde tekrar mesaj atılmaz
     *                    (ör. dolandırıcı aynı numaradan 20 kez ararsa 20 SMS gitmesin).
     */
    fun send(ctx: Context, text: String, throttleKey: String? = null, throttleMs: Long = 10 * 60_000L) {
        val app = ctx.applicationContext
        val prefs = Prefs(app)
        prefs.addEvent(text)
        if (throttleKey != null && !prefs.shouldNotify(throttleKey, throttleMs)) return
        if (prefs.notifyMethod == NotifyMethod.NONE) return
        executor.execute {
            var error: String? = null
            for (attempt in 1..3) {
                error = sendNow(app, text)
                if (error == null) break
                try {
                    Thread.sleep(20_000L)
                } catch (e: InterruptedException) {
                    break
                }
            }
            if (error != null) prefs.addEvent("Bildirim gönderilemedi: $error")
        }
    }

    /** Bekleyerek gönderir; ana iş parçacığından çağırmayın. Hata varsa açıklamasını döner. */
    fun sendNow(ctx: Context, text: String): String? {
        val prefs = Prefs(ctx)
        val time = SimpleDateFormat("HH:mm", Locale("tr")).format(Date())
        val full = "Güvenli Hat (${prefs.whoName}): $text [$time]"
        return when (prefs.notifyMethod) {
            NotifyMethod.NONE -> "Bildirim kapalı"
            NotifyMethod.SMS -> sendSms(ctx, prefs, full)
            NotifyMethod.TELEGRAM -> sendTelegram(prefs.telegramToken, prefs.telegramChatId, full)
            NotifyMethod.TELEGRAM_SMS -> {
                // İnternet kapalıysa Telegram hemen hata verir; beklemeden SMS'e geç.
                val tgError = sendTelegram(prefs.telegramToken, prefs.telegramChatId, full)
                if (tgError == null) {
                    null
                } else {
                    val smsError = sendSms(ctx, prefs, full)
                    if (smsError == null) {
                        prefs.addEvent("Telegram gitmedi ($tgError), SMS ile gönderildi.")
                        null
                    } else {
                        "Telegram: $tgError / SMS: $smsError"
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun sendSms(ctx: Context, prefs: Prefs, text: String): String? {
        if (!PhoneUtils.has(ctx, Manifest.permission.SEND_SMS)) return "SMS izni yok"
        val targets = prefs.safeContacts
        if (targets.isEmpty()) return "Güvenli kişi eklenmemiş"
        return try {
            val sms: SmsManager = if (Build.VERSION.SDK_INT >= 31) {
                ctx.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            // Türkçe karakterli SMS 70 karakterde bölünür; sade harflerle 160 karakter tek SMS olur.
            val parts = sms.divideMessage(toGsm(text))
            targets.forEach { sms.sendMultipartTextMessage(it.number, null, parts, null, null) }
            null
        } catch (e: Exception) {
            e.message ?: "SMS hatası"
        }
    }

    private fun toGsm(s: String): String {
        val map = mapOf(
            'ç' to 'c', 'Ç' to 'C', 'ğ' to 'g', 'Ğ' to 'G', 'ı' to 'i', 'İ' to 'I',
            'ö' to 'o', 'Ö' to 'O', 'ş' to 's', 'Ş' to 'S', 'ü' to 'u', 'Ü' to 'U',
        )
        return s.map { map[it] ?: it }.joinToString("").filter { it.code < 0x2000 }
    }

    private fun sendTelegram(token: String, chatId: String, text: String): String? {
        if (token.isBlank() || chatId.isBlank()) return "Telegram ayarı eksik"
        var conn: HttpURLConnection? = null
        return try {
            conn = URL("https://api.telegram.org/bot$token/sendMessage").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            // Kısa tutuldu: gitmezse SMS yedeğine hızla geçilsin
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            val body = "chat_id=" + URLEncoder.encode(chatId, "UTF-8") +
                "&text=" + URLEncoder.encode(text, "UTF-8")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code in 200..299) null
            else "Telegram $code: " + (conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "")
        } catch (e: Exception) {
            "İnternet yok veya Telegram'a ulaşılamadı"
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Botun gördüğü sohbetleri (aile grubu vb.) listeler: (chatId, başlık).
     * Ana iş parçacığından çağırmayın.
     */
    fun findTelegramChats(token: String): List<Pair<String, String>> {
        val conn = URL("https://api.telegram.org/bot$token/getUpdates").openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("Token hatalı olabilir (kod $code)")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val result = json.optJSONArray("result") ?: return emptyList()
            val found = LinkedHashMap<String, String>()
            for (i in 0 until result.length()) {
                val update = result.optJSONObject(i) ?: continue
                for (k in listOf("message", "edited_message", "my_chat_member", "channel_post")) {
                    val chat = update.optJSONObject(k)?.optJSONObject("chat") ?: continue
                    val id = chat.opt("id")?.toString() ?: continue
                    val title = chat.optString("title").ifBlank {
                        (chat.optString("first_name") + " " + chat.optString("last_name")).trim()
                    }.ifBlank { id }
                    found[id] = title
                }
            }
            return found.map { it.key to it.value }
        } finally {
            conn.disconnect()
        }
    }
}
