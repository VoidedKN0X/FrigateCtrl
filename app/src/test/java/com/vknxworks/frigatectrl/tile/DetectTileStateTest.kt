package com.vknxworks.frigatectrl.tile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectTileStateTest {
    private fun state(
        configured: Boolean = true, locked: Boolean = false, camera: String = "front",
        detecting: Boolean? = null, pending: Boolean? = null, failed: Boolean = false,
    ) = DetectTileState.of(configured, locked, camera, detecting, pending, failed)

    @Test fun orderOfChecks() {
        assertEquals(DetectTileState.NotSetUp, state(configured = false, locked = true, camera = ""))
        // Locked wins over everything the server said: nothing about the camera is shown.
        assertEquals(DetectTileState.Locked, state(locked = true, detecting = true))
        assertEquals(DetectTileState.NoCamera, state(camera = ""))
        assertEquals(DetectTileState.Connecting("front"), state())
        assertEquals(DetectTileState.Offline("front"), state(failed = true))
        assertEquals(DetectTileState.Known("front", true), state(detecting = true))
        assertEquals(DetectTileState.Known("front", false), state(detecting = false))
        assertEquals(DetectTileState.Switching("front", false), state(detecting = true, pending = false))
    }

    @Test fun websocketMessages() {
        assertEquals("front/detect/set", DetectMessages.setTopic("front"))
        assertEquals("ON", DetectMessages.payload(true))
        assertEquals("OFF", DetectMessages.payload(false))
        assertEquals(true, DetectMessages.stateFrom("front", "front/detect/state", "ON"))
        assertEquals(false, DetectMessages.stateFrom("front", "front/detect/state", "\"OFF\""))
        assertNull(DetectMessages.stateFrom("front", "back/detect/state", "ON"))
        assertNull(DetectMessages.stateFrom("front", "front/recordings/state", "ON"))
        assertNull(DetectMessages.stateFrom("front", "front/detect/state", "maybe"))
    }
}
