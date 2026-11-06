package com.guvenlihat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout

/** Yaşlı kişinin gördüğü ana ekran: büyük düğmeler. */
class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private val handler = Handler(Looper.getMainLooper())

    /** "Arama bekliyorum" geri sayımını ve cevapsız sayısını tazeler. */
    private val ticker = object : Runnable {
        override fun run() {
            if (Prefs(this@MainActivity).relaxUntil != 0L) build()
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = Ui.column(this, 20)
        setContentView(Ui.scroll(this, root))
    }

    override fun onResume() {
        super.onResume()
        checkProtectionLost()
        ContactsWatcher.checkInBackground(this)
        build()
        handler.postDelayed(ticker, 30_000)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun build() {
        root.removeAllViews()
        val prefs = Prefs(this)

        root.addView(Ui.label(this, "Güvenli Hat", 32f, Ui.TEXT, bold = true))
        val ok = Setup.isDefaultDialer(this) && PhoneUtils.hasContactsPermission(this)
        root.addView(
            Ui.label(
                this,
                if (ok) "✓ Koruma açık" else "⚠ Kurulum tamamlanmadı.\nAilenizden yardım isteyin.",
                22f, if (ok) Ui.GREEN else Ui.RED, bold = true,
            ),
        )

        // "Arama bekliyorum": hastane, kargo gibi tanınmayan bir aramayı beklerken filtreyi geçici aç
        if (prefs.filterUnknown) {
            if (!prefs.relaxActive && prefs.relaxUntil != 0L) prefs.relaxUntil = 0L
            if (prefs.relaxActive) {
                val left = (prefs.relaxUntil - System.currentTimeMillis()) / 60_000L + 1
                val box = Ui.label(
                    this, "⏳ Tanımadığınız numaralar da arayabilir.\nKalan süre: $left dakika", 22f, Ui.TEXT, bold = true,
                )
                val p = Ui.dp(this, 14)
                box.setPadding(p, p, p, p)
                box.setBackgroundColor(Ui.YELLOW)
                root.addView(box)
                root.addView(Ui.button(this, "Beklemeyi bitir", Ui.RED, 22f) {
                    CallManager.stopRelax(this)
                    build()
                })
            } else {
                root.addView(Ui.button(this, "⏳  Arama bekliyorum", Ui.KEY, 24f) { askRelax() })
            }
        }

        if (CallManager.hasCalls()) {
            root.addView(Ui.button(this, "Görüşmeye dön", Ui.GREEN, 28f) {
                startActivity(Intent(this, InCallActivity::class.java))
            })
        }

        val safe = prefs.safeContacts
        if (safe.isNotEmpty()) {
            root.addView(Ui.label(this, "Yakınlarım", 20f, Ui.GRAY))
            safe.forEach { c ->
                root.addView(Ui.button(this, "📞  ${c.name}", Ui.GREEN, 28f) {
                    Ui.ask(this, "Arama", "${c.name} aransın mı?", yes = "Ara") {
                        PhoneUtils.placeCall(this, c.number)
                    }
                })
            }
        }

        root.addView(Ui.label(this, " ", 8f))
        root.addView(Ui.button(this, "Rehber", Ui.BLUE, 26f) {
            startActivity(Intent(this, ContactsActivity::class.java))
        })
        val missed = prefs.missedCount
        val recentsText = if (missed > 0) "Son aramalar  ($missed cevapsız)" else "Son aramalar"
        root.addView(Ui.button(this, recentsText, if (missed > 0) Ui.RED else Ui.BLUE, 26f) {
            startActivity(Intent(this, RecentsActivity::class.java))
        })
        root.addView(Ui.button(this, "Numara tuşla", Ui.BLUE, 26f) {
            startActivity(Intent(this, DialpadActivity::class.java))
        })
        root.addView(Ui.label(this, " ", 16f))
        root.addView(Ui.button(this, "Ayarlar (aile için)", Ui.GRAY, 18f) {
            startActivity(Intent(this, SettingsActivity::class.java))
        })
    }

    private fun askRelax() {
        val options = listOf(15, 30, 45, 60).map { m ->
            Triple<String, Int, () -> Unit>("$m dakika", Ui.BLUE) {
                CallManager.startRelax(this, m)
                build()
            }
        }
        Ui.actions(
            this,
            "Tanımadığınız bir numaradan (hastane, doktor, kargo) arama mı bekliyorsunuz?\n\n" +
                "Seçtiğiniz süre boyunca bu aramalar engellenmez. Aileniz bilgilendirilir.",
            options,
        )
    }

    /** Uygulama varsayılan telefon uygulaması olmaktan çıkarılırsa aileye haber ver. */
    private fun checkProtectionLost() {
        val prefs = Prefs(this)
        val isDefault = Setup.isDefaultDialer(this)
        if (isDefault) {
            prefs.wasDefaultDialer = true
        } else if (prefs.wasDefaultDialer) {
            Notifier.send(
                this, "DİKKAT: Güvenli Hat artık varsayılan telefon uygulaması değil, koruma kapalı!",
                throttleKey = "role_lost", throttleMs = 6 * 60 * 60_000L,
            )
        }
    }
}
