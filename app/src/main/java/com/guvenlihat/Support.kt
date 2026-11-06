package com.guvenlihat

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.text.InputType
import android.widget.EditText

/**
 * Testçilerden destek: "Sorun bildir" ve "Geri bildirim gönder".
 * Sunucu yok: telefonun e-posta / WhatsApp uygulaması açılır, kişi göndermeden önce
 * içeriği görür. Olay geçmişindeki telefon numaraları maskelenir.
 */
object Support {

    /** Destek e-posta adresi. Boşsa kişi gönderilecek uygulamayı ve alıcıyı kendisi seçer. */
    const val EMAIL = ""

    val FEEDBACK_QUESTIONS = listOf(
        "1) Kurulumu kim yaptı, yaklaşık kaç dakika sürdü?",
        "2) Kurulum ne kadar kolaydı? (1 = çok zor, 5 = çok kolay)",
        "3) Kurulumda en çok nerede zorlandınız?",
        "4) Korunan kişi uygulamayı rahat kullanabiliyor mu? (1-5) Nerede zorlanıyor?",
        "5) Engellenmesi gerekirken geçen şüpheli bir arama oldu mu? Olduysa anlatın.",
        "6) Yanlışlıkla engellenen önemli bir arama oldu mu? (hastane, kargo, banka vb.)",
        "7) Süre sınırı hiç devreye girdi mi? Korunan kişi bunu nasıl karşıladı?",
        "8) Aileye giden bildirimler zamanında geldi mi?",
        "9) Pil çabuk bitme, uygulamanın kendiliğinden kapanması gibi bir sorun oldu mu?",
        "10) En çok işinize yarayan özellik hangisi?",
        "11) Neyi değiştirmek ya da eklemek isterdiniz?",
        "12) Uygulamayı bir tanıdığınıza önerir misiniz? (0 = asla, 10 = kesinlikle)",
    )

    /** Önce "ne oldu?" diye sorar, sonra rapor hazırlayıp gönderme ekranını açar. */
    fun reportProblem(act: Activity) {
        val box = Ui.column(act, 20)
        box.addView(Ui.label(act, "Ne oldu? Kısaca yazın. Telefon bilgisi ve son olaylar otomatik eklenecek.", 18f))
        val input = EditText(act)
        input.hint = "Örn: Kargo araması engellendi"
        input.textSize = 20f
        input.minLines = 3
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        box.addView(input)
        AlertDialog.Builder(act)
            .setTitle("Sorun bildir")
            .setView(box)
            .setPositiveButton("Devam") { _, _ ->
                val body = "SORUN:\n" + input.text.toString().trim().ifEmpty { "(açıklama yazılmadı)" } +
                    "\n\n" + diagnostics(act)
                share(act, "Güvenli Hat sorun bildirimi", body)
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    fun sendFeedback(act: Activity) {
        val body = "Lütfen her sorunun altına cevabınızı yazın.\n\n" +
            FEEDBACK_QUESTIONS.joinToString("\n\nCevap: \n\n") + "\n\nCevap: \n\n" +
            "--- Otomatik eklenen bilgiler ---\n" + deviceInfo(act)
        share(act, "Güvenli Hat geri bildirim", body)
    }

    private fun share(act: Activity, subject: String, body: String) {
        val prefs = Prefs(act)
        val i = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "$subject (${prefs.whoName})")
            .putExtra(Intent.EXTRA_TEXT, body)
        if (EMAIL.isNotBlank()) i.putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
        try {
            act.startActivity(Intent.createChooser(i, "Nasıl gönderilsin?"))
        } catch (e: Exception) {
            Ui.info(act, "Gönderilemedi", "Telefonda e-posta veya mesaj uygulaması bulunamadı.")
        }
    }

    private fun appVersion(act: Activity): String = try {
        @Suppress("DEPRECATION")
        act.packageManager.getPackageInfo(act.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    private fun deviceInfo(act: Activity): String =
        "Telefon: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "Sistem: ${Build.DISPLAY}\n" +
            "Güvenli Hat: ${appVersion(act)}\n"

    private fun yesNo(b: Boolean) = if (b) "Evet" else "HAYIR"

    /** Telefon, kurulum durumu, ayarlar ve maskelenmiş son olaylar. */
    fun diagnostics(act: Activity): String {
        val prefs = Prefs(act)
        val missing = Setup.missingPermissions(act, prefs)
        val sb = StringBuilder()
        sb.append("--- Telefon ---\n").append(deviceInfo(act))
        sb.append("\n--- Kurulum ---\n")
        sb.append("Varsayılan telefon uygulaması: ${yesNo(Setup.isDefaultDialer(act))}\n")
        sb.append("Eksik izinler: ${if (missing.isEmpty()) "yok" else missing.joinToString { it.substringAfterLast('.') }}\n")
        sb.append("Sessiz engelleme: ${yesNo(Setup.hasScreeningRole(act))}\n")
        sb.append("Pil kısıtlaması kaldırıldı: ${yesNo(Setup.isIgnoringBattery(act))}\n")
        sb.append("\n--- Ayarlar ---\n")
        sb.append("Süre sınırı: ${prefs.limitMinutes} dk (${prefs.limitMode.name})\n")
        sb.append("Kesilen numara engeli: ${prefs.redialBlockMinutes} dk\n")
        sb.append("Bilinmeyen filtre: ${yesNo(prefs.filterUnknown)}\n")
        sb.append("Arama bekliyorum açık: ${yesNo(prefs.relaxActive)}\n")
        sb.append("Bildirim yöntemi: ${prefs.notifyMethod.name}\n")
        sb.append("Güvenli kişi sayısı: ${prefs.safeContacts.size}\n")
        sb.append("Sesli okuma: ${yesNo(prefs.announceCaller)}\n")
        sb.append("\n--- Son olaylar (numaralar gizlendi) ---\n")
        prefs.events().take(40).forEach { sb.append(mask(it)).append('\n') }
        return sb.toString()
    }

    /** 0546 439 40 48 → 0546***48 (kişisel veriyi korumak için). */
    fun mask(text: String): String =
        // Önünde rakam, iki nokta ya da nokta olan eşleşmez (saat "10:39" numaraya karışmasın)
        Regex("(?<![\\d:.])\\+?\\d[\\d ]{6,}\\d").replace(text) { m ->
            val d = m.value.filter { it.isDigit() || it == '+' }
            if (d.length < 7) m.value else d.take(if (d.startsWith("+")) 6 else 4) + "***" + d.takeLast(2)
        }
}
