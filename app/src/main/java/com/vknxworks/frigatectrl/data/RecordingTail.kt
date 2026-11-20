package com.vknxworks.frigatectrl.data

import kotlin.math.floor

/**
 * Keeps a camera's list of recording segments up to date without asking Frigate for the whole day again.
 *
 * Frigate only ever adds segments at the end of a camera's recordings, so a refresh asks for the segments
 * from a little before the newest one known ([cut]) and puts them in place of that last part ([merge]).
 * Everything that starts before the cut is kept as it was loaded; the part from the cut on is exactly what
 * Frigate answered, so segments that were added, changed or removed there are all picked up.
 */
object RecordingTail {
    /** How far before the newest known segment a refresh starts again (seconds). */
    const val OVERLAP = 120.0

    /**
     * The time (whole seconds) to ask for segments from, or null when the whole window has to be loaded:
     * nothing is known yet, or the cut would fall on or before the window's start ([windowStart]).
     * [known] is sorted by start time.
     */
    fun cut(known: List<RecordingSegment>, windowStart: Double): Double? {
        val last = known.lastOrNull() ?: return null
        val cut = floor(last.start - OVERLAP)
        return if (cut > windowStart) cut else null
    }

    /**
     * [known] with everything that starts at or after [cut] replaced by what Frigate answered for the time
     * from [cut] on ([tail]). Whichever way Frigate picks segments for a time range (by their start, or by
     * overlapping it), every segment that starts at or after [cut] is in [tail]; one that started earlier
     * and only reaches into the range is already in [known] and is taken from there. Both lists are sorted
     * by start time, and so is the result.
     */
    fun merge(known: List<RecordingSegment>, tail: List<RecordingSegment>, cut: Double): List<RecordingSegment> {
        val out = ArrayList<RecordingSegment>(known.size + tail.size)
        for (s in known) if (s.start < cut) out.add(s)
        for (s in tail) if (s.start >= cut) out.add(s)
        return out
    }
}
