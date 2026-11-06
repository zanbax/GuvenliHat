package com.guvenlihat

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Gelen aramada arayanın adını sesli okur ("Ayşe arıyor").
 * Ek izin gerektirmez; telefondaki Türkçe metin okuma motorunu kullanır.
 */
object CallerAnnouncer : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var pending: String? = null

    /** null: henüz bilinmiyor, true: Türkçe hazır, false: Türkçe ses yok */
    var turkishReady: Boolean? = null
        private set

    fun init(ctx: Context) {
        if (tts == null) {
            turkishReady = null
            tts = TextToSpeech(ctx.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null) {
            turkishReady = false
            return
        }
        val r = engine.setLanguage(Locale("tr", "TR"))
        turkishReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                // Zil sesi seviyesinde çalsın
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        engine.setSpeechRate(0.9f)
        pending?.let { speakNow(it) }
        pending = null
    }

    /** Telefon sessizde veya titreşimdeyse konuşmaz. */
    fun announce(ctx: Context, text: String) {
        val am = ctx.getSystemService(AudioManager::class.java)
        if (am != null && am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        say(ctx, text)
    }

    /** Ayarlardaki "Dene" düğmesi için: sessiz modu dikkate almaz. */
    fun say(ctx: Context, text: String) {
        init(ctx)
        if (turkishReady == true) speakNow(text) else pending = text
    }

    fun stop() {
        pending = null
        try {
            tts?.stop()
        } catch (e: Exception) {
        }
    }

    private fun speakNow(text: String) {
        if (turkishReady != true) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "caller")
        } catch (e: Exception) {
        }
    }

    /** Arayana göre okunacak cümle. */
    fun sentenceFor(ctx: Context, number: String?, unknown: Boolean): String {
        if (number.isNullOrBlank()) return "Gizli numara arıyor"
        val name = PhoneUtils.contactName(ctx, number) ?: PhoneUtils.safeName(Prefs(ctx), number)
        return when {
            name != null -> "$name arıyor"
            unknown -> "Dikkat. Rehberde olmayan bir numara arıyor"
            else -> "Bir numara arıyor"
        }
    }
}
