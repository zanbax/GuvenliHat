package com.guvenlihat

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.provider.CallLog
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Son aramalar. Kırmızı sadece rehberde olmayan (şüpheli) numaralar için kullanılır.
 * Sistemin engellediği aramalar burada gösterilmez:
 * yaşlı kişi dolandırıcıyı merak edip geri aramasın (aile bunları bildirimle görür).
 */
class RecentsActivity : Activity() {

    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = Ui.column(this, 16)
        root.setBackgroundColor(Ui.BG)
        root.addView(Ui.label(this, "Son Aramalar", 30f, Ui.TEXT, bold = true))

        list = Ui.column(this, 0)
        val scroll = Ui.scroll(this, list)
        scroll.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        root.addView(scroll)
        root.addView(Ui.button(this, "Geri", Ui.GRAY, 20f) { finish() })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        // Liste görüldü: cevapsız sayısını ve simgedeki rozeti sıfırla
        MissedCalls.clear(this)
        list.removeAllViews()
        if (!PhoneUtils.has(this, Manifest.permission.READ_CALL_LOG)) {
            list.addView(Ui.label(this, "Arama geçmişi izni yok. Ailenizden ayarları yapmasını isteyin.", 22f, Ui.RED))
            return
        }
        fill()
    }

    private fun whenText(time: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = time }
        val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
        val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
        val tr = Locale("tr")
        val hour = SimpleDateFormat("HH:mm", tr).format(Date(time))
        return when {
            sameYear && dayDiff == 0 -> "Bugün $hour"
            sameYear && dayDiff == 1 -> "Dün $hour"
            else -> SimpleDateFormat("d MMMM HH:mm", tr).format(Date(time))
        }
    }

    private fun fill() {
        var count = 0
        val prefs = Prefs(this)
        try {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE),
                null, null, CallLog.Calls.DATE + " DESC",
            )?.use { c ->
                while (c.moveToNext() && count < 60) {
                    val number = c.getString(0) ?: ""
                    val type = c.getInt(2)
                    if (type == CallLog.Calls.BLOCKED_TYPE) continue
                    // İsimsiz kayıtlarda isim alanı numaranın kendisi olabilir: harf yoksa isim sayma
                    val name = c.getString(1)?.takeIf { n -> n.any { it.isLetter() } }
                        ?: PhoneUtils.contactName(this, number)
                        ?: PhoneUtils.safeName(prefs, number)
                    val known = name != null || PhoneUtils.isKnown(this, number) == true
                    if (type == CallLog.Calls.REJECTED_TYPE && !known) continue

                    val typeText = when (type) {
                        CallLog.Calls.INCOMING_TYPE -> "↙ Gelen"
                        CallLog.Calls.OUTGOING_TYPE -> "↗ Giden"
                        CallLog.Calls.MISSED_TYPE -> "✕ Cevapsız"
                        CallLog.Calls.REJECTED_TYPE -> "✕ Reddedildi"
                        else -> ""
                    }
                    val title = name ?: number.ifBlank { "Gizli Numara" }
                    val time = whenText(c.getLong(3))
                    val onClick = { if (number.isNotBlank()) CallFlow.start(this, number) }

                    val item = if (!known) {
                        // Şüpheli: kırmızı zemin ve uyarı
                        Ui.listItem(
                            this,
                            Ui.avatar(this, "?", null, 52, Ui.RED_TINT, Ui.RED).also {
                                (it as? android.widget.TextView)?.text = "!"
                            },
                            title, "⚠ Rehberde Yok · $typeText · $time",
                            titleColor = Ui.RED_DARK, subColor = Ui.RED,
                            fill = Ui.RED_TINT, stroke = Ui.RED, strokeDp = 2, onClick = onClick,
                        )
                    } else {
                        val missed = type == CallLog.Calls.MISSED_TYPE
                        Ui.listItem(
                            this,
                            Ui.avatar(this, title, PhoneUtils.photoUri(this, number), 52, Ui.BLUE_TINT, Ui.BLUE_DARK),
                            title, "$typeText · $time",
                            subColor = if (missed) Ui.ORANGE else Ui.SECONDARY, onClick = onClick,
                        )
                    }
                    list.addView(item)
                    count++
                }
            }
        } catch (e: Exception) {
        }
        if (count == 0) list.addView(Ui.label(this, "Kayıt yok.", 22f, Ui.SECONDARY))
    }
}
