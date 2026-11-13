package com.vknxworks.frigatectrl.tile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.vknxworks.frigatectrl.App
import com.vknxworks.frigatectrl.MainActivity
import com.vknxworks.frigatectrl.R
import com.vknxworks.frigatectrl.data.FrigateSocket
import com.vknxworks.frigatectrl.notify.AlertNotifier
import org.json.JSONObject

/**
 * Quick Settings tile that switches object detection on or off for the camera chosen in Settings.
 *
 * It uses the same channel as the camera switches in the app and Frigate's own web UI: Frigate's websocket
 * (`<camera>/detect/set` ON|OFF, answered on `<camera>/detect/state`). That needs no MQTT broker, changes
 * the running Frigate only (like the web UI's switch, not the config file) and works on every Frigate version
 * FrigateCtrl supports, with the same login, certificate pin and redirect rules as the rest of the app.
 *
 * The connection is only open while the Quick Settings panel shows the tile. While the app lock has locked
 * FrigateCtrl, the tile shows "Locked" and nothing about the camera; changes always need the phone unlocked.
 */
class DetectTileService : TileService() {
    private val main = Handler(Looper.getMainLooper())
    private var socket: FrigateSocket? = null
    private var camera = ""
    private var detecting: Boolean? = null
    private var pending: Boolean? = null
    private var failed = false

    /** No answer from Frigate in time: say so instead of spinning forever. */
    private val giveUp = Runnable {
        if (pending != null || detecting == null) {
            pending = null
            if (detecting == null) failed = true
            render()
        }
    }

    override fun onStartListening() {
        App.init(applicationContext)
        camera = App.settings.tileCamera
        detecting = null
        pending = null
        failed = false
        render()
        if (state() is DetectTileState.Connecting) connect()
    }

    override fun onStopListening() {
        disconnect()
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    override fun onClick() {
        when (val s = state()) {
            DetectTileState.NotSetUp, DetectTileState.Locked, DetectTileState.NoCamera -> openApp()
            is DetectTileState.Offline -> { failed = false; render(); connect() }
            is DetectTileState.Known -> {
                val target = !s.detecting
                // Changing a camera always needs the phone unlocked.
                if (isLocked) unlockAndRun { switchTo(target) } else switchTo(target)
            }
            is DetectTileState.Connecting, is DetectTileState.Switching -> Unit
        }
    }

    private fun locked(): Boolean {
        val lockOn = App.vault.enabled || App.lock.legacyPinSet
        return lockOn && !App.vault.isOpen
    }

    private fun state() = DetectTileState.of(
        configured = App.prefs.isConfigured,
        locked = locked(),
        camera = camera,
        detecting = detecting,
        pending = pending,
        failed = failed,
    )

    private fun connect() {
        disconnect()
        val s = FrigateSocket(App.api)
        socket = s
        s.onMessage = { topic, payload -> onMessage(topic, payload) }
        s.onDisconnected = {
            if (socket === s) {
                socket = null
                if (detecting == null || pending != null) { pending = null; failed = true }
                render()
            }
        }
        s.connect()
        s.requestState()
        main.removeCallbacks(giveUp)
        main.postDelayed(giveUp, TIMEOUT_MS)
    }

    private fun disconnect() {
        main.removeCallbacks(giveUp)
        socket?.let { it.onMessage = null; it.onDisconnected = null; it.close() }
        socket = null
    }

    private fun onMessage(topic: String, payload: String) {
        if (locked()) { disconnect(); render(); return }
        val cam = camera
        if (topic == "camera_activity") {
            val cfg = runCatching { JSONObject(payload).optJSONObject(cam)?.optJSONObject("config") }.getOrNull()
            if (cfg == null || !cfg.has("detect")) {
                // Frigate doesn't know this camera (renamed or removed): nothing to switch.
                if (detecting == null) { failed = true; render() }
                return
            }
            if (pending == null) detecting = cfg.optBoolean("detect")
        } else {
            val on = DetectMessages.stateFrom(cam, topic, payload) ?: return
            detecting = on
            if (pending == on) pending = null
        }
        failed = false
        if (pending == null) main.removeCallbacks(giveUp)
        render()
    }

    private fun switchTo(on: Boolean) {
        if (locked()) { render(); return }
        val s = socket ?: return connect()
        pending = on
        render()
        s.publish(DetectMessages.setTopic(camera), DetectMessages.payload(on))
        main.removeCallbacks(giveUp)
        main.postDelayed(giveUp, TIMEOUT_MS)
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        runCatching { startActivityAndCollapse(pi) }
    }

    private fun render() {
        val tile = qsTile ?: return
        val s = state()
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_detect)
        tile.label = getString(R.string.tile_detect_label)
        val camLabel = camera.takeIf { it.isNotBlank() }?.let { AlertNotifier.cameraLabel(it) }.orEmpty()
        when (s) {
            DetectTileState.NotSetUp -> set(tile, Tile.STATE_INACTIVE, getString(R.string.tile_sub_setup))
            DetectTileState.Locked -> set(tile, Tile.STATE_INACTIVE, getString(R.string.tile_sub_locked))
            DetectTileState.NoCamera -> set(tile, Tile.STATE_INACTIVE, getString(R.string.tile_sub_pick))
            is DetectTileState.Connecting -> set(tile, Tile.STATE_UNAVAILABLE, camLabel)
            is DetectTileState.Offline -> set(tile, Tile.STATE_INACTIVE, getString(R.string.tile_sub_offline, camLabel))
            is DetectTileState.Switching -> set(
                tile, if (s.to) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE, getString(R.string.tile_sub_switching, camLabel),
            )
            is DetectTileState.Known -> set(
                tile, if (s.detecting) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE, camLabel,
                getString(if (s.detecting) R.string.tile_state_on else R.string.tile_state_off),
            )
        }
        tile.updateTile()
    }

    private fun set(tile: Tile, state: Int, subtitle: String, description: String? = null) {
        tile.state = state
        tile.subtitle = subtitle
        tile.stateDescription = description
    }

    companion object {
        private const val TIMEOUT_MS = 8_000L

        /** Asks Android to refresh the tile (after the camera for it changed). */
        fun refresh(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, DetectTileService::class.java)) }
        }
    }
}
