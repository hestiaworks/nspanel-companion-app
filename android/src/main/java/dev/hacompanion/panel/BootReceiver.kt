package dev.hacompanion.panel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Whether the dashboard has to be started, for the reason given.
 *
 * Being replaced is the case that matters most and was missing: Android
 * kills the app to install the new version and starts nothing afterwards,
 * so the panel is left showing whatever was behind it — on this hardware,
 * the vendor's launcher. The updater add-on installs unattended, so nobody
 * is standing at the wall to notice, and the panel simply stops being a
 * dashboard until someone touches it.
 *
 * Boot is the opposite: Android starts the selected Home activity itself,
 * and starting it again on this firmware produces a second task. So at boot
 * this is only a fallback for when some other launcher is Home.
 */
fun shouldRelaunch(action: String?, isHome: Boolean): Boolean = when (action) {
    Intent.ACTION_MY_PACKAGE_REPLACED -> true
    Intent.ACTION_BOOT_COMPLETED -> !isHome
    else -> false
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val selectedHome = context.packageManager.resolveActivity(
            homeIntent,
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
        )
        val isHome = selectedHome?.activityInfo?.packageName == context.packageName
        if (!shouldRelaunch(intent.action, isHome)) return

        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
        )
    }
}
