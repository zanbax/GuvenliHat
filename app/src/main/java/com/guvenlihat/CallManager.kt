package com.guvenlihat

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.VideoProfile

/**
 * Uygulamanın beyni: aramaları takip eder, bilinmeyenleri engeller,
 * süre dolunca görüşmeyi keser ve güvenli kişileri sırayla arar.
 */
object CallManager {

    const val REASON_UNKNOWN = "unknown"
    const val REASON_REDIAL = "redial"

    /** 112 vb. aradıktan sonra bu süre boyunca gelen aramalar engellenmez (geri arama için). */
    private const val EMERGENCY_GRACE_MS = 60 * 60_000L

    /** Güvenli kişi bu sürede açmazsa sıradakine geçilir. */
    private const val CHAIN_RING_TIMEOUT_MS = 45_000L

    class CallInfo(
        val number: String?,
        val incoming: Boolean,
        val limited: Boolean,
        val fromChain: Boolean,
        val unknown: Boolean,
        /** Rehbere son 24 saatte eklenmiş numara */
        val recent: Boolean = false,
    ) {
        var activeSince = 0L
        var wasActive = false
        var cutByLimit = false
        var rejectedByUser = false
    }

    private val main = Handler(Looper.getMainLooper())
    private val calls = mutableListOf<Call>()
    private val infos = HashMap<Call, CallInfo>()
    private val listeners = LinkedHashSet<() -> Unit>()
    private lateinit var app: Context

    var service: GuardInCallService? = null

    /** Görüşme ekranında gösterilen büyük sarı uyarı. */
    var banner: String? = null
        private set

    private var chainActive = false

