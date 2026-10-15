package com.vknxworks.frigatectrl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vknxworks.frigatectrl.App
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.Locale

/** Frigate's /api/stats, refreshed every 5 seconds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(bottomPadding: Dp = 0.dp) {
    val cs = MaterialTheme.colorScheme
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var storage by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            try {
                stats = App.api.stats(); error = null
                if (n++ % 6 == 0) storage = App.api.recordingStorage() // slower to compute; every 30 s
            } catch (e: Exception) { error = e.message ?: "Couldn't load stats" }
            delay(5000)
        }
    }

    Column(Modifier.fillMaxSize().background(cs.background)) {
        // ---- compact header, matching the Cameras tab ----
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(cs.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.MonitorHeart, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("System health", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        error != null && stats != null -> "Offline · showing last data"
                        stats == null -> "Loading…"
                        else -> "Live · refreshes every 5 s"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) cs.error else cs.onSurfaceVariant,
                )
            }
        }

        val s = stats
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(bottom = bottomPadding), Alignment.Center) {
                if (error != null) Text(error!!, color = cs.error) else CircularProgressIndicator()
            }
            return@Column
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 4.dp, bottom = bottomPadding + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val service = s.optJSONObject("service")
            OverviewCard(service, s)

            // ---- system ----
            val system = s.optJSONObject("cpu_usages")?.optJSONObject("frigate.full_system")
            val gpus = s.optJSONObject("gpu_usages")
            if (system != null || gpus != null) HealthCard("System") {
                system?.let {
                    Meter("CPU", pct(it.opt("cpu")))
                    Meter("Memory", pct(it.opt("mem")))
                }
                gpus?.keys()?.forEach { name ->
                    val g = gpus.optJSONObject(name) ?: return@forEach
                    Meter("$name GPU", pct(g.opt("gpu")))
                    if (g.has("mem")) Meter("$name memory", pct(g.opt("mem")))
                }
            }

            // ---- detectors ----
            val detectors = s.optJSONObject("detectors")
            if (detectors != null && detectors.length() > 0) HealthCard("Detectors") {
                detectors.keys().forEach { name ->
                    val d = detectors.optJSONObject(name) ?: return@forEach
                    val speed = d.optDouble("inference_speed", Double.NaN)
                    val temp = d.optDouble("temperature", Double.NaN)
                    StatRow(
                        name,
                        buildList {
                            if (!speed.isNaN()) add("${fmt1(speed)} ms")
                            if (!temp.isNaN()) add("${fmt1(temp)} °C")
                        }.joinToString(" · "),
                        warn = !speed.isNaN() && speed > 100,
                    )
                }
                Text("Inference time per frame — lower is better. Above ~100 ms detection may fall behind.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }

            // ---- storage ----
            val disks = service?.optJSONObject("storage")
            if (disks != null) HealthCard("Storage") {
                if (service?.optBoolean("retention_unmet", false) == true) WarningLine("Recordings are being deleted early because the disk is full")
                listOf("/media/frigate/recordings" to "Recordings", "/media/frigate/clips" to "Clips & snapshots", "/tmp/cache" to "Cache")
                    .forEach { (path, label) ->
                        val d = disks.optJSONObject(path) ?: return@forEach
                        val total = d.optDouble("total", 0.0); val used = d.optDouble("used", 0.0)
                        if (total > 0) Meter(label, used / total * 100, "${size(used)} of ${size(total)}")
                    }
                storage?.let { st ->
                    val rows = st.keys().asSequence().mapNotNull { cam ->
                        st.optJSONObject(cam)?.let { cam to it.optDouble("usage", 0.0) }
                    }.filter { it.second > 0 }.sortedByDescending { it.second }.toList()
                    if (rows.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = cs.outlineVariant.copy(alpha = 0.5f))
                        Text("Recordings per camera", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                        rows.forEach { (cam, mb) ->
                            val bw = st.optJSONObject(cam)?.optDouble("bandwidth", Double.NaN) ?: Double.NaN
                            StatRow(cam.replace('_', ' '), size(mb) + if (!bw.isNaN()) " · ${size(bw)}/h" else "")
                        }
                    }
                }
            }

            // ---- cameras ----
            val cams = s.optJSONObject("cameras")
            if (cams != null && cams.length() > 0) HealthCard("Cameras") {
                cams.keys().forEach { name ->
                    val c = cams.optJSONObject(name) ?: return@forEach
                    val fps = c.optDouble("camera_fps", 0.0)
                    val det = c.optDouble("detection_fps", 0.0)
                    val skipped = c.optDouble("skipped_fps", 0.0)
                    val quality = c.optString("connection_quality", "")
                    StatRow(
                        name.replace('_', ' '),
                        buildList {
                            add("${fmt1(fps)} fps")
                            add("detect ${fmt1(det)}")
                            if (skipped > 0) add("skipped ${fmt1(skipped)}")
                            if (quality.isNotBlank()) add(quality)
                        }.joinToString(" · "),
                        warn = fps <= 0.0 || skipped > 0 || quality.equals("poor", true),
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(service: JSONObject?, s: JSONObject) {
    val cs = MaterialTheme.colorScheme
    val version = service?.optString("version").orEmpty()
    val latest = service?.optString("latest_version").orEmpty()
    val uptime = service?.optLong("uptime", 0) ?: 0
    HealthCard(null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Memory, null, tint = cs.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Frigate ${version.substringBefore('-')}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Up ${duration(uptime)}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        if (latest.isNotBlank() && latest != "disabled" && version.isNotBlank() &&
            !version.startsWith(latest) && latest.first().isDigit()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.NewReleases, null, tint = cs.tertiary, modifier = Modifier.size(18.dp))
                Text("  Frigate $latest is available", style = MaterialTheme.typography.bodyMedium, color = cs.tertiary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            BigStat("Camera fps", fmt1(s.optDouble("camera_fps", 0.0)))
            BigStat("Detection fps", fmt1(s.optDouble("detection_fps", 0.0)))
            BigStat("Skipped", fmt1(s.optDouble("skipped_fps", 0.0)))
        }
    }
}

@Composable
private fun HealthCard(title: String?, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun BigStat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatRow(label: String, value: String, warn: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (warn) Icon(Icons.Outlined.Warning, null, tint = Status.detection, modifier = Modifier.size(16.dp).padding(end = 4.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
            color = if (warn) Status.detection else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Meter(label: String, percent: Double?, detail: String? = null) {
    if (percent == null) return
    val p = percent.coerceIn(0.0, 100.0)
    val color: Color = when {
        p >= 90 -> Status.alert
        p >= 75 -> Status.detection
        else -> MaterialTheme.colorScheme.primary
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(detail ?: "${fmt1(p)} %", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { (p / 100).toFloat() },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
    }
}

@Composable
private fun WarningLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Warning, null, tint = Status.alert, modifier = Modifier.size(18.dp))
        Text("  $text", style = MaterialTheme.typography.bodyMedium, color = Status.alert)
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
