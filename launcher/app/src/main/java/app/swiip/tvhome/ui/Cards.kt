package app.swiip.tvhome.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import app.swiip.tvhome.TvApp
import app.swiip.tvhome.WatchItem
import app.swiip.tvhome.WatchNext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * tv-material surfaces only react to D-pad keys. The phone touchpad injects real taps, so tiles
 * and menu entries also need plain touch handling.
 */
fun Modifier.touchable(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier =
    pointerInput(onClick, onLongClick) {
        detectTapGestures(onTap = { onClick() }, onLongPress = onLongClick?.let { { _ -> it() } })
    }

/** An app's 16:9 banner (or its icon on its own colours); focus adds a white edge and a glow in the app's colour. */
@Composable
fun BannerCard(
    app: TvApp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.touchable(onClick, onLongClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Neon.Glass, focusedContainerColor = Neon.GlassHigh),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        // No focus glow: tv-material draws it as a blurred shadow, which made every D-pad move miss frames on this TV.
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color(0x14FFFFFF)), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
        ),
    ) {
        if (app.banner != null) {
            Image(app.banner, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(app.colorA, app.colorB))),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                app.icon?.let { Image(it, null, Modifier.size(36.dp)) }
                Spacer(Modifier.height(4.dp))
                Text(app.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
        if (caption != null) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(caption, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Something you were watching: its still (or the app banner), title, episode and progress. */
@Composable
fun WatchCard(
    item: WatchItem,
    app: TvApp?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val art by produceState<ImageBitmap?>(null, item.artUri) {
        val uri = item.artUri ?: return@produceState
        value = withContext(Dispatchers.IO) { WatchNext.artwork(context, uri, 360) }
    }
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.touchable(onClick, onLongClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Neon.Glass, focusedContainerColor = Neon.GlassHigh),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color(0x14FFFFFF)), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(app?.colorA ?: Neon.Violet, app?.colorB ?: Neon.Cyan)))
        ) {
            val picture = art ?: app?.banner
            if (picture != null) Image(picture, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000))))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(item.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                item.subtitle?.let { Text(it, fontSize = 9.sp, color = Color(0xCCFFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                item.progress?.let { p ->
                    Box(
                        Modifier
                            .padding(top = 3.dp)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color(0x40FFFFFF), RoundedCornerShape(50))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(p)
                                .height(3.dp)
                                .background(Color.White, RoundedCornerShape(50))
                        )
                    }
                }
            }
            app?.icon?.let {
                Image(
                    it, app.label,
                    Modifier
                        .padding(6.dp)
                        .size(18.dp)
                        .clip(RoundedCornerShape(5.dp))
                )
            }
        }
    }
}

/** A row inside a panel: icon, title, optional detail on the right. */
@Composable
fun PanelRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    detailColor: Color = Neon.Dim,
    onLongClick: (() -> Unit)? = null,
    /** Replaces [icon], e.g. a device's brand badge. */
    leading: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .touchable(onClick, onLongClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Neon.GlassHigh, focusedContentColor = Neon.Ink),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) leading() else Icon(icon, null, tint = Neon.Cyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (detail != null) Text(detail, fontSize = 11.sp, color = detailColor, maxLines = 1)
        }
    }
}

/** Rounded pill button (hero actions, category filters). [selected] fills it light. */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    trailing: String? = null,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = modifier.height(38.dp).touchable(onClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Neon.Ink else Neon.Glass,
            contentColor = if (selected) Neon.Space else Neon.Ink,
            focusedContainerColor = Color.White,
            focusedContentColor = Neon.Space,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(1.dp, Color(0x1FFFFFFF)), shape = shape)),
    ) {
        Row(Modifier.padding(horizontal = 16.dp).height(38.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                Text(trailing, fontSize = 11.sp, color = if (selected) Neon.Space.copy(alpha = 0.6f) else Neon.Dim)
            }
        }
    }
}
