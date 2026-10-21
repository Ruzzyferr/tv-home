package app.swiip.tvhome

import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes

/** System shortcuts used by Control Center and Spotlight. [intent] is null for actions handled inside the launcher. */
enum class QuickAction(@StringRes val labelRes: Int, val glyph: String, val intent: Intent?) {
    WEB(R.string.qa_web, "🌐", null),
    SETTINGS(R.string.qa_settings, "⚙", Intent(Settings.ACTION_SETTINGS)),
    WIFI(R.string.qa_wifi, "📶", Intent(Settings.ACTION_WIFI_SETTINGS)),
    BLUETOOTH(R.string.qa_bluetooth, "🎮", Intent(Settings.ACTION_BLUETOOTH_SETTINGS)),
    DISPLAY(R.string.qa_display, "🖥", Intent(Settings.ACTION_DISPLAY_SETTINGS)),
    APPS(R.string.qa_apps, "📦", Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)),
    ACCESSIBILITY(R.string.qa_accessibility, "♿", Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
    TOGGLE_HIDDEN(R.string.qa_hidden, "👁", null),
}
