package app.swiip.tvhome.remote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.swiip.tvhome.MainActivity

/**
 * Turns a phone into a mouse/keyboard for the TV. Serves the touchpad page on [RemoteAccess.PORT],
 * draws a cursor overlay and injects taps, scrolls, D-pad, media keys and text.
 *
 * Protocol (one text frame per command):
 *   m dx dy · c (tap) · l (long press) · s dx dy (scroll) · k <key> · t <text> · e (IME enter)
 */
class PointerService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private lateinit var cursor: CursorView
    private lateinit var cursorParams: WindowManager.LayoutParams
    private var server: RemoteServer? = null

    private var screenW = 1920
    private var screenH = 1080
    private var x = 960f
    private var y = 540f

    private val hideCursor = Runnable { cursor.animate().alpha(0f).setDuration(400).start() }

    override fun onServiceConnected() {
        windowManager = getSystemService(WindowManager::class.java)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenW = metrics.widthPixels
        screenH = metrics.heightPixels
        x = screenW / 2f
        y = screenH / 2f

        cursor = CursorView(this).apply { alpha = 0f }
        cursorParams = WindowManager.LayoutParams(
            cursor.sizePx,
            cursor.sizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        windowManager.addView(cursor, cursorParams)
        placeCursor()

        server = RemoteServer(assets, RemoteAccess.pin(this)) { command -> main.post { handle(command) } }
            .also { runCatching { it.start(0, true) }.onFailure { e -> Log.e(TAG, "Remote server failed", e) } }
    }

    override fun onDestroy() {
        server?.stop()
        if (::cursor.isInitialized) runCatching { windowManager.removeView(cursor) }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) = Unit

    override fun onInterrupt() = Unit

    private fun handle(command: String) {
        val op = command.substringBefore(' ')
        val arg = command.substringAfter(' ', "")
        when (op) {
            "m" -> arg.floats()?.let { (dx, dy) -> move(dx, dy) }
            "c" -> tap(duration = 40)
            "l" -> tap(duration = 900)
            "s" -> arg.floats()?.let { (dx, dy) -> scroll(dx, dy) }
            "k" -> key(arg)
            "t" -> typeText(arg)
            "e" -> imeEnter()
        }
    }

    private fun String.floats(): Pair<Float, Float>? {
        val parts = split(' ')
        val a = parts.getOrNull(0)?.toFloatOrNull() ?: return null
        val b = parts.getOrNull(1)?.toFloatOrNull() ?: return null
        return a to b
    }

    /** Deltas arrive in "1080p pixels" so the speed feels the same on a 4K panel. */
    private fun move(dx: Float, dy: Float) {
        val scale = screenW / 1920f
        x = (x + dx * scale).coerceIn(0f, screenW - 1f)
        y = (y + dy * scale).coerceIn(0f, screenH - 1f)
        placeCursor()
    }

    private fun placeCursor() {
        cursorParams.x = x.toInt()
        cursorParams.y = y.toInt()
        windowManager.updateViewLayout(cursor, cursorParams)
        cursor.animate().cancel()
        cursor.alpha = 1f
        main.removeCallbacks(hideCursor)
        main.postDelayed(hideCursor, CURSOR_TIMEOUT_MS)
    }

    private fun tap(duration: Long) {
        cursor.pulse()
        val path = Path().apply { moveTo(x, y) }
        dispatch(path, duration)
        placeCursor()
    }

    private fun scroll(dx: Float, dy: Float) {
        val scale = screenW / 1920f
        val startX = x.coerceIn(20f, screenW - 20f)
        val startY = y.coerceIn(20f, screenH - 20f)
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo((startX - dx * scale).coerceIn(1f, screenW - 1f), (startY - dy * scale).coerceIn(1f, screenH - 1f))
        }
        dispatch(path, 120)
    }

    private fun dispatch(path: Path, duration: Long) {
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun key(name: String) {
        when (name) {
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "up", "down", "left", "right", "ok" -> dpad(name)
            "playpause" -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "next" -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            "prev" -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "rew" -> mediaKey(KeyEvent.KEYCODE_MEDIA_REWIND)
            "ff" -> mediaKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            "volup" -> volume(AudioManager.ADJUST_RAISE)
            "voldown" -> volume(AudioManager.ADJUST_LOWER)
            "mute" -> volume(AudioManager.ADJUST_TOGGLE_MUTE)
        }
    }

    /** Real D-pad events need Android 13; older systems get a tap for OK and nothing for arrows. */
    private fun dpad(name: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (name == "ok") tap(duration = 40)
            return
        }
        performGlobalAction(
            when (name) {
                "up" -> GLOBAL_ACTION_DPAD_UP
                "down" -> GLOBAL_ACTION_DPAD_DOWN
                "left" -> GLOBAL_ACTION_DPAD_LEFT
                "right" -> GLOBAL_ACTION_DPAD_RIGHT
                else -> GLOBAL_ACTION_DPAD_CENTER
            }
        )
    }

    private fun mediaKey(code: Int) {
        val audio = getSystemService(AudioManager::class.java)
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    private fun volume(direction: Int) {
        getSystemService(AudioManager::class.java)
            .adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    /** The phone sends the whole field on every keystroke, so the TV field mirrors it live. */
    private fun typeText(text: String) {
        val field = focusedField() ?: return
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun imeEnter() {
        val field = focusedField() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            field.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        }
    }

    /** Input focus as reported by the system first; otherwise search every window for a focused editable node. */
    private fun focusedField(): AccessibilityNodeInfo? {
        findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }?.let { return it }
        val roots = windows.mapNotNull { it.root } + listOfNotNull(rootInActiveWindow)
        val found = roots.firstNotNullOfOrNull { findEditable(it, depth = 0) }
        Log.d(TAG, "focused field: ${found?.className ?: "none"} (roots=${roots.size})")
        return found
    }

    private fun findEditable(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (node.isEditable && node.isFocused) return node
        if (depth > 40) return null
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findEditable(child, depth + 1)?.let { return it }
        }
        return null
    }

    private companion object {
        const val TAG = "TvHome"
        const val CURSOR_TIMEOUT_MS = 4000L
    }
}
