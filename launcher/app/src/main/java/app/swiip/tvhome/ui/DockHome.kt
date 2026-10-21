package app.swiip.tvhome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.swiip.tvhome.AppCategory
import app.swiip.tvhome.R
import app.swiip.tvhome.HomePrefs
import app.swiip.tvhome.QuickAction
import app.swiip.tvhome.SystemStats
import app.swiip.tvhome.TvApp
import app.swiip.tvhome.TvSource
import app.swiip.tvhome.Usage
import app.swiip.tvhome.WatchItem
import app.swiip.tvhome.WatchNext
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import app.swiip.tvhome.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.exp

interface HomeActions {
    fun openApp(app: TvApp)
    fun openSource(source: TvSource)
    fun openQuick(action: QuickAction)
    fun openWeb()
    fun appInfo(app: TvApp)
    fun uninstall(app: TvApp)
    fun makeDefaultBrowser(app: TvApp)
    fun freeMemory()
    fun openWatch(item: WatchItem)
    fun toggleLanguage()
}

/** [url] is null when the TV has no network; [enabled] is false until the PointerService is switched on. */
data class RemoteHint(val url: String?, val pin: String, val enabled: Boolean)

/** What the top-left area describes: an app, something you were watching in it, or a Dock tool. */
private sealed interface Hero {
    data class App(val app: TvApp) : Hero
    data class Content(val item: WatchItem, val app: TvApp?) : Hero
    data class Source(val source: TvSource) : Hero
    data class Tool(val title: String, val subtitle: String) : Hero
}

private val Hero.app: TvApp?
    get() = when (this) {
        is Hero.App -> app
        is Hero.Content -> app
        is Hero.Source, is Hero.Tool -> null
    }

enum class Overlay { None, Launchpad, Sources, Control, Search }

private data class DockTool(val overlay: Overlay, @StringRes val titleRes: Int, @StringRes val subtitleRes: Int, val icon: ImageVector)

private val DockTools = listOf(
    DockTool(Overlay.Launchpad, R.string.tool_launchpad, R.string.tool_launchpad_sub, Glyphs.Grid),
    DockTool(Overlay.Sources, R.string.tool_sources, R.string.tool_sources_sub, Glyphs.Input),
    DockTool(Overlay.Search, R.string.tool_search, R.string.tool_search_sub, Glyphs.Search),
    DockTool(Overlay.Control, R.string.tool_control, R.string.tool_control_sub, Glyphs.Sliders),
)

private val NotContent = setOf("com.android.vending", "com.android.tv.settings", "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx")

private val NotContentCategories = setOf(AppCategory.TOOLS, AppCategory.SYSTEM, AppCategory.OTHER)

private val DockIcon = 56.dp
private val SidePadding = 64.dp

