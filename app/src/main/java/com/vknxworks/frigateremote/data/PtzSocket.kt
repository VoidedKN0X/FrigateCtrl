package com.vknxworks.frigateremote.data

import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/**
 * Frigate's websocket (/ws) — the same channel the web UI uses for ONVIF PTZ commands.
 * Payloads: MOVE_UP/DOWN/LEFT/RIGHT, ZOOM_IN/OUT, FOCUS_IN/OUT, STOP, preset_<name>.
 */
class PtzSocket(private val api: FrigateApi) {
    @Volatile private var ws: WebSocket? = null
    @Volatile var connected = false
        private set

    fun connect() {
        if (ws != null) return
        val req = Request.Builder().url(api.wsUrl("/ws")).build()
        ws = api.http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { connected = true }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false; ws = null
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false; ws = null
            }
        })
    }

    fun send(camera: String, payload: String) {
        if (ws == null) connect()
        val msg = JSONObject()
            .put("topic", "$camera/ptz")
            .put("payload", payload)
            .put("retain", false)
            .toString()
        ws?.send(msg)
    }

    fun close() {
        ws?.close(1000, null)
        ws = null
        connected = false
    }
}
