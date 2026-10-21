package app.swiip.tvhome

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/** Everything the user customises, kept in SharedPreferences and exposed as Compose state. */
class HomePrefs(context: Context) {
    private val sp = context.getSharedPreferences("home", Context.MODE_PRIVATE)

    val favorites = mutableStateListOf<String>().apply { addAll(readList(KEY_FAVORITES)) }
    val hidden = mutableStateListOf<String>().apply { addAll(readList(KEY_HIDDEN)) }

    /** Manual row choices that beat [Categorizer]; package → category name. */
    val categoryOverrides = mutableStateMapOf<String, AppCategory>().apply {
        readList(KEY_CATEGORIES).forEach { entry ->
            val (pkg, name) = entry.split('=', limit = 2).takeIf { it.size == 2 } ?: return@forEach
            AppCategory.entries.firstOrNull { it.name == name }?.let { put(pkg, it) }
        }
    }

    var browser by mutableStateOf(sp.getString(KEY_BROWSER, null))
        private set

    /** Off by default: the family panel found IP, PIN, RAM and version numbers confusing on the home screen. */
    var detailed by mutableStateOf(sp.getBoolean(KEY_DETAILED, false))
        private set

    fun toggleDetailed() {
        detailed = !detailed
        save()
    }

    /** On first run, pin the popular streaming apps that are actually installed. */
    fun seedFavoritesOnce(installed: Collection<String>) {
        if (sp.contains(KEY_FAVORITES)) return
        favorites.addAll(DEFAULT_FAVORITES.filter { it in installed })
        save()
    }

    fun categoryOf(app: TvApp): AppCategory = categoryOverrides[app.packageName] ?: app.autoCategory

    fun setCategory(pkg: String, category: AppCategory?) {
        if (category == null) categoryOverrides.remove(pkg) else categoryOverrides[pkg] = category
        save()
    }

    fun toggleFavorite(pkg: String) {
        if (!favorites.remove(pkg)) favorites.add(pkg)
        save()
    }

    fun moveFavorite(pkg: String, delta: Int) {
        val from = favorites.indexOf(pkg)
        val to = from + delta
        if (from < 0 || to !in favorites.indices) return
        favorites.add(to, favorites.removeAt(from))
        save()
    }

    fun toggleHidden(pkg: String) {
        if (!hidden.remove(pkg)) {
            hidden.add(pkg)
            favorites.remove(pkg)
        }
        save()
    }

    fun chooseBrowser(pkg: String) {
        browser = pkg
        save()
    }

    private fun readList(key: String): List<String> =
        sp.getString(key, null)?.split(',')?.filter { it.isNotBlank() }.orEmpty()

    private fun save() = sp.edit {
        putString(KEY_FAVORITES, favorites.joinToString(","))
        putString(KEY_HIDDEN, hidden.joinToString(","))
        putString(KEY_CATEGORIES, categoryOverrides.entries.joinToString(",") { "${it.key}=${it.value.name}" })
        putString(KEY_BROWSER, browser)
        putBoolean(KEY_DETAILED, detailed)
    }

    companion object {
        /** Dock entries are package names, or this prefix + a TV input id for pinned sources (e.g. the PS5). */
        private const val SOURCE_PREFIX = "src:"

        fun sourceId(inputId: String) = SOURCE_PREFIX + inputId

        fun inputIdOf(entry: String): String? = entry.takeIf { it.startsWith(SOURCE_PREFIX) }?.removePrefix(SOURCE_PREFIX)

        private const val KEY_FAVORITES = "favorites"
        const val KEY_HIDDEN = "hidden"
        const val KEY_CATEGORIES = "categories"
        const val KEY_BROWSER = "browser"
        const val KEY_DETAILED = "detailed"
        val DEFAULT_FAVORITES = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom",
            "com.spotify.tv.android",
            "com.phlox.tvwebbrowser",
        )
    }
}
