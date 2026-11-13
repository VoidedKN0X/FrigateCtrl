package com.vknxworks.frigatectrl.data

import kotlin.math.ceil

/** A Frigate server version such as "0.18.2-6a1b2c3" (only the numbers matter here). */
data class FrigateVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<FrigateVersion> {
    override fun compareTo(other: FrigateVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    companion object {
        private val NUMBERS = Regex("""^\s*"?v?(\d{1,4})\.(\d{1,4})(?:\.(\d{1,4}))?""")

        fun parse(text: String): FrigateVersion? {
            val m = NUMBERS.find(text) ?: return null
            return FrigateVersion(
                m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull() ?: 0,
            )
        }

        /** Frigate 0.18: export jobs with progress, custom exports for time-lapses, bulk delete. */
        val EXPORT_JOBS = FrigateVersion(0, 18, 0)
    }
}

/** An export job as Frigate 0.18 reports it while it's queued or running. */
data class ExportJob(
    val id: String,
    /** queued, running, success, failed or cancelled. */
    val status: String,
    /** What the server is doing right now, when it says (e.g. "encoding"). */
    val step: String?,
    /** 0..100, when the server reports it. */
    val percent: Double?,
    val error: String?,
)

/** Where one export stands, from its job (newer Frigate) and its entry in the exports list. */
sealed interface ExportProgress {
    data object Queued : ExportProgress
    /** [fraction] is 0..1, or null when the server doesn't say how far it is. */
    data class Running(val fraction: Float?) : ExportProgress
    data object Ready : ExportProgress
    data class Failed(val message: String?) : ExportProgress
    /** Not in the list (yet): Frigate adds it a moment after accepting the request. */
    data object Unknown : ExportProgress

    val finished: Boolean get() = this is Ready || this is Failed

    companion object {
        /**
         * [job] is the server's job for the export, if it has one; [item] its entry in /api/exports, if listed;
         * [sinceStartSec] how long ago the export was asked for (an export that never shows up has failed).
         */
        fun of(job: ExportJob?, item: ExportItem?, sinceStartSec: Double = 0.0): ExportProgress {
            if (job != null) when (job.status.lowercase()) {
                "queued" -> return Queued
                "running" -> return Running(job.percent?.let { (it / 100.0).coerceIn(0.0, 1.0).toFloat() })
                "failed", "cancelled" -> return Failed(job.error)
                // "success": the export entry below is the truth (it may still be finishing its thumbnail).
            }
            return when {
                item == null -> if (sinceStartSec > NEVER_LISTED_SEC) Failed(null) else Unknown
                item.inProgress -> Running(null)
                else -> Ready
            }
        }

        /** An export that hasn't appeared in the list after this long didn't start. */
        const val NEVER_LISTED_SEC = 120.0
    }
}

/** How export requests look for a given Frigate version (pure, so it can be unit-tested). */
object ExportRequests {
    data class Start(val path: String, val playback: String?)

    private fun seg(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /**
     * Start of [start]..[end] of [camera]. Times are whole seconds: the start rounds down and the end up, so the
     * export never cuts off the chosen range. On Frigate 0.18+ a time-lapse goes to the custom export route,
     * whose defaults are Frigate's time-lapse settings; older versions take `playback` in the body instead.
     */
    fun start(camera: String, start: Double, end: Double, timelapse: Boolean, version: FrigateVersion?): Start {
        val a = minOf(start, end)
        val b = maxOf(start, end)
        val range = "${seg(camera)}/start/${a.toLong()}/end/${ceil(b).toLong()}"
        val newApi = version != null && version >= FrigateVersion.EXPORT_JOBS
        return when {
            timelapse && newApi -> Start("/api/export/custom/$range", null)
            newApi -> Start("/api/export/$range", null)
            else -> Start("/api/export/$range", if (timelapse) "timelapse_25x" else "realtime")
        }
    }

    /** Frigate 0.18 removed DELETE /api/export/<id>; it deletes with POST /api/exports/delete. */
    fun bulkDelete(version: FrigateVersion?): Boolean = version != null && version >= FrigateVersion.EXPORT_JOBS
}
