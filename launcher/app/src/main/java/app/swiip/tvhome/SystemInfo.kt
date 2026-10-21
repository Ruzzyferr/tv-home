package app.swiip.tvhome

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager

/** Live numbers for the menu bar and Control Center. Everything here works without root or special permissions. */
data class SystemStats(
    val ramUsed: Long,
    val ramTotal: Long,
    val storageUsed: Long,
    val storageTotal: Long,
    val network: String,
    /** 0..4 bars for Wi-Fi, -1 when not on Wi-Fi. */
    val wifiBars: Int,
) {
    val ramPercent get() = if (ramTotal == 0L) 0 else (ramUsed * 100 / ramTotal).toInt()
    val storagePercent get() = if (storageTotal == 0L) 0 else (storageUsed * 100 / storageTotal).toInt()

    companion object {
        fun read(context: Context): SystemStats {
            val mem = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
            val fs = StatFs(Environment.getDataDirectory().path)
            val (network, bars) = network(context)
            return SystemStats(
                ramUsed = mem.totalMem - mem.availMem,
                ramTotal = mem.totalMem,
                storageUsed = fs.totalBytes - fs.availableBytes,
                storageTotal = fs.totalBytes,
                network = network,
                wifiBars = bars,
            )
        }

        private fun network(context: Context): Pair<String, Int> {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Bağlantı yok" to -1
            return when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet" to -1
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    val dbm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) caps.signalStrength else -60
                    "Wi-Fi" to when {
                        dbm > -55 -> 4
                        dbm > -66 -> 3
                        dbm > -77 -> 2
                        dbm > -88 -> 1
                        else -> 0
                    }
                }
                else -> "Ağ" to -1
            }
        }
    }
}

/** "RAM'i boşalt": asks Android to drop the cached background processes of every app it knows about. */
object MemoryCleaner {
    /** Background processes that are always worth dropping on this TV (Play Store alone holds ~390 MB). */
    private val EXTRA = listOf("com.android.vending", "com.google.android.tts", "com.google.android.youtube.tv")

    /** Returns the bytes that became available. Call off the main thread; it waits briefly for the kernel to reclaim. */
    fun free(context: Context, packages: Collection<String>): Long {
        val am = context.getSystemService(ActivityManager::class.java)
        val before = availableBytes(am)
        (packages + EXTRA).distinct().filter { it != context.packageName }.forEach { pkg ->
            runCatching { am.killBackgroundProcesses(pkg) }
        }
        Thread.sleep(800)
        return (availableBytes(am) - before).coerceAtLeast(0)
    }

    private fun availableBytes(am: ActivityManager) = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
}

/** Media volume for the Control Center slider (0..100). */
object Volume {
    fun get(context: Context): Int {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
    }

    fun set(context: Context, percent: Int) {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (percent.coerceIn(0, 100) * max + 50) / 100, AudioManager.FLAG_SHOW_UI)
    }
}

/**
 * "Devam et", last-opened times and app sizes. Needs usage access, which `tv install` grants with
 * `appops set app.swiip.tvhome GET_USAGE_STATS allow`; without it these simply return nothing.
 */
object Usage {
    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Package → last time it was in the foreground, over the last 30 days. */
    fun lastUsed(context: Context): Map<String, Long> {
        if (!hasAccess(context)) return emptyMap()
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        return runCatching {
            usm.queryAndAggregateUsageStats(now - 30L * DAY, now)
                .filterValues { it.totalTimeInForeground > 0 }
                .mapValues { it.value.lastTimeUsed }
        }.getOrDefault(emptyMap())
    }

    /** App + data + cache bytes, or null without usage access. Call off the main thread. */
    fun sizeBytes(context: Context, pkg: String): Long? = runCatching {
        val stats = context.getSystemService(StorageStatsManager::class.java)
            .queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, Process.myUserHandle())
        stats.appBytes + stats.dataBytes + stats.cacheBytes
    }.getOrNull()

    fun relative(time: Long, now: Long = System.currentTimeMillis()): String {
        val minutes = (now - time) / 60_000
        return when {
            minutes < 2 -> "az önce"
            minutes < 60 -> "$minutes dk önce"
            minutes < 24 * 60 -> "${minutes / 60} sa önce"
            minutes < 48 * 60 -> "dün"
            minutes < 14 * 24 * 60 -> "${minutes / (24 * 60)} gün önce"
            else -> "${minutes / (7 * 24 * 60)} hafta önce"
        }
    }

    private const val DAY = 24 * 60 * 60 * 1000L
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    else -> "${bytes / (1L shl 20)} MB"
}
