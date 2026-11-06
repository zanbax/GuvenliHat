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
import android.graphics.Outline
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/** Büyük, okunaklı, yaşlı dostu arayüz parçaları (XML yerine kodla). */
object Ui {
    // Yumuşak ama yüksek kontrastlı palet (beyaz yazı / zemin üstü yazı hepsi 4.5:1 üstü)
    val GREEN = Color.parseColor("#2F7D57")
    val RED = Color.parseColor("#B23B3B")
    val BLUE = Color.parseColor("#3B6EA5")
    val GRAY = Color.parseColor("#6B7378")
    val KEY = Color.parseColor("#5C6670")
    val ORANGE = Color.parseColor("#A85400")
    val YELLOW = Color.parseColor("#FBE8B0")
    val DARK = Color.parseColor("#1B2733")
    val LIGHT = Color.parseColor("#C5CED6")
    val WARN_ON_DARK = Color.parseColor("#FFD27A")
    val TEXT = Color.parseColor("#2A2A2A")
    val SECONDARY = Color.parseColor("#5F6368")
    val BG = Color.parseColor("#F6F4EF")
    val CARD = Color.WHITE
    val BORDER = Color.parseColor("#D9D5CC")
    val RED_TINT = Color.parseColor("#FBEDEC")
    val RED_DARK = Color.parseColor("#8A2626")
    val GREEN_TINT = Color.parseColor("#DCEFE4")
    val GREEN_DARK = Color.parseColor("#1E5A3E")
    val BLUE_TINT = Color.parseColor("#E3ECF6")
    val BLUE_DARK = Color.parseColor("#24476F")

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

    /** Beyaz (veya renkli) kart zemini, ince çerçeveli, dokununca hafif dalga efekti. */
    fun cardBackground(ctx: Context, fill: Int = CARD, stroke: Int = BORDER, strokeDp: Int = 1): Drawable {
        val shape = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(ctx, 14).toFloat()
            setStroke(dp(ctx, strokeDp), stroke)
        }
        return RippleDrawable(ColorStateList.valueOf(0x22000000), shape, null)
    }

    /** İsmin baş harfi (Türkçe büyük harf: i → İ). */
    fun initial(name: String): String {
        val c = name.trim().firstOrNull { it.isLetterOrDigit() } ?: return "?"
        return c.toString().uppercase(Locale("tr"))
    }

    /** Yuvarlak rozet: rehberde fotoğraf varsa fotoğraf, yoksa renkli zeminde baş harf. */
    fun avatar(ctx: Context, name: String, photo: Uri?, sizeDp: Int, tint: Int, textColor: Int): View {
        val size = dp(ctx, sizeDp)
        if (photo != null) {
            val iv = ImageView(ctx)
            try {
                iv.setImageURI(photo)
            } catch (e: Exception) {
            }
            if (iv.drawable != null) {
                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                iv.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
                iv.clipToOutline = true
                iv.layoutParams = LinearLayout.LayoutParams(size, size)
                return iv
            }
        }
        val t = TextView(ctx)
        t.text = initial(name)
        t.gravity = Gravity.CENTER
        t.textSize = sizeDp * 0.42f
        t.setTextColor(textColor)
        t.setTypeface(t.typeface, Typeface.BOLD)
        t.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(tint)
        }
        t.layoutParams = LinearLayout.LayoutParams(size, size)
        return t
    }

    /**
     * Liste satırı: solda rozet, sağda büyük başlık ve renkli alt satır.
     * Tüm satır dokunulabilir.
     */
    fun listItem(
        ctx: Context, avatarView: View?, title: String, subtitle: String?,
        titleColor: Int = TEXT, subColor: Int = SECONDARY,
        fill: Int = CARD, stroke: Int = BORDER, strokeDp: Int = 1, onClick: () -> Unit,
    ): LinearLayout {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = cardBackground(ctx, fill, stroke, strokeDp)
        val p = dp(ctx, 12)
        row.setPadding(p, p, p, p)
        row.minimumHeight = dp(ctx, 76)
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, dp(ctx, 5), 0, dp(ctx, 5)) }
        if (avatarView != null) {
            row.addView(avatarView)
            (avatarView.layoutParams as? LinearLayout.LayoutParams)?.rightMargin = dp(ctx, 14)
        }
        val texts = LinearLayout(ctx)
        texts.orientation = LinearLayout.VERTICAL
        texts.addView(label(ctx, title, 23f, titleColor, bold = true).apply { setPadding(0, 0, 0, 0) })
        if (!subtitle.isNullOrBlank()) {
            texts.addView(label(ctx, subtitle, 18f, subColor).apply { setPadding(0, dp(ctx, 2), 0, 0) })
        }
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.isClickable = true
        row.setOnClickListener { onClick() }
        return row
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
