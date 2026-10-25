package com.vknxworks.frigatectrl.notify

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.vknxworks.frigatectrl.App
import com.vknxworks.frigatectrl.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where FrigateCtrl's registration with ntfy stands. */
sealed interface PushStatus {
    data object Off : PushStatus
    /** Push is on, but ntfy isn't installed (any more). */
    data object NoNtfy : PushStatus
    data object Registering : PushStatus
    data class Registered(val endpoint: String) : PushStatus
    /** ntfy's server is down for now; the address stays the same and ntfy says when it's back. */
    data class Unavailable(val endpoint: String) : PushStatus
    data class Failed(val reason: String) : PushStatus
}

/**
 * Wake-up pushes through ntfy only. Frigate can't post to ntfy by itself, so a small publisher (a Home Assistant
 * automation or an MQTT-to-ntfy bridge on Frigate's frigate/reviews topic) posts to the address ntfy gives
 * FrigateCtrl. A push only wakes the app up: it then asks your own Frigate server what's new.
 */
object Push {
    private const val PREFS = "push"
    private const val KEY_ENDPOINT = "endpoint"

    private val _status = MutableStateFlow<PushStatus>(PushStatus.Off)
    val status: StateFlow<PushStatus> = _status.asStateFlow()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun endpoint(context: Context): String? = prefs(context).getString(KEY_ENDPOINT, null)

    fun load(context: Context, enabled: Boolean) {
        val endpoint = endpoint(context)
        _status.value = when {
            !enabled -> PushStatus.Off
            !UnifiedPush.isNtfyInstalled(context) -> PushStatus.NoNtfy
            endpoint != null && UnifiedPush.unavailable.value -> PushStatus.Unavailable(endpoint)
            endpoint != null -> PushStatus.Registered(endpoint)
            _status.value is PushStatus.Failed -> _status.value
            else -> PushStatus.Registering
        }
    }

    /** At start-up: registers with ntfy again, which keeps the same address and recovers from a reinstalled ntfy. */
    fun start(context: Context, enabled: Boolean) {
        load(context, enabled)
        if (enabled && UnifiedPush.isNtfyInstalled(context)) UnifiedPush.register(context, context.getString(R.string.app_name))
    }

    /** Asks ntfy for an address. Returns false (status [PushStatus.NoNtfy]) when ntfy isn't installed. */
    fun register(context: Context): Boolean {
        if (!UnifiedPush.register(context, context.getString(R.string.app_name))) {
            _status.value = PushStatus.NoNtfy
            return false
        }
        if (_status.value !is PushStatus.Registered) _status.value = PushStatus.Registering
        return true
    }

    fun unregister(context: Context) {
        runCatching { UnifiedPush.unregister(context) }
        prefs(context).edit().remove(KEY_ENDPOINT).apply()
        _status.value = PushStatus.Off
    }

    internal fun onEndpoint(context: Context, url: String) {
        prefs(context).edit().putString(KEY_ENDPOINT, url).apply()
        UnifiedPush.onEndpoint(context)
        _status.value = PushStatus.Registered(url)
    }

    internal fun onFailed(context: Context, reason: String) {
        UnifiedPush.onLost(context, reason)
        _status.value = PushStatus.Failed(reason)
    }

    internal fun onUnregistered(context: Context) {
        UnifiedPush.onLost(context, null)
        prefs(context).edit().remove(KEY_ENDPOINT).apply()
        _status.value = PushStatus.Failed("UNREGISTERED")
    }

    internal fun onTempUnavailable(context: Context) {
        UnifiedPush.onTempUnavailable()
        endpoint(context)?.let { _status.value = PushStatus.Unavailable(it) }
    }

    internal fun onMessage() {
        UnifiedPush.onMessage()
        val st = _status.value
        if (st is PushStatus.Unavailable) _status.value = PushStatus.Registered(st.endpoint)
    }
}

/**
 * Receives what ntfy hands over: the push address, registration errors and wake-up pushes. Exported because
 * ntfy is another app, so every broadcast must carry the secret token of FrigateCtrl's registration or it's
 * ignored. From Android 14 a broadcast from a sender that identifies itself as another app than ntfy is
 * dropped too. Nothing in a push is ever shown: at most a review id is taken from it, and only what your own
 * Frigate server answers is notified.
 */
class PushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra(UnifiedPush.EXTRA_TOKEN) ?: return
        if (token != UnifiedPush.token(context)) return
        if (Build.VERSION.SDK_INT >= 34) {
            val sender = sentFromPackage
            if (sender != null && sender != UnifiedPush.NTFY) return
        }
        val app = context.applicationContext
        when (intent.action) {
            UnifiedPush.ACTION_NEW_ENDPOINT -> {
                val endpoint = intent.getStringExtra(UnifiedPush.EXTRA_ENDPOINT) ?: return
                UnifiedPush.ack(app, token, intent.getStringExtra(UnifiedPush.EXTRA_ID))
                Push.onEndpoint(app, endpoint)
            }
            UnifiedPush.ACTION_REGISTRATION_FAILED -> Push.onFailed(app, intent.getStringExtra(UnifiedPush.EXTRA_REASON) ?: "UNKNOWN")
            UnifiedPush.ACTION_UNREGISTERED -> Push.onUnregistered(app)
            UnifiedPush.ACTION_TEMP_UNAVAILABLE -> Push.onTempUnavailable(app)
            UnifiedPush.ACTION_MESSAGE -> {
                val bytes = intent.getByteArrayExtra(UnifiedPush.EXTRA_BYTES_MESSAGE)
                    ?: intent.getStringExtra(UnifiedPush.EXTRA_MESSAGE)?.toByteArray()
                UnifiedPush.ack(app, token, intent.getStringExtra(UnifiedPush.EXTRA_ID))
                Push.onMessage()
                App.init(app)
                if (!App.notify.enabled || !App.notify.push) return
                val hint = bytes?.let { AlertCheck.hintFrom(it.toString(Charsets.UTF_8)) }
                AlertWorker.runNow(app, hint)
            }
        }
    }
}

/**
 * ntfy binds to this for a few seconds before it delivers a message. That lifts FrigateCtrl to foreground
 * importance, so the check can start straight away even while the phone is in deep sleep.
 */
class RaiseToForegroundService : Service() {
    private val binder = Binder()
    override fun onBind(intent: Intent?): IBinder = binder
}
