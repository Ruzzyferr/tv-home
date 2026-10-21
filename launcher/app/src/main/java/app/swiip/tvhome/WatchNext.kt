package app.swiip.tvhome

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.tv.TvContract.WatchNextPrograms
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.net.HttpURLConnection
import java.net.URL

/** One "keep watching" entry: from the app's media session (WatchHistory) or the system Watch Next list. */
@Immutable
data class WatchItem(
    val title: String,
    /** "S2 · E5 · Episode title" for episodes, null for films and videos. */
    val subtitle: String?,
    val packageName: String,
    val intent: Intent?,
    val artUri: Uri?,
    /** 0..1, or null when the app did not report position and duration. */
    val progress: Float?,
    val lastEngaged: Long,
    /** Stills from inside the video, played as a slideshow in the big artwork (YouTube only). */
    val frames: List<Uri> = emptyList(),
)

/**
 * Reads [WatchNextPrograms] from the TV provider. READ_EPG_DATA is a normal permission, so this works
 * for a sideloaded launcher; it only returns what apps (YouTube, Netflix, Prime...) chose to publish.
 */
object WatchNext {
    private const val TAG = "TvHome"

    private val PROJECTION = arrayOf(
        WatchNextPrograms.COLUMN_TITLE,
        WatchNextPrograms.COLUMN_EPISODE_TITLE,
        WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
        WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
        WatchNextPrograms.COLUMN_PACKAGE_NAME,
        WatchNextPrograms.COLUMN_INTENT_URI,
        WatchNextPrograms.COLUMN_POSTER_ART_URI,
        WatchNextPrograms.COLUMN_THUMBNAIL_URI,
        WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
        WatchNextPrograms.COLUMN_DURATION_MILLIS,
        WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
        WatchNextPrograms.COLUMN_BROWSABLE,
    )

    fun load(context: Context, limit: Int = 12): List<WatchItem> = runCatching {
        val items = ArrayList<WatchItem>()
        context.contentResolver.query(WatchNextPrograms.CONTENT_URI, PROJECTION, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(11) == 0) continue // not browsable: the app hid it
                val title = c.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                val season = c.getString(2)
                val episode = c.getString(3)
                val subtitle = listOfNotNull(
                    season?.let { context.getString(R.string.episode_season, it) },
                    episode?.let { context.getString(R.string.episode_number, it) },
                    c.getString(1)?.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { null }
                val position = c.getLong(8)
                val duration = c.getLong(9)
                items += WatchItem(
                    title = title,
                    subtitle = subtitle,
                    packageName = c.getString(4).orEmpty(),
                    intent = c.getString(5)?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() },
                    artUri = (c.getString(7) ?: c.getString(6))?.let(Uri::parse),
                    progress = if (position > 0 && duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else null,
                    lastEngaged = c.getLong(10),
                )
            }
        }
        Log.i(TAG, "watch next: ${items.size} items from ${items.map { it.packageName }.distinct()}")
        items.sortedByDescending { it.lastEngaged }.take(limit)
    }.onFailure { Log.w(TAG, "watch next unavailable", it) }.getOrDefault(emptyList())

    // Small in-memory cache so moving back and forth over the row does not re-download artwork.
    private val art = LruCache<String, ImageBitmap>(16)

    /** Decodes a content:// or https:// image at about [targetWidth] px. Call off the main thread. */
    fun artwork(context: Context, uri: Uri, targetWidth: Int): ImageBitmap? {
        art.get(uri.toString())?.let { return it }
        val bytes = runCatching {
            when (uri.scheme) {
                "http", "https" -> (URL(uri.toString()).openConnection() as HttpURLConnection).run {
                    connectTimeout = 4000
                    readTimeout = 6000
                    inputStream.use { it.readBytes() }
                }
                else -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
        }.getOrNull() ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }) ?: return null
        return bitmap.asImageBitmap().also { art.put(uri.toString(), it) }
    }
}
