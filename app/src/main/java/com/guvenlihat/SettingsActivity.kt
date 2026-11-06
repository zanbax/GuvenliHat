package com.guvenlihat

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.Toast

/** PIN korumalı aile ayarları ve kurulum sihirbazı. */
class SettingsActivity : Activity() {

    companion object {
        private const val RC_DIALER = 10
        private const val RC_SCREENING = 11
        private const val RC_PERMS = 12
        private const val RC_PICK = 13
    }

    private lateinit var prefs: Prefs
    private lateinit var root: LinearLayout
    private var unlocked = false
    private var wrongPins = 0
    private var nameField: EditText? = null
    private var tokenField: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        root = Ui.column(this, 16)
        setContentView(Ui.scroll(this, root))
        unlocked = savedInstanceState?.getBoolean("unlocked") == true
        if (!unlocked) showPinGate()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("unlocked", unlocked)
    }

    override fun onResume() {
        super.onResume()
        if (unlocked) build()
    }

    override fun onPause() {
        saveFields()
        CareMonitor.schedule(this)
        super.onPause()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    // ---------------- PIN ----------------

    private fun pinField(hint: String): EditText {
        val e = EditText(this)
        e.hint = hint
        e.textSize = 26f
        e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        return e
    }

    private fun showPinGate() {
        root.removeAllViews()
        if (!prefs.hasPin) {
            root.addView(Ui.label(this, "Aile PIN'i oluşturun", 28f, bold = true))
            root.addView(
                Ui.label(
                    this,
                    "Bu PIN, ayarların korunan kişi tarafından ya da telefonda yönlendiren " +
                        "bir dolandırıcı tarafından değiştirilmesini engeller. Korunan kişiye söylemeyin.",
                    18f, Ui.GRAY,
                ),
            )
            val p1 = pinField("Yeni PIN (en az 4 rakam)")
            val p2 = pinField("PIN tekrar")
            root.addView(p1)
            root.addView(p2)
            root.addView(Ui.button(this, "PIN'i kaydet", Ui.GREEN) {
                val a = p1.text.toString()
                when {
                    a.length < 4 -> toast("PIN en az 4 rakam olmalı")
                    a != p2.text.toString() -> toast("PIN'ler aynı değil")
                    else -> {
                        prefs.setPin(a)
                        unlocked = true
                        build()
                    }
                }
            })
        } else {
            root.addView(Ui.label(this, "Aile PIN'i", 28f, bold = true))
            val p = pinField("PIN")
            root.addView(p)
            root.addView(Ui.button(this, "Giriş", Ui.GREEN) {
                if (prefs.checkPin(p.text.toString())) {
                    unlocked = true
                    Notifier.send(this, "Güvenli Hat ayarları açıldı.", throttleKey = "settings_open", throttleMs = 30 * 60_000L)
                    build()
                } else {
                    wrongPins++
                    p.setText("")
                    toast("Yanlış PIN")
                    if (wrongPins == 3) Notifier.send(this, "Ayarlara 3 kez yanlış PIN girildi.")
                }
            })
        }
        root.addView(Ui.button(this, "Geri", Ui.GRAY, 20f) { finish() })
    }

    // ---------------- Ayarlar ----------------

    private fun saveFields() {
        nameField?.let { prefs.protectedName = it.text.toString() }
        tokenField?.let {
            val t = it.text.toString().trim()
            if (t != prefs.telegramToken) {
                prefs.telegramToken = t
                prefs.telegramChatId = ""
                prefs.telegramChatTitle = ""
            }
        }
    }

    private fun section(title: String) {
        root.addView(Ui.label(this, " ", 10f))
        root.addView(Ui.label(this, title, 24f, Ui.GREEN, bold = true))
    }

    /** Açıklama metni: sola yaslı, satır aralığı biraz açık (daha rahat okunur). */
    private fun note(text: String) {
        val t = Ui.label(this, text, 17f, Ui.SECONDARY)
        t.setLineSpacing(0f, 1.2f)
        t.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8))
        root.addView(t)
    }

    private fun statusRow(title: String, ok: Boolean, buttonText: String, onFix: () -> Unit) {
        root.addView(Ui.label(this, (if (ok) "✓ " else "✗ ") + title, 20f, if (ok) Ui.GREEN else Ui.RED, bold = true))
        if (!ok) root.addView(Ui.button(this, buttonText, Ui.BLUE, 20f, onFix))
    }

    private fun smallButton(text: String, color: Int = Ui.BLUE, onClick: () -> Unit) =
        root.addView(Ui.button(this, text, color, 18f, onClick))

    private fun <T> choice(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
        val group = RadioGroup(this)
        for ((value, text) in options) {
            val rb = RadioButton(this)
            rb.id = View.generateViewId()
            rb.text = text
            rb.textSize = 20f
            rb.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6))
            group.addView(rb)
            if (value == selected) group.check(rb.id)
            rb.setOnClickListener { onPick(value) }
        }
        root.addView(group)
    }

    private fun build() {
        saveFields()
        nameField = null
        tokenField = null
        root.removeAllViews()
        if (Setup.isDefaultDialer(this)) prefs.wasDefaultDialer = true

        root.addView(Ui.label(this, "Aile Ayarları", 30f, bold = true))

        // 1. Kurulum
        section("1. Kurulum")
        val needsRestricted = !Setup.isDefaultDialer(this) ||
            (prefs.notifyMethod.usesSms && !PhoneUtils.has(this, Manifest.permission.SEND_SMS))
        if (Build.VERSION.SDK_INT >= 33 && needsRestricted) {
            root.addView(
                Ui.label(
                    this,
                    "\"Erişim reddedildi\" uyarısı çıkarsa: Uygulama bilgisi sayfasında sağ üstteki ⋮ menüsünden " +
                        "\"Kısıtlanmış ayarlara izin ver\"i seçin, sonra buraya dönüp tekrar deneyin.",
                    18f, Ui.RED,
                ),
            )
            smallButton("Uygulama bilgisini aç", Ui.GRAY) { Setup.openAppDetails(this) }
        }
        statusRow("Varsayılan telefon uygulaması", Setup.isDefaultDialer(this), "Güvenli Hat'ı varsayılan yap") {
            Setup.requestDefaultDialer(this, RC_DIALER)
        }
        if (!Setup.isDefaultDialer(this)) {
            smallButton("Çıkmazsa: Varsayılan uygulamalar ayarını aç", Ui.GRAY) { Setup.openDefaultApps(this) }
        }
        val missing = Setup.missingPermissions(this, prefs)
        statusRow("İzinler", missing.isEmpty(), "İzinleri ver") {
            requestPermissions(missing.toTypedArray(), RC_PERMS)
        }
        if (missing.isNotEmpty()) {
            smallButton("İzin penceresi çıkmazsa: uygulama ayarlarını aç", Ui.GRAY) { Setup.openAppDetails(this) }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            statusRow("Sessiz engelleme (çalmadan reddetme)", Setup.hasScreeningRole(this), "Aç") {
                Setup.requestScreeningRole(this, RC_SCREENING)
            }
        }
        statusRow("Pil kısıtlaması kaldırıldı", Setup.isIgnoringBattery(this), "Kısıtlamayı kaldır") {
            Setup.requestIgnoreBattery(this)
        }
        if (Setup.isXiaomi()) {
            note("Xiaomi telefonlarda şu iki ayar da mutlaka açılmalı (durumları buradan görülemiyor):")
            smallButton("Otomatik başlatmayı aç") { Setup.openXiaomiAutostart(this) }
            smallButton("Diğer izinler: 'Kilit ekranında göster' ve 'Arka planda açılır pencere'") {
                Setup.openXiaomiPermissions(this)
            }
        }
        note("Cevapsız arama sayısının Güvenli Hat simgesinde görünmesi için bildirim ayarlarında 'Uygulama simgesi işaretleri' (rozet) açık olmalı.")
        smallButton("Bildirim ayarlarını aç") { Setup.openNotificationSettings(this) }
        note(
            "WhatsApp aramaları: WhatsApp > Ayarlar > Gizlilik > Aramalar > " +
                "'Bilinmeyen arayanları sessize al' seçeneğini açın.",
        )
        smallButton("WhatsApp'ı aç") { if (!Setup.openWhatsApp(this)) toast("WhatsApp bulunamadı") }
        note("Ana ekrandaki eski 'Telefon' simgesini kaldırıp yerine Güvenli Hat simgesini koyun.")

        // 2. Korunan kişi
        section("2. Korunan kişi")
        note("Bildirimlerde görünecek ad (ör. Annem):")
        val nf = EditText(this)
        nf.setText(prefs.protectedName)
        nf.hint = "Annem"
        nf.textSize = 22f
        nf.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        nameField = nf
        root.addView(nf)

        // 3. Güvenli kişiler
        section("3. Güvenli kişiler")
        note("Uzun görüşme kesildiğinde bu kişiler sırayla aranır. SMS bildirimi de bu kişilere gider. Aramaları asla kesilmez.")
        val safe = prefs.safeContacts
        if (safe.isEmpty()) root.addView(Ui.label(this, "Henüz kimse eklenmedi.", 20f, Ui.RED))
        safe.forEachIndexed { i, c ->
            val row = Ui.row(this)
            row.addView(Ui.weighted(Ui.label(this, "${i + 1}. ${c.name}\n${c.number}", 20f), weight = 3f))
            row.addView(Ui.weighted(Ui.button(this, "Sil", Ui.RED, 18f) {
                prefs.safeContacts = prefs.safeContacts.filterIndexed { j, _ -> j != i }
                build()
            }))
            root.addView(row)
        }
        if (safe.size < 3) {
            smallButton("Rehberden ekle") {
                startActivityForResult(
                    Intent(this, ContactsActivity::class.java).putExtra(ContactsActivity.EXTRA_PICK, true), RC_PICK,
                )
            }
            smallButton("Numara yazarak ekle") { addSafeManually() }
        }

        // 4. Süre sınırı
        section("4. Görüşme süre sınırı")
        note("Süre dolunca görüşme kesilir, güvenli kişiler aranır ve aileye haber verilir.")
        choice(
            listOf(
                0 to "Kapalı", 1 to "1 dakika (sadece deneme için)", 5 to "5 dakika", 10 to "10 dakika",
                15 to "15 dakika", 20 to "20 dakika", 30 to "30 dakika",
            ),
            prefs.limitMinutes,
        ) { prefs.limitMinutes = it }
        root.addView(Ui.label(this, "Kimlere uygulansın?", 20f, bold = true))
        choice(
            listOf(
                LimitMode.UNKNOWN_ONLY to "Sadece rehberde olmayan numaralar (önerilen)",
                LimitMode.ALL_EXCEPT_SAFE to "Güvenli kişiler dışında herkes",
            ),
            prefs.limitMode,
        ) { prefs.limitMode = it }
        root.addView(Ui.label(this, "Kesilen numara ne kadar süre engellensin?", 20f, bold = true))
        choice(
            listOf(
                1 to "1 dakika (deneme için)", 5 to "5 dakika", 10 to "10 dakika", 15 to "15 dakika",
                30 to "30 dakika", 60 to "1 saat (önerilen)", 180 to "3 saat", 1440 to "1 gün",
            ),
            prefs.redialBlockMinutes,
        ) { prefs.redialBlockMinutes = it }
        note("Dolandırıcılar kesilen hattı genelde hemen geri arar; kısa süreler bu yüzden daha az korur.")

        // 5. Filtre
        section("5. Bilinmeyen arama filtresi")
        @Suppress("DEPRECATION")
        val sw = Switch(this)
        sw.text = "Rehberde olmayan ve gizli numaralar çalmasın"
        sw.textSize = 20f
        sw.isChecked = prefs.filterUnknown
        sw.setOnCheckedChangeListener { _, checked -> prefs.filterUnknown = checked }
        root.addView(sw)
        note("Engellenen arama telefonda çalmaz, aileye bildirilir. 112 vb. arandıktan sonra 1 saat filtre kendiliğinden gevşer (geri arama için).")

        // Sesli okuma
        section("Sesli okuma")
        @Suppress("DEPRECATION")
        val tts = Switch(this)
        tts.text = "Gelen aramada arayanın adını sesli söyle (\"Ayşe arıyor\")"
        tts.textSize = 20f
        tts.isChecked = prefs.announceCaller
        tts.setOnCheckedChangeListener { _, checked ->
            prefs.announceCaller = checked
            if (checked) CallerAnnouncer.init(this)
        }
        root.addView(tts)
        note("Telefon sessizdeyken okumaz. Ses, zil sesi seviyesinde çıkar.")
        smallButton("Sesi dene") { testVoice() }

        // 6. Bildirim
        section("6. Aileye bildirim")
        choice(
            listOf(
                NotifyMethod.NONE to "Kapalı",
                NotifyMethod.SMS to "SMS (güvenli kişilere)",
                NotifyMethod.TELEGRAM to "Telegram (aile grubuna)",
                NotifyMethod.TELEGRAM_SMS to "Telegram, internet yoksa SMS (önerilen)",
            ),
            prefs.notifyMethod,
        ) {
            prefs.notifyMethod = it
            root.post { build() }
        }
        val method = prefs.notifyMethod
        if (method == NotifyMethod.TELEGRAM_SMS) {
            note("Mesaj önce internetten Telegram'a gönderilir. İnternet kapalıysa ya da gitmezse aynı mesaj SMS ile güvenli kişilere gider.")
        }
        if (method.usesSms) {
            note(
                "SMS'ler korunan kişinin hattından gönderilir. Paketinde SMS yoksa operatör " +
                    "ücret alabilir. Aynı numara için 10 dakikada bir mesaj gider.",
            )
            if (!PhoneUtils.has(this, Manifest.permission.SEND_SMS)) {
                smallButton("SMS iznini ver") { requestPermissions(arrayOf(Manifest.permission.SEND_SMS), RC_PERMS) }
            }
        }
        if (method.usesTelegram) {
            note(
                "1) Telegram'da @BotFather'a yazın, /newbot ile bir bot oluşturun ve verdiği token'ı aşağıya yapıştırın.\n" +
                    "2) Bir aile grubu kurun, botu gruba ekleyin ve grupta /start yazın.\n" +
                    "3) 'Aile grubunu bul' düğmesine basın.",
            )
            val tf = EditText(this)
            tf.setText(prefs.telegramToken)
            tf.hint = "Bot token (123456:ABC...)"
            tf.textSize = 18f
            tf.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            tokenField = tf
            root.addView(tf)
            val chat = prefs.telegramChatTitle.ifBlank { prefs.telegramChatId }
            root.addView(
                Ui.label(
                    this, if (chat.isBlank()) "Grup seçilmedi" else "Seçili: $chat", 20f,
                    if (chat.isBlank()) Ui.RED else Ui.GREEN, bold = true,
                ),
            )
            smallButton("Aile grubunu bul") { findTelegramChats() }
        }
        if (prefs.notifyMethod != NotifyMethod.NONE) {
            smallButton("Deneme mesajı gönder", Ui.GREEN) { sendTest() }
        }

        // Güvenlik uyarıları
        section("Yardım düğmesi ve uyarılar")
        fun switchRow(text: String, value: Boolean, onChange: (Boolean) -> Unit) {
            @Suppress("DEPRECATION")
            val sw = Switch(this)
            sw.text = text
            sw.textSize = 20f
            sw.isChecked = value
            sw.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6))
            sw.setOnCheckedChangeListener { _, c -> onChange(c) }
            root.addView(sw)
        }
        switchRow("Ana ekranda Yardım düğmesi", prefs.helpButtonEnabled) { prefs.helpButtonEnabled = it }
        note("3 saniye basılı tutulunca güvenli kişiler sırayla aranır, hepsine konumlu \"Yardım istedi\" mesajı gider. Yanlışlıkla basılırsa ana ekrandan iptal edilebilir.")
        switchRow("Düşük pil uyarısı (%15)", prefs.lowBatteryAlert) {
            prefs.lowBatteryAlert = it
            CareMonitor.schedule(this)
        }
        switchRow("Telefon yeniden açılınca haber ver", prefs.bootAlert) { prefs.bootAlert = it }

        // Bakım: hareketsizlik ve konum (isteğe bağlı, varsayılanda kapalı)
        section("Bakım: hareketsizlik ve konum")
        note("İsteğe bağlıdır. Açmadan önce korunan kişiye anlatın ve onayını alın.")

        root.addView(Ui.label(this, "Hareketsizlik uyarısı", 20f, bold = true))
        note("Telefon bu süre boyunca hiç kullanılmazsa (ekran açılmazsa, arama yapılmazsa) güvenli kişilere son konum ve pil bilgisiyle haber gider. Uyku saatleri sayılmaz, gece kimse uyandırılmaz.")
        choice(
            listOf(0 to "Kapalı", 1 to "1 saat (sadece deneme için)", 6 to "6 saat", 12 to "12 saat", 24 to "24 saat"),
            prefs.inactivityHours,
        ) {
            if (prefs.inactivityHours == 0 && it > 0) {
                CareMonitor.markActive(this)
                prefs.inactivityAlerted = false
            }
            prefs.inactivityHours = it
            CareMonitor.schedule(this)
            root.post { build() }
        }
        if (prefs.inactivityHours > 0) {
            smallButton("Uyku saatleri: ${hourRange(prefs.sleepStart, prefs.sleepEnd, "yok")}  (değiştir)", Ui.GRAY) {
                pickHours("Uyku saatleri", listOf(21 to 7, 22 to 7, 22 to 8, 23 to 8, 23 to 9), "Uyku saati yok") { st, en ->
                    prefs.sleepStart = st
                    prefs.sleepEnd = en
                    build()
                }
            }
            statusRow("Telefon kullanımını görme izni", CareMonitor.hasUsageAccess(this), "İzin ver (Kullanım erişimi)") {
                Setup.openUsageAccess(this)
            }
            if (!CareMonitor.hasUsageAccess(this)) {
                note("Açılan listede Güvenli Hat'ı bulup izni açın. Bu izin olmadan sadece aramalar ve Güvenli Hat'ın açılması \"kullanım\" sayılır.")
            }
        }

        root.addView(Ui.label(this, "Aralıklı konum paylaşımı", 20f, bold = true))
        @Suppress("DEPRECATION")
        val loc = Switch(this)
        loc.text = "Konumu belirli aralıklarla güvenli kişilere gönder"
        loc.textSize = 20f
        loc.isChecked = prefs.locationEnabled
        loc.setOnCheckedChangeListener { _, checked ->
            prefs.locationEnabled = checked
            if (checked) prefs.lastLocationSentAt = 0L
            CareMonitor.schedule(this)
            if (checked && !CareMonitor.hasLocationPermission(this)) {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), RC_PERMS,
                )
            }
            root.post { build() }
        }
        root.addView(loc)
        if (prefs.locationEnabled) {
            note("Seçilen saatler arasında konum (harita linki) ve pil seviyesi gönderilir. Her gönderimde korunan kişinin telefonunda sessiz bir \"Konumunuz yakınlarınıza gönderildi\" bildirimi çıkar.")
            root.addView(Ui.label(this, "Ne zaman gönderilsin?", 18f, bold = true))
            choice(
                listOf(false to "Belirli aralıklarla (örn. 2 saatte bir)", true to "Belirli saatlerde (örn. 14:00 ve 17:00)"),
                prefs.locationAtTimes,
            ) {
                prefs.locationAtTimes = it
                CareMonitor.schedule(this)
                root.post { build() }
            }
            if (prefs.locationAtTimes) {
                val times = prefs.locationTimes
                val label = if (times.isEmpty()) "seçilmedi" else times.joinToString(", ") { String.format(java.util.Locale.US, "%02d:00", it) }
                smallButton("Saatler: $label  (değiştir)", Ui.GRAY) { pickTimes() }
                note("Konum seçilen saatten sonraki birkaç dakika içinde gönderilir (en geç yarım saat).")
            } else {
                choice(
                    listOf(1 to "Saatte bir", 2 to "2 saatte bir", 3 to "3 saatte bir", 6 to "6 saatte bir"),
                    prefs.locationIntervalHours,
                ) { prefs.locationIntervalHours = it }
                smallButton("Gönderim saatleri: ${hourRange(prefs.locationStart, prefs.locationEnd, "bütün gün")}  (değiştir)", Ui.GRAY) {
                    pickHours("Gönderim saatleri", listOf(8 to 20, 9 to 21, 7 to 22), "Bütün gün") { st, en ->
                        prefs.locationStart = st
                        prefs.locationEnd = en
                        build()
                    }
                }
            }
            statusRow("Konum izni", CareMonitor.hasLocationPermission(this), "İzin ver") {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), RC_PERMS,
                )
            }
            if (CareMonitor.hasLocationPermission(this)) {
                statusRow("Konuma arka planda erişim (\"Her zaman izin ver\")", CareMonitor.hasBackgroundLocation(this), "Her zaman izin ver") {
                    requestBackgroundLocation()
                }
            }
            smallButton("Konumu şimdi gönder (deneme)", Ui.GREEN) {
                CareMonitor.sendLocationNow(this)
                toast("Konum alınıyor ve gönderiliyor...")
            }
        }

        // Destek (deneme sürecinde testçiler için)
        section("Destek")
        note("Bir sorun olursa ya da görüşünüzü iletmek isterseniz: telefon bilgisi ve son olaylar otomatik eklenir, telefon numaraları gizlenir. Göndermeden önce içeriği görebilirsiniz. Destek adresi: " + Support.EMAIL)
        smallButton("Sorun bildir", Ui.ORANGE) { Support.reportProblem(this) }
        smallButton("Geri bildirim gönder (anket)") { Support.sendFeedback(this) }

        // 7. PIN
        section("7. PIN")
        smallButton("PIN'i değiştir") { changePin() }

        // 8. Olaylar
        section("8. Son olaylar")
        val events = prefs.events()
        if (events.isEmpty()) note("Henüz olay yok.")
        events.take(30).forEach { root.addView(Ui.label(this, it, 15f)) }

        root.addView(Ui.label(this, " ", 10f))
        root.addView(Ui.button(this, "Kaydet ve çık", Ui.GREEN) {
            saveFields()
            finish()
        })
    }

    private fun hourRange(start: Int, end: Int, none: String): String =
        if (start == end) none else String.format(java.util.Locale.US, "%02d:00 – %02d:00", start, end)

    private fun pickHours(title: String, options: List<Pair<Int, Int>>, noneLabel: String, onPick: (Int, Int) -> Unit) {
        val items = options.map { (st, en) ->
            Triple<String, Int, () -> Unit>(hourRange(st, en, noneLabel), Ui.BLUE) { onPick(st, en) }
        } + Triple<String, Int, () -> Unit>(noneLabel, Ui.GRAY) { onPick(0, 0) }
        Ui.actions(this, title, items)
    }

    /** Konumun gönderileceği saatleri çoklu seçimle belirle (06:00–23:00). */
    private fun pickTimes() {
        val hours = (6..23).toList()
        val labels = hours.map { String.format(java.util.Locale.US, "%02d:00", it) }.toTypedArray()
        val current = prefs.locationTimes
        val checked = BooleanArray(hours.size) { hours[it] in current }
        AlertDialog.Builder(this)
            .setTitle("Hangi saatlerde gönderilsin?")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Kaydet") { _, _ ->
                prefs.locationTimes = hours.filterIndexed { i, _ -> checked[i] }
                CareMonitor.schedule(this)
                build()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT < 29) return
        if (Build.VERSION.SDK_INT >= 30) {
            toast("Açılan ekranda \"Her zaman izin ver\" seçeneğini seçin")
        }
        try {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), RC_PERMS)
        } catch (e: Exception) {
            Setup.openAppDetails(this)
        }
    }

    private fun addSafeManually() {
        val box = Ui.column(this, 20)
        val name = EditText(this)
        name.hint = "Ad (ör. Ayşe)"
        name.textSize = 22f
        val num = EditText(this)
        num.hint = "Numara (05xx...)"
        num.textSize = 22f
        num.inputType = InputType.TYPE_CLASS_PHONE
        box.addView(name)
        box.addView(num)
        AlertDialog.Builder(this)
            .setTitle("Güvenli kişi ekle")
            .setView(box)
            .setPositiveButton("Ekle") { _, _ ->
                val n = name.text.toString().trim()
                val p = num.text.toString().trim()
                if (n.isEmpty() || PhoneUtils.key(p).length < 7) {
                    toast("Ad ve numara gerekli")
                } else {
                    prefs.safeContacts = prefs.safeContacts + SafeContact(n, p)
                    build()
                }
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun changePin() {
        val box = Ui.column(this, 20)
        val p = pinField("Yeni PIN (en az 4 rakam)")
        box.addView(p)
        AlertDialog.Builder(this)
            .setTitle("PIN'i değiştir")
            .setView(box)
            .setPositiveButton("Kaydet") { _, _ ->
                val v = p.text.toString()
                if (v.length < 4) toast("PIN en az 4 rakam olmalı") else {
                    prefs.setPin(v)
                    toast("PIN değişti")
                }
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun findTelegramChats() {
        saveFields()
        val token = prefs.telegramToken
        if (token.isBlank()) {
            toast("Önce bot token'ını girin")
            return
        }
        toast("Aranıyor...")
        Thread {
            val result = runCatching { Notifier.findTelegramChats(token) }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                result.onSuccess { chats ->
                    if (chats.isEmpty()) {
                        Ui.info(
                            this, "Grup bulunamadı",
                            "Botu gruba ekleyip grupta /start yazın, sonra tekrar deneyin.",
                        )
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("Bildirimler nereye gitsin?")
                            .setItems(chats.map { it.second }.toTypedArray()) { _, i ->
                                prefs.telegramChatId = chats[i].first
                                prefs.telegramChatTitle = chats[i].second
                                build()
                            }
                            .show()
                    }
                }
                result.onFailure { Ui.info(this, "Hata", it.message ?: "Bağlantı hatası") }
            }
        }.start()
    }

    private fun testVoice() {
        CallerAnnouncer.say(this, "Ayşe arıyor")
        // Motor hazırlanırken biraz bekleyip Türkçe ses olup olmadığına bak
        root.postDelayed({
            if (CallerAnnouncer.turkishReady == false) {
                Ui.info(
                    this, "Türkçe ses bulunamadı",
                    "Telefonda Türkçe metin okuma sesi yok. Play Store'dan \"Google Konuşma Hizmetleri\" " +
                        "uygulamasını yükleyin veya güncelleyin, sonra tekrar deneyin.",
                )
            }
        }, 2500)
    }

    private fun sendTest() {
        saveFields()
        toast("Gönderiliyor...")
        val app = applicationContext
        Thread {
            val err = Notifier.sendNow(app, "Deneme mesajı. Bildirimler çalışıyor ✓")
            runOnUiThread { toast(err ?: "Gönderildi ✓") }
        }.start()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_PICK && resultCode == RESULT_OK && data != null) {
            val n = data.getStringExtra(ContactsActivity.RESULT_NAME) ?: return
            val p = data.getStringExtra(ContactsActivity.RESULT_NUMBER) ?: return
            if (!PhoneUtils.isSafe(prefs, p) && prefs.safeContacts.size < 3) {
                prefs.safeContacts = prefs.safeContacts + SafeContact(n, p)
            }
        }
        // build() onResume'da yeniden çalışır
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (unlocked) build()
    }
}
