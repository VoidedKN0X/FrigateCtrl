package com.vknxworks.frigatectrl.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsBackupTest {
    private val current = mapOf<String, Any>(
        SettingsBackup.GRID_COLUMNS to 0,
        SettingsBackup.START_MUTED to true,
        SettingsBackup.HIDE_SCREENSHOTS to true,
        SettingsBackup.HIDE_RECENTS to false,
        SettingsBackup.MUTED_CAMERAS to setOf("garage"),
    )

    @Test fun aFileNeverSwitchesAHideSwitchOff() {
        val incoming = mapOf<String, Any>(SettingsBackup.HIDE_SCREENSHOTS to false, SettingsBackup.HIDE_RECENTS to true)
        assertEquals(listOf(SettingsBackup.HIDE_RECENTS), SettingsBackup.changes(current, incoming))
    }

    @Test fun onlyDifferentValuesAreChanges() {
        val incoming = mapOf<String, Any>(
            SettingsBackup.GRID_COLUMNS to 2,
            SettingsBackup.START_MUTED to true,
            SettingsBackup.MUTED_CAMERAS to setOf("garage"),
        )
        assertEquals(listOf(SettingsBackup.GRID_COLUMNS), SettingsBackup.changes(current, incoming))
    }

    @Test fun refusesOtherFiles() {
        assertNull(SettingsBackup.parse("not json"))
        assertNull(SettingsBackup.parse("""{"app":"OtherApp","kind":"settings","version":1,"settings":{}}"""))
        assertNull(SettingsBackup.parse("""{"app":"FrigateCtrl","kind":"settings","version":99,"settings":{}}"""))
    }
}
