package app.yomu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Relance la poignée au démarrage de la liseuse, si elle était active. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = Prefs(context)
        if (prefs.startOnBoot && prefs.handleEnabled && Settings.canDrawOverlays(context)) {
            try {
                EdgeService.start(context)
            } catch (_: Exception) {
            }
        }
    }
}
