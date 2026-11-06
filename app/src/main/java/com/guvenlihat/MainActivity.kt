package com.guvenlihat

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Build
import android.view.MotionEvent
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
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
        CareMonitor.markActive(this)
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

        // Yardım düğmesi ve "yanlışlıkla bastım" seçeneği
        if (prefs.helpButtonEnabled) {
            val since = System.currentTimeMillis() - prefs.helpSentAt
            if (since in 0 until 10 * 60_000L) {
                val box = Ui.label(this, "🆘 Yardım çağrısı gönderildi.\nYakınlarınıza haber verildi.", 20f, Ui.RED_DARK, bold = true)
                val p = Ui.dp(this, 14)
                box.setPadding(p, p, p, p)
                box.background = Ui.cardBackground(this, Ui.RED_TINT, Ui.RED, 2)
                content.addView(box)
                content.addView(Ui.button(this, "Yanlışlıkla bastım, sorun yok", Ui.GRAY, 20f) { cancelHelp() })
            } else {
                content.addView(helpButton())
            }
        }

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

    // ---------------- Yardım (SOS) ----------------

    /**
     * Sevimli ama dikkat çekici kırmızı kart: beyaz dairede kalp, büyük "Yardım" yazısı.
     * Yanlışlıkla basılmasın diye 3 saniye basılı tutulur; bu sırada kart soldan sağa dolar.
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun helpButton(): View {
        val frame = FrameLayout(this)
        val m = Ui.dp(this, 2)
        frame.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 96)).apply {
            setMargins(0, Ui.dp(this@MainActivity, 6), 0, Ui.dp(this@MainActivity, 6))
        }
        frame.background = GradientDrawable().apply {
            setColor(Ui.RED)
            cornerRadius = Ui.dp(this@MainActivity, 20).toFloat()
        }
        frame.clipToOutline = true

        // Basılı tutarken dolan koyu katman
        val fill = View(this)
        fill.setBackgroundColor(Ui.RED_DARK)
        frame.addView(fill, FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val p = Ui.dp(this, 16)
        row.setPadding(p, 0, p, 0)

        val circle = FrameLayout(this)
        circle.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        val heart = ImageView(this)
        heart.setImageResource(R.drawable.ic_heart)
        heart.setColorFilter(Ui.RED)
        val hs = Ui.dp(this, 34)
        circle.addView(heart, FrameLayout.LayoutParams(hs, hs, Gravity.CENTER))
        val cs = Ui.dp(this, 62)
        row.addView(circle, LinearLayout.LayoutParams(cs, cs).apply { rightMargin = Ui.dp(this@MainActivity, 16) })

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        val title = Ui.label(this, "Yardım", 30f, Color.WHITE, bold = true)
        title.setPadding(0, 0, 0, 0)
        val hint = Ui.label(this, "3 saniye basılı tutun", 17f, Color.WHITE)
        hint.setPadding(0, 0, 0, 0)
        texts.addView(title)
        texts.addView(hint)
        row.addView(texts)
        frame.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        var animator: ValueAnimator? = null
        frame.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    animator?.cancel()
                    animator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 3000
                        addUpdateListener { a ->
                            val f = a.animatedValue as Float
                            fill.layoutParams = fill.layoutParams.apply { width = (v.width * f).toInt() }
                            fill.requestLayout()
                            val left = 3 - (f * 3).toInt()
                            hint.text = if (left > 0) "Bırakmayın... $left" else "Gönderiliyor"
                        }
                        addListener(object : AnimatorListenerAdapter() {
                            private var cancelled = false
                            override fun onAnimationCancel(animation: Animator) {
                                cancelled = true
                            }
                            override fun onAnimationEnd(animation: Animator) {
                                if (!cancelled) triggerHelp()
                            }
                        })
                        start()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val done = animator?.isRunning == false
                    animator?.cancel()
                    if (!done) {
                        fill.layoutParams = fill.layoutParams.apply { width = 0 }
                        fill.requestLayout()
                        hint.text = "3 saniye basılı tutun"
                    }
                    true
                }
                else -> true
            }
        }
        frame.contentDescription = "Yardım düğmesi. Yardım çağırmak için 3 saniye basılı tutun."
        return frame
    }

    private fun triggerHelp() {
        val prefs = Prefs(this)
        prefs.helpSentAt = System.currentTimeMillis()
        try {
            @Suppress("DEPRECATION")
            val vib = getSystemService(android.os.Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                vib?.vibrate(android.os.VibrationEffect.createOneShot(400, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vib?.vibrate(400)
            }
        } catch (e: Exception) {
        }
        val first = prefs.safeContacts.firstOrNull()
        Notifier.send(
            this,
            "🆘 YARDIM İSTEDİ! Telefonundaki Yardım düğmesine bastı." +
                (if (first != null) " ${first.name} aranıyor." else "") + " Konum birkaç saniye içinde gelecek.",
        )
        val app = applicationContext
        CareMonitor.getLocation(app) { loc ->
            Notifier.send(app, "🆘 Yardım isteyenin konumu: " + CareMonitor.locationText(loc) + " " + CareMonitor.batteryText(app))
        }
        CallManager.startHelp(this)
        build()
    }

    private fun cancelHelp() {
        val prefs = Prefs(this)
        prefs.helpSentAt = 0L
        CallManager.cancelHelp()
        Notifier.send(this, "✓ Yardım düğmesine yanlışlıkla basılmış, sorun yok.")
        build()
    }

    // ---------------- Alt menü (her zaman görünür) ----------------

    private fun buildBottomBar() {
        bottomBar.removeAllViews()
        val missed = Prefs(this).missedCount
        // Her düğmenin kendi yumuşak rengi var: yaşlılar rengiyle de hatırlasın
        bottomBar.addView(
            navTile(R.drawable.ic_nav_contacts, "Rehber", Ui.BLUE_TINT, Ui.BLUE, 0) {
                startActivity(Intent(this, ContactsActivity::class.java))
            },
        )
        bottomBar.addView(
            navTile(R.drawable.ic_nav_history, "Aramalar", Ui.PURPLE_TINT, Ui.PURPLE, missed) {
                startActivity(Intent(this, RecentsActivity::class.java))
            },
        )
        bottomBar.addView(
            navTile(R.drawable.ic_nav_dialpad, "Tuşla", Ui.AMBER_TINT, Ui.AMBER, 0) {
                startActivity(Intent(this, DialpadActivity::class.java))
            },
        )
    }

    /** Alt menü kartı: renkli daire içinde ikon, altında kısa ad, gerekirse köşede sayı rozeti. */
    private fun navTile(icon: Int, label: String, tint: Int, color: Int, badge: Int, onClick: () -> Unit): View {
        val frame = FrameLayout(this)
        val m = Ui.dp(this, 4)
        frame.layoutParams = LinearLayout.LayoutParams(0, Ui.dp(this, 104), 1f).apply { setMargins(m, m, m, m) }
        frame.background = if (badge > 0) {
            Ui.cardBackground(this, Ui.CARD, Ui.ORANGE, 2)
        } else {
            Ui.cardBackground(this, Ui.CARD, Ui.BORDER, 1)
        }
        frame.isClickable = true
        frame.setOnClickListener { onClick() }

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER

        val circle = FrameLayout(this)
        circle.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(tint)
        }
        val circleSize = Ui.dp(this, 54)
        val iv = ImageView(this)
        iv.setImageResource(icon)
        iv.setColorFilter(color)
        val iconSize = Ui.dp(this, 32)
        circle.addView(iv, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
        col.addView(circle, LinearLayout.LayoutParams(circleSize, circleSize))

        val text = Ui.label(this, label, 18f, Ui.TEXT, bold = true)
        text.setPadding(0, Ui.dp(this, 4), 0, 0)
        text.gravity = Gravity.CENTER
        text.maxLines = 1
        col.addView(text)
        frame.addView(
            col,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER,
            ),
        )

        if (badge > 0) {
            val b = Ui.label(this, if (badge > 9) "9+" else badge.toString(), 15f, Color.WHITE, bold = true)
            b.gravity = Gravity.CENTER
            b.setPadding(0, 0, 0, 0)
            b.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Ui.ORANGE)
            }
            val bs = Ui.dp(this, 28)
            frame.addView(
                b,
                FrameLayout.LayoutParams(bs, bs, Gravity.TOP or Gravity.END).apply {
                    setMargins(0, Ui.dp(this@MainActivity, 6), Ui.dp(this@MainActivity, 6), 0)
                },
            )
        }
        return frame
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
