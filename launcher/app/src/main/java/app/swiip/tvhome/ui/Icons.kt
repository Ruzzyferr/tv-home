package app.swiip.tvhome.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Thin line icons (24x24 grid, 1.8 stroke) matching the Dock style. Tint them with Icon(tint = ...). */
object Glyphs {
    val Grid = icon("grid") {
        roundRect(3.5f, 3.5f, 7f, 7f); roundRect(13.5f, 3.5f, 7f, 7f)
        roundRect(3.5f, 13.5f, 7f, 7f); roundRect(13.5f, 13.5f, 7f, 7f)
    }
    val Search = icon("search") { circle(11f, 11f, 6.5f); moveTo(16f, 16f); lineTo(20.5f, 20.5f) }
    val Input = icon("input") { moveTo(4f, 8f); lineTo(20f, 8f); lineTo(18f, 16f); lineTo(6f, 16f); close(); moveTo(8.5f, 11.5f); lineTo(15.5f, 11.5f) }
    val Sliders = icon("sliders") {
        moveTo(4f, 7f); lineTo(14f, 7f); moveTo(18f, 7f); lineTo(20f, 7f); moveTo(4f, 17f); lineTo(8f, 17f); moveTo(12f, 17f); lineTo(20f, 17f)
        circle(16f, 7f, 2f); circle(10f, 17f, 2f)
    }
    val Wifi = icon("wifi") {
        moveTo(2.5f, 9f); curveTo(8f, 4f, 16f, 4f, 21.5f, 9f)
        moveTo(5.5f, 12.5f); curveTo(9f, 9.5f, 15f, 9.5f, 18.5f, 12.5f)
        moveTo(8.8f, 16f); curveTo(10.6f, 14.5f, 13.4f, 14.5f, 15.2f, 16f)
        circle(12f, 19f, 1f)
    }
    val Ethernet = icon("ethernet") { roundRect(4f, 6f, 16f, 10f); moveTo(8f, 16f); lineTo(8f, 19f); moveTo(16f, 16f); lineTo(16f, 19f); moveTo(8f, 10f); lineTo(16f, 10f) }
    val Bluetooth = icon("bt") { moveTo(7f, 7f); lineTo(17f, 17f); lineTo(12f, 21f); lineTo(12f, 3f); lineTo(17f, 7f); lineTo(7f, 17f) }
    val Phone = icon("phone") { roundRect(7f, 2.5f, 10f, 19f); moveTo(11f, 18.5f); lineTo(13f, 18.5f) }
    val Gear = icon("gear") {
        circle(12f, 12f, 3.2f)
        moveTo(12f, 2.5f); lineTo(12f, 5.5f); moveTo(12f, 18.5f); lineTo(12f, 21.5f); moveTo(2.5f, 12f); lineTo(5.5f, 12f); moveTo(18.5f, 12f); lineTo(21.5f, 12f)
        moveTo(5.3f, 5.3f); lineTo(7.4f, 7.4f); moveTo(16.6f, 16.6f); lineTo(18.7f, 18.7f); moveTo(5.3f, 18.7f); lineTo(7.4f, 16.6f); moveTo(16.6f, 7.4f); lineTo(18.7f, 5.3f)
    }
    val Picture = icon("picture") { roundRect(3f, 4f, 18f, 14f); moveTo(3f, 15f); lineTo(8f, 10f); lineTo(12f, 14f); lineTo(15f, 11f); lineTo(21f, 17f) }
    val Volume = icon("volume") { moveTo(4f, 9f); lineTo(8f, 9f); lineTo(13f, 5f); lineTo(13f, 19f); lineTo(8f, 15f); lineTo(4f, 15f); close(); moveTo(16.5f, 8.5f); curveTo(18.5f, 10.5f, 18.5f, 13.5f, 16.5f, 15.5f) }
    val Antenna = icon("antenna") {
        moveTo(12f, 12f); lineTo(12f, 21f); moveTo(8f, 21f); lineTo(16f, 21f)
        moveTo(5.5f, 5f); curveTo(3f, 8f, 3f, 12f, 5.5f, 15f); moveTo(18.5f, 5f); curveTo(21f, 8f, 21f, 12f, 18.5f, 15f)
        circle(12f, 10f, 1.4f)
    }
    val Tv = icon("tv") { roundRect(3f, 5f, 18f, 12f); moveTo(8f, 21f); lineTo(16f, 21f) }
    val Home = icon("home") { moveTo(3.5f, 11f); lineTo(12f, 4f); lineTo(20.5f, 11f); moveTo(6f, 9.5f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 9.5f) }
    val Play = icon("play") { moveTo(8f, 5.5f); lineTo(18.5f, 12f); lineTo(8f, 18.5f); close() }
    val More = icon("more") { circle(6f, 12f, 1.2f); circle(12f, 12f, 1.2f); circle(18f, 12f, 1.2f) }
    val Globe = icon("globe") {
        circle(12f, 12f, 8.5f)
        moveTo(3.5f, 12f); lineTo(20.5f, 12f)
        moveTo(12f, 3.5f); curveTo(9f, 7f, 9f, 17f, 12f, 20.5f)
        moveTo(12f, 3.5f); curveTo(15f, 7f, 15f, 17f, 12f, 20.5f)
    }
    val Eye = icon("eye") { moveTo(2.5f, 12f); curveTo(6f, 5.5f, 18f, 5.5f, 21.5f, 12f); curveTo(18f, 18.5f, 6f, 18.5f, 2.5f, 12f); close(); circle(12f, 12f, 3f) }

    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
            .build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
        arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
    }

    private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float = 2f) {
        moveTo(x + r, y)
        lineTo(x + w - r, y); arcToRelative(r, r, 0f, false, true, r, r)
        lineTo(x + w, y + h - r); arcToRelative(r, r, 0f, false, true, -r, r)
        lineTo(x + r, y + h); arcToRelative(r, r, 0f, false, true, -r, -r)
        lineTo(x, y + r); arcToRelative(r, r, 0f, false, true, r, -r)
        close()
    }
}
