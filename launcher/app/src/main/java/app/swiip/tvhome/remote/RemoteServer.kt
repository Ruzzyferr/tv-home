package app.swiip.tvhome.remote

import android.content.res.AssetManager
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoWSD.WebSocketFrame.CloseCode
import java.io.IOException

/** Serves the touchpad page at `/` and accepts commands over a WebSocket at `/ws?pin=XXXX`. */
class RemoteServer(
    private val assets: AssetManager,
    private val pin: String,
    private val onCommand: (String) -> Unit,
) : NanoWSD(RemoteAccess.PORT) {

    private val page by lazy { assets.open("remote.html").bufferedReader().use { it.readText() } }

    override fun serveHttp(session: IHTTPSession): Response =
        if (session.uri == "/" || session.uri == "/index.html") {
            NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", page)
        } else {
            NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket =
        Session(handshake, handshake.parameters["pin"]?.firstOrNull() == pin)

    private inner class Session(handshake: IHTTPSession, private val authorized: Boolean) : WebSocket(handshake) {
        override fun onOpen() {
            runCatching {
                if (authorized) send("ok") else close(CloseCode.PolicyViolation, "pin", false)
            }
        }

        override fun onMessage(message: WebSocketFrame) {
            if (authorized) message.textPayload?.let(onCommand)
        }

        override fun onClose(code: CloseCode?, reason: String?, initiatedByRemote: Boolean) = Unit

        override fun onPong(pong: WebSocketFrame?) = Unit

        override fun onException(exception: IOException?) {
            Log.d("TvHome", "Remote socket closed: ${exception?.message}")
        }
    }
}
