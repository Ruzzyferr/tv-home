package app.swiip.tvhome

import android.content.Intent
import android.provider.Settings

/** System shortcuts shown in the "Kontrol Merkezi" row. [intent] is null for actions handled inside the launcher. */
enum class QuickAction(val label: String, val glyph: String, val intent: Intent?) {
    WEB("İnternet", "🌐", null),
    SETTINGS("Ayarlar", "⚙", Intent(Settings.ACTION_SETTINGS)),
    WIFI("Ağ", "📶", Intent(Settings.ACTION_WIFI_SETTINGS)),
    BLUETOOTH("Bluetooth", "🎮", Intent(Settings.ACTION_BLUETOOTH_SETTINGS)),
    DISPLAY("Görüntü & Ses", "🖥", Intent(Settings.ACTION_DISPLAY_SETTINGS)),
    APPS("Uygulama yönetimi", "📦", Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)),
    ACCESSIBILITY("Erişilebilirlik", "♿", Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
    TOGGLE_HIDDEN("Gizli uygulamalar", "👁", null),
}
