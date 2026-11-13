package com.vknxworks.frigatectrl.tile

/**
 * What the detection tile shows, worked out from plain facts so it can be unit-tested without Android.
 */
sealed interface DetectTileState {
    /** No Frigate server set up yet. */
    data object NotSetUp : DetectTileState
    /** The app lock is on and FrigateCtrl is locked: nothing about the cameras is shown. */
    data object Locked : DetectTileState
    /** No camera chosen for the tile in Settings. */
    data object NoCamera : DetectTileState
    /** Waiting for Frigate to say whether detection is on. */
    data class Connecting(val camera: String) : DetectTileState
    /** A switch was sent and Frigate hasn't confirmed it yet. */
    data class Switching(val camera: String, val to: Boolean) : DetectTileState
    data class Known(val camera: String, val detecting: Boolean) : DetectTileState
    /** Frigate couldn't be reached, or doesn't know the camera. */
    data class Offline(val camera: String) : DetectTileState

    companion object {
        fun of(
            configured: Boolean,
            locked: Boolean,
            camera: String,
            detecting: Boolean?,
            pending: Boolean?,
            failed: Boolean,
        ): DetectTileState = when {
            !configured -> NotSetUp
            locked -> Locked
            camera.isBlank() -> NoCamera
            pending != null -> Switching(camera, pending)
            failed -> Offline(camera)
            detecting == null -> Connecting(camera)
            else -> Known(camera, detecting)
        }
    }
}

/** Frigate's answers on its websocket that concern detection on one camera. */
object DetectMessages {
    fun setTopic(camera: String) = "$camera/detect/set"
    fun stateTopic(camera: String) = "$camera/detect/state"
    fun payload(on: Boolean) = if (on) "ON" else "OFF"

    /** "ON"/"OFF" on `<camera>/detect/state`; null for anything else. */
    fun stateFrom(camera: String, topic: String, payload: String): Boolean? =
        if (topic != stateTopic(camera)) null
        else when (payload.trim().trim('"').uppercase()) {
            "ON" -> true
            "OFF" -> false
            else -> null
        }
}
