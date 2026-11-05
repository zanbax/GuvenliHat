package com.guvenlihat

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

/** Büyük tuşlu numara çevirme ekranı. Sistem "Ara/Çevir" istekleri de buraya gelir. */
class DialpadActivity : Activity() {

    private lateinit var display: TextView
    private val number = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = Ui.column(this, 16)

        intent?.data?.schemeSpecificPart?.let { pre ->
            number.append(pre.filter { it.isDigit() || it == '+' || it == '*' || it == '#' })
        }

        display = Ui.label(this, "", 38f, Ui.TEXT, bold = true)
        display.gravity = Gravity.CENTER
        display.minHeight = Ui.dp(this, 80)
        root.addView(display)

        val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("*", "0", "#"))
        for (rowKeys in keys) {
            val row = Ui.row(this)
            for (k in rowKeys) {
                row.addView(Ui.weighted(Ui.button(this, k, Ui.KEY, 34f) {
                    if (number.length < 20) number.append(k)
                    refresh()
                }))
            }
            root.addView(row)
        }

        val bottom = Ui.row(this)
        bottom.addView(Ui.weighted(Ui.button(this, "⌫ Sil", Ui.GRAY, 24f) {
            if (number.isNotEmpty()) number.deleteCharAt(number.length - 1)
            refresh()
        }))
        bottom.addView(Ui.weighted(Ui.button(this, "📞 Ara", Ui.GREEN, 28f) {
            CallFlow.start(this, number.toString())
        }, weight = 2f))
        root.addView(bottom)

        root.addView(Ui.button(this, "Geri", Ui.GRAY, 20f) { finish() })

        setContentView(Ui.scroll(this, root))
        refresh()
    }

    private fun refresh() {
        display.text = if (number.isEmpty()) "Numara girin" else number.toString()
        display.setTextColor(if (number.isEmpty()) Ui.GRAY else Ui.TEXT)
    }
}
