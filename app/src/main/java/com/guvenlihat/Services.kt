package com.guvenlihat

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallScreeningService
import android.telecom.InCallService

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CallNotification.createChannels(this)
    }
}

/** Uygulama varsayılan telefon uygulaması olunca sistem tüm aramaları buraya bildirir. */
class GuardInCallService : InCallService() {

    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
    }

    override fun onDestroy() {
        if (CallManager.service === this) CallManager.service = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        CallManager.service = this
        CallManager.onCallAdded(this, call)
    }

    override fun onCallRemoved(call: Call) {
        CallManager.onCallRemoved(call)
    }

    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        CallManager.notifyUi()
    }
}

/** Gelen arama telefon çalmadan önce buradan geçer; bilinmeyenler sessizce reddedilir. */
class GuardScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        val incoming = Build.VERSION.SDK_INT < 29 ||
            details.callDirection == Call.Details.DIRECTION_INCOMING
        if (!incoming) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        val number = PhoneUtils.numberOf(details)
        val reason = try {
            CallManager.blockReason(this, number)
        } catch (e: Exception) {
            null
        }
        if (reason == null) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        respondToCall(
            details,
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipNotification(true)
                .build(),
        )
        CallManager.reportBlocked(this, number, reason)
    }
}

/** Bildirimdeki "Kapat / Reddet" düğmesi. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_HANGUP) CallManager.hangup()
    }

    companion object {
        const val ACTION_HANGUP = "com.guvenlihat.HANGUP"
    }
}
