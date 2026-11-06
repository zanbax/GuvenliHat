package com.guvenlihat

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

/**
 * Büyük yazılı rehber: arama, kişi ekleme, düzenleme ve silme.
 * Ekleme ve düzenleme telefonun kendi Kişiler ekranında yapılır; her değişiklik
 * ContactsWatcher tarafından aileye bildirilir.
 * EXTRA_PICK ile açılırsa sadece kişi seçip geri döner (ayarlar için).
 */
class ContactsActivity : Activity() {

    companion object {
        const val EXTRA_PICK = "pick"
        const val RESULT_NAME = "name"
        const val RESULT_NUMBER = "number"
    }

    private data class Entry(val name: String, val number: String, val contactId: Long, val lookupKey: String?) {
        /** İsimsiz kayıtlarda isim alanı numaranın kendisidir */
        val hasName: Boolean get() = name.any { it.isLetter() }
        val title: String get() = if (hasName) name else number
    }

    private var all = listOf<Entry>()
    private lateinit var list: LinearLayout
    private lateinit var search: EditText
    private var pickMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickMode = intent.getBooleanExtra(EXTRA_PICK, false)

        val root = Ui.column(this, 16)
        root.setBackgroundColor(Ui.BG)
        root.addView(Ui.label(this, if (pickMode) "Kişi Seçin" else "Rehber", 30f, Ui.TEXT, bold = true))

        if (!pickMode) {
            root.addView(Ui.button(this, "+  Yeni Kişi Ekle", Ui.GREEN, 24f) { addContact() })
        }

        search = EditText(this)
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

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = render(s?.toString() ?: "")
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    override fun onResume() {
        super.onResume()
        // Kişiler ekranından dönünce listeyi yenile ve değişiklikleri aileye bildir
        if (PhoneUtils.hasContactsPermission(this)) {
            all = load()
            ContactsWatcher.checkInBackground(this)
        }
        render(search.text.toString())
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
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val number = c.getString(1) ?: continue
                    if (seen.add(name + "|" + PhoneUtils.key(number))) {
                        out.add(Entry(name, number, c.getLong(2), c.getString(3)))
                    }
                }
            }
        } catch (e: Exception) {
        }
        // İsimsiz (sadece numarayla kayıtlı) kişiler listenin sonuna
        return out.sortedBy { !it.hasName }
    }

    private fun render(query: String) {
        list.removeAllViews()
        if (!PhoneUtils.hasContactsPermission(this)) {
            list.addView(Ui.label(this, "Rehbere erişim izni yok. Ailenizden ayarları yapmasını isteyin.", 22f, Ui.RED))
            return
        }
        val q = query.trim()
        val shown = all.filter {
            q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.number.contains(q)
        }.take(300)
        if (shown.isEmpty()) {
            list.addView(Ui.label(this, "Kişi bulunamadı.", 22f, Ui.GRAY))
            return
        }
        for (e in shown) {
            // Uzun listede hız için fotoğraf yerine baş harf rozeti
            val avatar = Ui.avatar(this, e.title, null, 52, Ui.BLUE_TINT, Ui.BLUE_DARK)
            val item = if (e.hasName) {
                Ui.listItem(this, avatar, e.name, e.number) { onTap(e) }
            } else {
                Ui.listItem(this, avatar, e.number, "İsimsiz Kayıt") { onTap(e) }
            }
            list.addView(item)
        }
    }

    private fun onTap(e: Entry) {
        if (pickMode) {
            setResult(RESULT_OK, Intent().putExtra(RESULT_NAME, e.name).putExtra(RESULT_NUMBER, e.number))
            finish()
            return
        }
        Ui.actions(
            this, if (e.hasName) "${e.name}\n${e.number}" else e.number,
            listOf(
                Triple("📞  Ara", Ui.GREEN) { CallFlow.start(this, e.number) },
                Triple("✏️  Düzenle", Ui.BLUE) { editContact(e) },
                Triple("🗑  Sil", Ui.RED) { confirmDelete(e) },
            ),
        )
    }

    private fun lookupUri(e: Entry): Uri =
        if (e.lookupKey != null) {
            ContactsContract.Contacts.getLookupUri(e.contactId, e.lookupKey)
        } else {
            Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, e.contactId.toString())
        }

    private fun addContact() {
        val i = Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
            .putExtra("finishActivityOnSaveCompleted", true)
        startSafely(i)
    }

    private fun editContact(e: Entry) {
        val i = Intent(Intent.ACTION_EDIT, lookupUri(e))
            .putExtra("finishActivityOnSaveCompleted", true)
        startSafely(i)
    }

    private fun confirmDelete(e: Entry) {
        if (!PhoneUtils.has(this, Manifest.permission.WRITE_CONTACTS)) {
            Ui.info(this, "İzin yok", "Kişi silme izni verilmemiş. Ailenizden ayarları yapmasını isteyin.")
            return
        }
        Ui.ask(this, "Kişiyi sil", "${e.title} rehberden silinsin mi?\n\nBu işlem geri alınamaz.", yes = "Sil") {
            val ok = try {
                contentResolver.delete(lookupUri(e), null, null) > 0
            } catch (ex: Exception) {
                false
            }
            Toast.makeText(this, if (ok) "${e.title} silindi" else "Silinemedi", Toast.LENGTH_LONG).show()
            all = load()
            render(search.text.toString())
            ContactsWatcher.checkInBackground(this)
        }
    }

    private fun startSafely(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Kişiler uygulaması açılamadı", Toast.LENGTH_LONG).show()
        }
    }
}
