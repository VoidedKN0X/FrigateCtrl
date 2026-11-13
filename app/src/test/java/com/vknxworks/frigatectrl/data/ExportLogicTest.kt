package com.vknxworks.frigatectrl.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportLogicTest {
    private val v017 = FrigateVersion(0, 17, 2)
    private val v018 = FrigateVersion(0, 18, 2)

    @Test fun parsesVersions() {
        assertEquals(FrigateVersion(0, 18, 2), FrigateVersion.parse("0.18.2-6a1b2c3"))
        assertEquals(FrigateVersion(0, 14, 0), FrigateVersion.parse("\"0.14\""))
        assertEquals(FrigateVersion(0, 16, 1), FrigateVersion.parse("v0.16.1"))
        assertNull(FrigateVersion.parse("unknown"))
        assertTrue(FrigateVersion(0, 18, 0) > FrigateVersion(0, 17, 9))
        assertTrue(FrigateVersion(1, 0, 0) > FrigateVersion(0, 99, 0))
    }

    @Test fun startOnOldFrigateSendsPlayback() {
        val r = ExportRequests.start("front_door", 1000.9, 1060.2, timelapse = false, version = v017)
        assertEquals("/api/export/front_door/start/1000/end/1061", r.path)
        assertEquals("realtime", r.playback)
        val t = ExportRequests.start("front_door", 1000.0, 1060.0, timelapse = true, version = v017)
        assertEquals("timelapse_25x", t.playback)
        assertEquals("/api/export/front_door/start/1000/end/1060", t.path)
    }

    @Test fun unknownVersionUsesTheOldForm() {
        val r = ExportRequests.start("cam", 10.0, 20.0, timelapse = true, version = null)
        assertEquals("/api/export/cam/start/10/end/20", r.path)
        assertEquals("timelapse_25x", r.playback)
        assertFalse(ExportRequests.bulkDelete(null))
    }

    @Test fun frigate018UsesCustomExportForTimelapse() {
        val real = ExportRequests.start("cam", 10.0, 20.0, timelapse = false, version = v018)
        assertEquals("/api/export/cam/start/10/end/20", real.path)
        assertNull(real.playback)
        val lapse = ExportRequests.start("cam", 10.0, 20.0, timelapse = true, version = v018)
        assertEquals("/api/export/custom/cam/start/10/end/20", lapse.path)
        assertNull(lapse.playback)
        assertTrue(ExportRequests.bulkDelete(v018))
        assertFalse(ExportRequests.bulkDelete(v017))
    }

    @Test fun reversedRangeAndOddCameraNamesAreSafe() {
        val r = ExportRequests.start("back yard/../x", 50.0, 20.5, timelapse = false, version = v018)
        assertEquals("/api/export/back%20yard%2F..%2Fx/start/20/end/50", r.path)
    }

    private fun item(inProgress: Boolean) = ExportItem("cam_abc123", "cam", "", 0.0, "/exports/x.mp4", null, inProgress)
    private fun job(status: String, pct: Double? = null, err: String? = null) = ExportJob("cam_abc123", status, null, pct, err)

    @Test fun progressFromJob() {
        assertEquals(ExportProgress.Queued, ExportProgress.of(job("queued"), null))
        assertEquals(ExportProgress.Running(0.42f), ExportProgress.of(job("running", 42.0), item(true)))
        assertEquals(ExportProgress.Running(1f), ExportProgress.of(job("running", 250.0), item(true)))
        assertEquals(ExportProgress.Running(null), ExportProgress.of(job("running"), item(true)))
        assertEquals(ExportProgress.Failed("ffmpeg error"), ExportProgress.of(job("failed", err = "ffmpeg error"), item(true)))
        assertEquals(ExportProgress.Failed(null), ExportProgress.of(job("cancelled"), null))
    }

    @Test fun progressFromListOnOlderServers() {
        assertEquals(ExportProgress.Running(null), ExportProgress.of(null, item(true)))
        assertEquals(ExportProgress.Ready, ExportProgress.of(null, item(false)))
        // A finished job: the list decides (the file may still be being finalised).
        assertEquals(ExportProgress.Running(null), ExportProgress.of(job("success"), item(true)))
        assertEquals(ExportProgress.Ready, ExportProgress.of(job("success"), item(false)))
    }

    @Test fun exportThatNeverShowsUpFails() {
        assertEquals(ExportProgress.Unknown, ExportProgress.of(null, null, sinceStartSec = 5.0))
        assertEquals(ExportProgress.Failed(null), ExportProgress.of(null, null, sinceStartSec = 300.0))
        assertTrue(ExportProgress.Ready.finished)
        assertFalse(ExportProgress.Queued.finished)
    }
}
