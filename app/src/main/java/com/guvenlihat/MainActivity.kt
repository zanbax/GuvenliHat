package com.guvenlihat

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/**
 * Yaşlı kişinin gördüğü ana ekran.
 * Üstte başlık ve durum, ortada kaydırılabilir "Yakınlarım", altta her zaman görünen menü.
 */
class MainActivity : Activity() {

    companion object {
        /** Yakınlarım'da en fazla kaç kişi görünsün (güvenli kişiler dahil) */
        private const val MAX_PEOPLE = 6
        private const val FREQUENT_DAYS = 60
    }

    private lateinit var header: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private val handler = Handler(Looper.getMainLooper())

    /** "Arama bekliyorum" geri sayımını tazeler. */
    private val ticker = object : Runnable {
        override fun run() {
            if (Prefs(this@MainActivity).relaxUntil != 0L) build()
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)

        header = Ui.column(this, 16)
        root.addView(header)

        content = Ui.column(this, 16)
        content.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 16))
        val scroll = Ui.scroll(this, content)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        bottomBar = Ui.row(this)
        bottomBar.setBackgroundColor(Ui.CARD)
        val p = Ui.dp(this, 8)
        bottomBar.setPadding(p, p, p, p)
        val divider = View(this)
        divider.setBackgroundColor(Ui.BORDER)
        root.addView(divider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)))
        root.addView(bottomBar)

        setContentView(root)
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
        buildHeader()
        buildContent()
        buildBottomBar()
    }

    // ---------------- Üst kısım ----------------

    private fun buildHeader() {
        header.removeAllViews()
        val row = Ui.row(this)
        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        titles.addView(Ui.label(this, "Güvenli Hat", 30f, Ui.TEXT, bold = true).apply { setPadding(0, 0, 0, 0) })
        val ok = Setup.isDefaultDialer(this) && PhoneUtils.hasContactsPermission(this)
        titles.addView(
            Ui.label(
                this, if (ok) "✓ Koruma Açık" else "⚠ Kurulum Tamamlanmadı",
                19f, if (ok) Ui.GREEN else Ui.RED, bold = true,
            ),
        )
        row.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val family = Ui.label(this, "⚙ Aile", 17f, Ui.SECONDARY)
        family.background = Ui.cardBackground(this, Ui.BG, Ui.BORDER)
        val p = Ui.dp(this, 10)
        family.setPadding(p, Ui.dp(this, 6), p, Ui.dp(this, 6))
        family.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        row.addView(family)
        header.addView(row)
        if (!ok) {
            header.addView(Ui.label(this, "Ailenizden yardım isteyin.", 17f, Ui.RED))
        }
    }

    // ---------------- Orta kısım ----------------

    private fun buildContent() {
        content.removeAllViews()
        val prefs = Prefs(this)

        if (CallManager.hasCalls()) {
            content.addView(Ui.button(this, "Görüşmeye Dön", Ui.GREEN, 26f) {
                startActivity(Intent(this, InCallActivity::class.java))
            })
        }

        // "Arama bekliyorum": hastane, kargo gibi tanınmayan bir aramayı beklerken filtreyi geçici aç
        if (prefs.filterUnknown) {
            if (!prefs.relaxActive && prefs.relaxUntil != 0L) prefs.relaxUntil = 0L
            if (prefs.relaxActive) {
                val left = (prefs.relaxUntil - System.currentTimeMillis()) / 60_000L + 1
                val box = Ui.label(
                    this, "⏳ Tanımadığınız numaralar da arayabilir.\nKalan süre: $left dakika", 21f, Ui.TEXT, bold = true,
                )
                val p = Ui.dp(this, 14)
                box.setPadding(p, p, p, p)
                box.background = Ui.cardBackground(this, Ui.YELLOW, Ui.YELLOW)
                content.addView(box)
                content.addView(Ui.button(this, "Beklemeyi Bitir", Ui.RED, 22f) {
                    CallManager.stopRelax(this)
                    build()
                })
            } else {
                content.addView(Ui.button(this, "⏳  Arama Bekliyorum", Ui.KEY, 22f) { askRelax() })
            }
        }

        content.addView(Ui.label(this, "Yakınlarım", 20f, Ui.SECONDARY, bold = true).apply {
            setPadding(0, Ui.dp(this@MainActivity, 10), 0, Ui.dp(this@MainActivity, 4))
        })

        val people = people(prefs)
        if (people.isEmpty()) {
            content.addView(
                Ui.label(this, "Aileniz ayarlardan yakınlarınızı ekleyebilir.", 19f, Ui.SECONDARY),
            )
            return
        }
        // İkişerli kartlar
        people.chunked(2).forEach { pair ->
            val row = Ui.row(this)
            pair.forEach { person -> row.addView(personCard(person)) }
            if (pair.size == 1) {
                val empty = View(this)
                row.addView(empty, LinearLayout.LayoutParams(0, 1, 1f))
            }
            content.addView(row)
        }
    }

    private data class Person(val name: String, val number: String, val safe: Boolean)

    /** Önce güvenli kişiler, sonra son 2 ayda en sık konuşulan rehber kişileri. */
    private fun people(prefs: Prefs): List<Person> {
        val out = mutableListOf<Person>()
        val seen = HashSet<String>()
        prefs.safeContacts.forEach {
            if (seen.add(PhoneUtils.key(it.number))) out.add(Person(it.name, it.number, true))
        }
        if (out.size >= MAX_PEOPLE || !PhoneUtils.has(this, Manifest.permission.READ_CALL_LOG)) return out

        val counts = LinkedHashMap<String, Int>()
        val numbers = HashMap<String, String>()
        try {
            val since = System.currentTimeMillis() - FREQUENT_DAYS * 24L * 60 * 60_000L
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE),
                CallLog.Calls.DATE + " > ?", arrayOf(since.toString()), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val number = c.getString(0) ?: continue
                    val type = c.getInt(1)
                    if (type != CallLog.Calls.INCOMING_TYPE && type != CallLog.Calls.OUTGOING_TYPE) continue
                    val k = PhoneUtils.key(number)
                    if (k.length < 7 || k in seen) continue
                    counts[k] = (counts[k] ?: 0) + 1
                    numbers.putIfAbsent(k, number)
                }
            }
        } catch (e: Exception) {
        }
        for ((k, _) in counts.entries.sortedByDescending { it.value }) {
            if (out.size >= MAX_PEOPLE) break
            val number = numbers[k] ?: continue
            val name = PhoneUtils.contactName(this, number) ?: continue // sadece rehberdeki isimli kişiler
            seen.add(k)
            out.add(Person(name, number, false))
        }
        return out
    }

    private fun personCard(person: Person): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER_HORIZONTAL
        card.background = if (person.safe) {
            Ui.cardBackground(this, Ui.CARD, Ui.GREEN, 2)
        } else {
            Ui.cardBackground(this, Ui.CARD, Ui.BORDER, 1)
        }
        val p = Ui.dp(this, 10)
        card.setPadding(p, Ui.dp(this, 14), p, Ui.dp(this, 12))

        val avatar = Ui.avatar(
            this, person.name, PhoneUtils.photoUri(this, person.number), 64,
            if (person.safe) Ui.GREEN_TINT else Ui.BLUE_TINT,
            if (person.safe) Ui.GREEN_DARK else Ui.BLUE_DARK,
        )
        card.addView(avatar)
        val name = Ui.label(this, person.name, 21f, Ui.TEXT, bold = true)
        name.gravity = Gravity.CENTER
        name.maxLines = 2
        card.addView(name)
        if (person.safe) {
            val tag = Ui.label(this, "🛡 Güvenli", 16f, Ui.GREEN)
            tag.setPadding(0, 0, 0, 0)
            card.addView(tag)
        }
        card.isClickable = true
        card.setOnClickListener {
            Ui.ask(this, "Arama", "${person.name} aransın mı?", yes = "Ara") {
                PhoneUtils.placeCall(this, person.number)
            }
        }
        val m = Ui.dp(this, 5)
        card.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(m, m, m, m)
        }
        return card
    }

    // ---------------- Alt menü (her zaman görünür) ----------------

    private fun buildBottomBar() {
        bottomBar.removeAllViews()
        val missed = Prefs(this).missedCount
        bottomBar.addView(Ui.weighted(Ui.button(this, "📖\nRehber", Ui.BLUE, 18f) {
            startActivity(Intent(this, ContactsActivity::class.java))
        }, marginDp = 3))
        val recentsText = if (missed > 0) "🕘 $missed Cevapsız\nSon Aramalar" else "🕘\nSon Aramalar"
        bottomBar.addView(Ui.weighted(Ui.button(this, recentsText, if (missed > 0) Ui.ORANGE else Ui.BLUE, 18f) {
            startActivity(Intent(this, RecentsActivity::class.java))
        }, marginDp = 3))
        bottomBar.addView(Ui.weighted(Ui.button(this, "☎\nTuşla", Ui.BLUE, 18f) {
            startActivity(Intent(this, DialpadActivity::class.java))
        }, marginDp = 3))
    }

    private fun askRelax() {
        val options = listOf(15, 30, 45, 60).map { m ->
            Triple<String, Int, () -> Unit>("$m Dakika", Ui.BLUE) {
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
