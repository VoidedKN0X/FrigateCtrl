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
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vknxworks.frigateremote.App
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * Live view. A fast-refreshing snapshot is shown immediately; the go2rtc MSE video (the same
 * /live/mse websocket the Frigate web UI uses) fades in on top only once real, non-black frames
 * are confirmed advancing. If video can't start, it stays on snapshots and shows why.
 */
@Composable
fun LiveView(camera: String, streamName: String, muted: Boolean, modifier: Modifier = Modifier) {
    var attempt by remember(camera, streamName) { mutableIntStateOf(0) }
    var videoOk by remember(camera, streamName, attempt) { mutableStateOf(false) }
    var failure by remember(camera, streamName, attempt) { mutableStateOf<String?>(null) }

    // Safety net in case the player page never reports anything.
    LaunchedEffect(camera, streamName, attempt) {
        delay(20_000)
        if (!videoOk && failure == null) failure = "Video didn't start (timeout)"
    }

    Box(modifier.background(Color.Black)) {
        if (!videoOk) {
            RemoteImage(
                App.api.latestJpg(camera, 720), Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit, refreshMs = 500,
            )
        }
        if (failure == null) {
            MseWebView(
                key = "$streamName#$attempt",
                streamName = streamName,
                muted = muted,
                visible = videoOk,
                onState = { s ->
                    when {
                        s == "playing" -> videoOk = true
                        s == "stalled" -> videoOk = false
                        s.startsWith("failed") -> {
                            videoOk = false
                            failure = s.substringAfter("failed:", "").ifBlank { "Video stream unavailable" }
                        }
                    }
                },
            )
        }
        failure?.let { reason ->
            Column(
                Modifier.align(Alignment.BottomStart).padding(8.dp).widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color(0xCC000000))
                    .clickable { attempt++ }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("Snapshot mode · tap to retry video", color = Color.White, fontSize = 12.sp)
                Text(reason, color = Color(0xFFB9C2CE), fontSize = 11.sp, maxLines = 2)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MseWebView(
    key: String,
    streamName: String,
    muted: Boolean,
    visible: Boolean,
    onState: (String) -> Unit,
) {
    val context = LocalContext.current
    val api = App.api
    val stateCb by rememberUpdatedState(onState)

    val webView = remember(key) {
        WebView(context).apply {
            setBackgroundColor(AColor.TRANSPARENT)
            alpha = 0f
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()

            val base = api.baseUrl
            val wv = this
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(wv, true)
                if (api.token.isNotEmpty()) setCookie(base, "frigate_token=${api.token}; path=/")
                flush()
            }

            val main = Handler(Looper.getMainLooper())
            addJavascriptInterface(object {
                @JavascriptInterface fun onState(s: String) { main.post { stateCb(s) } }
            }, "Android")

            var pageLoaded = false
            var authProblem: String? = null
            webViewClient = object : WebViewClient() {
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    if (App.prefs.allowInsecureTls) handler.proceed() else {
                        handler.cancel()
                        stateCb("failed:Certificate not trusted by the video player")
                    }
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame && (response.statusCode == 401 || response.statusCode == 403))
                        authProblem = "Login not accepted for video (HTTP ${response.statusCode})"
                }
                override fun onPageFinished(view: WebView, url: String) {
                    // Step 1 loads a real URL on the Frigate origin so the TLS decision and the auth
                    // cookie apply; step 2 swaps in the player page on that same origin.
                    if (!pageLoaded && !url.startsWith("data:") && url != "about:blank") {
                        pageLoaded = true
                        val ws = api.wsUrl("/live/mse/api/ws?src=") + java.net.URLEncoder.encode(streamName, "UTF-8")
                        view.loadDataWithBaseURL("$base/", playerHtml(ws, muted, authProblem), "text/html", "utf-8", null)
                    }
                }
            }
            loadUrl("$base/api/version")
        }
    }

    LaunchedEffect(webView, muted) {
        webView.evaluateJavascript("window.setMuted && window.setMuted($muted)", null)
    }
    DisposableEffect(webView) {
        onDispose {
            webView.evaluateJavascript("window.stop && window.stop()", null)
            webView.destroy()
        }
    }
    AndroidView(
        factory = { webView },
        update = { it.alpha = if (visible) 1f else 0f },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun playerHtml(wsUrl: String, muted: Boolean, authProblem: String?): String = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:transparent;overflow:hidden}
video{width:100%;height:100%;object-fit:contain;background:#000}</style></head>
<body><video id="v" autoplay playsinline ${if (muted) "muted" else ""}></video>
<script>
const WS_URL = ${JSONObject.quote(wsUrl)};
const AUTH_PROBLEM = ${JSONObject.quote(authProblem ?: "")};
const video = document.getElementById('v');
const MS = window.MediaSource || window.ManagedMediaSource;
let ws=null, ms=null, sb=null, queue=[], stopped=false;
let noHevc=false, codecUsed='', lastErr='', closes=0;
let ok=false, lastT=-1, stall=0, black=0, startedAt=Date.now();

function report(s){ try{ Android.onState(s) }catch(e){} }
window.setMuted = m => { video.muted = m; if(!m) video.play().catch(()=>{}); };
window.stop = () => { stopped = true; try{ ws && ws.close() }catch(e){} };

function codecs(){
  const all=['avc1.640029','avc1.64002A','avc1.640033','avc1.42E01E','avc1.4D401F'];
  if(!noHevc) all.push('hvc1.1.6.L153.B0');
  all.push('mp4a.40.2','mp4a.40.5','flac','opus');
  return all.filter(c => MS.isTypeSupported('video/mp4; codecs="'+c+'"')).join();
}

function pump(){
  if(!sb || sb.updating) return;
  if(queue.length){
    try { sb.appendBuffer(queue.shift()); }
    catch(e){ queue=[]; try{ const b=sb.buffered; if(b.length) sb.remove(b.start(0), Math.max(b.start(0)+0.1, video.currentTime-1)); }catch(_){} }
    return;
  }
  const b = sb.buffered;
  if(b.length){
    const end=b.end(b.length-1), start=b.start(0);
    if(end - video.currentTime > 2 || video.currentTime < start) video.currentTime = Math.max(start, end - 0.4);
    if(video.currentTime - start > 20){ try{ sb.remove(start, video.currentTime - 8); }catch(e){} }
    if(video.paused) video.play().catch(()=>{});
  }
}

function connect(){
  if(stopped) return;
  if(!MS){ report('failed:This phone\'s WebView has no MediaSource support'); return; }
  queue=[]; sb=null; codecUsed='';
  ms = new MS();
  video.src = URL.createObjectURL(ms);
  ms.addEventListener('sourceopen', () => {
    const sock = new WebSocket(WS_URL);
    ws = sock;
    sock.binaryType = 'arraybuffer';
    sock.onopen = () => sock.send(JSON.stringify({type:'mse', value: codecs()}));
    sock.onerror = () => { lastErr = AUTH_PROBLEM || 'Could not open the live stream websocket'; };
    sock.onmessage = ev => {
      if(ws !== sock) return;
      if(typeof ev.data === 'string'){
        const m = JSON.parse(ev.data);
        if(m.type === 'mse'){
          codecUsed = m.value;
          try { sb = ms.addSourceBuffer(m.value); sb.mode='segments'; sb.addEventListener('updateend', pump); }
          catch(e){ lastErr = 'Codec not playable here: ' + m.value; restart(true); }
        } else if(m.type === 'error'){ lastErr = 'go2rtc: ' + m.value; }
      } else { queue.push(ev.data); pump(); }
    };
    sock.onclose = () => {
      if(stopped || ws !== sock) return;
      closes++;
      setTimeout(connect, 1500);
    };
  }, {once:true});
}

function restart(dropHevc){
  if(dropHevc) noHevc = true;
  const old = ws; ws = null;
  try{ old && old.close() }catch(e){}
  black=0; stall=0; lastT=-1;
  setTimeout(connect, 300);
}

// Is the current frame essentially pure black? (WebView H.265 decode failure looks like this.)
const cv = document.createElement('canvas'); cv.width=32; cv.height=18;
const cx = cv.getContext('2d', {willReadFrequently:true});
function isBlack(){
  try{
    cx.drawImage(video, 0, 0, 32, 18);
    const d = cx.getImageData(0,0,32,18).data;
    let max=0; for(let i=0;i<d.length;i+=4){ max=Math.max(max,d[i],d[i+1],d[i+2]); }
    return max < 12;
  }catch(e){ return false; }
}

setInterval(() => {
  if(stopped) return;
  const t = video.currentTime;
  const advancing = video.readyState >= 2 && video.videoWidth > 0 && t > lastT + 0.05;
  lastT = t;
  if(advancing){
    stall = 0;
    if(isBlack()){
      black++;
      if(black >= 3 && codecUsed.indexOf('hvc1') >= 0 && !noHevc){ restart(true); return; }
      if(black < 6) return;           // a genuinely dark scene is fine after a few seconds
    } else black = 0;
    if(!ok){ ok = true; report('playing'); }
  } else {
    stall++;
    if(ok && stall >= 6){ ok = false; report('stalled'); restart(false); }
  }
  if(!ok && Date.now() - startedAt > 15000){
    let why = lastErr || AUTH_PROBLEM;
    if(!why && noHevc) why = 'Camera stream is H.265, which this phone can\'t play in the app';
    if(!why) why = closes > 0 ? 'Live stream keeps disconnecting' : 'No video frames received';
    report('failed:' + why);
    stopped = true;
  }
}, 1000);

connect();
</script></body></html>
"""