@Composable
fun DockHome(
    apps: List<TvApp>,
    sources: List<TvSource>,
    prefs: HomePrefs,
    remote: RemoteHint?,
    stats: SystemStats?,
    lastUsed: Map<String, Long>,
    watching: List<WatchItem>,
    homeSignal: Int,
    actions: HomeActions,
) {
    val context = LocalContext.current
    var hero by remember { mutableStateOf<Hero?>(null) }
    var lastApp by remember { mutableStateOf<TvApp?>(null) }
    var overlay by remember { mutableStateOf(Overlay.None) }
    var menuApp by remember { mutableStateOf<TvApp?>(null) }
    var categoryApp by remember { mutableStateOf<TvApp?>(null) }
    var dockFocus by remember { mutableIntStateOf(-1) }
    val dockFirst = remember { FocusRequester() }

    val byPackage = remember(apps) { apps.associateBy { it.packageName } }
    // Remembered so focus changes (which recompose this function) hand the Dock and rows the same list
    // instances; with strong skipping they then skip recomposition instead of rebuilding every frame.
    val favoriteIds = prefs.favorites.toList()
    val hiddenIds = prefs.hidden.toList()
    val sourcesById = remember(sources) { sources.associateBy { it.id } }
    val dockEntries = remember(byPackage, sourcesById, favoriteIds, hiddenIds) {
        favoriteIds.mapNotNull { id ->
            val input = HomePrefs.inputIdOf(id)
            when {
                input != null -> sourcesById[input]?.let { DockEntry.Source(it) }
                id in hiddenIds -> null
                else -> byPackage[id]?.let { DockEntry.App(it) }
            }
        }
    }
    val favorites = remember(dockEntries) { dockEntries.filterIsInstance<DockEntry.App>().map { it.app } }
    var sourceMenu by remember { mutableStateOf<TvSource?>(null) }
    // "Devam et" is for things you watch or play, so system screens (Settings, Play Store) stay out.
    val categoryOverrides = prefs.categoryOverrides.toMap()
    val recents = remember(byPackage, lastUsed, hiddenIds, categoryOverrides) {
        lastUsed.entries.sortedByDescending { it.value }
            .mapNotNull { byPackage[it.key] }
            // Tools (AdGuard runs in the background and counts as "used") and system apps are not something to continue.
            .filter { it.packageName !in hiddenIds && it.packageName !in NotContent && prefs.categoryOf(it) !in NotContentCategories }
            .take(10)
    }

    fun focusApp(app: TvApp) {
        hero = Hero.App(app)
        lastApp = app
    }

    fun goHome() {
        overlay = Overlay.None
        runCatching { dockFirst.requestFocus() }
    }
    BackHandler { goHome() }
    LaunchedEffect(homeSignal) { if (homeSignal > 0) goHome() }
    LaunchedEffect(apps.isNotEmpty()) {
        repeat(6) {
            withFrameNanos { }
            if (runCatching { dockFirst.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    // Artwork and backdrop follow focus only once it rests for a moment, so fast D-pad runs stay smooth.
    val focusedApp = hero?.app ?: lastApp
    val settledApp by produceState(focusedApp, focusedApp) {
        delay(180)
        value = focusedApp
    }

    // Backdrop colours: the focused app's, or a pinned device's brand colours (PlayStation blue).
    val focusedAmbience = when (val h = hero) {
        is Hero.Source -> deviceStyleOf(h.source)?.let { Ambience(it.a, it.b, null) }
        else -> focusedApp?.let { Ambience(it.colorA, it.colorB, it.backdrop) }
    }
    val settledAmbience by produceState(focusedAmbience, focusedAmbience) {
        delay(180)
        value = focusedAmbience
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop(settledAmbience)
        MenuBar(
            title = when (val h = hero) { is Hero.App -> h.app.label; is Hero.Content -> h.item.title; is Hero.Source -> h.source.label; is Hero.Tool -> h.title; null -> "" },
            stats = stats,
            remote = remote,
            detailed = prefs.detailed,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        HeroInfo(
            hero = hero,
            prefs = prefs,
            lastUsed = lastUsed,
            onOpen = {
                when (val h = hero) {
                    is Hero.Content -> actions.openWatch(h.item)
                    else -> lastApp?.let(actions::openApp)
                }
            },
            onMenu = { lastApp?.let { menuApp = it } },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = SidePadding, top = 74.dp),
        )
        HeroArt(
            // Inputs show their device badge; Dock tools have no artwork (the last app's banner there was misleading).
            app = if (hero is Hero.Tool || hero is Hero.Source) null else settledApp,
            source = (hero as? Hero.Source)?.source,
            content = (hero as? Hero.Content)?.item,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 66.dp, end = SidePadding),
        )

        // An app whose video/episode is already in the row is not shown a second time as a plain app card.
        val watchingApps = watching.map { it.packageName }.toSet()
        val row = recents.ifEmpty { favorites }.filter { it.packageName !in watchingApps }
        if (row.isNotEmpty() || watching.isNotEmpty()) {
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 290.dp)
            ) {
                SectionLabel(
                    stringResource(if (recents.isNotEmpty() || watching.isNotEmpty()) R.string.home_continue else R.string.home_favorites),
                    watching.size + row.size,
                    Modifier.padding(start = SidePadding),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = SidePadding, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // What you were actually watching (episode, video, film) comes before plain apps.
                    items(watching, key = { "w:" + it.packageName + it.title }) { item ->
                        val app = byPackage[item.packageName]
                        WatchCard(
                            item = item,
                            app = app,
                            onClick = { actions.openWatch(item) },
                            onLongClick = { app?.let { menuApp = it } },
                            modifier = Modifier
                                .size(156.dp, 88.dp)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        hero = Hero.Content(item, app)
                                        if (app != null) lastApp = app
                                    }
                                },
                        )
                    }
                    items(row, key = { it.packageName }) { app ->
                        BannerCard(
                            app = app,
                            caption = lastUsed[app.packageName]?.let { Usage.relative(context, it) },
                            onClick = { actions.openApp(app) },
                            onLongClick = { menuApp = app },
                            modifier = Modifier
                                .size(156.dp, 88.dp)
                                .onFocusChanged { if (it.isFocused) focusApp(app) },
                        )
                    }
                }
            }
        }

        Dock(
            favorites = dockEntries,
            focusedIndex = dockFocus,
            firstRequester = dockFirst,
            onFocusIndex = { dockFocus = it },
            onAppFocused = ::focusApp,
            onSourceFocused = { hero = Hero.Source(it) },
            onToolFocused = { hero = Hero.Tool(context.getString(it.titleRes), context.getString(it.subtitleRes)) },
            onApp = actions::openApp,
            onAppMenu = { menuApp = it },
            onSource = actions::openSource,
            onSourceMenu = { sourceMenu = it },
            onTool = { overlay = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
        )
    }

    when (overlay) {
        Overlay.Launchpad -> Launchpad(apps, prefs, onOpen = actions::openApp, onMenu = { menuApp = it }, onDismiss = { overlay = Overlay.None })
        Overlay.Sources -> SourcesPanel(sources, prefs, onOpen = actions::openSource, onMenu = { sourceMenu = it }, onDismiss = { overlay = Overlay.None })
        Overlay.Control -> ControlCenter(stats, remote, prefs, actions, onDismiss = { overlay = Overlay.None })
        Overlay.Search -> Spotlight(apps, sources, prefs, favorites, actions, onDismiss = { overlay = Overlay.None })
        Overlay.None -> Unit
    }
    menuApp?.let { app ->
        AppMenu(app = app, prefs = prefs, actions = actions, onPickCategory = { categoryApp = app }, onDismiss = { menuApp = null })
    }
    categoryApp?.let { app -> CategoryPicker(app, prefs) { categoryApp = null } }
    sourceMenu?.let { source -> SourceMenu(source, prefs, actions) { sourceMenu = null } }
}

