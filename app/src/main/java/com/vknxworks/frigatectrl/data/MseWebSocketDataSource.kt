package com.vknxworks.frigatectrl.data

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Reads go2rtc's MSE websocket (Frigate proxies it at /live/mse/api/ws?src=<stream>) as a plain
 * byte stream of fragmented MP4, so ExoPlayer can play live video natively.
 */
@OptIn(UnstableApi::class)
class MseWebSocketDataSource(private val client: OkHttpClient) : BaseDataSource(true) {

    class Factory(private val client: OkHttpClient) : DataSource.Factory {
        override fun createDataSource(): DataSource = MseWebSocketDataSource(client)
    }

    private val eof = ByteArray(0)
    private val queue = LinkedBlockingQueue<ByteArray>()
    private var ws: WebSocket? = null
    private var uri: Uri? = null
    private var current: ByteArray? = null
    private var pos = 0
    private var opened = false
    @Volatile private var failure: IOException? = null

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        queue.clear(); current = null; pos = 0; failure = null

        val req = Request.Builder().url(dataSpec.uri.toString()).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Offer what Android can decode: H.264/H.265 video; AAC, FLAC and Opus audio.
                // FLAC matters: go2rtc only forwards G.711 (PCMA/PCMU) camera audio over MSE by
                // converting it to FLAC, and only when the player lists "flac".
                val codecs = "avc1.640029,avc1.64002A,avc1.640033,avc1.4D401F,avc1.42E01E,hvc1.1.6.L153.B0," +
                        "mp4a.40.2,mp4a.40.5,flac,opus"
                webSocket.send(JSONObject().put("type", "mse").put("value", codecs).toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = try { JSONObject(text) } catch (_: Exception) { return }
                if (msg.optString("type") == "error") {
                    failure = IOException("go2rtc: " + msg.optString("value"))
                    queue.offer(eof)
                }
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                queue.offer(bytes.toByteArray())
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                failure = IOException(
                    response?.let { r ->
                        when (r.code) {
                            401, 403 -> "login rejected (HTTP ${r.code})"
                            404 -> "stream not found (HTTP 404)"
                            else -> "HTTP ${r.code}"
                        }
                    } ?: (t.message ?: "connection failed"), t
                )
                queue.offer(eof)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                queue.offer(eof)
            }
        })
        opened = true
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        var cur = current
        while (cur == null || pos >= cur.size) {
            val next = queue.poll(15, TimeUnit.SECONDS)
                ?: throw IOException("no data from the live stream")
            if (next === eof) {
                failure?.let { throw it }
                return C.RESULT_END_OF_INPUT
            }
            cur = next; current = next; pos = 0
        }
        val n = min(length, cur.size - pos)
        System.arraycopy(cur, pos, buffer, offset, n)
        pos += n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        ws?.close(1000, null)
        ws = null
        queue.clear()
        current = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}
