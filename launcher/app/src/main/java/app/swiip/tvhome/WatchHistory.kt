package app.swiip.tvhome

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What you last played in each app (YouTube video, Netflix episode, Prime film, Spotify song), taken from
 * the app's media session: title, channel/series, artwork and position. YouTube and co. no longer publish
 * "continue watching" to the shared Watch Next list on this TV, but every player exposes a media session.
 */
object WatchHistory {
    private const val TAG = "TvHome"
    private const val MAX = 12
    private const val PREFS = "watch_history"
    private val YOUTUBE_ID = Regex("^[A-Za-z0-9_-]{11}$")

    @Synchronized
    fun record(context: Context, pkg: String, meta: MediaMetadata, state: PlaybackState?) {
        if (pkg == context.packageName) return
        val title = meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return
        if (title.isBlank()) return
        val subtitle = meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val key = "$pkg|$title"

        val entries = read(context)
        val previous = entries.firstOrNull { it.optString("key") == key }
        if (previous == null) Log.i(TAG, "media: $pkg \"$title\" keys=${meta.keySet()} id=${meta.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)}")
        // A missing id is looked up later, once, by fillYouTubeIds (no network on every play/pause).
        val youtubeId = if ("youtube" in pkg) previous?.optString("ytId")?.takeIf { it.isNotBlank() } ?: youtubeIdOf(meta) else null
        val entry = JSONObject().apply {
            put("key", key)
            put("pkg", pkg)
            put("title", title)
            subtitle?.let { put("subtitle", it) }
            put("duration", meta.getLong(MediaMetadata.METADATA_KEY_DURATION))
            put("position", state?.position ?: previous?.optLong("position") ?: 0L)
            meta.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.let { put("mediaId", it) }
            meta.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)?.let { put("mediaUri", it) }
            youtubeId?.let { put("ytId", it) }
            if (previous?.optBoolean("ytTried") == true) put("ytTried", true)
            put("updated", System.currentTimeMillis())
            put("art", previous?.optString("art")?.takeIf { it.isNotBlank() } ?: saveArt(context, key, meta) ?: "")
        }
        val updated = listOf(entry) + entries.filter { it.optString("key") != key }
        write(context, updated.take(MAX))
    }

    /** Finds video ids for YouTube entries that lack one, trying each entry only once. Call off the main thread. */
    @Synchronized
    fun fillYouTubeIds(context: Context) {
        val entries = read(context)
        var changed = false
        for (e in entries) {
            if ("youtube" !in e.optString("pkg") || e.optString("ytId").isNotBlank() || e.optBoolean("ytTried")) continue
            YouTubeLookup.find(e.optString("title"), e.optString("subtitle").ifBlank { null })?.let { e.put("ytId", it) }
            e.put("ytTried", true)
            changed = true
        }
        if (changed) write(context, entries)
    }

    fun load(context: Context): List<WatchItem> = read(context).mapNotNull { e ->
        val pkg = e.optString("pkg").ifBlank { return@mapNotNull null }
        val duration = e.optLong("duration")
        val position = e.optLong("position")
        val ytId = e.optString("ytId").ifBlank { null }
        // YouTube publishes a cover plus frames from inside every video. The first frame is skipped: it is
        // often an intro card or colour bars, which panel testers found off-putting.
        val frames = ytId?.let { id -> listOf("hqdefault", "hq2", "hq3").map { Uri.parse("https://i.ytimg.com/vi/$id/$it.jpg") } }.orEmpty()
        WatchItem(
            title = e.optString("title"),
            subtitle = e.optString("subtitle").ifBlank { null },
            packageName = pkg,
            intent = resumeIntent(pkg, ytId.orEmpty(), e.optString("mediaUri")),
            artUri = e.optString("art").ifBlank { null }?.let(Uri::parse) ?: frames.firstOrNull(),
            progress = if (duration > 0 && position > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else null,
            lastEngaged = e.optLong("updated"),
            frames = frames,
        )
    }

    private fun youtubeIdOf(meta: MediaMetadata): String? {
        val id = meta.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        if (id != null && YOUTUBE_ID.matches(id)) return id
        val uri = meta.getString(MediaMetadata.METADATA_KEY_MEDIA_URI) ?: return null
        return Regex("(?:v=|youtu\\.be/)([A-Za-z0-9_-]{11})").find(uri)?.groupValues?.get(1)
    }

    /** YouTube: open the exact video by id; other apps only reopen (and usually resume on their own). */
    private fun resumeIntent(pkg: String, youtubeId: String, mediaUri: String): Intent? = when {
        "youtube" in pkg && YOUTUBE_ID.matches(youtubeId) ->
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$youtubeId")).setPackage(pkg)
        mediaUri.startsWith("http") || mediaUri.contains("://") ->
            Intent(Intent.ACTION_VIEW, Uri.parse(mediaUri)).setPackage(pkg)
        else -> null
    }

    /** Artwork arrives as a bitmap or a URI; bitmaps are saved small so the row can show them after the app closes. */
    private fun saveArt(context: Context, key: String, meta: MediaMetadata): String? {
        val bitmap = meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (bitmap == null) {
            return meta.getString(MediaMetadata.METADATA_KEY_ART_URI)
                ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        }
        return runCatching {
            val dir = File(context.filesDir, "watch").apply { mkdirs() }
            val file = File(dir, "${key.hashCode().toUInt()}.jpg")
            val w = minOf(bitmap.width, 480)
            val scaled = if (bitmap.width > w) Bitmap.createScaledBitmap(bitmap, w, bitmap.height * w / bitmap.width, true) else bitmap
            file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            Uri.fromFile(file).toString()
        }.onFailure { Log.w(TAG, "art save failed", it) }.getOrNull()
    }

    private fun read(context: Context): List<JSONObject> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("entries", "[]")
        val array = JSONArray(raw)
        (0 until array.length()).map { array.getJSONObject(it) }
    }.getOrDefault(emptyList())

    private fun write(context: Context, entries: List<JSONObject>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString("entries", JSONArray(entries).toString())
        }
    }
}

