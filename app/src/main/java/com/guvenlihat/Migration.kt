package com.guvenlihat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Güvenli Hat (com.guvenlihat) -> WeAreNear (app.wearenear) geçişi.
 *
 * İki uygulama bir süre aynı telefonda birlikte durabilir. Aileye aynı mesaj iki kez gitmesin diye
 * eski uygulama, yeni uygulama kurulup varsayılan telefon uygulaması olunca "emekliye ayrılır":
 * aile mesajlarını ve arka plan kontrollerini (konum, hareketsizlik, pil, açılış) durdurur.
 * Yeni uygulama kurulmadan ya da kurulumu bitmeden eski uygulama korumaya aynen devam eder.
 */
object Migration {
    const val OLD_PACKAGE = "com.guvenlihat"
    const val NEW_PACKAGE = "app.wearenear"

    fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }

    /** Bu, eski uygulamaysa ve görevi yeni uygulamaya geçtiyse true. */
    fun retired(ctx: Context): Boolean =
        BuildConfig.LEGACY && isInstalled(ctx, NEW_PACKAGE) && !Setup.isDefaultDialer(ctx)

    /** Yeni uygulamada: eski uygulama hâlâ telefonda mı? */
    fun oldAppStillInstalled(ctx: Context): Boolean = !BuildConfig.LEGACY && isInstalled(ctx, OLD_PACKAGE)

    /** Uygulama bilgileri ekranı; oradan "Kaldır" ile silinir. */
    fun openAppDetails(ctx: Context, pkg: String) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
        }
    }

    fun launch(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            false
        }
    }
}
