package app.swiip.tvhome.remote

import android.content.Context
import androidx.core.content.edit
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.random.Random

/** Where the phone touchpad page is served and the PIN that protects it. */
object RemoteAccess {
    const val PORT = 8765

    fun pin(context: Context): String {
        val sp = context.getSharedPreferences("remote", Context.MODE_PRIVATE)
        sp.getString("pin", null)?.let { return it }
        return Random.nextInt(1000, 10000).toString().also { sp.edit { putString("pin", it) } }
    }

    /** LAN address first; a Tailscale address (100.64.0.0/10) is listed after it for use away from home. */
    fun addresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .sortedBy { it.startsWith("100.") }
    }.getOrDefault(emptyList())
}