/**
 * The focused app's own colours and artwork fill the screen and cross-fade as focus moves. The art is a
 * 32x18 thumbnail stretched with bilinear filtering: it looks blurred but costs nothing, unlike a live blur.
 */
/** Two colours and an optional tiny artwork thumbnail: everything the backdrop is painted from. */
private data class Ambience(val a: Color, val b: Color, val art: ImageBitmap?)

@Composable
private fun Backdrop(ambience: Ambience?) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Neon.Space)
    ) {
        // Each backdrop is painted once into its own GPU texture; changing focus only blends two
        // finished textures, instead of recomputing full-screen gradients on every animation frame.
        Crossfade(ambience, animationSpec = tween(350), label = "backdrop") { shown ->
            StaticBackdrop(shown)
        }
    }
}

@Composable
private fun StaticBackdrop(ambience: Ambience?) {
    val a = ambience?.a ?: Neon.Violet
    val b = ambience?.b ?: Neon.Cyan
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        ambience?.art?.let { art ->
            Image(
                art, null,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.32f },
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(Brush.radialGradient(listOf(a.copy(alpha = 0.42f), Color.Transparent), center = Offset(size.width * 0.82f, size.height * 0.16f), radius = size.width * 0.62f))
                    drawRect(Brush.radialGradient(listOf(b.copy(alpha = 0.40f), Color.Transparent), center = Offset(size.width, size.height), radius = size.width * 0.55f))
                    drawRect(Brush.horizontalGradient(listOf(Neon.Space.copy(alpha = 0.78f), Color.Transparent), endX = size.width * 0.58f))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Neon.Space.copy(alpha = 0.88f)), startY = size.height * 0.48f))
                }
        )
    }
}

