package com.guvenlihat

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Büyük, okunaklı, yaşlı dostu arayüz parçaları (XML yerine kodla). */
object Ui {
    val GREEN = Color.parseColor("#2E7D32")
    val RED = Color.parseColor("#C62828")
    val BLUE = Color.parseColor("#1565C0")
    val GRAY = Color.parseColor("#616161")
    val KEY = Color.parseColor("#37474F")
    val YELLOW = Color.parseColor("#FFD54F")
    val DARK = Color.parseColor("#0D1B2A")
    val LIGHT = Color.parseColor("#B0BEC5")
    val TEXT = Color.parseColor("#212121")

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun background(color: Int, radius: Float): Drawable {
        val shape = GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
        return RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), shape, null)
    }

    fun button(ctx: Context, label: String, color: Int, size: Float = 24f, onClick: () -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.textSize = size
        b.setTextColor(Color.WHITE)
        b.isAllCaps = false
        b.background = background(color, dp(ctx, 16).toFloat())
        b.minHeight = dp(ctx, 72)
        b.stateListAnimator = null
        b.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12))
        b.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, dp(ctx, 6), 0, dp(ctx, 6)) }
        b.setOnClickListener { onClick() }
        return b
    }

    fun label(
        ctx: Context, s: String, size: Float = 20f, color: Int = TEXT, bold: Boolean = false,
    ): TextView {
        val t = TextView(ctx)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        t.setPadding(0, dp(ctx, 4), 0, dp(ctx, 4))
        return t
    }

    fun column(ctx: Context, padDp: Int = 16): LinearLayout {
        val l = LinearLayout(ctx)
        l.orientation = LinearLayout.VERTICAL
        val p = dp(ctx, padDp)
        l.setPadding(p, p, p, p)
        return l
    }

    fun row(ctx: Context): LinearLayout {
        val l = LinearLayout(ctx)
        l.orientation = LinearLayout.HORIZONTAL
        l.gravity = Gravity.CENTER_VERTICAL
        l.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        return l
    }

    /** Satır içinde eşit genişlik. */
    fun <T : View> weighted(v: T, weight: Float = 1f, marginDp: Int = 4): T {
        val m = dp(v.context, marginDp)
        v.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight).apply {
            setMargins(m, m, m, m)
        }
        return v
    }

    fun scroll(ctx: Context, child: View): ScrollView {
        val s = ScrollView(ctx)
        s.isFillViewport = true
        s.addView(child)
        return s
    }

    /** Büyük yazılı Evet/Hayır penceresi. */
    fun ask(
        act: Activity, title: String, message: String, yes: String, no: String = "Vazgeç", onYes: () -> Unit,
    ) {
        val body = label(act, message, 22f)
        val p = dp(act, 20)
        body.setPadding(p, p, p, p)
        val dialog = AlertDialog.Builder(act)
            .setTitle(title)
            .setView(body)
            .setPositiveButton(yes) { _, _ -> onYes() }
            .setNegativeButton(no, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.textSize = 22f
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.textSize = 22f
    }

    /** Büyük düğmeli seçenek penceresi (Ara / Düzenle / Sil gibi). */
    fun actions(act: Activity, title: String, items: List<Triple<String, Int, () -> Unit>>) {
        val box = column(act, 16)
        box.addView(label(act, title, 24f, TEXT, bold = true))
        val dialog = AlertDialog.Builder(act).setView(scroll(act, box)).create()
        for ((text, color, action) in items) {
            box.addView(button(act, text, color, 24f) {
                dialog.dismiss()
                action()
            })
        }
        box.addView(button(act, "Vazgeç", GRAY, 20f) { dialog.dismiss() })
        dialog.show()
    }

    fun info(act: Activity, title: String, message: String) {
        val body = label(act, message, 22f)
        val p = dp(act, 20)
        body.setPadding(p, p, p, p)
        val dialog = AlertDialog.Builder(act).setTitle(title).setView(body).setPositiveButton("Tamam", null).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.textSize = 22f
    }
}

/** Yaşlı kişinin başlattığı her arama buradan geçer: engel ve "rehberde yok" uyarısı. */
object CallFlow {
    fun start(act: Activity, rawNumber: String) {
        val number = rawNumber.trim()
        if (number.isEmpty()) return
        val prefs = Prefs(act)
        val emergency = PhoneUtils.isEmergency(number)

        if (!emergency && prefs.redialBlockedUntil(PhoneUtils.key(number)) > 0) {
            Ui.info(
                act, "Aranamaz",
                "Bu numarayla görüşmeniz güvenliğiniz için kesildi. Bir süre aranamaz.\n\nLütfen önce bir yakınınızı arayın.",
            )
            return
        }
        if (emergency) {
            PhoneUtils.placeCall(act, number)
            return
        }
        val name = PhoneUtils.contactName(act, number) ?: PhoneUtils.safeName(prefs, number)
        if (name == null && PhoneUtils.isKnown(act, number) == false) {
            Ui.ask(
                act, "Dikkat!",
                "$number rehberinizde kayıtlı değil.\n\n" +
                    "Tanımadığınız kişiler sizden para, şifre veya kod isteyebilir. " +
                    "Böyle bir şey istenirse hemen kapatın.\n\nYine de aransın mı?",
                yes = "Ara",
            ) { PhoneUtils.placeCall(act, number) }
        } else {
            Ui.ask(act, "Arama", "${name ?: number} aransın mı?", yes = "Ara") {
                PhoneUtils.placeCall(act, number)
            }
        }
    }
}
