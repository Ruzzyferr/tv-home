package app.swiip.tvhome

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Google TV may refuse a third-party default HOME app. When the stock launcher's home activity
 * comes to the front (Home button), this service immediately brings TV Home forward instead.
 * The service only receives window changes from the stock launcher packages (see res/xml/home_watcher.xml).
 */
class HomeWatcherService : AccessibilityService() {

    /**
     * At boot the stock home screen is already showing before this service binds, so no window
     * event arrives. Binding shortly after boot therefore means "TV just turned on": open TV Home.
     */
    override fun onServiceConnected() {
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS) openHome()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString().orEmpty()
        // `adb logcat -s TvHome` shows which stock windows appear, to tune PASS_THROUGH on a new TV.
        Log.d(TAG, "window: $pkg / $cls")
        if (pkg !in STOCK_LAUNCHERS || !cls.endsWith("Activity") || PASS_THROUGH.any { it in cls }) return
        openHome()
    }

    private fun openHome() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        )
    }

    override fun onInterrupt() = Unit

    private companion object {
        const val TAG = "TvHome"
        const val BOOT_WINDOW_MS = 3 * 60_000L
        val STOCK_LAUNCHERS = setOf("com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher")

        /** Stock windows that must stay usable: settings panel, notifications, inputs, search. */
        val PASS_THROUGH = listOf("Settings", "Dashboard", "Panel", "Notification", "Input", "Search")
    }
}
