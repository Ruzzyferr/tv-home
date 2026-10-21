package app.swiip.tvhome

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri

/** Rows on the home screen, in display order. A row only appears when it has at least one app. */
enum class AppCategory(val title: String) {
    VIDEO("Film & Dizi"),
    LIVE("Canlı TV & Spor"),
    MUSIC("Müzik & Radyo"),
    KIDS("Çocuk"),
    GAMES("Oyunlar"),
    BROWSERS("Tarayıcılar"),
    SOCIAL("Sosyal & İletişim"),
    NEWS("Haberler"),
    TOOLS("Araçlar"),
    SYSTEM("Sistem"),
    OTHER("Diğer"),
}

/**
 * Picks a row for a newly seen app without any network lookup:
 * browser capability → the app's own declared category → keywords in package/label → system flag.
 */
object Categorizer {

    fun browserPackages(context: Context): Set<String> {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        return context.packageManager.queryIntentActivities(probe, PackageManager.MATCH_ALL)
            .mapTo(HashSet()) { it.activityInfo.packageName }
    }

    fun classify(info: ApplicationInfo, label: String, browsers: Set<String>): AppCategory {
        val text = "${info.packageName} $label".lowercase()
        // Ad blockers and downloaders also open web links, but they are tools, not browsers.
        val isTool = NOT_BROWSERS.any { it in text }
        if (!isTool && (info.packageName in browsers || "browser" in text || "tarayıcı" in text)) return AppCategory.BROWSERS
        KEYWORDS.firstOrNull { (_, words) -> words.any { it in text } }?.let { return it.first }
        declared(info)?.let { return it }
        val isSystem = info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        return if (isSystem) AppCategory.SYSTEM else AppCategory.OTHER
    }

    @Suppress("DEPRECATION")
    private fun declared(info: ApplicationInfo): AppCategory? = when {
        info.category == ApplicationInfo.CATEGORY_GAME || info.flags and ApplicationInfo.FLAG_IS_GAME != 0 -> AppCategory.GAMES
        info.category == ApplicationInfo.CATEGORY_AUDIO -> AppCategory.MUSIC
        info.category == ApplicationInfo.CATEGORY_VIDEO || info.category == ApplicationInfo.CATEGORY_IMAGE -> AppCategory.VIDEO
        info.category == ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
        info.category == ApplicationInfo.CATEGORY_NEWS -> AppCategory.NEWS
        info.category == ApplicationInfo.CATEGORY_PRODUCTIVITY ||
            info.category == ApplicationInfo.CATEGORY_MAPS ||
            info.category == ApplicationInfo.CATEGORY_ACCESSIBILITY -> AppCategory.TOOLS
        else -> null
    }

    private val NOT_BROWSERS = listOf("adguard", "downloader", "esaba")

    /** Checked in order, so the more specific rows (kids, live) win over generic video words. */
    private val KEYWORDS: List<Pair<AppCategory, List<String>>> = listOf(
        AppCategory.KIDS to listOf("kids", "çocuk", "cocuk", "cartoon", "minika", "yoyo"),
        AppCategory.GAMES to listOf("game", "oyun", "geforce", "xcloud", "xbox", "playstation", "retroarch", "emulator", "steamlink"),
        AppCategory.LIVE to listOf(
            "livetv", "live tv", "canlı", "canli", "sport", "spor", "bein", "dazn", "iptv", "tivimate",
            "smarters", "tvcenter", "tv.channels", "todtv", "beonetv", "digiturk", "dsmart", "tvplus",
            "turkcell.ott", "channels",
        ),
        AppCategory.MUSIC to listOf(
            "music", "müzik", "muzik", "spotify", "deezer", "fizy", "radio", "radyo", "podcast",
            "soundcloud", "tidal", "audio", "karaoke",
        ),
        AppCategory.VIDEO to listOf(
            "youtube", "netflix", "disney", "amazonvideo", "primevideo", "hbo", "wbd", "mubi", "blutv",
            "exxen", "gain", "puhutv", "tabii", "trt", "plex", "kodi", "vlc", "mxtech", "jellyfin", "emby",
            "stremio", "smarttube", "twitch", "video", "movie", "film", "dizi", "cinema", "player", "apple.atve",
            "iqiyi", "shahid", "mbc",
        ),
        AppCategory.SOCIAL to listOf("facebook", "instagram", "tiktok", "whatsapp", "telegram", "zoom", "meet", "skype", "discord"),
        AppCategory.NEWS to listOf("news", "haber"),
        AppCategory.TOOLS to listOf(
            "file", "dosya", "vpn", "tailscale", "speedtest", "downloader", "airscreen", "airreceiver",
            "remote", "launcher", "cleaner", "analiti", "x-plore", "sendfiles", "anydesk", "rustdesk",
            "mouse", "buttonmapper", "button mapper", "weather", "hava", "adguard",
        ),
    )
}
