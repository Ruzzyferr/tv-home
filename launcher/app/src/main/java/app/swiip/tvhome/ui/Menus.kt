package app.swiip.tvhome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.DenseListItem
import androidx.tv.material3.Text
import app.swiip.tvhome.AppCategory
import app.swiip.tvhome.HomePrefs
import app.swiip.tvhome.TvApp
import app.swiip.tvhome.TvSource

/** [keepOpen] entries (e.g. moving a favorite) can be pressed repeatedly without closing the menu. */
data class MenuEntry(val label: String, val keepOpen: Boolean = false, val run: () -> Unit)

@Composable
fun AppMenu(app: TvApp, prefs: HomePrefs, actions: HomeActions, onPickCategory: () -> Unit, onDismiss: () -> Unit) {
    val pkg = app.packageName
    val isFavorite = pkg in prefs.favorites
    val entries = buildList {
        add(MenuEntry(if (isFavorite) "★  Dock'tan çıkar" else "☆  Dock'a ekle") { prefs.toggleFavorite(pkg) })
        if (isFavorite) {
            add(MenuEntry("◀  Dock'ta sola taşı", keepOpen = true) { prefs.moveFavorite(pkg, -1) })
            add(MenuEntry("▶  Dock'ta sağa taşı", keepOpen = true) { prefs.moveFavorite(pkg, +1) })
        }
        add(MenuEntry("▦  Kategori değiştir", run = onPickCategory))
        if (app.isBrowser && prefs.browser != pkg) {
            add(MenuEntry("🌐  Varsayılan tarayıcı yap") { actions.makeDefaultBrowser(app) })
        }
        add(MenuEntry(if (pkg in prefs.hidden) "👁  Göster" else "🙈  Gizle") { prefs.toggleHidden(pkg) })
        add(MenuEntry("ⓘ  Uygulama bilgisi") { actions.appInfo(app) })
        add(MenuEntry("🗑  Kaldır") { actions.uninstall(app) })
    }
    MenuDialog(app.label, prefs.categoryOf(app).title, entries, onDismiss)
}

/** Long-press on a TV input (Kaynaklar or the Dock): pin it to the Dock for one-press switching, or reorder it. */
@Composable
fun SourceMenu(source: TvSource, prefs: HomePrefs, actions: HomeActions, onDismiss: () -> Unit) {
    val id = HomePrefs.sourceId(source.id)
    val pinned = id in prefs.favorites
    val entries = buildList {
        add(MenuEntry("▶  Aç") { actions.openSource(source) })
        add(MenuEntry(if (pinned) "★  Dock'tan çıkar" else "☆  Dock'a ekle") { prefs.toggleFavorite(id) })
        if (pinned) {
            add(MenuEntry("◀  Dock'ta sola taşı", keepOpen = true) { prefs.moveFavorite(id, -1) })
            add(MenuEntry("▶  Dock'ta sağa taşı", keepOpen = true) { prefs.moveFavorite(id, +1) })
        }
    }
    MenuDialog(source.label, source.detail, entries, onDismiss)
}

@Composable
fun CategoryPicker(app: TvApp, prefs: HomePrefs, onDismiss: () -> Unit) {
    val current = prefs.categoryOf(app)
    val entries = listOf(MenuEntry("✦  Otomatik (${app.autoCategory.title})") { prefs.setCategory(app.packageName, null) }) +
        AppCategory.entries.map { category ->
            MenuEntry((if (category == current) "●  " else "○  ") + category.title) {
                prefs.setCategory(app.packageName, category)
            }
        }
    MenuDialog("Kategori seç", app.label, entries, onDismiss)
}

@Composable
private fun MenuDialog(title: String, subtitle: String?, entries: List<MenuEntry>, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    val shape = RoundedCornerShape(20.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .ignoreHeldOk()
                .width(440.dp)
                .heightIn(max = 500.dp)
                .background(Neon.Panel, shape)
                .border(1.5.dp, Neon.Edge, shape)
                .padding(18.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it.uppercase(), color = Neon.Cyan, fontSize = 12.sp, letterSpacing = 3.sp) }
            Spacer(Modifier.height(12.dp))
            entries.forEachIndexed { index, entry ->
                val select = {
                    entry.run()
                    if (!entry.keepOpen) onDismiss()
                }
                DenseListItem(
                    selected = false,
                    onClick = select,
                    headlineContent = { Text(entry.label) },
                    modifier = (if (index == 0) Modifier.focusRequester(first) else Modifier).touchable(select),
                )
            }
        }
        LaunchedEffect(Unit) {
            // The dialog window attaches a frame or two later; retry briefly instead of crashing.
            repeat(5) {
                withFrameNanos { }
                if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            }
        }
    }
}