@Composable
private fun MenuBar(title: String, stats: SystemStats?, remote: RemoteHint?, detailed: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(Color(0x700A0E18))
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Glyphs.Home, null, tint = Neon.Cyan, modifier = Modifier.size(15.dp))
        Text("TV Home", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(title, fontSize = 12.sp, color = Neon.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (detailed && remote != null && remote.enabled && remote.url != null) {
            StatusItem(Glyphs.Phone, "${remote.url.removePrefix("http://")} · PIN ${remote.pin}")
        }
        if (stats != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusItem(if (stats.network == SystemStats.NetworkKind.ETHERNET) Glyphs.Ethernet else Glyphs.Wifi, stats.networkName(LocalContext.current))
                if (stats.wifiBars >= 0) SignalBars(stats.wifiBars, Modifier.padding(start = 6.dp))
            }
            if (detailed) Text("RAM %${stats.ramPercent}", fontSize = 12.sp, color = if (stats.ramPercent > 85) Color(0xFFFBBF24) else Neon.Ink)
        }
        Clock()
    }
}

@Composable
private fun StatusItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Neon.Ink, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp)
    }
}

@Composable
private fun SignalBars(bars: Int, modifier: Modifier = Modifier) {
    Row(modifier.height(11.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(4) { i ->
            Box(
                Modifier
                    .size(3.dp, (4 + i * 2.4f).dp)
                    .background(if (i < bars) Neon.Ink else Neon.Ink.copy(alpha = 0.25f), RoundedCornerShape(1.dp))
            )
        }
    }
}

@Composable
private fun Clock() {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Ticks only while the home screen is visible; nothing runs while you watch something else.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = LocalDateTime.now()
                delay(1_000L * (60 - now.second))
            }
        }
    }
    // Formatter follows the app language ("Per 22 Eki" / "Thu 22 Oct"), so it is rebuilt when that changes.
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateTimeFormatter.ofPattern("EEE d MMM", locale) }
    Text(now.format(dateFormat), fontSize = 12.sp)
    Text(now.format(TimeFormat), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun HeroInfo(
    hero: Hero?,
    prefs: HomePrefs,
    lastUsed: Map<String, Long>,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.width(430.dp)) {
        AnimatedContent(
            targetState = hero,
            // Fade only: a slide re-measures the text block every frame.
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(100)) },
            label = "hero",
        ) { h ->
            Column {
                when (h) {
                    is Hero.App -> {
                        Eyebrow(stringResource(prefs.categoryOf(h.app).titleRes))
                        HeroTitle(h.app.label)
                        AppChips(h.app, lastUsed[h.app.packageName], prefs.detailed)
                    }
                    is Hero.Content -> {
                        Eyebrow(h.app?.label ?: stringResource(R.string.home_continue))
                        HeroTitle(h.item.title)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            h.item.subtitle?.let { Chip(it) }
                            h.item.progress?.let { Chip(stringResource(R.string.home_watched, (it * 100).toInt())) }
                            if (h.item.lastEngaged > 0) Chip(Usage.relative(LocalContext.current, h.item.lastEngaged))
                        }
                    }
                    is Hero.Source -> {
                        Eyebrow(stringResource(R.string.home_input, h.source.detail))
                        HeroTitle(h.source.label)
                        Text(
                            (if (h.source.connected) stringResource(R.string.home_input_connected) else "") + stringResource(R.string.home_input_hint),
                            fontSize = 15.sp,
                            color = Neon.Dim,
                        )
                    }
                    is Hero.Tool -> {
                        Eyebrow("TV Home")
                        HeroTitle(h.title)
                        Text(h.subtitle, fontSize = 15.sp, color = Neon.Dim)
                    }
                    null -> HeroTitle("")
                }
            }
        }
        AnimatedVisibility(visible = hero is Hero.App || hero is Hero.Content) {
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(stringResource(if (hero is Hero.Content) R.string.home_continue else R.string.home_open), onOpen, icon = Glyphs.Play, selected = true)
                PillButton(stringResource(R.string.home_options), onMenu, icon = Glyphs.More)
            }
        }
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(text.uppercase(), color = Neon.Cyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
}

