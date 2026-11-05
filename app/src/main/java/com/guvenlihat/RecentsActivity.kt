package com.guvenlihat

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.provider.CallLog
import android.view.Gravity
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Son aramalar. Engellenen aramalar burada gösterilmez:
 * yaşlı kişi dolandırıcıyı merak edip geri aramasın (aile bunları bildirimle görür).
 */
class RecentsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = Ui.column(this, 16)
        root.addView(Ui.label(this, "Son aramalar", 30f, Ui.TEXT, bold = true))

        val list = Ui.column(this, 0)
        val scroll = Ui.scroll(this, list)
        scroll.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        root.addView(scroll)
        root.addView(Ui.button(this, "Geri", Ui.GRAY, 20f) { finish() })
        setContentView(root)

        if (!PhoneUtils.has(this, Manifest.permission.READ_CALL_LOG)) {
            list.addView(Ui.label(this, "Arama geçmişi izni yok. Ailenizden ayarları yapmasını isteyin.", 22f, Ui.RED))
            return
        }
        fill(list)
    }

    private fun fill(list: LinearLayout) {
        val fmt = SimpleDateFormat("d MMM HH:mm", Locale("tr"))
        var count = 0
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
                    val name = c.getString(1)?.takeIf { it.isNotBlank() }
                        ?: PhoneUtils.contactName(this, number)
                        ?: PhoneUtils.safeName(Prefs(this), number)
                    val known = name != null
                    if (type == CallLog.Calls.REJECTED_TYPE && !known) continue

                    val typeText = when (type) {
                        CallLog.Calls.INCOMING_TYPE -> "Gelen"
                        CallLog.Calls.OUTGOING_TYPE -> "Giden"
                        CallLog.Calls.MISSED_TYPE -> "Cevapsız"
                        CallLog.Calls.REJECTED_TYPE -> "Reddedildi"
                        else -> ""
                    }
                    val title = name ?: number.ifBlank { "Gizli numara" }
                    val extra = if (known) "" else "  ·  rehberde yok"
                    val color = when {
                        !known -> Ui.GRAY
                        type == CallLog.Calls.MISSED_TYPE -> Ui.RED
                        else -> Ui.BLUE
                    }
                    val b = Ui.button(this, "$title\n$typeText  ·  ${fmt.format(Date(c.getLong(3)))}$extra", color, 22f) {
                        if (number.isNotBlank()) CallFlow.start(this, number)
                    }
                    b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    list.addView(b)
                    count++
                }
            }
        } catch (e: Exception) {
        }
        if (count == 0) list.addView(Ui.label(this, "Kayıt yok.", 22f, Ui.GRAY))
    }
}
