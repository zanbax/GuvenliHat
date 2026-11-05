package com.guvenlihat

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.telecom.TelecomManager

/** Kurulum adımları: varsayılan telefon uygulaması, izinler, Xiaomi ayarları. */
object Setup {

    fun isDefaultDialer(ctx: Context): Boolean = try {
        ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage == ctx.packageName
    } catch (e: Exception) {
        false
    }

    fun requestDefaultDialer(act: Activity, requestCode: Int) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = act.getSystemService(RoleManager::class.java)
                act.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER), requestCode)
            } else {
                val i = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                    .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, act.packageName)
                act.startActivityForResult(i, requestCode)
            }
        } catch (e: Exception) {
            openDefaultApps(act)
        }
    }

    fun hasScreeningRole(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 29) return true
        return try {
            ctx.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        } catch (e: Exception) {
            false
        }
    }

    fun requestScreeningRole(act: Activity, requestCode: Int) {
        if (Build.VERSION.SDK_INT < 29) return
        try {
            val rm = act.getSystemService(RoleManager::class.java)
            act.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), requestCode)
        } catch (e: Exception) {
        }
    }

    fun neededPermissions(prefs: Prefs): List<String> {
        val list = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
        )
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        if (prefs.notifyMethod == NotifyMethod.SMS) list.add(Manifest.permission.SEND_SMS)
        return list
    }

    fun missingPermissions(ctx: Context, prefs: Prefs): List<String> =
        neededPermissions(prefs).filter { !PhoneUtils.has(ctx, it) }

    fun isIgnoringBattery(ctx: Context): Boolean = try {
        ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true
    } catch (e: Exception) {
        false
    }

    fun requestIgnoreBattery(act: Activity) {
        val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + act.packageName))
        if (!tryStart(act, i)) openAppDetails(act)
    }

    fun isXiaomi(): Boolean = listOf("xiaomi", "redmi", "poco").any {
        Build.MANUFACTURER.contains(it, ignoreCase = true) || Build.BRAND.contains(it, ignoreCase = true)
    }

    fun openXiaomiAutostart(act: Activity) {
        val i = Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        )
        if (!tryStart(act, i)) openAppDetails(act)
    }

    /** "Kilit ekranında göster" ve "Arka planda açılır pencere" izinlerinin olduğu MIUI ekranı. */
    fun openXiaomiPermissions(act: Activity) {
        val candidates = listOf(
            "com.miui.permcenter.permissions.PermissionsEditorActivity",
            "com.miui.permcenter.permissions.AppPermissionsEditorActivity",
        )
        for (cls in candidates) {
            val i = Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName("com.miui.securitycenter", cls)
                .putExtra("extra_pkgname", act.packageName)
            if (tryStart(act, i)) return
        }
        openAppDetails(act)
    }

    fun openAppDetails(act: Activity) {
        tryStart(act, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + act.packageName)))
    }

    fun openDefaultApps(act: Activity) {
        if (!tryStart(act, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))) openAppDetails(act)
    }

    fun openWhatsApp(act: Activity): Boolean {
        val i = act.packageManager.getLaunchIntentForPackage("com.whatsapp") ?: return false
        return tryStart(act, i)
    }

    private fun tryStart(act: Activity, i: Intent): Boolean = try {
        act.startActivity(i)
        true
    } catch (e: Exception) {
        false
    }
}
