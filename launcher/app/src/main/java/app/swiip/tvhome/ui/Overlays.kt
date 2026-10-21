package app.swiip.tvhome.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.ui.res.stringResource
import app.swiip.tvhome.AppCategory
import app.swiip.tvhome.AppLanguage
import app.swiip.tvhome.R
import app.swiip.tvhome.HomePrefs
import app.swiip.tvhome.QuickAction
import app.swiip.tvhome.SystemStats
import app.swiip.tvhome.TvApp
import app.swiip.tvhome.TvSource
import app.swiip.tvhome.Volume
import app.swiip.tvhome.formatBytes

/** Retries briefly: dialog windows attach a frame or two after composition. */
@Composable
private fun FocusOnOpen(requester: FocusRequester, after: () -> Unit = {}) {
    LaunchedEffect(Unit) {
        repeat(6) {
            withFrameNanos { }
            if (runCatching { requester.requestFocus() }.isSuccess) {
                after()
                return@LaunchedEffect
            }
        }
    }
}

@Composable
private fun PanelTitle(text: String) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 3.sp,
        color = Neon.Dim,
        modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 6.dp),
    )
}

/* ---------------- Launchpad ---------------- */

@Composable
fun Launchpad(apps: List<TvApp>, prefs: HomePrefs, onOpen: (TvApp) -> Unit, onMenu: (TvApp) -> Unit, onDismiss: () -> Unit) {
    GlassDialog(onDismiss, dim = 0.3f) {
        // This TV has no cross-window blur, so the grid gets its own near-opaque ground instead of the home screen showing through.
        Box(Modifier.fillMaxSize().background(Color(0xF0080B14)))
        var category by remember { mutableStateOf<AppCategory?>(null) }
        val visible = apps.filter { it.packageName !in prefs.hidden }
        val categories = AppCategory.entries.filter { c -> visible.any { prefs.categoryOf(it) == c } }
        val shown = if (category == null) visible else visible.filter { prefs.categoryOf(it) == category }
        val firstTile = remember { FocusRequester() }

        Column(Modifier.fillMaxSize().padding(top = 30.dp)) {
            // Focusing a category is enough to filter: no extra OK press.
            LazyRow(
                contentPadding = PaddingValues(horizontal = 64.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    PillButton(stringResource(R.string.lp_all), onClick = { runCatching { firstTile.requestFocus() } }, selected = category == null, trailing = "${visible.size}",
                        modifier = Modifier.onFocusChanged { if (it.isFocused) category = null })
                }
                items(categories) { c ->
                    PillButton(stringResource(c.titleRes), onClick = { runCatching { firstTile.requestFocus() } }, selected = category == c,
                        trailing = "${visible.count { prefs.categoryOf(it) == c }}",
                        modifier = Modifier.onFocusChanged { if (it.isFocused) category = c })
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                contentPadding = PaddingValues(start = 64.dp, end = 64.dp, top = 22.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(shown, key = { _, app -> app.packageName }) { index, app ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BannerCard(
                            app = app,
                            onClick = { onDismiss(); onOpen(app) },
                            onLongClick = { onMenu(app) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .then(if (index == 0) Modifier.focusRequester(firstTile) else Modifier),
                        )
                        Text(app.label, fontSize = 11.sp, color = Color(0xFFC9D1E6), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
        FocusOnOpen(firstTile)
    }
}

/* ---------------- Sources (stack above the Dock) ---------------- */

@Composable
fun SourcesPanel(sources: List<TvSource>, prefs: HomePrefs, onOpen: (TvSource) -> Unit, onMenu: (TvSource) -> Unit, onDismiss: () -> Unit) {
    GlassDialog(onDismiss, dim = 0.3f) {
        val first = remember { FocusRequester() }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 104.dp)
                .width(340.dp)
                .glass(RoundedCornerShape(24.dp), Neon.Panel)
                .padding(10.dp)
        ) {
            PanelTitle(stringResource(R.string.sources_title))
            if (sources.isEmpty()) {
                Text(stringResource(R.string.sources_empty), fontSize = 13.sp, color = Neon.Dim, modifier = Modifier.padding(8.dp))
            }
            val connectedText = stringResource(R.string.source_connected)
            val inDockText = stringResource(R.string.source_in_dock)
            sources.forEachIndexed { i, source ->
                val icon = when {
                    source.isPassthrough -> Glyphs.Input
                    source.label.contains("anten", ignoreCase = true) -> Glyphs.Antenna
                    else -> Glyphs.Tv
                }
                val inDock = HomePrefs.sourceId(source.id) in prefs.favorites
                PanelRow(
                    icon = icon,
                    title = source.label,
                    detail = listOfNotNull(source.detail, connectedText.takeIf { source.isPassthrough && source.connected }, inDockText.takeIf { inDock }).joinToString(" · "),
                    detailColor = if (source.isPassthrough && source.connected) Neon.Ok else Neon.Dim,
                    onClick = { onDismiss(); onOpen(source) },
                    onLongClick = { onMenu(source) },
                    leading = if (deviceStyleOf(source) != null) {
                        { DeviceBadge(source, markSize = 9.sp, modifier = Modifier.size(34.dp, 22.dp).clip(RoundedCornerShape(6.dp))) }
                    } else null,
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
        if (sources.isNotEmpty()) FocusOnOpen(first)
    }
}

/* ---------------- Control Center ---------------- */

@Composable
fun ControlCenter(stats: SystemStats?, remote: RemoteHint?, prefs: HomePrefs, actions: HomeActions, onDismiss: () -> Unit) {
    GlassDialog(onDismiss, dim = 0.3f) {
        val first = remember { FocusRequester() }
        fun open(action: QuickAction) {
            onDismiss()
            actions.openQuick(action)
        }
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 24.dp)
                .width(352.dp)
                .glass(RoundedCornerShape(26.dp), Neon.Panel)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val context = LocalContext.current
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ControlTile(
                    if (stats?.network == SystemStats.NetworkKind.ETHERNET) Glyphs.Ethernet else Glyphs.Wifi,
                    stringResource(R.string.cc_network), stats?.networkName(context) ?: "—", { open(QuickAction.WIFI) },
                    Modifier.weight(1f).focusRequester(first),
                )
                ControlTile(Glyphs.Bluetooth, "Bluetooth", stringResource(R.string.cc_bluetooth_detail), { open(QuickAction.BLUETOOTH) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Address and PIN only in the detailed view: guests in the room should not be able to read them off the screen.
                val mouse = when {
                    remote == null || !remote.enabled -> stringResource(R.string.cc_mouse_off)
                    remote.url == null -> stringResource(R.string.cc_mouse_no_network)
                    !prefs.detailed -> stringResource(R.string.cc_mouse_hidden)
                    else -> "${remote.url.removePrefix("http://")}\nPIN ${remote.pin}"
                }
                ControlTile(Glyphs.Phone, stringResource(R.string.cc_mouse), mouse, { }, Modifier.weight(1f))
                ControlTile(Glyphs.Picture, stringResource(R.string.cc_display), stringResource(R.string.cc_display_detail), { open(QuickAction.DISPLAY) }, Modifier.weight(1f))
            }
            VolumeSlider()
            if (stats != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(20.dp), Neon.Glass)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatLine(stringResource(R.string.cc_ram), stats.ramPercent, "${formatBytes(stats.ramUsed)} / ${formatBytes(stats.ramTotal)}")
                    StatLine(stringResource(R.string.cc_storage), stats.storagePercent, "${formatBytes(stats.storageUsed)} / ${formatBytes(stats.storageTotal)}")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(stringResource(R.string.cc_free_ram), actions::freeMemory, icon = Glyphs.Sliders)
                PillButton(stringResource(if (prefs.detailed) R.string.cc_details_on else R.string.cc_details_off), prefs::toggleDetailed, icon = Glyphs.Eye, selected = prefs.detailed)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(stringResource(R.string.cc_all_settings), { open(QuickAction.SETTINGS) }, icon = Glyphs.Gear)
                PillButton(stringResource(R.string.cc_apps), { open(QuickAction.APPS) }, icon = Glyphs.Grid)
                // Shows the language it switches to, in that language ("English" / "Türkçe").
                if (AppLanguage.isSupported()) PillButton(stringResource(R.string.cc_language), actions::toggleLanguage, icon = Glyphs.Globe)
            }
        }
        FocusOnOpen(first)
    }
}

@Composable
private fun ControlTile(icon: ImageVector, title: String, detail: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.height(84.dp).touchable(onClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Neon.Glass, focusedContainerColor = Neon.GlassHigh, focusedContentColor = Neon.Ink),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(Neon.Cyan, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = Neon.Space, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, fontSize = 10.sp, color = Neon.Dim, lineHeight = 13.sp, maxLines = 2)
            }
        }
    }
}

@Composable
private fun VolumeSlider() {
    val context = LocalContext.current
    var volume by remember { mutableIntStateOf(Volume.get(context)) }
    val fill by animateFloatAsState(volume / 100f, spring(dampingRatio = 0.8f, stiffness = 600f), label = "volume")
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(20.dp)
    Surface(
        onClick = { },
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val step = when (e.key) {
                    Key.DirectionLeft -> -5
                    Key.DirectionRight -> 5
                    else -> return@onPreviewKeyEvent false
                }
                volume = (volume + step).coerceIn(0, 100)
                Volume.set(context, volume)
                true
            },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Neon.Glass, focusedContainerColor = Neon.GlassHigh, focusedContentColor = Neon.Ink),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Glyphs.Volume, null, tint = Neon.Ink, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.cc_volume), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(if (focused) stringResource(R.string.cc_volume_hint) + "   $volume" else "$volume", fontSize = 11.sp, color = Neon.Dim)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Color(0x1AFFFFFF), RoundedCornerShape(50))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fill)
                        .fillMaxHeight()
                        .background(Neon.Edge, RoundedCornerShape(50))
                )
            }
        }
    }
}

