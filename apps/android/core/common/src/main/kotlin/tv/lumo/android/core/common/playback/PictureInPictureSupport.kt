package tv.lumo.android.core.common.playback

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process

/**
 * Whether this device, and this installation, can show a floating window (S15-01/02, ADR 0013).
 *
 * Two questions, asked separately because they are answered by different people:
 *
 * - [deviceSupports] — the hardware and the system. Android 8 or later (the
 *   window's parameters and actions start there) and the platform feature.
 *   A television answers no on purpose: ADR 0013 keeps it out, and the TV
 *   application never asks.
 * - [allowedByUser] — the per-application switch in Android's own settings
 *   ("Picture-in-picture" → Lumo). It can change while the application runs,
 *   so it is asked at the moment a window is about to open, never cached.
 */
object PictureInPictureSupport {

    fun deviceSupports(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val packages = context.packageManager
        return packages.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
            !packages.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    fun allowedByUser(context: Context): Boolean {
        if (!deviceSupports(context)) return false
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return true
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
