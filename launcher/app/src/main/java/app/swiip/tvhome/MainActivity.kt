package app.swiip.tvhome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import app.swiip.tvhome.remote.RemoteAccess
import app.swiip.tvhome.ui.DockHome
import app.swiip.tvhome.ui.HomeActions
import app.swiip.tvhome.ui.RemoteHint
import app.swiip.tvhome.ui.TvHomeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity(), HomeActions {

    private lateinit var prefs: HomePrefs
    private var apps by mutableStateOf<List<TvApp>>(emptyList())
    private var sources by mutableStateOf<List<TvSource>>(emptyList())
    private var remote by mutableStateOf<RemoteHint?>(null)
    private var stats by mutableStateOf<SystemStats?>(null)
    private var lastUsed by mutableStateOf<Map<String, Long>>(emptyMap())
    private var watching by mutableStateOf<List<WatchItem>>(emptyList())
    private var homeSignal by mutableIntStateOf(0)
    private var reloadJob: Job? = null
    private var statsJob: Job? = null

    /** Install / uninstall / update: rebuild the lists, which also creates or drops category rows. */
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reload(debounceMs = 400)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = HomePrefs(this)
        setContent {
            TvHomeTheme {
                DockHome(apps, sources, prefs, remote, stats, lastUsed, watching, homeSignal, this)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        reload()
    }

    override fun onResume() {
        super.onResume()
        remote = remoteHint()
        lifecycleScope.launch { lastUsed = withContext(Dispatchers.IO) { Usage.lastUsed(this@MainActivity) } }
        lifecycleScope.launch {
            watching = withContext(Dispatchers.IO) {
                WatchHistory.fillYouTubeIds(this@MainActivity)
                (WatchHistory.load(this@MainActivity) + WatchNext.load(this@MainActivity))
                    .sortedByDescending { it.lastEngaged }
                    .distinctBy { it.packageName + "|" + it.title }
                    .take(12)
            }
        }
        // Menu bar numbers refresh every few seconds, only while the home screen is in front.
        statsJob = lifecycleScope.launch {
            while (isActive) {
                stats = withContext(Dispatchers.IO) { SystemStats.read(this@MainActivity) }
                delay(5_000)
            }
        }
    }

    override fun onPause() {
        statsJob?.cancel()
        super.onPause()
    }

    /** Home pressed while already home: jump back to the Dock. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeSignal++
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    private fun reload(debounceMs: Long = 0) {
        reloadJob?.cancel()
        reloadJob = lifecycleScope.launch {
            delay(debounceMs)
            val loadedApps = withContext(Dispatchers.IO) { AppRepository.load(this@MainActivity) }
            val loadedSources = withContext(Dispatchers.IO) { TvSources.load(this@MainActivity) }
            prefs.seedFavoritesOnce(loadedApps.map { it.packageName })
            apps = loadedApps
            sources = loadedSources
        }
    }

    private fun remoteHint(): RemoteHint {
        val services = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val enabled = services.split(':').any { it.startsWith("$packageName/") && it.endsWith("PointerService") }
        val url = RemoteAccess.addresses().firstOrNull()?.let { "http://$it:${RemoteAccess.PORT}" }
        return RemoteHint(url, RemoteAccess.pin(this), enabled)
    }

    override fun openApp(app: TvApp) = launch(app.launchIntent)

    override fun openSource(source: TvSource) = launch(TvSources.intentFor(this, source))

    override fun openQuick(action: QuickAction) {
        action.intent?.let { launch(Intent(it)) }
    }

    override fun openWeb() {
        val browser = apps.firstOrNull { it.packageName == prefs.browser } ?: apps.firstOrNull { it.isBrowser }
        if (browser != null) launch(browser.launchIntent) else toast("Tarayıcı yok – .\\tv sideload brave veya Play Store'dan TV Bro")
    }

    override fun appInfo(app: TvApp) =
        launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null)))

    override fun uninstall(app: TvApp) =
        launch(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null)))

    override fun makeDefaultBrowser(app: TvApp) {
        prefs.chooseBrowser(app.packageName)
        toast("${app.label} artık İnternet kısayolu. Linkler için: .\\tv browser ${app.packageName}")
    }

    /** Resumes the exact episode/video through the app's own deep link; falls back to opening the app. */
    override fun openWatch(item: WatchItem) {
        val intent = item.intent
        if (intent != null) {
            try {
                startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
        apps.firstOrNull { it.packageName == item.packageName }?.let(::openApp) ?: toast("Açılamadı")
    }

    override fun freeMemory() {
        lifecycleScope.launch {
            val freed = withContext(Dispatchers.IO) { MemoryCleaner.free(this@MainActivity, apps.map { it.packageName }) }
            stats = withContext(Dispatchers.IO) { SystemStats.read(this@MainActivity) }
            toast(if (freed > 0) "${formatBytes(freed)} boşaldı · RAM %${stats?.ramPercent}" else "Arka planda kapatılacak uygulama yoktu")
        }
    }

    private fun launch(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast("Açılamadı: ${e.javaClass.simpleName}")
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
