package com.vknxworks.frigateremote.ui

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.vknxworks.frigateremote.App
import kotlinx.coroutines.delay
import org.json.JSONObject

enum class LiveStatus { Connecting, Playing, Fallback }

/**
 * Low-latency live view: go2rtc MSE stream (the same /live/mse websocket Frigate's web UI uses)
 * played in a WebView. Falls back to fast snapshot polling if MSE doesn't start.
 */
@Composable
fun LiveView(
    camera: String,
    streamName: String,
    muted: Boolean,
    modifier: Modifier = Modifier,
    onStatus: (LiveStatus) -> Unit = {},
) {
    var status by remember(camera, streamName) { mutableStateOf(LiveStatus.Connecting) }
    val statusCb by rememberUpdatedState(onStatus)
    LaunchedEffect(status) { statusCb(status) }

    // If the stream isn't playing after a while, switch to snapshots.
    LaunchedEffect(camera, streamName) {
        delay(9000)
        if (status == LiveStatus.Connecting) status = LiveStatus.Fallback
    }

    Box(modifier.background(Color.Black)) {
        if (status == LiveStatus.Fallback) {
            RemoteImage(
                App.api.latestJpg(camera, 720), Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit, refreshMs = 400,
            )
        } else {
            MseWebView(
                streamName = streamName,
                muted = muted,
                onPlaying = { status = LiveStatus.Playing },
                onFailed = { status = LiveStatus.Fallback },
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MseWebView(streamName: String, muted: Boolean, onPlaying: () -> Unit, onFailed: () -> Unit) {
    val context = LocalContext.current
    val api = App.api
    val playingCb by rememberUpdatedState(onPlaying)
    val failCb by rememberUpdatedState(onFailed)

    val webView = remember(streamName) {
        WebView(context).apply {
            setBackgroundColor(AColor.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()

            val base = api.baseUrl
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(this@apply, true)
                if (api.token.isNotEmpty()) setCookie(base, "frigate_token=${api.token}; path=/")
                flush()
            }

            val main = Handler(Looper.getMainLooper())
            addJavascriptInterface(object {
                @JavascriptInterface fun onState(s: String) {
                    main.post { if (s == "playing") playingCb() else if (s == "failed") failCb() }
                }
            }, "Android")

            var pageLoaded = false
            webViewClient = object : WebViewClient() {
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    if (App.prefs.allowInsecureTls) handler.proceed() else handler.cancel()
                }
                override fun onPageFinished(view: WebView, url: String) {
                    // Step 1 loads a real URL so the TLS decision + cookies apply to the origin,
                    // step 2 swaps in the player page on that same origin.
                    if (!pageLoaded && !url.startsWith("data:") && url != "about:blank") {
                        pageLoaded = true
                        val ws = api.wsUrl("/live/mse/api/ws?src=") + java.net.URLEncoder.encode(streamName, "UTF-8")
                        view.loadDataWithBaseURL("$base/", playerHtml(ws, muted), "text/html", "utf-8", null)
                    }
                }
            }
            loadUrl("$base/api/version")
        }
    }

    LaunchedEffect(muted) {
        webView.evaluateJavascript("window.setMuted && window.setMuted($muted)", null)
    }
    DisposableEffect(webView) {
        onDispose {
            webView.evaluateJavascript("window.stop && window.stop()", null)
            webView.destroy()
        }
    }
    AndroidView({ webView }, Modifier.fillMaxSize())
}

private fun playerHtml(wsUrl: String, muted: Boolean): String = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}
video{width:100%;height:100%;object-fit:contain;background:#000}</style></head>
<body><video id="v" autoplay playsinline ${if (muted) "muted" else ""}></video>
<script>
const WS_URL = ${JSONObject.quote(wsUrl)};
const video = document.getElementById('v');
const MS = window.MediaSource || window.ManagedMediaSource;
let ws=null, ms=null, sb=null, queue=[], fails=0, stopped=false, reported=false;

function report(s){ try{ Android.onState(s) }catch(e){} }
window.setMuted = m => { video.muted = m; if(!m) video.play().catch(()=>{}); };
window.stop = () => { stopped = true; try{ ws && ws.close() }catch(e){} };

function codecs(){
  const all=['avc1.640029','avc1.64002A','avc1.640033','hvc1.1.6.L153.B0','mp4a.40.2','mp4a.40.5','flac','opus'];
  return all.filter(c => MS.isTypeSupported('video/mp4; codecs="'+c+'"')).join();
}

function pump(){
  if(!sb || sb.updating) return;
  if(queue.length){
    try { sb.appendBuffer(queue.shift()); }
    catch(e){ queue=[]; try{ const b=sb.buffered; if(b.length) sb.remove(b.start(0), video.currentTime-1);}catch(_){} }
    return;
  }
  const b = sb.buffered;
  if(b.length){
    const end=b.end(b.length-1), start=b.start(0);
    if(end - video.currentTime > 1.5 || video.currentTime < start) video.currentTime = end - 0.3;
    if(video.currentTime - start > 20){ try{ sb.remove(start, video.currentTime - 8); }catch(e){} }
    if(video.paused) video.play().catch(()=>{});
  }
}

function connect(){
  if(stopped) return;
  if(!MS){ report('failed'); return; }
  queue=[]; sb=null;
  ms = new MS();
  video.src = URL.createObjectURL(ms);
  ms.addEventListener('sourceopen', () => {
    ws = new WebSocket(WS_URL);
    ws.binaryType = 'arraybuffer';
    ws.onopen = () => ws.send(JSON.stringify({type:'mse', value: codecs()}));
    ws.onmessage = ev => {
      if(typeof ev.data === 'string'){
        const m = JSON.parse(ev.data);
        if(m.type === 'mse'){
          try { sb = ms.addSourceBuffer(m.value); sb.mode='segments'; sb.addEventListener('updateend', pump); }
          catch(e){ report('failed'); }
        } else if(m.type === 'error'){ report('failed'); }
      } else { queue.push(ev.data); pump(); }
    };
    ws.onclose = () => {
      if(stopped) return;
      fails++;
      if(fails >= 3 && !reported) report('failed');
      setTimeout(connect, 1500);
    };
  }, {once:true});
}
video.addEventListener('playing', () => { fails=0; if(!reported){ reported=true; report('playing'); } });
connect();
</script></body></html>
"""
