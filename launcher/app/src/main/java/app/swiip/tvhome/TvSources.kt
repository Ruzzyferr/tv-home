package app.swiip.tvhome

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/** A TV input: an HDMI port, a device on it (HDMI-CEC, e.g. a PlayStation), or a tuner / Live TV. */
data class TvSource(
    val id: String,
    val label: String,
    /** Port name for a CEC device ("HDMI 2"), otherwise the input type. */
    val detail: String,
    val isPassthrough: Boolean,
    val connected: Boolean,
    val packageName: String,
)

object TvSources {
    fun load(context: Context): List<TvSource> {
        val manager = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
        val inputs = runCatching { manager.tvInputList }.getOrDefault(emptyList()).filter { !it.isHidden(context) }
        val byId = inputs.associateBy { it.id }
        // A CEC device (child) replaces its port in the list, so "PlayStation 5" shows instead of a bare "HDMI 2".
        val portsWithDevice = inputs.mapNotNull { it.parentId }.toSet()

        return inputs
            .filter { it.id !in portsWithDevice }
            .map { info ->
                val state = runCatching { manager.getInputState(info.id) }.getOrDefault(TvInputManager.INPUT_STATE_CONNECTED)
                val port = info.parentId?.let { byId[it] }?.loadLabel(context)?.toString()
                TvSource(
                    id = info.id,
                    label = info.loadLabel(context).toString(),
                    detail = port ?: typeName(context, info),
                    isPassthrough = info.isPassthroughInput,
                    // This TV reports empty HDMI ports as "standby", so only a named CEC device or a fully
                    // connected input counts as plugged in.
                    connected = info.parentId != null || state == TvInputManager.INPUT_STATE_CONNECTED,
                    packageName = info.serviceInfo.packageName,
                )
            }
            // Devices that are plugged in first, then the rest in the TV's own order.
            .sortedByDescending { it.connected && it.isPassthrough }
    }

    private fun typeName(context: Context, info: TvInputInfo): String = when (info.type) {
        TvInputInfo.TYPE_HDMI -> "HDMI"
        TvInputInfo.TYPE_TUNER -> context.getString(R.string.source_broadcast)
        TvInputInfo.TYPE_COMPOSITE, TvInputInfo.TYPE_COMPONENT -> "AV"
        else -> context.getString(R.string.source_input)
    }

    fun intentFor(context: Context, source: TvSource): Intent {
        val intent = if (source.isPassthrough) {
            Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(source.id))
        } else {
            context.packageManager.getLeanbackLaunchIntentForPackage(source.packageName)
                ?: Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
