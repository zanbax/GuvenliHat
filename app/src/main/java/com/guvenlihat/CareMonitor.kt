package com.guvenlihat

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.CallLog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bakım modülü (isteğe bağlı, varsayılanda kapalı):
 *  - Hareketsizlik uyarısı: belirlenen süre telefon hiç kullanılmazsa aileye haber verir
 *    (uyku saatleri sayılmaz).
 *  - Aralıklı konum: belirlenen saatler arasında düzenli olarak konum ve pil bilgisi gönderir.
 * Her 30 dakikada bir alarmla uyanır; sürekli çalışan bir servis yoktur (pil dostu).
 */
object CareMonitor {

    private const val ACTION_TICK = "com.guvenlihat.CARE_TICK"
    private const val TICK_MS = 30 * 60_000L
    private const val HOUR = 60 * 60_000L

    // ---------------- Planlama ----------------

    fun isActive(prefs: Prefs) = prefs.inactivityHours > 0 || prefs.locationEnabled

    /** Bir sonraki kontrolü kurar (gerekmiyorsa iptal eder). */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = tickIntent(ctx)
        if (!isActive(Prefs(ctx))) {
            am.cancel(pi)
            return
        }
        // Belirli saatlerde gönderimde bir sonraki saate hizala (en geç 30 dk sonra yine uyan)
        var delay = TICK_MS
        val next = nextScheduledTime(Prefs(ctx), System.currentTimeMillis())
        if (next != null) delay = minOf(delay, maxOf(60_000L, next - System.currentTimeMillis() + 60_000L))
        try {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay, pi)
        } catch (e: Exception) {
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay, pi)
        }
    }

    private fun tickIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 10, Intent(ctx, CareReceiver::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Uygulama içinde bir şey yapıldığında (ekran açıldı, arama) çağrılır. */
    fun markActive(ctx: Context) {
        Prefs(ctx).lastActivityAt = System.currentTimeMillis()
    }

    // ---------------- Her 30 dakikada bir ----------------

    fun onTick(ctx: Context, done: () -> Unit) {
        schedule(ctx)
        val prefs = Prefs(ctx)
        val now = System.currentTimeMillis()

        val inactivityAlert = checkInactivity(ctx, prefs, now)
        val locationDue = isLocationDue(prefs, now)

        if (inactivityAlert == null && !locationDue) {
            done()
            return
        }
        getLocation(ctx) { loc ->
            val extra = locationText(loc) + " " + batteryText(ctx)
            if (inactivityAlert != null) Notifier.send(ctx, "$inactivityAlert $extra")
            if (locationDue) {
                prefs.lastLocationSentAt = now
                val time = SimpleDateFormat("HH:mm", Locale("tr")).format(Date(now))
                Notifier.send(ctx, "📍 Konum ($time): $extra")
                showSharedNotice(ctx, time)
            }
            done()
        }
    }

    // ---------------- Hareketsizlik ----------------

    /** Uyarı gerekiyorsa mesaj metni, yoksa null. "Tekrar hareket var" mesajını kendisi gönderir. */
    private fun checkInactivity(ctx: Context, prefs: Prefs, now: Long): String? {
        val hours = prefs.inactivityHours
        if (hours <= 0) return null
        val last = lastActivity(ctx, prefs, now)
        if (last <= 0L) {
            prefs.lastActivityAt = now // ilk çalışma: başlangıç noktası
            return null
        }
        val awake = awakeMillis(last, now, prefs.sleepStart, prefs.sleepEnd)
        if (awake < hours * HOUR) {
            if (prefs.inactivityAlerted) {
                prefs.inactivityAlerted = false
                Notifier.send(ctx, "✓ Telefon tekrar kullanılıyor, hareketsizlik uyarısı kapandı.")
            }
            return null
        }
        // Uyku saatlerinde kimseyi uyandırma; sabah ilk kontrolde gönderilir
        if (inHours(hourOf(now), prefs.sleepStart, prefs.sleepEnd)) return null
        if (prefs.inactivityAlerted) return null
        prefs.inactivityAlerted = true
        val lastText = SimpleDateFormat("d MMMM HH:mm", Locale("tr")).format(Date(last))
        return "⚠ HAREKETSİZLİK: Telefon $hours saattir (uyku saatleri hariç) hiç kullanılmadı. " +
            "Son hareket: $lastText. Lütfen arayıp kontrol edin."
    }

    /** Son kullanım zamanı: ekran açılması, uygulama kullanımı ve aramalardan en yenisi. */
    fun lastActivity(ctx: Context, prefs: Prefs, now: Long): Long {
        var last = prefs.lastActivityAt
        if (hasUsageAccess(ctx)) {
            try {
                val usm = ctx.getSystemService(UsageStatsManager::class.java)
                val from = maxOf(last, now - 3 * 24 * HOUR)
                val events = usm?.queryEvents(from, now)
                val e = UsageEvents.Event()
                val wakes = prefs.systemWakes()
                while (events != null && events.hasNextEvent()) {
                    events.getNextEvent(e)
                    if (!isUserEvent(e.eventType)) continue
                    // Sadece ekranın yanması, gelen arama ya da kendi bildirimimiz yüzünden olduysa sayma
                    if (isScreenOnOnly(e.eventType) && wakes.any { e.timeStamp - it in -5_000L..120_000L }) continue
                    last = maxOf(last, e.timeStamp)
                }
            } catch (ex: Exception) {
            }
        }
        if (PhoneUtils.has(ctx, Manifest.permission.READ_CALL_LOG)) {
            try {
                ctx.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.TYPE),
                    null, null, CallLog.Calls.DATE + " DESC",
                )?.use { c ->
                    var n = 0
                    while (c.moveToNext() && n < 20) {
                        n++
                        val type = c.getInt(2)
                        if (type == CallLog.Calls.INCOMING_TYPE || type == CallLog.Calls.OUTGOING_TYPE) {
                            last = maxOf(last, c.getLong(0) + c.getLong(1) * 1000L)
                            break
                        }
                    }
                }
            } catch (ex: Exception) {
            }
        }
        if (last > prefs.lastActivityAt) prefs.lastActivityAt = last
        return last
    }

    private fun isUserEvent(type: Int): Boolean {
        @Suppress("DEPRECATION")
        if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) return true
        if (type == UsageEvents.Event.USER_INTERACTION) return true
        if (Build.VERSION.SDK_INT >= 28 &&
            (type == UsageEvents.Event.SCREEN_INTERACTIVE || type == UsageEvents.Event.KEYGUARD_HIDDEN)
        ) return true
        return false
    }

    private fun isScreenOnOnly(type: Int): Boolean =
        Build.VERSION.SDK_INT >= 28 && type == UsageEvents.Event.SCREEN_INTERACTIVE

    /** Kullanıcı dokunmadan telefonun uyanabileceği bir an (hareketsizlik hesabında yok sayılır). */
    fun markSystemWake(ctx: Context) {
        Prefs(ctx).addSystemWake(System.currentTimeMillis())
    }

    @Suppress("DEPRECATION")
    fun hasUsageAccess(ctx: Context): Boolean = try {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        false
    }

    /** İki zaman arasında uyku saatlerine denk gelmeyen süre (5 dakikalık adımlarla). */
    fun awakeMillis(start: Long, end: Long, sleepStart: Int, sleepEnd: Int): Long {
        if (end <= start) return 0L
        val step = 5 * 60_000L
        val cal = Calendar.getInstance()
        var t = start
        var total = 0L
        while (t < end) {
            cal.timeInMillis = t
            if (!inHours(cal.get(Calendar.HOUR_OF_DAY), sleepStart, sleepEnd)) total += minOf(step, end - t)
            t += step
        }
        return total
    }

    fun hourOf(time: Long): Int = Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.HOUR_OF_DAY)

    /** Saat [start, end) aralığında mı? Gece yarısını aşan aralıkları da anlar (22→8). start == end: aralık yok. */
    fun inHours(hour: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> hour in start until end
        else -> hour >= start || hour < end
    }

    // ---------------- Konum ----------------

    /** Bugün (veya yarın) saat h:00'ın zaman damgası. */
    private fun atHour(now: Long, h: Int, dayOffset: Int = 0): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** "Belirli saatlerde" modunda bir sonraki gönderim zamanı. */
    private fun nextScheduledTime(prefs: Prefs, now: Long): Long? {
        if (!prefs.locationEnabled || !prefs.locationAtTimes) return null
        val hours = prefs.locationTimes
        if (hours.isEmpty()) return null
        return hours.map { atHour(now, it) }.filter { it > now }.minOrNull() ?: atHour(now, hours.first(), 1)
    }

    private fun isLocationDue(prefs: Prefs, now: Long): Boolean {
        if (!prefs.locationEnabled) return false
        if (prefs.locationAtTimes) {
            // Seçilen saat geldiyse ve o saatten beri gönderilmediyse (en fazla 2 saat geç kalınabilir)
            return prefs.locationTimes.any { h ->
                val t = atHour(now, h)
                now >= t && now - t < 2 * HOUR && prefs.lastLocationSentAt < t
            }
        }
        val h = hourOf(now)
        // start == end "bütün gün" demek
        if (prefs.locationStart != prefs.locationEnd && !inHours(h, prefs.locationStart, prefs.locationEnd)) return false
        // 10 dakika tolerans: 30 dakikalık kontrol aralığı kaymasın
        return now - prefs.lastLocationSentAt >= prefs.locationIntervalHours * HOUR - 10 * 60_000L
    }

    fun hasLocationPermission(ctx: Context) =
        PhoneUtils.has(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ||
            PhoneUtils.has(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasBackgroundLocation(ctx: Context) =
        Build.VERSION.SDK_INT < 29 || PhoneUtils.has(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    /** Güncel konumu dener (en çok ~9 sn), olmazsa bilinen son konumu verir. */
    @Suppress("MissingPermission", "DEPRECATION")
    fun getLocation(ctx: Context, cb: (Location?) -> Unit) {
        if (!hasLocationPermission(ctx)) {
            cb(null)
            return
        }
        val lm = ctx.getSystemService(LocationManager::class.java)
        if (lm == null) {
            cb(null)
            return
        }
        val candidates = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) candidates.add(LocationManager.FUSED_PROVIDER)
        candidates.add(LocationManager.NETWORK_PROVIDER)
        candidates.add(LocationManager.GPS_PROVIDER)
        val providers = candidates.filter {
            try {
                lm.isProviderEnabled(it)
            } catch (e: Exception) {
                false
            }
        }
        val lastKnown = providers.mapNotNull {
            try {
                lm.getLastKnownLocation(it)
            } catch (e: Exception) {
                null
            }
        }.maxByOrNull { it.time }
        if (providers.isEmpty()) {
            cb(lastKnown)
            return
        }

        val finished = AtomicBoolean(false)
        val main = Handler(Looper.getMainLooper())
        val finish: (Location?) -> Unit = { loc ->
            if (finished.compareAndSet(false, true)) cb(loc ?: lastKnown)
        }
        main.postDelayed({ finish(null) }, 9_000)
        val provider = providers.first()
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(provider, null, ctx.mainExecutor) { loc -> finish(loc) }
            } else {
                main.post {
                    try {
                        lm.requestSingleUpdate(provider, object : LocationListener {
                            override fun onLocationChanged(location: Location) = finish(location)
                            override fun onStatusChanged(p: String?, status: Int, extras: Bundle?) {}
                            override fun onProviderEnabled(p: String) {}
                            override fun onProviderDisabled(p: String) = finish(null)
                        }, Looper.getMainLooper())
                    } catch (e: Exception) {
                        finish(null)
                    }
                }
            }
        } catch (e: Exception) {
            finish(null)
        }
    }

    /**
     * Şeffaflık: konum her gönderildiğinde korunan kişinin telefonunda sessiz bir bildirim.
     * Ses, titreşim yok, ekranı uyandırmaz; yenisi eskisinin yerine geçer.
     */
    @Suppress("DEPRECATION")
    private fun showSharedNotice(ctx: Context, time: String) {
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java) ?: return
        markSystemWake(ctx)
        val open = PendingIntent.getActivity(
            ctx, 5, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = if (Build.VERSION.SDK_INT >= 26) {
            android.app.Notification.Builder(ctx, CallNotification.CH_CARE)
        } else {
            android.app.Notification.Builder(ctx).setPriority(android.app.Notification.PRIORITY_LOW)
        }
        b.setSmallIcon(R.drawable.ic_location)
            .setContentTitle("📍 Konumunuz yakınlarınıza gönderildi")
            .setContentText("Saat $time. Aileniz ayarlardan bunu kapatabilir.")
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(open)
        try {
            nm.notify(1003, b.build())
        } catch (e: Exception) {
        }
    }

    fun locationText(loc: Location?): String {
        if (loc == null) return "Konum alınamadı (telefonun konumu kapalı olabilir)."
        val lat = String.format(Locale.US, "%.5f", loc.latitude)
        val lng = String.format(Locale.US, "%.5f", loc.longitude)
        val age = (System.currentTimeMillis() - loc.time) / 60_000L
        val details = listOfNotNull(
            if (loc.hasAccuracy()) "yaklaşık ${loc.accuracy.toInt()} m" else null,
            if (age >= 15) "$age dk önceki konum" else null,
        ).joinToString(", ")
        val suffix = if (details.isEmpty()) "" else " ($details)"
        return "Harita: https://maps.google.com/?q=$lat,$lng$suffix"
    }

    fun batteryText(ctx: Context): String = try {
        val level = ctx.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (level in 0..100) "Pil: %$level." else ""
    } catch (e: Exception) {
        ""
    }

    /** Ayarlardaki "Şimdi gönder" düğmesi. */
    fun sendLocationNow(ctx: Context) {
        val app = ctx.applicationContext
        getLocation(app) { loc ->
            Notifier.send(app, "📍 Konum (elle istendi): " + locationText(loc) + " " + batteryText(app))
            showSharedNotice(app, SimpleDateFormat("HH:mm", Locale("tr")).format(Date()))
        }
    }
}

/** 30 dakikalık kontrol alarmı; telefon açılınca ve uygulama güncellenince planı yeniden kurar. */
class CareReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            "com.guvenlihat.CARE_TICK" -> {
                val pending = goAsync()
                Thread {
                    try {
                        CareMonitor.onTick(app) {
                            try {
                                pending.finish()
                            } catch (e: Exception) {
                            }
                        }
                    } catch (e: Exception) {
                        try {
                            pending.finish()
                        } catch (ignored: Exception) {
                        }
                    }
                }.start()
            }
            else -> CareMonitor.schedule(app) // BOOT_COMPLETED, MY_PACKAGE_REPLACED
        }
    }
}
