package com.vknxworks.frigatectrl.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vknxworks.frigatectrl.App
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.URLEncoder

enum class TalkState { Idle, Connecting, Talking, Error }

/**
 * Two-way talk: sends the phone's microphone to the camera's speaker through go2rtc's WebRTC
 * backchannel (the same sendonly-audio connection Frigate's web UI opens).
 *
 * WebRTC runs in an invisible WebView on a secure origin (needed for microphone access); the
 * signalling goes through the app's own authenticated websocket to /live/webrtc/api/ws.
 */
class TalkSession(private val activity: Activity, private val stream: String) {
    var state by mutableStateOf(TalkState.Idle)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val main = Handler(Looper.getMainLooper())
    private var web: WebView? = null
    private var ws: WebSocket? = null
    private val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @SuppressLint("SetJavaScriptEnabled")
    fun start() {
        if (state == TalkState.Connecting || state == TalkState.Talking) return
        error = null
        state = TalkState.Connecting

        val wv = WebView(activity).apply {
            alpha = 0f
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    // Only ever grant the microphone, and only to our own page.
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in request.resources)
                        request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                    else request.deny()
                }
            }
            addJavascriptInterface(Bridge(), "Android")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) { if (ws == null) openSignalling() }
            }
        }
        // Attached (invisibly) so WebRTC and audio capture run normally.
        (activity.window.decorView as ViewGroup).addView(wv, ViewGroup.LayoutParams(1, 1))
        web = wv
        wv.loadDataWithBaseURL("https://appassets.androidplatform.net/", PAGE, "text/html", "utf-8", null)
    }

    private fun openSignalling() {
        val url = App.api.wsUrl("/live/webrtc/api/ws?src=") + URLEncoder.encode(stream, "UTF-8")
        ws = App.api.http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                main.post { web?.evaluateJavascript("start(${App.api.iceServersJson})", null) }
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                main.post { web?.evaluateJavascript("onSignal(${JSONObject.quote(text)})", null) }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val why = when (response?.code) {
                    401, 403 -> "Login rejected"
                    404 -> "Stream not found"
                    else -> t.message ?: "Connection failed"
                }
                main.post { fail(why) }
            }
        })
    }

    private inner class Bridge {
        @JavascriptInterface fun signal(json: String) { ws?.send(json) }

        @JavascriptInterface fun state(s: String) {
            main.post {
                when {
                    s == "connected" -> { state = TalkState.Talking; routeToSpeaker(true) }
                    s == "failed" || s == "disconnected" ->
                        if (state != TalkState.Idle) fail("Connection to the camera was lost")
                    s.startsWith("error:") -> fail(s.removePrefix("error:"))
                }
            }
        }
    }

    private fun fail(why: String) {
        stop()
        error = friendly(why)
        state = TalkState.Error
    }

    private fun friendly(raw: String): String = when {
        raw.contains("backchannel", true) || raw.contains("codecs not matched", true) ||
            raw.contains("sendonly", true) -> "This camera doesn't accept audio from Frigate (no backchannel)"
        raw.contains("NotAllowed", true) -> "Microphone permission was denied"
        else -> raw
    }

    /** Keep the camera's live audio on the loudspeaker while the mic is in use. */
    private fun routeToSpeaker(on: Boolean) {
        runCatching {
            if (on) {
                audio.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?.let { audio.setCommunicationDevice(it) }
            } else audio.clearCommunicationDevice()
        }
    }

    fun stop() {
        web?.evaluateJavascript("stop()", null)
        ws?.close(1000, null)
        ws = null
        web?.let { w ->
            (w.parent as? ViewGroup)?.removeView(w)
            w.destroy()
        }
        web = null
        routeToSpeaker(false)
        state = TalkState.Idle
    }

    private companion object {
        const val PAGE = """<!doctype html><html><body><script>
let pc = null, stream = null;
function report(s){ try{ Android.state(s) }catch(e){} }
async function start(ice){
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true }, video: false });
  } catch(e) { report('error:Microphone unavailable (' + e.name + ')'); return; }
  pc = new RTCPeerConnection({ bundlePolicy: 'max-bundle', iceServers: ice });
  stream.getTracks().forEach(t => pc.addTransceiver(t, { direction: 'sendonly' }));
  pc.onicecandidate = ev => {
    if (ev.candidate) Android.signal(JSON.stringify({ type: 'webrtc/candidate', value: ev.candidate.candidate }));
  };
  pc.onconnectionstatechange = () => report(pc.connectionState);
  const offer = await pc.createOffer();
  await pc.setLocalDescription(offer);
  Android.signal(JSON.stringify({ type: 'webrtc/offer', value: pc.localDescription.sdp }));
}
function onSignal(text){
  const m = JSON.parse(text);
  if (!pc) return;
  if (m.type === 'webrtc/answer') pc.setRemoteDescription({ type: 'answer', sdp: m.value }).catch(e => report('error:' + e));
  else if (m.type === 'webrtc/candidate') pc.addIceCandidate({ candidate: m.value, sdpMid: '0' }).catch(() => {});
  else if (m.type === 'error') report('error:' + m.value);
}
function stop(){
  try { stream && stream.getTracks().forEach(t => t.stop()) } catch(e) {}
  try { pc && pc.close() } catch(e) {}
  pc = null; stream = null;
}
</script></body></html>"""
    }
}
