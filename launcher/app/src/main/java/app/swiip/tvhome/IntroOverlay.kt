package app.swiip.tvhome

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.swiip.tvhome.ui.SwiipIntro

/**
 * Plays the Swiip intro in an accessibility overlay, above whatever app was on screen when the TV
 * woke up (an activity would only play it when the home screen happened to be in front).
 * The window takes key focus so the remote can skip it: releasing any key closes it.
 */
class IntroOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var owner: OverlayOwner? = null
    private val close = Runnable { dismiss() }

    fun show() {
        if (root != null) return
        val owner = OverlayOwner().also { this.owner = it }
        val compose = ComposeView(context).apply { setContent { SwiipIntro(onDone = ::dismiss) } }
        val root = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_UP) dismiss()
                return true
            }
        }.apply {
            // Compose looks its lifecycle up from the window's root view.
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            isFocusable = true
            addView(compose)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        owner.start()
        val added = runCatching { windowManager.addView(root, params) }
        if (added.isFailure) {
            Log.e("TvHome", "Intro overlay failed", added.exceptionOrNull())
            owner.stop()
            return
        }
        this.root = root
        root.requestFocus()
        // The window holds key focus, so it must never outlive the animation, whatever Compose does.
        main.postDelayed(close, SAFETY_MS)
    }

    fun dismiss() {
        main.removeCallbacks(close)
        val view = root ?: return
        root = null
        runCatching { windowManager.removeView(view) }
        owner?.stop()
        owner = null
    }

    /** The lifecycle Compose needs outside an activity: resumed while the overlay is attached. */
    private class OverlayOwner : SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry get() = savedState.savedStateRegistry

        fun start() {
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    private companion object {
        const val SAFETY_MS = 12_000L
    }
}
