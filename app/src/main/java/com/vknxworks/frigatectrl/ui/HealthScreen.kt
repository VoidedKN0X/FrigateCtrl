package com.vknxworks.frigatectrl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vknxworks.frigatectrl.App
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.Locale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.vknxworks.frigatectrl.R
import com.vknxworks.frigatectrl.ui.components.CtrlCard
import com.vknxworks.frigatectrl.ui.components.CtrlHeroCard
import com.vknxworks.frigatectrl.ui.components.CtrlProgress
import com.vknxworks.frigatectrl.ui.components.CtrlSectionLabel
import com.vknxworks.frigatectrl.ui.components.CtrlStatusDot
import com.vknxworks.frigatectrl.ui.theme.CtrlColors
import com.vknxworks.frigatectrl.ui.theme.CtrlFonts
import com.vknxworks.frigatectrl.ui.theme.CtrlType

/** Frigate's /api/stats, refreshed every 5 seconds. */
@Composable
fun HealthScreen(onYou: () -> Unit, bottomPadding: Dp = 0.dp) {
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var storage by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            try {
                stats = App.api.stats(); error = null
                if (n++ % 6 == 0) storage = App.api.recordingStorage() // slower to compute; every 30 s
            } catch (e: Exception) { error = e.message ?: ctx.getString(R.string.stats_load_failed) }
            delay(5000)
        }
    }

    Column(Modifier.fillMaxSize().background(CtrlColors.Background)) {
        // ---- series tab title, with the live status under it ----
        TabTitle(
            stringResource(R.string.system_health), onYou, Modifier.statusBarsPadding(),
            label = stringResource(
                when {
                    error != null && stats != null -> R.string.health_label_offline
                    stats == null -> R.string.health_label_loading
                    else -> R.string.health_label_live
                },
            ),
        ) {
            Text(
                when {
                    error != null && stats != null -> stringResource(R.string.health_offline)
                    stats == null -> stringResource(R.string.loading)
                    else -> stringResource(R.string.health_live)
                },
                style = CtrlType.Mono,
                color = if (error != null) CtrlColors.Error else CtrlColors.Muted,
            )
        }

        val s = stats
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(bottom = bottomPadding), Alignment.Center) {
                if (error != null) Text(error!!, color = CtrlColors.Error) else CircularProgressIndicator(color = CtrlColors.Accent)
            }
            return@Column
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = bottomPadding + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val service = s.optJSONObject("service")
            OverviewCard(service, s)

            // ---- system ----
            val system = s.optJSONObject("cpu_usages")?.optJSONObject("frigate.full_system")
            val gpus = s.optJSONObject("gpu_usages")
            if (system != null || gpus != null) HealthCard(stringResource(R.string.health_system)) {
                system?.let {
                    Meter("CPU", pct(it.opt("cpu")))
                    Meter(stringResource(R.string.health_memory), pct(it.opt("mem")))
                }
                gpus?.keys()?.forEach { name ->
                    val g = gpus.optJSONObject(name) ?: return@forEach
                    Meter(stringResource(R.string.health_gpu, name), pct(g.opt("gpu")))
                    if (g.has("mem")) Meter(stringResource(R.string.health_gpu_memory, name), pct(g.opt("mem")))
                }
            }

            // ---- detectors ----
            val detectors = s.optJSONObject("detectors")
            if (detectors != null && detectors.length() > 0) HealthCard(stringResource(R.string.health_detectors)) {
                detectors.keys().forEach { name ->
                    val d = detectors.optJSONObject(name) ?: return@forEach
                    val speed = d.optDouble("inference_speed", Double.NaN)
                    val temp = d.optDouble("temperature", Double.NaN)
                    StatRow(
                        name,
                        buildList {
                            if (!speed.isNaN()) add("${fmt1(speed)} ms")
                            if (!temp.isNaN()) add("${fmt1(temp)} °C")
                        }.joinToString(stringResource(R.string.list_separator)),
                        warn = !speed.isNaN() && speed > 100,
                    )
                }
                Text(stringResource(R.string.health_inference_note),
                    style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
            }

            // ---- storage ----
            val disks = service?.optJSONObject("storage")
            if (disks != null) HealthCard(stringResource(R.string.health_storage)) {
                if (service?.optBoolean("retention_unmet", false) == true) WarningLine(stringResource(R.string.health_retention_unmet))
                listOf("/media/frigate/recordings" to stringResource(R.string.health_recordings), "/media/frigate/clips" to stringResource(R.string.health_clips), "/tmp/cache" to stringResource(R.string.health_cache))
                    .forEach { (path, label) ->
                        val d = disks.optJSONObject(path) ?: return@forEach
                        val total = d.optDouble("total", 0.0); val used = d.optDouble("used", 0.0)
                        if (total > 0) Meter(label, used / total * 100, stringResource(R.string.health_used_of, size(used), size(total)))
                    }
                storage?.let { st ->
                    val rows = st.keys().asSequence().mapNotNull { cam ->
                        st.optJSONObject(cam)?.let { cam to it.optDouble("usage", 0.0) }
                    }.filter { it.second > 0 }.sortedByDescending { it.second }.toList()
                    if (rows.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = CtrlColors.Divider)
                        CtrlSectionLabel(stringResource(R.string.health_per_camera))
                        rows.forEach { (cam, mb) ->
                            val bw = st.optJSONObject(cam)?.optDouble("bandwidth", Double.NaN) ?: Double.NaN
                            StatRow(cam.replace('_', ' '), if (!bw.isNaN()) stringResource(R.string.storage_with_rate, size(mb), size(bw)) else size(mb))
                        }
                    }
                }
            }

            // ---- cameras ----
            val cams = s.optJSONObject("cameras")
            if (cams != null && cams.length() > 0) HealthCard(stringResource(R.string.tab_cameras)) {
                cams.keys().forEach { name ->
                    val c = cams.optJSONObject(name) ?: return@forEach
                    val fps = c.optDouble("camera_fps", 0.0)
                    val det = c.optDouble("detection_fps", 0.0)
                    val skipped = c.optDouble("skipped_fps", 0.0)
                    val quality = c.optString("connection_quality", "")
                    val detectText = stringResource(R.string.health_detect, fmt1(det))
                    val skippedText = stringResource(R.string.health_skipped_value, fmt1(skipped))
                    StatRow(
                        name.replace('_', ' '),
                        buildList {
                            add("${fmt1(fps)} fps")
                            add(detectText)
                            if (skipped > 0) add(skippedText)
                            if (quality.isNotBlank()) add(quality)
                        }.joinToString(stringResource(R.string.list_separator)),
                        warn = fps <= 0.0 || skipped > 0 || quality.equals("poor", true),
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(service: JSONObject?, s: JSONObject) {
    val version = service?.optString("version").orEmpty()
    val latest = service?.optString("latest_version").orEmpty()
    val uptime = service?.optLong("uptime", 0) ?: 0
    // The hero: detection fps, the number that says whether Frigate is keeping up.
    CtrlHeroCard(
        label = stringResource(R.string.health_detection_fps),
        trailingLabel = stringResource(R.string.you_frigate_version, version.substringBefore('-')),
    ) {
        Text(fmt1(s.optDouble("detection_fps", 0.0)), style = CtrlType.HeroNumber, color = CtrlColors.OnAccent)
        Text(
            stringResource(R.string.health_uptime, duration(uptime)),
            style = CtrlType.Mono, color = CtrlColors.OnAccent,
        )
        if (latest.isNotBlank() && latest != "disabled" && version.isNotBlank() &&
            !version.startsWith(latest) && latest.first().isDigit()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.NewReleases, null, tint = CtrlColors.OnAccent, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.health_update, latest), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    color = CtrlColors.OnAccent, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        StatCard(stringResource(R.string.health_camera_fps), fmt1(s.optDouble("camera_fps", 0.0)), Modifier.weight(1f))
        StatCard(stringResource(R.string.health_skipped), fmt1(s.optDouble("skipped_fps", 0.0)), Modifier.weight(1f))
    }
}

@Composable
private fun HealthCard(title: String?, content: @Composable () -> Unit) {
    CtrlCard {
        if (title != null) CtrlSectionLabel(title)
        content()
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    CtrlCard(modifier) {
        CtrlSectionLabel(label)
        Text(value, fontFamily = CtrlFonts.Grotesk, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.9).sp, color = CtrlColors.Text)
    }
}

@Composable
private fun StatRow(label: String, value: String, warn: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp)) {
        Text(label.replaceFirstChar { it.uppercase() }, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(value, style = CtrlType.Mono, color = if (warn) Status.detection else CtrlColors.Muted)
        Spacer(Modifier.width(10.dp))
        CtrlStatusDot(if (warn) Status.detection else CtrlColors.Accent)
    }
}

@Composable
private fun Meter(label: String, percent: Double?, detail: String? = null) {
    if (percent == null) return
    val p = percent.coerceIn(0.0, 100.0)
    val color: Color = when {
        p >= 90 -> Status.alert
        p >= 75 -> Status.detection
        else -> CtrlColors.Text
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(detail ?: "${fmt1(p)} %", style = CtrlType.Mono, color = CtrlColors.Muted)
        }
        CtrlProgress((p / 100).toFloat(), color = color)
    }
}

@Composable
private fun WarningLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Warning, null, tint = Status.alert, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Status.alert, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Accepts 12.3, "12.3", "12.3%" — Frigate reports some percentages as strings. */
private fun pct(v: Any?): Double? = when (v) {
    is Number -> v.toDouble()
    is String -> v.trim().removeSuffix("%").trim().toDoubleOrNull()
    else -> null
}

private fun fmt1(v: Double) = String.format(Locale.US, "%.1f", v)

private fun size(mb: Double): String = when {
    mb >= 1024 * 1024 -> String.format(Locale.US, "%.2f TB", mb / 1024 / 1024)
    mb >= 1024 -> String.format(Locale.US, "%.1f GB", mb / 1024)
    else -> String.format(Locale.US, "%.0f MB", mb)
}

private fun duration(sec: Long): String {
    val d = sec / 86400; val h = (sec % 86400) / 3600; val m = (sec % 3600) / 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}
