package app.swiip.tvhome.ui

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import app.swiip.tvhome.R

object Neon {
    val Cyan = Color(0xFF62E3E8)
    val Violet = Color(0xFFA094FF)
    val Space = Color(0xFF06080E)
    val Ink = Color(0xFFEEF2FF)
    val Dim = Color(0xFF8C97B2)
    val Ok = Color(0xFF4ADE80)
    val Glass = Color(0x99161C2E)
    val GlassHigh = Color(0xCC283250)
    val Panel = Color(0xF0141A2C)
    val PanelHigh = Color(0xFF28324E)
    val Line = Color(0x1AEEF2FF)
    val Edge = Brush.linearGradient(listOf(Cyan, Violet))
}

val Sora = FontFamily(
    Font(R.font.sora, FontWeight.ExtraLight),
    Font(R.font.sora, FontWeight.Light),
    Font(R.font.sora, FontWeight.Normal),
    Font(R.font.sora, FontWeight.SemiBold),
    Font(R.font.sora, FontWeight.Bold),
)

val Onest = FontFamily(
    Font(R.font.onest, FontWeight.Normal),
    Font(R.font.onest, FontWeight.Medium),
    Font(R.font.onest, FontWeight.SemiBold),
    Font(R.font.onest, FontWeight.Bold),
)

@Composable
fun TvHomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Neon.Cyan,
            onPrimary = Color.Black,
            background = Neon.Space,
            onBackground = Neon.Ink,
            surface = Neon.Panel,
            onSurface = Neon.Ink,
            surfaceVariant = Neon.GlassHigh,
            onSurfaceVariant = Neon.Ink,
            // Focused list items use the inverse colours: light fill, dark text.
            inverseSurface = Neon.Ink,
            inverseOnSurface = Neon.Space,
        ),
    ) {
        // Colour goes through LocalContentColor (not the text style) so surfaces can flip it, e.g. dark text on a light button.
        CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = Onest), LocalContentColor provides Neon.Ink) {
            content()
        }
    }
}

/**
 * A menu opened by holding OK receives that same press's repeats and its release, which would "click" the
 * first entry by itself. Only an OK press that starts after the window opened is let through.
 */
@Composable
fun Modifier.ignoreHeldOk(): Modifier {
    var armed by remember { mutableStateOf(false) }
    return onPreviewKeyEvent { e ->
        val isOk = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
        if (!isOk) return@onPreviewKeyEvent false
        when (e.type) {
            KeyEventType.KeyDown -> {
                if (e.nativeKeyEvent.repeatCount == 0) armed = true
                !armed
            }
            KeyEventType.KeyUp -> !armed
            else -> false
        }
    }
}

/** Frosted panel look used by the menu bar, Dock and every overlay. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(28.dp), color: Color = Neon.Glass): Modifier =
    background(color, shape).border(1.dp, Color(0x1FFFFFFF), shape)

/**
 * Full-screen overlay window: traps focus, closes on Back, dims what is behind it and, where the TV
 * supports cross-window blur (Android 12+), blurs it like macOS.
 */
@Composable
fun GlassDialog(onDismiss: () -> Unit, dim: Float = 0.45f, content: @Composable BoxScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setDimAmount(dim)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    window.attributes = window.attributes.apply { blurBehindRadius = 60 }
                }
            }
        }
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        Box(Modifier.fillMaxSize().ignoreHeldOk()) {
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.72f, stiffness = 520f), initialScale = 0.96f),
            ) {
                Box(Modifier.fillMaxSize(), content = content)
            }
        }
    }
}
