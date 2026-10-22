package app.swiip.tvhome.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private val Ink = Color(0xFFF6EFE6)
private val Gold = Color(0xFFFFC56B)
private val Grin = Color(0xFFFF6A3D)
private val Night = Color(0xFF0A0A0F)

private val Rise = CubicBezierEasing(.2f, .7f, .2f, 1f)
private val Bounce = CubicBezierEasing(.34f, 1.45f, .64f, 1f)
private val Lift = CubicBezierEasing(.45f, 0f, .2f, 1f)
private val Draw = CubicBezierEasing(.4f, 0f, .2f, 1f)

/** The design timeline is 9 s (same keyframes as the swiip.app logo); the TV plays it 1.5x faster. */
private const val SPEED = 1.5f
private const val PLAY_MS = 9_000f / SPEED * 0.88f
private const val FADE_MS = 450f

/**
 * Swiip opening animation, shown when the TV starts: "swiip" rises in, two gold stars fall onto the dotless
 * ı's, lift into eyes, a grin draws itself underneath and the face chuckles. Any key skips it.
 */
@Composable
fun SwiipIntro(onDone: () -> Unit) {
    val logo = remember { LogoPaths() }
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        clock.animateTo(PLAY_MS + FADE_MS, tween((PLAY_MS + FADE_MS).toInt(), easing = LinearEasing))
        onDone()
    }
    val ms = clock.value
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - step(ms, PLAY_MS, PLAY_MS + FADE_MS, LinearEasing) }
            .background(Night),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.width(600.dp).aspectRatio(SwiipLogo.VIEW_WIDTH / SwiipLogo.VIEW_HEIGHT)) {
            drawLogo(logo, ms * SPEED / 90f)
        }
    }
}

private class LogoPaths {
    val letters = SwiipLogo.letters.mapIndexed { i, d ->
        parse(d).also { if (i == SwiipLogo.letters.lastIndex) it.addPath(parse(SwiipLogo.TAIL), Offset(SwiipLogo.TAIL_X, SwiipLogo.TAIL_Y)) }
    }
    val star = parse(SwiipLogo.STAR)
    val grin = listOf(SwiipLogo.GRIN_LEFT, SwiipLogo.GRIN_RIGHT).map { PathMeasure().apply { setPath(parse(it), false) } }
    val grinPart = Path()

    private fun parse(d: String) = PathParser().parsePathString(d).toPath()
}

/** [p] is the position in the 9 s design timeline, in percent, so the numbers match the CSS keyframes. */
private fun DrawScope.drawLogo(logo: LogoPaths, p: Float) {
    val k = size.width / SwiipLogo.VIEW_WIDTH
    withTransform({
        scale(k, k, pivot = Offset.Zero)
        translate(left = -SwiipLogo.VIEW_LEFT)
    }) {
        // Letters rise in one after another; both ı's share a delay, like the logo files.
        logo.letters.forEachIndexed { i, path ->
            val delay = intArrayOf(0, 1, 2, 2, 3)[i] * 0.89f
            val t = step(p - delay, 3f, 17f, Rise)
            translate(top = 250f * (1f - t)) { drawPath(path, Ink.copy(alpha = t.coerceIn(0f, 1f))) }
        }

        val face = chuckle(p)
        val lift = -SwiipLogo.RISE * step(p, 40f, 50f, Lift)
        drawStar(logo, SwiipLogo.EYE_1_X, SwiipLogo.EYE_Y + lift + face, p, start = 18f, spin = -1f)
        drawStar(logo, SwiipLogo.EYE_2_X, SwiipLogo.EYE_Y + lift + face, p, start = 24f, spin = 1f)

        val grown = step(p, 50f, 70f, Draw)
        if (grown > 0f) {
            translate(SwiipLogo.FACE_X, SwiipLogo.FACE_Y + face) {
                logo.grin.forEach { measure ->
                    logo.grinPart.reset()
                    measure.getSegment(0f, measure.length * grown, logo.grinPart, true)
                    drawPath(logo.grinPart, Grin, style = Stroke(width = 30f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

/** A star falls spinning onto its ı, then twinkles (a quarter turn) once the face is complete. */
private fun DrawScope.drawStar(logo: LogoPaths, x: Float, y: Float, p: Float, start: Float, spin: Float) {
    val fall = step(p, start, start + 12f, Bounce)
    if (fall <= 0f) return
    val twinkleUp = step(p, 80f, 83f, Bounce)
    val twinkleDown = step(p, 83f, 86f, Bounce)
    val rotation = spin * 200f * (1f - fall) - spin * 45f * (twinkleUp + twinkleDown)
    val scale = (0.5f + 0.5f * fall) * (1f + 0.25f * twinkleUp - 0.25f * twinkleDown)
    withTransform({
        translate(x, y - 1100f * (1f - fall))
        rotate(rotation, pivot = Offset.Zero)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        drawPath(logo.star, Gold.copy(alpha = fall.coerceIn(0f, 1f)))
    }
}

/** The face bounces three times, smaller each time, like a chuckle. */
private fun chuckle(p: Float): Float {
    val keys = floatArrayOf(71f, 73f, 75f, 77f, 79f, 81f, 83f)
    val values = floatArrayOf(0f, -22f, 0f, -14f, 0f, -6f, 0f)
    for (i in 0 until keys.lastIndex) {
        if (p < keys[i + 1]) return if (p < keys[i]) 0f else
            values[i] + (values[i + 1] - values[i]) * step(p, keys[i], keys[i + 1], FastOutSlowInEasing)
    }
    return 0f
}

private fun step(t: Float, start: Float, end: Float, easing: Easing): Float =
    easing.transform(((t - start) / (end - start)).coerceIn(0f, 1f))
