package com.guvenlihat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.telecom.Call

/** Gelen arama (tam ekran) ve devam eden arama bildirimleri. */
object CallNotification {

    private const val CH_INCOMING = "incoming_v1"
    private const val CH_ONGOING = "ongoing_v1"
    private const val ID = 1001
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val incoming = NotificationChannel(CH_INCOMING, "Gelen aramalar", NotificationManager.IMPORTANCE_HIGH).apply {
            // Zil sesini sistem çalıyor
            setSound(null, null)
            enableVibration(false)
        }
        val ongoing = NotificationChannel(CH_ONGOING, "Devam eden arama", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannels(listOf(incoming, ongoing))
    }

    @Suppress("DEPRECATION")
    fun update(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val call = CallManager.primaryCall()
        if (call == null) {
            nm.cancel(ID)
            return
        }
        val ringing = CallManager.stateOf(call) == Call.STATE_RINGING
        val title = PhoneUtils.label(ctx, PhoneUtils.numberOf(call))

        val openIntent = Intent(ctx, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val openPi = PendingIntent.getActivity(ctx, 1, openIntent, FLAGS)

        val b = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(ctx, if (ringing) CH_INCOMING else CH_ONGOING)
        } else {
            Notification.Builder(ctx)
        }
        b.setSmallIcon(R.drawable.ic_call)
            .setContentTitle(title)
            .setContentText(if (ringing) "Gelen arama" else "Görüşme sürüyor")
            .setOngoing(true)
            .setContentIntent(openPi)
            .setCategory(Notification.CATEGORY_CALL)

        if (ringing) {
            b.setFullScreenIntent(openPi, true)
            b.setPriority(Notification.PRIORITY_MAX)
            val answerIntent = Intent(ctx, InCallActivity::class.java)
                .setAction(InCallActivity.ACTION_ANSWER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            b.addAction(action(ctx, "Aç", PendingIntent.getActivity(ctx, 2, answerIntent, FLAGS)))
            b.addAction(action(ctx, "Reddet", hangupIntent(ctx)))
        } else {
            b.setPriority(Notification.PRIORITY_LOW)
            b.addAction(action(ctx, "Kapat", hangupIntent(ctx)))
        }
        try {
            nm.notify(ID, b.build())
        } catch (e: Exception) {
        }
    }

    private fun hangupIntent(ctx: Context): PendingIntent {
        val i = Intent(ctx, CallActionReceiver::class.java).setAction(CallActionReceiver.ACTION_HANGUP)
        return PendingIntent.getBroadcast(ctx, 3, i, FLAGS)
    }

    private fun action(ctx: Context, title: String, pi: PendingIntent): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_call), title, pi).build()
}
