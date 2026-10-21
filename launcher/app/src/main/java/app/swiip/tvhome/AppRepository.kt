package app.swiip.tvhome

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import java.text.Collator
import java.util.Locale

/** Never mutated after loading, so Compose may skip anything whose inputs are the same TvApp. */
@Immutable
data class TvApp(
    val packageName: String,
    val label: String,
    val launchIntent: Intent,
    /** 16:9 TV banner; null for phone-only apps. */
    val banner: ImageBitmap?,
    /** Square launcher icon, used in the Dock. */
    val icon: ImageBitmap?,
    val autoCategory: AppCategory,
    val isBrowser: Boolean,
    /** Two colours taken from the app's own artwork; they tint the backdrop when the app is focused. */
    val colorA: Color,
    val colorB: Color,
    val version: String,
    /** 32x18 copy of the banner; drawn stretched it gives a soft blurred backdrop at almost no GPU cost. */
    val backdrop: ImageBitmap?,
)

object AppRepository {
    private const val TAG = "TvHome"

    /** Must match the card and Dock sizes in the UI. */
    private const val BANNER_WIDTH_DP = 320
    private const val ICON_DP = 64

    /** Leanback apps first; phone-only (sideloaded) apps are added only if they have no TV entry. */
    fun load(context: Context): List<TvApp> {
        val pm = context.packageManager
        val leanback = pm.queryIntentActivities(mainIntent(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
        val phone = pm.queryIntentActivities(mainIntent(Intent.CATEGORY_LAUNCHER), 0)
        val browsers = Categorizer.browserPackages(context)

        val seen = HashSet<String>()
        val apps = ArrayList<TvApp>()
        for ((infos, category) in listOf(leanback to Intent.CATEGORY_LEANBACK_LAUNCHER, phone to Intent.CATEGORY_LAUNCHER)) {
            for (info in infos) {
                val pkg = info.activityInfo.packageName
                if (pkg == context.packageName || !seen.add(pkg)) continue
                runCatching { apps += toTvApp(context, info, category, browsers) }
                    .onFailure { Log.w(TAG, "Skipping $pkg", it) }
            }
        }
        val collator = Collator.getInstance(Locale.getDefault())
        return apps.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    private fun mainIntent(category: String) = Intent(Intent.ACTION_MAIN).addCategory(category)

    private fun toTvApp(context: Context, info: ResolveInfo, category: String, browsers: Set<String>): TvApp {
        val pm = context.packageManager
        val activity = info.activityInfo
        val label = info.loadLabel(pm).toString()
        val density = context.resources.displayMetrics.density

        // Decode at the real pixel size (sharp on 4K UIs) but never above the source size (RAM on 2 GB TVs).
        val bannerDrawable = runCatching { activity.loadBanner(pm) }.getOrNull()
        // Banners are opaque, so RGB_565 halves their memory with no visible loss (2.4 GB TV).
        val bannerBitmap = bannerDrawable?.let {
            val w = (BANNER_WIDTH_DP * density).toInt().coerceAtMost(it.intrinsicWidth.takeIf { iw -> iw > 0 } ?: Int.MAX_VALUE)
            it.toBitmap(w, w * 9 / 16, Bitmap.Config.RGB_565)
        }
        val iconPx = (ICON_DP * density).toInt()
        val iconBitmap = runCatching { activity.loadIcon(pm).toBitmap(iconPx, iconPx) }.getOrNull()
        val (colorA, colorB) = colorsOf(bannerBitmap ?: iconBitmap)

        return TvApp(
            packageName = activity.packageName,
            label = label,
            launchIntent = mainIntent(category)
                .setClassName(activity.packageName, activity.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            banner = bannerBitmap?.asImageBitmap(),
            icon = iconBitmap?.asImageBitmap(),
            autoCategory = Categorizer.classify(activity.applicationInfo, label, browsers),
            isBrowser = activity.packageName in browsers,
            colorA = colorA,
            colorB = colorB,
            version = runCatching { pm.getPackageInfo(activity.packageName, 0).versionName }.getOrNull().orEmpty(),
            backdrop = bannerBitmap?.let { Bitmap.createScaledBitmap(it, 32, 18, true).asImageBitmap() },
        )
    }

    private fun colorsOf(bitmap: Bitmap?): Pair<Color, Color> {
        if (bitmap == null) return DEFAULT_A to DEFAULT_B
        val palette = Palette.from(bitmap).maximumColorCount(12).generate()
        val a = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch
        val b = palette.darkVibrantSwatch ?: palette.mutedSwatch ?: palette.darkMutedSwatch ?: palette.dominantSwatch
        return (a?.let { Color(it.rgb) } ?: DEFAULT_A) to (b?.let { Color(it.rgb) } ?: DEFAULT_B)
    }

    private val DEFAULT_A = Color(0xFF6D5BD0)
    private val DEFAULT_B = Color(0xFF1C6E8C)
}
