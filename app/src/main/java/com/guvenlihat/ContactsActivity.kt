package com.guvenlihat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout

/** Büyük yazılı rehber. EXTRA_PICK ile açılırsa kişi seçip geri döner (ayarlar için). */
class ContactsActivity : Activity() {

    companion object {
        const val EXTRA_PICK = "pick"
        const val RESULT_NAME = "name"
        const val RESULT_NUMBER = "number"
    }

    private data class Entry(val name: String, val number: String)

    private var all = listOf<Entry>()
    private lateinit var list: LinearLayout
    private var pickMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickMode = intent.getBooleanExtra(EXTRA_PICK, false)

        val root = Ui.column(this, 16)
        root.addView(Ui.label(this, if (pickMode) "Kişi seçin" else "Rehber", 30f, Ui.TEXT, bold = true))

        val search = EditText(this)
        search.hint = "İsim ile ara..."
        search.textSize = 24f
        search.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        root.addView(search)

        list = Ui.column(this, 0)
        val scroll = Ui.scroll(this, list)
        scroll.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        root.addView(scroll)
        root.addView(Ui.button(this, "Geri", Ui.GRAY, 20f) { finish() })
        setContentView(root)

        if (!PhoneUtils.hasContactsPermission(this)) {
            list.addView(Ui.label(this, "Rehbere erişim izni yok. Ailenizden ayarları yapmasını isteyin.", 22f, Ui.RED))
            return
        }
        all = load()
        render("")
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = render(s?.toString() ?: "")
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun load(): List<Entry> {
        val out = mutableListOf<Entry>()
        val seen = HashSet<String>()
        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val number = c.getString(1) ?: continue
                    if (seen.add(name + "|" + PhoneUtils.key(number))) out.add(Entry(name, number))
                }
            }
        } catch (e: Exception) {
        }
        return out
    }

    private fun render(query: String) {
        list.removeAllViews()
        val q = query.trim()
        val shown = all.filter {
            q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.number.contains(q)
        }.take(300)
        if (shown.isEmpty()) {
            list.addView(Ui.label(this, "Kişi bulunamadı.", 22f, Ui.GRAY))
            return
        }
        for (e in shown) {
            val b = Ui.button(this, "${e.name}\n${e.number}", Ui.BLUE, 24f) { onPick(e) }
            b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            list.addView(b)
        }
    }

    private fun onPick(e: Entry) {
        if (pickMode) {
            setResult(RESULT_OK, Intent().putExtra(RESULT_NAME, e.name).putExtra(RESULT_NUMBER, e.number))
            finish()
        } else {
            CallFlow.start(this, e.number)
        }
    }
}