@Composable
private fun StatLine(label: String, percent: Int, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(detail, fontSize = 11.sp, color = Neon.Dim)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Color(0x1AFFFFFF), RoundedCornerShape(50))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(percent / 100f)
                    .fillMaxHeight()
                    .background(if (percent > 85) Color(0xFFFBBF24) else Neon.Cyan, RoundedCornerShape(50))
            )
        }
    }
}

/* ---------------- Spotlight ---------------- */

private data class Hit(val title: String, val kind: String, val app: TvApp?, val glyph: ImageVector?, val run: () -> Unit)

private fun norm(s: String) = s.lowercase()
    .replace('ı', 'i').replace('ğ', 'g').replace('ü', 'u').replace('ş', 's').replace('ö', 'o').replace('ç', 'c')
    .replace("i̇", "i")

@Composable
fun Spotlight(
    apps: List<TvApp>,
    sources: List<TvSource>,
    prefs: HomePrefs,
    suggestions: List<TvApp>,
    actions: HomeActions,
    onDismiss: () -> Unit,
) {
    GlassDialog(onDismiss, dim = 0.35f) {
        var query by remember { mutableStateOf("") }
        val field = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        val q = norm(query.trim())
        val context = LocalContext.current
        val kindFavorite = stringResource(R.string.search_kind_favorite)
        val kindInput = stringResource(R.string.search_kind_input)
        val kindSetting = stringResource(R.string.search_kind_setting)
        val title = { c: AppCategory -> context.getString(c.titleRes) }
        val hits = if (q.isEmpty()) {
            suggestions.map { Hit(it.label, kindFavorite, it, null) { actions.openApp(it) } }
        } else {
            // Category names match too: "spor" / "sports" finds every live/sports app.
            apps.filter { it.packageName !in prefs.hidden && (q in norm(it.label) || q in norm(title(prefs.categoryOf(it)))) }
                .map { Hit(it.label, title(prefs.categoryOf(it)), it, null) { actions.openApp(it) } } +
                sources.filter { q in norm(it.label) || q in norm("$kindInput kaynak giris input hdmi") }
                    .map { Hit(it.label, kindInput, null, if (it.isPassthrough) Glyphs.Input else Glyphs.Tv) { actions.openSource(it) } } +
                QuickAction.entries.filter { it.intent != null && q in norm(context.getString(it.labelRes) + " $kindSetting") }
                    .map { Hit(context.getString(it.labelRes), kindSetting, null, Glyphs.Gear) { actions.openQuick(it) } }
        }.take(7)

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 54.dp)
                .width(600.dp)
                .glass(RoundedCornerShape(26.dp), Neon.Panel)
        ) {
            Row(Modifier.padding(horizontal = 22.dp).height(66.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Glyphs.Search, null, tint = Neon.Cyan, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(14.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 26.sp, color = Neon.Ink),
                    cursorBrush = SolidColor(Neon.Cyan),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { hits.firstOrNull()?.let { onDismiss(); it.run() } }),
                    modifier = Modifier.weight(1f).focusRequester(field),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) Text(stringResource(R.string.search_hint), fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 26.sp, color = Color(0xFF5D6886))
                            inner()
                        }
                    },
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Neon.Line))
            Column(Modifier.padding(8.dp)) {
                if (hits.isEmpty()) {
                    Text(stringResource(R.string.search_no_results, query), fontSize = 13.sp, color = Neon.Dim, modifier = Modifier.padding(14.dp))
                }
                hits.forEach { hit ->
                    HitRow(hit) {
                        onDismiss()
                        hit.run()
                    }
                }
            }
        }
        FocusOnOpen(field) { keyboard?.show() }
    }
}

@Composable
private fun HitRow(hit: Hit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp).touchable(onClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Neon.GlassHigh, focusedContentColor = Neon.Ink),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(Neon.Glass, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                val icon = hit.app?.icon
                when {
                    icon != null -> Image(icon, null, Modifier.size(34.dp))
                    hit.glyph != null -> Icon(hit.glyph, null, tint = Neon.Cyan, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Text(hit.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(hit.kind, fontSize = 11.sp, color = Neon.Dim)
        }
    }
}
