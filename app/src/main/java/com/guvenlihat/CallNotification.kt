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

    // v2: simgede rozet (sayı) göstermesinler; rozet sadece cevapsız aramalar için
    private const val CH_INCOMING = "incoming_v2"
    private const val CH_ONGOING = "ongoing_v2"
    const val CH_MISSED = "missed_v1"
    private const val ID = 1001
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val incoming = NotificationChannel(CH_INCOMING, "Gelen aramalar", NotificationManager.IMPORTANCE_HIGH).apply {
            // Zil sesini sistem çalıyor
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        val ongoing = NotificationChannel(CH_ONGOING, "Devam eden arama", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        }
        val missed = NotificationChannel(CH_MISSED, "Cevapsız aramalar", NotificationManager.IMPORTANCE_DEFAULT).apply {
            setShowBadge(true)
        }
        nm.createNotificationChannels(listOf(incoming, ongoing, missed))
        for (old in listOf("incoming_v1", "ongoing_v1")) {
            try {
                nm.deleteNotificationChannel(old)
            } catch (e: Exception) {
            }
        }
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

/** Cevapsız arama bildirimi: uygulama simgesinde sayı (rozet) olarak da görünür. */
object MissedCalls {

    private const val ID = 1002

    fun add(ctx: Context, name: String) {
        val prefs = Prefs(ctx)
        prefs.missedCount = prefs.missedCount + 1
        prefs.missedLastName = name
        show(ctx)
    }

    /** Sistem (Telecom) kendi saydığı cevapsız arama sayısını bildirdiğinde. */
    fun syncFromSystem(ctx: Context, count: Int, number: String?) {
        val prefs = Prefs(ctx)
        if (count <= 0) {
            prefs.missedCount = 0
            cancel(ctx)
            return
        }
        if (count > prefs.missedCount) {
            prefs.missedCount = count
            if (!number.isNullOrBlank()) prefs.missedLastName = PhoneUtils.label(ctx, number)
        }
        show(ctx)
    }

    /** "Son aramalar" açılınca sayı sıfırlanır. */
    fun clear(ctx: Context) {
        Prefs(ctx).missedCount = 0
        cancel(ctx)
        try {
            // Varsayılan telefon uygulaması olarak sistemin sayacını da sıfırla
            ctx.getSystemService(android.telecom.TelecomManager::class.java)?.cancelMissedCallsNotification()
        } catch (e: Exception) {
        }
    }

    private fun cancel(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java)?.cancel(ID)
    }

    @Suppress("DEPRECATION")
    private fun show(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val prefs = Prefs(ctx)
        val count = prefs.missedCount
        if (count <= 0) {
            nm.cancel(ID)
            return
        }
        val open = Intent(ctx, RecentsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            ctx, 4, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(ctx, CallNotification.CH_MISSED)
        } else {
            Notification.Builder(ctx)
        }
        b.setSmallIcon(R.drawable.ic_call)
            .setContentTitle(if (count == 1) "Cevapsız arama" else "$count cevapsız arama")
            .setContentText(if (count == 1) prefs.missedLastName else "Son: ${prefs.missedLastName}")
            .setNumber(count)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(Notification.PRIORITY_DEFAULT)
            .setCategory(if (Build.VERSION.SDK_INT >= 31) Notification.CATEGORY_MISSED_CALL else Notification.CATEGORY_CALL)
        try {
            nm.notify(ID, b.build())
        } catch (e: Exception) {
        }
    }
}
