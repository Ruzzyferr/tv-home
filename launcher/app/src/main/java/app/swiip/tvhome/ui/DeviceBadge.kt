package app.swiip.tvhome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import app.swiip.tvhome.TvSource

/**
 * HDMI-CEC only tells the TV a device's name ("PlayStation 5"), never a logo, so well-known devices get
 * their brand colours and short mark here. Unknown devices fall back to a plain input icon.
 */
data class DeviceStyle(val mark: String, val a: Color, val b: Color, val fg: Color = Color.White, val italic: Boolean = false)

fun deviceStyleOf(source: TvSource): DeviceStyle? {
    val l = source.label.lowercase()
    return when {
        "playstation" in l || l.startsWith("ps5") || l.startsWith("ps4") || l.startsWith("ps3") ->
            DeviceStyle(Regex("\\d").find(l)?.let { "PS${it.value}" } ?: "PS", Color(0xFF0070D1), Color(0xFF00246B), italic = true)
        "xbox" in l -> DeviceStyle("XBOX", Color(0xFF107C10), Color(0xFF0A3D0A))
        "switch" in l || "nintendo" in l -> DeviceStyle("Switch", Color(0xFFE60012), Color(0xFF7A0009))
        "apple" in l -> DeviceStyle("tv", Color(0xFF3A3A3C), Color(0xFF000000))
        "fire" in l -> DeviceStyle("fire tv", Color(0xFFFF9900), Color(0xFF232F3E))
        "chromecast" in l || "google tv" in l -> DeviceStyle("Chromecast", Color(0xFF4285F4), Color(0xFF1A3D7C))
        "shield" in l || "nvidia" in l -> DeviceStyle("SHIELD", Color(0xFF76B900), Color(0xFF1A1A1A))
        "roku" in l -> DeviceStyle("Roku", Color(0xFF6C3C97), Color(0xFF2B1640))
        else -> null
    }
}

/** Brand tile for a TV input: colours + mark when the device is known, otherwise the generic input icon. */
@Composable
fun DeviceBadge(source: TvSource, markSize: TextUnit, modifier: Modifier = Modifier, caption: String? = null, glossy: Boolean = false) {
    val style = deviceStyleOf(source)
    Box(
        modifier.background(
            Brush.linearGradient(
                if (style != null) listOf(style.a, style.b) else listOf(Color(0xFF1B2A52), Color(0xFF0E1428))
            )
        ),
        contentAlignment = Alignment.Center,
    ) {
        // Soft top highlight so the tile sits next to real app icons in the Dock instead of looking flat.
        if (glossy) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.28f), 0.55f to Color.Transparent))
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (style != null) {
                Text(
                    style.mark,
                    color = style.fg,
                    fontFamily = Sora,
                    fontWeight = FontWeight.Bold,
                    fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
                    fontSize = markSize,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1,
                )
            } else {
                Icon(
                    if (source.isPassthrough) Glyphs.Input else Glyphs.Tv, null, tint = Neon.Cyan,
                    modifier = Modifier.fillMaxSize(0.4f),
                )
            }
            if (caption != null) Text(caption, color = Color.White.copy(alpha = 0.8f), fontSize = markSize * 0.3f, maxLines = 1)
        }
    }
}