    /** Zincir "Yardım" düğmesiyle mi başladı (yoksa süre sınırıyla mı)? */
    private var chainForHelp = false
    private var chainIndex = 0
    private var wakeLock: PowerManager.WakeLock? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = handleState(call, state)
        override fun onDetailsChanged(call: Call, details: Call.Details) = notifyUi()
    }

    // ---------- Arayüz bağlantısı ----------

    fun addListener(l: () -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    fun notifyUi() {
        listeners.toList().forEach { it() }
        if (::app.isInitialized) CallNotification.update(app)
    }

    fun clearBanner() {
        banner = null
        notifyUi()
    }

    @Suppress("DEPRECATION")
    fun stateOf(call: Call): Int = if (Build.VERSION.SDK_INT >= 31) call.details.state else call.state

    fun hasCalls() = calls.isNotEmpty()

    fun info(call: Call): CallInfo? = infos[call]

    fun primaryCall(): Call? {
        val order = intArrayOf(
            Call.STATE_RINGING, Call.STATE_SELECT_PHONE_ACCOUNT, Call.STATE_DIALING,
            Call.STATE_CONNECTING, Call.STATE_ACTIVE, Call.STATE_HOLDING,
        )
        for (s in order) calls.firstOrNull { stateOf(it) == s }?.let { return it }
        return calls.firstOrNull()
    }

    // ---------- Engelleme kararı (ScreeningService ve InCallService ortak) ----------

    /** Gelen arama engellenecekse nedeni, değilse null. */
    fun blockReason(ctx: Context, number: String?): String? {
        val prefs = Prefs(ctx)
        if (number != null && !PhoneUtils.isEmergency(number) &&
            prefs.redialBlockedUntil(PhoneUtils.key(number)) > 0
        ) return REASON_REDIAL
        if (!prefs.filterUnknown) return null
        if (prefs.relaxActive) return null
        if (PhoneUtils.isSafe(prefs, number)) return null
        if (System.currentTimeMillis() - prefs.lastEmergencyCallAt < EMERGENCY_GRACE_MS) return null
        return if (PhoneUtils.isKnown(ctx, number) == false) REASON_UNKNOWN else null
    }

    fun reportBlocked(ctx: Context, number: String?, reason: String) {
        val text = when {
            reason == REASON_REDIAL -> "Az önce görüşmesi kesilen $number numarası tekrar aradı, engellendi."
            number == null -> "Gizli numaradan arandı, arama engellendi."
            else -> "Rehberde olmayan $number numarası aradı, arama engellendi."
        }
        Notifier.send(ctx, text, throttleKey = "blk_" + PhoneUtils.key(number).ifEmpty { "gizli" })
    }

    // ---------- InCallService olayları ----------

    fun onCallAdded(svc: GuardInCallService, call: Call) {
        app = svc.applicationContext
        val prefs = Prefs(app)
        val number = PhoneUtils.numberOf(call)
        val state = stateOf(call)
        val incoming = if (Build.VERSION.SDK_INT >= 29) {
            call.details.callDirection == Call.Details.DIRECTION_INCOMING
        } else {
            state == Call.STATE_RINGING
        }
        val emergency = PhoneUtils.isEmergency(number)
        if (incoming) CareMonitor.markSystemWake(app)
        if (!incoming && emergency) prefs.lastEmergencyCallAt = System.currentTimeMillis()

        // 1) Gelen arama: süzgeç servisi çalışmadıysa burada yedek olarak engelle
        if (incoming) {
            val reason = blockReason(app, number)
            if (reason != null) {
                call.reject(false, null)
                reportBlocked(app, number, reason)
                return
            }
        }

        // 2) Az önce kesilen numarayı geri arama denemesi
        if (!incoming && !emergency && number != null &&
            prefs.redialBlockedUntil(PhoneUtils.key(number)) > 0
        ) {
            call.disconnect()
            banner = "Bu numara güvenliğiniz için şu an aranamaz.\nLütfen bir yakınınızı arayın."
            Notifier.send(
                app, "Az önce görüşmesi kesilen $number numarasını tekrar aramaya çalıştı, engellendi.",
                throttleKey = "redialout_" + PhoneUtils.key(number),
            )
            showUi(app)
            return
        }

        val safe = PhoneUtils.isSafe(prefs, number)
        val unknown = PhoneUtils.isKnown(app, number) == false
        // Rehberi şimdi tara: dolandırıcı "numaramı kaydet" dediyse az önce eklenmiş olabilir
        if (!unknown && !safe && !emergency) ContactsWatcher.check(app)
        val recent = !unknown && !safe && !emergency && ContactsWatcher.isRecentlyAdded(app, number)
        val fromChain = chainActive && !incoming && safe
        val limited = prefs.limitMinutes > 0 && !emergency && !safe && when (prefs.limitMode) {
            LimitMode.UNKNOWN_ONLY -> unknown || recent
            LimitMode.ALL_EXCEPT_SAFE -> true
        }

        if (recent) {
            val name = PhoneUtils.contactName(app, number) ?: ""
            Notifier.send(
                app,
                "Rehbere son 24 saatte eklenen $name ($number) ile görüşme " +
                    (if (incoming) "geliyor." else "başlatıyor."),
                throttleKey = "recent_" + PhoneUtils.key(number), throttleMs = 30 * 60_000L,
            )
        }

        if (incoming && unknown && prefs.filterUnknown && prefs.relaxActive) {
            Notifier.send(
                app, "\"Arama bekliyorum\" açıkken rehberde olmayan ${number ?: "gizli numara"} aradı, arama çalıyor.",
                throttleKey = "relaxin_" + PhoneUtils.key(number).ifEmpty { "gizli" },
            )
        }
        if (!incoming && unknown && !emergency && !safe) {
            Notifier.send(
                app, "Rehberde olmayan $number numarasını arıyor.",
                throttleKey = "out_" + PhoneUtils.key(number), throttleMs = 30 * 60_000L,
            )
        }
        // Zincir sürerken kişi başka birini ararsa zinciri bırak
        if (chainActive && !fromChain && !incoming) chainActive = false

        val info = CallInfo(number, incoming, limited, fromChain, unknown, recent)
        infos[call] = info
        calls.add(call)
        call.registerCallback(callback, main)

        // Gelen aramada adı sesli oku (ayar açıksa): biraz zil çaldıktan sonra, iki kez
        if (incoming && state == Call.STATE_RINGING && prefs.announceCaller) {
            CallerAnnouncer.init(app)
            val sentence = CallerAnnouncer.sentenceFor(app, number, unknown)
            for (delay in longArrayOf(1_500L, 8_000L)) {
                main.postAtTime(Runnable {
                    if (infos[call] != null && stateOf(call) == Call.STATE_RINGING) {
                        CallerAnnouncer.announce(app, sentence)
                    }
                }, call, SystemClock.uptimeMillis() + delay)
            }
        }

        if (fromChain) {
            main.postAtTime(Runnable {
                if (infos[call]?.wasActive == false) call.disconnect()
            }, call, SystemClock.uptimeMillis() + CHAIN_RING_TIMEOUT_MS)
        }

        handleState(call, state)
        showUi(app)
    }

    fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        CallerAnnouncer.stop()
        main.removeCallbacksAndMessages(call)
        calls.remove(call)
        val info = infos.remove(call)
        if (infos.values.none { it.limited && it.wasActive }) releaseWakeLock()

        if (info != null && info.incoming && !info.wasActive) CareMonitor.markSystemWake(app)
        if (info != null && info.incoming && !info.wasActive && !info.rejectedByUser) {
            val cause = try {
                call.details.disconnectCause?.code
            } catch (e: Exception) {
                null
            }
            if (cause != DisconnectCause.REJECTED) {
                MissedCalls.add(app, PhoneUtils.label(app, info.number))
            }
        }

        if (info != null) {
            if (info.cutByLimit) {
                chainForHelp = false
                main.postDelayed({ startChain() }, 1500)
            } else if (info.fromChain && !info.wasActive && chainActive) {
                chainIndex++
                main.postDelayed({ placeChainCall() }, 2000)
            }
        }
        notifyUi()
    }

    private fun handleState(call: Call, state: Int) {
        val info = infos[call] ?: return
        when (state) {
            Call.STATE_ACTIVE -> if (!info.wasActive) {
                CareMonitor.markActive(app)
                if (info.incoming) CallerAnnouncer.stop()
                info.wasActive = true
                info.activeSince = SystemClock.elapsedRealtime()
                if (info.fromChain) {
                    // Güvenli kişi açtı, zincir tamam
                    chainActive = false
                    chainForHelp = false
                    banner = null
                }
                if (info.limited) startLimitTimer(call, info)
            }
            Call.STATE_SELECT_PHONE_ACCOUNT -> if (info.fromChain) {
                PhoneUtils.defaultAccount(app)?.let { call.phoneAccountSelected(it, false) }
            }
        }
        notifyUi()
    }

    // ---------- Süre sınırı ----------

    private fun startLimitTimer(call: Call, info: CallInfo) {
        val minutes = Prefs(app).limitMinutes
        if (minutes <= 0) return
        val limitMs = minutes * 60_000L
        // Ekran kapalıyken de zamanlayıcı işlesin
        acquireWakeLock(limitMs + 5 * 60_000L)
        val now = SystemClock.uptimeMillis()

        if (minutes >= 2) {
            main.postAtTime(Runnable {
                if (infos[call] != null) {
                    Notifier.send(
                        app,
                        "${who(info)} ile ${minutes / 2} dakikadır konuşuyor. " +
                            "$minutes dakikada otomatik kesilecek.",
                    )
                }
            }, call, now + limitMs / 2)
        }
        main.postAtTime(Runnable { cutCall(call, info, minutes) }, call, now + limitMs)
    }

    private fun cutCall(call: Call, info: CallInfo, minutes: Int) {
        if (infos[call] == null) return
        val prefs = Prefs(app)
        info.cutByLimit = true
        info.number?.let { prefs.blockRedial(PhoneUtils.key(it), prefs.redialBlockMinutes) }
        banner = "Bu konuşma güvenliğiniz için sonlandırıldı."
        Notifier.send(
            app,
            "${who(info)} ile $minutes dakika konuştu, görüşme otomatik KESİLDİ. " +
                "Dolandırıcılık olabilir, lütfen hemen arayın!",
        )
        call.disconnect()
        showUi(app)
    }

    private fun who(info: CallInfo): String {
        val n = info.number ?: return "Gizli numara"
        if (info.unknown) return "$n (rehberde yok)"
        val name = PhoneUtils.contactName(app, n)
        val tag = if (info.recent) ", rehbere yeni eklendi" else ""
        return if (name != null) "$name ($n$tag)" else n
    }

    // ---------- Güvenli kişileri sırayla arama ----------

    /** "Yardım" düğmesi: güvenli kişileri sırayla arar (mesajlar MainActivity'den gider). */
    fun startHelp(ctx: Context) {
        app = ctx.applicationContext
        chainForHelp = true
        startChain()
    }

    fun cancelHelp() {
        if (chainForHelp) {
            chainActive = false
            chainForHelp = false
            banner = null
            calls.firstOrNull { infos[it]?.fromChain == true }?.disconnect()
            notifyUi()
        }
    }

    private fun startChain() {
        val contacts = Prefs(app).safeContacts
        if (contacts.isEmpty()) {
            banner = if (chainForHelp) {
                "Güvenli kişi eklenmemiş.\nAcil bir durumsa 112'yi arayın."
            } else {
                "Bu konuşma güvenliğiniz için sonlandırıldı.\nLütfen bir yakınınızı arayın."
            }
            chainForHelp = false
            notifyUi()
            showUi(app)
            return
        }
        chainActive = true
        chainIndex = 0
        placeChainCall()
    }

    private fun placeChainCall() {
        if (!chainActive) return
        val contacts = Prefs(app).safeContacts
        if (chainIndex >= contacts.size) {
            chainActive = false
            banner = "Yakınlarınıza şu an ulaşılamadı.\nOnlara haber verildi, sizi arayacaklar."
            Notifier.send(
                app,
                if (chainForHelp) {
                    "🆘 Yardım çağrısından sonra telefonla ulaşılamadı. Lütfen HEMEN arayın!"
                } else {
                    "Görüşme kesildikten sonra yakınlarına telefonla ulaşılamadı. Lütfen hemen arayın!"
                },
            )
            chainForHelp = false
            notifyUi()
            showUi(app)
            return
        }
        val c = contacts[chainIndex]
        banner = if (chainForHelp) {
            "Yardım çağrısı gönderildi.\n${c.name} aranıyor..."
        } else {
            "Bu konuşma güvenliğiniz için sonlandırıldı.\n${c.name} aranıyor..."
        }
        notifyUi()
        showUi(app)
        PhoneUtils.placeCall(app, c.number, autoSim = true)
    }

    // ---------- "Arama bekliyorum" (geçici gevşetme) ----------

    fun startRelax(ctx: Context, minutes: Int) {
        val prefs = Prefs(ctx)
        prefs.relaxUntil = System.currentTimeMillis() + minutes * 60_000L
        Notifier.send(
            ctx,
            "\"Arama bekliyorum\" açıldı: $minutes dakika boyunca rehberde olmayan numaralar da arayabilir. " +
                "Süre sınırı açık kalıyor.",
        )
    }

    fun stopRelax(ctx: Context) {
        val prefs = Prefs(ctx)
        if (!prefs.relaxActive) return
        prefs.relaxUntil = 0L
        Notifier.send(ctx, "\"Arama bekliyorum\" erken kapatıldı, koruma tekrar tam açık.")
    }

    // ---------- Ekrandaki düğmeler ----------

    fun answer() {
        calls.firstOrNull { stateOf(it) == Call.STATE_RINGING }?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun hangup() {
        val c = primaryCall() ?: return
        if (infos[c]?.fromChain == true) {
            chainActive = false
            chainForHelp = false
        }
        if (stateOf(c) == Call.STATE_RINGING) {
            infos[c]?.rejectedByUser = true
            c.reject(false, null)
        } else {
            c.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    fun isSpeakerOn(): Boolean = service?.callAudioState?.route == CallAudioState.ROUTE_SPEAKER

    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        val s = service ?: return
        s.setAudioRoute(if (isSpeakerOn()) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
    }

    @Suppress("DEPRECATION")
    fun isMuted(): Boolean = service?.callAudioState?.isMuted == true

    fun toggleMute() {
        service?.setMuted(!isMuted())
    }

    fun showUi(ctx: Context) {
        CallNotification.update(ctx)
        try {
            ctx.startActivity(Intent(ctx, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // Arka plandan açılamazsa tam ekran bildirim devreye girer
        }
    }

    // ---------- Uyanık tutma ----------

    private fun acquireWakeLock(timeoutMs: Long) {
        try {
            if (wakeLock == null) {
                val pm = app.getSystemService(PowerManager::class.java) ?: return
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "guvenlihat:limit").apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.acquire(timeoutMs)
        } catch (e: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
        }
    }
}
