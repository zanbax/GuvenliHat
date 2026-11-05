package com.guvenlihat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout

/** Yaşlı kişinin gördüğü ana ekran: büyük düğmeler. */
class MainActivity : Activity() {

    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = Ui.column(this, 20)
        setContentView(Ui.scroll(this, root))
    }

    override fun onResume() {
        super.onResume()
        checkProtectionLost()
        build()
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
        root.addView(Ui.button(this, "Son aramalar", Ui.BLUE, 26f) {
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