/**
 * The YouTube TV app's media session carries the title and channel but not always the video id. One
 * search-page request finds it, which unlocks the cover, the in-video frames and an exact resume link.
 */
object YouTubeLookup {
    private val VIDEO_ID = Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"")

    fun find(title: String, channel: String?): String? = runCatching {
        val query = Uri.encode(listOfNotNull(title, channel).joinToString(" "))
        val conn = (java.net.URL("https://www.youtube.com/results?search_query=$query").openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 8000
            setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9")
            setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
        }
        val html = conn.inputStream.bufferedReader().use { it.readText() }
        VIDEO_ID.find(html)?.groupValues?.get(1)
    }.onFailure { Log.w("TvHome", "youtube lookup failed", it) }.getOrNull()
        .also { Log.i("TvHome", "youtube lookup \"$title\" -> $it") }
}

/**
 * Notification access is only the key Android asks for before it hands out other apps' media sessions;
 * notifications themselves are ignored. Enabled by `tv install` (cmd notification allow_listener).
 */
class MediaListener : NotificationListenerService() {
    private val main = Handler(Looper.getMainLooper())
    private val tracked = HashMap<Any, Pair<MediaController, MediaController.Callback>>()
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { track(it.orEmpty()) }

    override fun onListenerConnected() {
        val manager = getSystemService(MediaSessionManager::class.java)
        val me = ComponentName(this, MediaListener::class.java)
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsChanged, me, main)
            track(manager.getActiveSessions(me))
        }.onFailure { Log.w("TvHome", "media sessions unavailable", it) }
    }

    override fun onListenerDisconnected() {
        runCatching { getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(sessionsChanged) }
        tracked.values.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        tracked.clear()
    }

    private fun track(controllers: List<MediaController>) {
        for (controller in controllers) {
            if (controller.sessionToken in tracked) continue
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = save(controller, metadata, controller.playbackState)
                override fun onPlaybackStateChanged(state: PlaybackState?) = save(controller, controller.metadata, state)
                override fun onSessionDestroyed() {
                    tracked.remove(controller.sessionToken)?.let { controller.unregisterCallback(it.second) }
                }
            }
            controller.registerCallback(callback, main)
            tracked[controller.sessionToken] = controller to callback
            save(controller, controller.metadata, controller.playbackState)
        }
    }

    private fun save(controller: MediaController, metadata: MediaMetadata?, state: PlaybackState?) {
        if (metadata == null) return
        val pkg = controller.packageName
        // Disk work off the main thread; position updates are rare (play/pause/seek), so this stays cheap.
        Thread { WatchHistory.record(applicationContext, pkg, metadata, state) }.start()
    }
}
