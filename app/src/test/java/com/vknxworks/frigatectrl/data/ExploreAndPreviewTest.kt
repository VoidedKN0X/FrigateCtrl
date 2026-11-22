package com.vknxworks.frigatectrl.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreAndPreviewTest {
    @Test fun picksThePreviewClipForAMoment() {
        val clips = listOf(PreviewClip("/a.mp4", 100.0, 200.0), PreviewClip("/b.mp4", 200.0, 300.0))
        assertEquals("/a.mp4", PreviewPick.clipFor(clips, 150.0)?.src)
        assertEquals("/b.mp4", PreviewPick.clipFor(clips, 200.0)?.src)
        assertNull(PreviewPick.clipFor(clips, 300.0))
        assertEquals(50_000_000L, PreviewPick.offsetUs(clips[0], 150.0))
        assertEquals(0L, PreviewPick.offsetUs(clips[0], 90.0))
    }

    @Test fun picksTheCachedFrameBeforeAMoment() {
        val names = listOf("preview_front-110.5.webp", "preview_front-100.25.webp", "preview_back-105.0.webp", "junk")
        assertEquals(100.25, PreviewPick.frameTime("preview_front-100.25.webp", "front")!!, 0.0)
        assertNull(PreviewPick.frameTime("preview_back-105.0.webp", "front"))
        assertEquals("preview_front-100.25.webp", PreviewPick.frameFor(names, "front", 105.0))
        assertEquals("preview_front-110.5.webp", PreviewPick.frameFor(names, "front", 120.0))
        assertEquals("preview_front-100.25.webp", PreviewPick.frameFor(names, "front", 50.0))
        assertNull(PreviewPick.frameFor(names, "garage", 105.0))
        assertTrue(PreviewPick.safeFrameName("preview_front-100.25.webp"))
        assertFalse(PreviewPick.safeFrameName("../../etc/passwd"))
    }

    @Test fun buildsExploreRequests() {
        val f = ExploreFilter(cameras = setOf("front door"), labels = setOf("person", "car"), lastHours = 24)
        assertEquals(
            "/api/events?cameras=front%20door&labels=car,person&after=913600&limit=50&sort=date_desc&include_thumbnails=0",
            ExploreRequests.list(f, nowSec = 1_000_000.0),
        )
        val all = ExploreFilter(lastHours = 0)
        assertEquals(
            "/api/events?cameras=all&labels=all&before=500.000&limit=50&sort=date_desc&include_thumbnails=0",
            ExploreRequests.list(all, nowSec = 1_000_000.0, before = 500.0),
        )
        assertTrue(ExploreRequests.search(all.copy(query = " red car "), 1_000_000.0).startsWith("/api/events/search?query=red%20car&search_type=thumbnail,description&"))
        assertTrue(ExploreRequests.idOk("1718000000.123456-abc123"))
        assertFalse(ExploreRequests.idOk("../x"))
    }

    @Test fun onlyFrigate018AndNewerGetProfilesAndMotionSearch() {
        assertFalse(Frigate018.supported(null))
        assertFalse(Frigate018.supported(FrigateVersion(0, 17, 2)))
        assertTrue(Frigate018.supported(FrigateVersion(0, 18, 0)))
        assertTrue(Frigate018.supported(FrigateVersion(1, 0, 0)))
        assertTrue(Frigate018.profileNameOk("away_mode"))
        assertFalse(Frigate018.profileNameOk("a b"))
        assertTrue(Frigate018.jobIdOk("motion_search-12.ab"))
        assertFalse(Frigate018.jobIdOk("a/b"))
    }

    @Test
    fun motionSearchPollingGivesUp() {
        assertFalse(MotionPoll.giveUp(0, 0))
        assertFalse(MotionPoll.giveUp(MotionPoll.MAX_UNREADABLE - 1, 60_000))
        assertTrue(MotionPoll.giveUp(MotionPoll.MAX_UNREADABLE, 60_000))
        assertTrue(MotionPoll.giveUp(0, MotionPoll.MAX_MS))
    }
}
