package com.vknxworks.frigatectrl.data

import android.os.Handler
import android.os.Looper
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/**
 * Frigate's websocket (/ws) — the same channel the web UI uses.
 *  - PTZ: topic "<camera>/ptz", payload MOVE_UP/DOWN/LEFT/RIGHT, ZOOM_IN/OUT, FOCUS_IN/OUT, STOP, preset_<name>
 *  - Toggles: "<camera>/<feature>/set" ON|OFF; Frigate answers on "<camera>/<feature>/state"
 *  - Sending "onConnect" makes Frigate publish "camera_activity" with every camera's current settings.
 * Incoming messages are delivered on the main thread to [onMessage].
 */
class FrigateSocket(private val api: FrigateApi) {
    @Volatile private var ws: WebSocket? = null
    @Volatile var connected = false
        private set
    private val main = Handler(Looper.getMainLooper())
    private val pending = mutableListOf<String>()

    var onMessage: ((topic: String, payload: String) -> Unit)? = null

    fun connect() {
        if (ws != null) return
        val req = Request.Builder().url(api.wsUrl("/ws")).build()
        ws = api.http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
                synchronized(pending) { pending.forEach { webSocket.send(it) }; pending.clear() }
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val o = try { JSONObject(text) } catch (_: Exception) { return }
                val topic = o.optString("topic")
                val payload = o.opt("payload")?.toString().orEmpty()
                main.post { onMessage?.invoke(topic, payload) }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false; ws = null
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false; ws = null
            }
        })
    }

    fun publish(topic: String, payload: String) {
        val msg = JSONObject().put("topic", topic).put("payload", payload).put("retain", false).toString()
        val sock = ws
        if (sock != null && connected) sock.send(msg)
        else {
            synchronized(pending) { pending += msg }
            connect()
        }
    }

    fun send(camera: String, ptzCommand: String) = publish("$camera/ptz", ptzCommand)

    /** Ask Frigate for the current per-camera settings (answered as "camera_activity"). */
    fun requestState() = publish("onConnect", "")

    fun close() {
        ws?.close(1000, null)
        ws = null
        connected = false
        synchronized(pending) { pending.clear() }
    }
}
