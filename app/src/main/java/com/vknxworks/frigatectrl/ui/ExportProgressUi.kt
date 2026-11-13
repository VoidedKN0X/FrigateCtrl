package com.vknxworks.frigatectrl.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vknxworks.frigatectrl.App
import com.vknxworks.frigatectrl.R
import com.vknxworks.frigatectrl.data.Clips
import com.vknxworks.frigatectrl.data.ExportItem
import com.vknxworks.frigatectrl.data.ExportProgress
import com.vknxworks.frigatectrl.ui.theme.CtrlColors
import com.vknxworks.frigatectrl.ui.theme.CtrlShape
import com.vknxworks.frigatectrl.ui.theme.CtrlType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** "Queued", "Exporting 42 %", "Ready" or "Failed" for an export. */
@Composable
fun exportProgressLabel(p: ExportProgress): String = when (p) {
    ExportProgress.Queued, ExportProgress.Unknown -> stringResource(R.string.export_queued)
    is ExportProgress.Running -> p.fraction?.let { stringResource(R.string.export_running_percent, (it * 100).toInt()) }
        ?: stringResource(R.string.export_in_progress)
    ExportProgress.Ready -> stringResource(R.string.export_ready)
    is ExportProgress.Failed -> stringResource(R.string.export_failed_short)
}

/** A thin progress bar: determinate with a [fraction], else moving. */
@Composable
fun ExportProgressBar(fraction: Float?, modifier: Modifier = Modifier) {
    val m = modifier.fillMaxWidth().height(3.dp).clip(CtrlShape.Field)
    if (fraction != null) LinearProgressIndicator(
        progress = { fraction }, modifier = m, color = CtrlColors.AccentText, trackColor = CtrlColors.Raised,
        drawStopIndicator = {},
    ) else LinearProgressIndicator(m, color = CtrlColors.AccentText, trackColor = CtrlColors.Raised)
}

/** The export just started from History, followed until it's ready (then it can be saved from here). */
data class WatchedExport(val id: String, val camera: String, val askedAtSec: Double)

/**
 * A bar above the timeline that follows the export [watch] on the server: queued, its percentage while Frigate
 * makes it (0.18 and newer; a moving bar on older versions), then **Save to gallery**, or why it failed.
 */
@Composable
fun ExportWatchBar(watch: WatchedExport, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember(watch.id) { mutableStateOf<ExportProgress>(ExportProgress.Unknown) }
    var item by remember(watch.id) { mutableStateOf<ExportItem?>(null) }
    var saving by remember(watch.id) { mutableStateOf<Float?>(null) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(watch.id) {
        while (true) {
            lifecycle.awaitStarted()
            try {
                val job = App.api.exportJob(watch.id)
                // While a job reports progress, the export list isn't needed on every round.
                val listed = if (job == null || job.status.equals("success", true)) App.api.exports().firstOrNull { it.id == watch.id } else item
                item = listed
                progress = ExportProgress.of(job, listed, System.currentTimeMillis() / 1000.0 - watch.askedAtSec)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Offline for a moment: keep showing the last state and try again.
            }
            if (progress.finished) break
            delay(2_000)
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(CtrlShape.Field).background(CtrlColors.Raised).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.export_on_server), style = CtrlType.Label, fontSize = 10.sp, color = CtrlColors.Muted)
                Text(
                    when (val p = progress) {
                        is ExportProgress.Failed -> p.message?.let { stringResource(R.string.export_failed, it) } ?: stringResource(R.string.export_failed_short)
                        else -> saving?.let { stringResource(R.string.export_downloading_percent, (it * 100).toInt()) } ?: exportProgressLabel(p)
                    },
                    color = if (progress is ExportProgress.Failed) CtrlColors.Error else CtrlColors.Text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
                )
            }
            val ready = item?.takeIf { progress == ExportProgress.Ready }
            if (ready != null) TextButton(
                onClick = {
                    if (saving != null) return@TextButton
                    saving = 0f
                    scope.launch {
                        try {
                            val name = Clips.saveExport(ctx, ready) { saving = it }
                            Toast.makeText(ctx, ctx.getString(R.string.clip_saved, name), Toast.LENGTH_LONG).show()
                            onDismiss()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Toast.makeText(ctx, ctx.getString(R.string.clip_failed, e.message.orEmpty()), Toast.LENGTH_LONG).show()
                        } finally { saving = null }
                    }
                },
                enabled = saving == null,
            ) {
                Icon(Icons.Outlined.Download, null, Modifier.size(18.dp), tint = CtrlColors.AccentText)
                Text(stringResource(R.string.save_to_gallery), color = CtrlColors.AccentText, modifier = Modifier.padding(start = 6.dp))
            }
            IconButton(onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.close), tint = CtrlColors.Muted) }
        }
        val p = progress
        val bar = saving ?: (p as? ExportProgress.Running)?.fraction
        if (saving != null || (!p.finished)) ExportProgressBar(bar, Modifier.padding(end = 10.dp, top = 2.dp))
    }
}
