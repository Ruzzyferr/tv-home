package app.swiip.tvhome.remote

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Neon arrow pointer; its tip sits at the view's top-left corner, which is the tap point. */
class CursorView(context: Context) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NEON
        setShadowLayer(10f, 0f, 0f, NEON)
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeJoin = Paint.Join.ROUND
        color = Color.BLACK
    }
    private val arrow = Path().apply {
        moveTo(2f, 2f)
        lineTo(2f, 40f)
        lineTo(12f, 30f)
        lineTo(20f, 46f)
        lineTo(27f, 43f)
        lineTo(19f, 27f)
        lineTo(33f, 27f)
        close()
    }

    /** The arrow is drawn for xhdpi (1080p UI); scale it so it is the same physical size on 720p and 4K UIs. */
    private val scale = context.resources.displayMetrics.density / 2f
    val sizePx = (BASE_SIZE_PX * scale).toInt()

    init {
        // Shadow layers on paths are drawn only in software; the view is tiny, so this is cheap.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.scale(scale, scale)
        canvas.drawPath(arrow, fill)
        canvas.drawPath(arrow, outline)
    }

    fun pulse() {
        animate().scaleX(0.8f).scaleY(0.8f).setDuration(70).withEndAction {
            animate().scaleX(1f).scaleY(1f).setDuration(90).start()
        }.start()
    }

    companion object {
        private const val BASE_SIZE_PX = 52
        private const val NEON = 0xFF00E5FF.toInt()
    }
}
