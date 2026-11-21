package com.vknxworks.frigatectrl.notify

/**
 * "Snooze this camera": for a while, alerts from one camera are not notified. Kept as camera name and
 * end time (wall-clock milliseconds) in the notification settings. Pure text handling, so it can be tested.
 */
object Snooze {
    /** How long the notification's button snoozes a camera. */
    const val DURATION_MS = 60 * 60_000L

    private val cameraName = Regex("[A-Za-z0-9_-]{1,64}")

    /** "front:1700000000000,back:1700000360000" to a map; anything odd is dropped. */
    fun decode(text: String): Map<String, Long> =
        text.split(',').mapNotNull { e ->
            val i = e.lastIndexOf(':')
            if (i <= 0) return@mapNotNull null
            val camera = e.substring(0, i).takeIf { cameraName.matches(it) } ?: return@mapNotNull null
            val until = e.substring(i + 1).toLongOrNull() ?: return@mapNotNull null
            camera to until
        }.toMap()

    fun encode(map: Map<String, Long>): String = map.entries.joinToString(",") { "${it.key}:${it.value}" }

    /** [map] without the snoozes that are over at [nowMs]. */
    fun active(map: Map<String, Long>, nowMs: Long): Map<String, Long> = map.filterValues { it > nowMs }

    /** [map] with [camera] snoozed until [untilMs]; snoozes that are already over are dropped. */
    fun with(map: Map<String, Long>, camera: String, untilMs: Long, nowMs: Long): Map<String, Long> =
        if (!cameraName.matches(camera)) active(map, nowMs) else active(map, nowMs) + (camera to untilMs)

    fun isSnoozed(map: Map<String, Long>, camera: String, nowMs: Long): Boolean = (map[camera] ?: 0L) > nowMs
}
