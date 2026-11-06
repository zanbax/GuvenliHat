package com.guvenlihat

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.telecom.Call
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** Gelen / giden / süren görüşme ekranı ve "konuşma sonlandırıldı" uyarısı. */
class InCallActivity : Activity() {

    companion object {
        const val ACTION_ANSWER = "com.guvenlihat.ANSWER"
    }

    private lateinit var bannerView: TextView
    private lateinit var nameView: TextView
    private lateinit var numberView: TextView
    private lateinit var stateView: TextView
    private lateinit var timerView: TextView
    private lateinit var simBox: LinearLayout
    private lateinit var keypadBox: LinearLayout
    private lateinit var controls: LinearLayout

    private var keypadOpen = false
    private val handler = Handler(Looper.getMainLooper())
    private var proximity: PowerManager.WakeLock? = null
    private val listener: () -> Unit = { render() }
    private val ticker = object : Runnable {
        override fun run() {
            renderTimer()
            handler.postDelayed(this, 1000)
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }

        val root = Ui.column(this, 20)
        root.setBackgroundColor(Ui.DARK)
        root.gravity = Gravity.CENTER_HORIZONTAL

        bannerView = Ui.label(this, "", 26f, Color.BLACK, bold = true)
        bannerView.setBackgroundColor(Ui.YELLOW)
        bannerView.gravity = Gravity.CENTER
        val p = Ui.dp(this, 16)
        bannerView.setPadding(p, p, p, p)
        root.addView(bannerView, matchWidth())

        nameView = centered(Ui.label(this, "", 34f, Color.WHITE, bold = true))
        numberView = centered(Ui.label(this, "", 22f, Ui.LIGHT))
        stateView = centered(Ui.label(this, "", 22f, Ui.LIGHT))
        timerView = centered(Ui.label(this, "", 30f, Color.WHITE))
        root.addView(nameView, matchWidth())
        root.addView(numberView, matchWidth())
        root.addView(stateView, matchWidth())
        root.addView(timerView, matchWidth())

        simBox = Ui.column(this, 0)
        root.addView(simBox, matchWidth())

        val spacer = View(this)
        root.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))

        keypadBox = Ui.column(this, 0)
        buildKeypad()
        keypadBox.visibility = View.GONE
        root.addView(keypadBox, matchWidth())

        controls = Ui.column(this, 0)
        root.addView(controls, matchWidth())

        setContentView(root)
        CallManager.addListener(listener)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == ACTION_ANSWER) CallManager.answer()
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onDestroy() {
        CallManager.removeListener(listener)
        releaseProximity()
        super.onDestroy()
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
    )

    private fun centered(t: TextView): TextView {
        t.gravity = Gravity.CENTER
        return t
    }

    private fun render() {
        if (isFinishing) return
        val call = CallManager.primaryCall()
        val banner = CallManager.banner
        bannerView.visibility = if (banner != null) View.VISIBLE else View.GONE
        bannerView.text = banner ?: ""
        controls.removeAllViews()
        simBox.removeAllViews()

        if (call == null) {
            nameView.text = ""
            numberView.text = ""
            stateView.text = ""
            timerView.text = ""
            keypadBox.visibility = View.GONE
            releaseProximity()
            if (banner == null) {
                finish()
                return
            }
            controls.addView(Ui.button(this, "Tamam", Ui.BLUE, 28f) {
                CallManager.clearBanner()
                finish()
            })
            return
        }

        val number = PhoneUtils.numberOf(call)
        val info = CallManager.info(call)
        val name = PhoneUtils.contactName(this, number) ?: PhoneUtils.safeName(Prefs(this), number)
        nameView.text = name ?: (number ?: "Gizli numara")
        when {
            info?.recent == true -> {
                numberView.text = "⚠ Rehbere yeni eklendi"
                numberView.setTextColor(Ui.YELLOW)
            }
            name != null -> {
                numberView.text = number ?: ""
                numberView.setTextColor(Ui.LIGHT)
            }
            info?.unknown == true -> {
                numberView.text = "⚠ Rehberinizde kayıtlı değil"
                numberView.setTextColor(Ui.YELLOW)
            }
            else -> numberView.text = ""
        }

        val state = CallManager.stateOf(call)
        stateView.text = when (state) {
            Call.STATE_RINGING -> "Sizi arıyor"
            Call.STATE_DIALING, Call.STATE_CONNECTING -> "Aranıyor..."
            Call.STATE_ACTIVE -> "Görüşme sürüyor"
            Call.STATE_HOLDING -> "Beklemede"
            Call.STATE_SELECT_PHONE_ACCOUNT -> "Hangi hat ile aransın?"
            Call.STATE_DISCONNECTING -> "Kapatılıyor..."
            else -> ""
        }
        renderTimer()

        when (state) {
            Call.STATE_RINGING -> {
                keypadBox.visibility = View.GONE
                val row = Ui.row(this)
                row.addView(Ui.weighted(Ui.button(this, "Aç", Ui.GREEN, 32f) { CallManager.answer() }))
                row.addView(Ui.weighted(Ui.button(this, "Reddet", Ui.RED, 32f) { CallManager.hangup() }))
                controls.addView(row)
            }
            Call.STATE_SELECT_PHONE_ACCOUNT -> {
                PhoneUtils.phoneAccounts(this).forEachIndexed { i, h ->
                    simBox.addView(Ui.button(this, PhoneUtils.accountLabel(this, h, i), Ui.BLUE, 26f) {
                        call.phoneAccountSelected(h, false)
                    })
                }
                controls.addView(Ui.button(this, "Vazgeç", Ui.RED, 28f) { call.disconnect() })
            }
            else -> {
                keypadBox.visibility = if (keypadOpen) View.VISIBLE else View.GONE
                val row = Ui.row(this)
                val speakerOn = CallManager.isSpeakerOn()
                row.addView(Ui.weighted(Ui.button(this, if (speakerOn) "Hoparlör\nAÇIK" else "Hoparlör", if (speakerOn) Ui.GREEN else Ui.KEY, 18f) {
                    CallManager.toggleSpeaker()
                }))
                row.addView(Ui.weighted(Ui.button(this, "Tuşlar", if (keypadOpen) Ui.GREEN else Ui.KEY, 18f) {
                    keypadOpen = !keypadOpen
                    render()
                }))
                val muted = CallManager.isMuted()
                row.addView(Ui.weighted(Ui.button(this, if (muted) "Ses\nKAPALI" else "Sessiz", if (muted) Ui.RED else Ui.KEY, 18f) {
                    CallManager.toggleMute()
                }))
                controls.addView(row)
                controls.addView(Ui.button(this, "Kapat", Ui.RED, 32f) { CallManager.hangup() })
            }
        }

        if (state == Call.STATE_ACTIVE && !CallManager.isSpeakerOn() && !keypadOpen) acquireProximity() else releaseProximity()
    }

    private fun renderTimer() {
        val call = CallManager.primaryCall()
        val info = call?.let { CallManager.info(it) }
        val state = call?.let { CallManager.stateOf(it) }
        if (info == null || !info.wasActive || (state != Call.STATE_ACTIVE && state != Call.STATE_HOLDING)) {
            timerView.text = ""
            return
        }
        val s = (SystemClock.elapsedRealtime() - info.activeSince) / 1000
        timerView.text = String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
    }

    /** Görüşme sırasında "1'e basın" gibi menüler için tuş takımı. */
    private fun buildKeypad() {
        val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("*", "0", "#"))
        for (rowKeys in keys) {
            val row = Ui.row(this)
            for (k in rowKeys) {
                row.addView(Ui.weighted(Ui.button(this, k, Ui.KEY, 28f) {
                    val c = CallManager.primaryCall() ?: return@button
                    c.playDtmfTone(k[0])
                    handler.postDelayed({ c.stopDtmfTone() }, 200)
                }, marginDp = 3))
            }
            keypadBox.addView(row)
        }
    }

    /** Telefon kulağa götürülünce ekranı kapatır (yanağın tuşlara basmaması için). */
    private fun acquireProximity() {
        if (proximity?.isHeld == true) return
        try {
            val pm = getSystemService(PowerManager::class.java) ?: return
            if (!pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
            if (proximity == null) {
                proximity = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "guvenlihat:proximity")
            }
            proximity?.acquire(4 * 60 * 60_000L)
        } catch (e: Exception) {
        }
    }

    private fun releaseProximity() {
        try {
            if (proximity?.isHeld == true) proximity?.release()
        } catch (e: Exception) {
        }
    }
}