/** Short names stay large; long titles (YouTube videos) shrink and wrap to two lines so they read in full. */
@Composable
private fun HeroTitle(text: String) {
    val start = when {
        text.length <= 14 -> 50.sp
        text.length <= 24 -> 40.sp
        text.length <= 40 -> 32.sp
        text.length <= 64 -> 26.sp
        else -> 22.sp
    }
    var size by remember(text) { mutableStateOf(start) }
    Text(
        text,
        fontFamily = Sora,
        fontWeight = FontWeight.Light,
        fontSize = size,
        lineHeight = size * 1.15f,
        letterSpacing = (-0.5).sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        // Still too long for two lines at this size: step down until it fits (floor 18sp).
        onTextLayout = { if (it.hasVisualOverflow && size.value > 18f) size = (size.value * 0.9f).sp },
        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
    )
}

/** Last opened always; size on disk and version only in the detailed view. */
@Composable
private fun AppChips(app: TvApp, lastUsedAt: Long?, detailed: Boolean) {
    val context = LocalContext.current
    val size by produceState<Long?>(null, app.packageName, detailed) {
        if (!detailed) return@produceState
        delay(250) // skip apps the focus only passes over
        value = withContext(Dispatchers.IO) { Usage.sizeBytes(context, app.packageName) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        lastUsedAt?.let { Chip(stringResource(R.string.home_last_opened, Usage.relative(context, it))) }
        if (detailed) {
            size?.let { Chip(formatBytes(it)) }
            if (app.version.isNotBlank()) Chip("v" + app.version.take(14))
        }
    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = Color(0xFFC9D1E6),
        modifier = Modifier
            .glass(RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Big artwork: the episode/video still when you are on something you were watching, otherwise the app banner. No shadow: blurred shadows were the main GPU cost. */
@Composable
private fun HeroArt(app: TvApp?, content: WatchItem?, source: TvSource?, modifier: Modifier = Modifier) {
    if (source != null) {
        val shape = RoundedCornerShape(22.dp)
        DeviceBadge(
            source = source,
            markSize = 72.sp,
            glossy = true,
            caption = source.detail + if (source.connected) " · " + stringResource(R.string.source_connected) else "",
            modifier = modifier
                .size(320.dp, 180.dp)
                .clip(shape)
                .border(1.dp, Color(0x26FFFFFF), shape),
        )
        return
    }
    val context = LocalContext.current
    // For a YouTube video: cover, then frames from inside the video, cross-fading every few seconds.
    val frames = content?.frames.orEmpty()
    var frame by remember(content) { mutableIntStateOf(0) }
    LaunchedEffect(content) {
        while (frames.size > 1) {
            delay(3_000)
            frame = (frame + 1) % frames.size
        }
    }
    val still by produceState<ImageBitmap?>(null, content, frame) {
        val uri = frames.getOrNull(frame) ?: content?.artUri
        if (uri == null) {
            value = null
            return@produceState
        }
        // Keep showing the previous picture until the next one has downloaded.
        value = withContext(Dispatchers.IO) { WatchNext.artwork(context, uri, 640) } ?: value
    }
    val shape = RoundedCornerShape(22.dp)
    Crossfade(still ?: app?.banner, animationSpec = tween(250), modifier = modifier.size(320.dp, 180.dp), label = "heroArt") { art ->
        if (app == null && art == null) return@Crossfade
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Brush.linearGradient(listOf(app?.colorA ?: Neon.Violet, app?.colorB ?: Neon.Cyan)))
                .border(1.dp, Color(0x26FFFFFF), shape)
        ) {
            if (art != null) {
                Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                app?.icon?.let { Image(it, app.label, Modifier.size(96.dp).align(Alignment.Center)) }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, count: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp, color = Neon.Dim)
        Spacer(Modifier.width(8.dp))
        Text("$count", fontSize = 11.sp, color = Neon.Cyan)
    }
}

/** macOS magnification: the focused icon grows most, its neighbours a little, all on springs. */
private fun dockScale(index: Int, focused: Int): Float =
    if (focused < 0) 1f else 1f + 0.42f * exp(-((index - focused) * (index - focused)) / 1.1f)

/** What the user pinned to the Dock: an app, or a TV input such as the PS5. */
private sealed interface DockEntry {
    data class App(val app: TvApp) : DockEntry
    data class Source(val source: TvSource) : DockEntry
}

@Composable
private fun Dock(
    favorites: List<DockEntry>,
    focusedIndex: Int,
    firstRequester: FocusRequester,
    onFocusIndex: (Int) -> Unit,
    onAppFocused: (TvApp) -> Unit,
    onSourceFocused: (TvSource) -> Unit,
    onToolFocused: (DockTool) -> Unit,
    onApp: (TvApp) -> Unit,
    onAppMenu: (TvApp) -> Unit,
    onSource: (TvSource) -> Unit,
    onSourceMenu: (TvSource) -> Unit,
    onTool: (Overlay) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .glass(RoundedCornerShape(26.dp), Color(0x8C141A2C))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .onFocusChanged { if (!it.hasFocus) onFocusIndex(-1) },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        favorites.forEachIndexed { i, entry ->
            val first = if (i == 0) Modifier.focusRequester(firstRequester) else Modifier
            when (entry) {
                is DockEntry.App -> {
                    val app = entry.app
                    DockItem(
                        scale = dockScale(i, focusedIndex),
                        modifier = first,
                        glow = app.colorA,
                        onFocused = { onFocusIndex(i); onAppFocused(app) },
                        onClick = { onApp(app) },
                        onLongClick = { onAppMenu(app) },
                    ) {
                        val icon = app.icon
                        if (icon != null) {
                            Image(icon, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else if (app.banner != null) {
                            Image(app.banner, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                }
                is DockEntry.Source -> {
                    val source = entry.source
                    DockItem(
                        scale = dockScale(i, focusedIndex),
                        modifier = first,
                        // Focus edge in the device's brand colour (PlayStation blue), like app icons use theirs.
                        glow = deviceStyleOf(source)?.a ?: Neon.Cyan,
                        onFocused = { onFocusIndex(i); onSourceFocused(source) },
                        onClick = { onSource(source) },
                        onLongClick = { onSourceMenu(source) },
                    ) {
                        if (deviceStyleOf(source) != null) {
                            DeviceBadge(source, markSize = 21.sp, modifier = Modifier.fillMaxSize(), glossy = true)
                        } else {
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .background(Brush.linearGradient(listOf(Color(0xFF1B2A52), Color(0xFF0E1428)))),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(if (source.isPassthrough) Glyphs.Input else Glyphs.Tv, null, tint = Neon.Cyan, modifier = Modifier.size(20.dp))
                                Text(source.label, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 9.sp, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 3.dp))
                            }
                        }
                    }
                }
            }
        }
        if (favorites.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.CenterVertically)
                    .size(1.dp, 40.dp)
                    .background(Neon.Line)
            )
        }
        DockTools.forEachIndexed { j, tool ->
            val i = favorites.size + j
            DockItem(
                scale = dockScale(i, focusedIndex),
                modifier = if (favorites.isEmpty() && j == 0) Modifier.focusRequester(firstRequester) else Modifier,
                glow = Neon.Cyan,
                onFocused = { onFocusIndex(i); onToolFocused(tool) },
                onClick = { onTool(tool.overlay) },
                onLongClick = { onTool(tool.overlay) },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(Color(0xFF2A3352), Color(0xFF171D30)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(tool.icon, stringResource(tool.titleRes), tint = Neon.Ink, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

@Composable
private fun DockItem(
    scale: Float,
    glow: Color,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val s by animateFloatAsState(scale, spring(dampingRatio = 0.55f, stiffness = 420f), label = "dockScale")
    val shape = RoundedCornerShape(15.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            // Growing icons push their neighbours apart, like the macOS Dock.
            .padding(horizontal = DockIcon * ((s - 1f) / 2f).coerceAtLeast(0f))
            .graphicsLayer {
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .size(DockIcon)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .touchable(onClick, onLongClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color(0x1AFFFFFF)), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, glow.copy(alpha = 0.9f).compositeOver(Color.White)), shape = shape),
        ),
    ) {
        Box(Modifier.fillMaxSize().clip(shape)) { content() }
    }
}
