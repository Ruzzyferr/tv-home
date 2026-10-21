package app.swiip.tvhome

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList

/**
 * Turkish ⇄ English for TV Home only (Android 13+ per-app language); the TV's own language is untouched.
 * Until switched, the app follows the TV's language (Turkish on a Turkish TV, English otherwise).
 */
object AppLanguage {
    fun isSupported() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun toggle(context: Context) {
        if (!isSupported()) return
        val current = context.resources.configuration.locales[0].language
        val next = if (current == "tr") "en" else "tr"
        // The system recreates the activity in the new language.
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(next)
    }
}
