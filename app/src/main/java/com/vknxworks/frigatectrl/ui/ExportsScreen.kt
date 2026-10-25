package com.vknxworks.frigatectrl.ui

import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.vknxworks.frigatectrl.App
import com.vknxworks.frigatectrl.R
import com.vknxworks.frigatectrl.data.Clips
import com.vknxworks.frigatectrl.data.ExportItem
import com.vknxworks.frigatectrl.notify.AlertNotifier
import com.vknxworks.frigatectrl.ui.components.CtrlAlertDialog
import com.vknxworks.frigatectrl.ui.components.CtrlChip
import com.vknxworks.frigatectrl.ui.components.CtrlStatusDot
import com.vknxworks.frigatectrl.ui.components.CtrlSubTopBar
import com.vknxworks.frigatectrl.ui.components.ctrlFieldColors
import com.vknxworks.frigatectrl.ui.components.topDivider
import com.vknxworks.frigatectrl.ui.theme.CtrlColors
import com.vknxworks.frigatectrl.ui.theme.CtrlShape
import com.vknxworks.frigatectrl.ui.theme.CtrlType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The server export playing full screen, if any (shown on top of the tabs by MainActivity). */
object ExportPlayer {
    var current by mutableStateOf<ExportItem?>(null)
}

/**
 * Frigate's server-side exports: the MP4s you (or the Frigate web UI) exported from a recording range. Lists
 * them newest first with their state, refreshing every few seconds while one is still being made.
 */
@Composable
fun ExportsScreen(onYou: () -> Unit, bottomPadding: Dp = 0.dp) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var exports by remember { mutableStateOf<List<ExportItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var camera by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<ExportItem?>(null) }
    var deleting by remember { mutableStateOf<ExportItem?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        while (true) {
            try {
                exports = App.api.exports(); error = null
            } catch (e: Exception) {
                error = e.message ?: ctx.getString(R.string.exports_load_failed)
            }
            // Quick refreshes while Frigate is still writing an export, so it turns ready by itself.
            delay(if (exports.orEmpty().any { it.inProgress }) 3_000 else 30_000)
        }
    }

    fun download(e: ExportItem, share: Boolean) {
        if (busyId != null) return
        busyId = e.id
        Toast.makeText(ctx, ctx.getString(R.string.clip_preparing), Toast.LENGTH_SHORT).show()
        scope.launch {
            try {
                if (share) Clips.shareExport(ctx, e)
                else Toast.makeText(ctx, ctx.getString(R.string.clip_saved, Clips.saveExport(ctx, e)), Toast.LENGTH_LONG).show()
            } catch (ex: Exception) {
                Toast.makeText(ctx, ctx.getString(R.string.clip_failed, ex.message.orEmpty()), Toast.LENGTH_LONG).show()
            } finally { busyId = null }
        }
    }

    Column(Modifier.fillMaxSize().background(CtrlColors.Background)) {
        val list = exports
        val running = list.orEmpty().count { it.inProgress }
        TabTitle(
            stringResource(R.string.exports_title), onYou, Modifier.statusBarsPadding(),
            label = stringResource(if (error != null && list != null) R.string.health_label_offline else R.string.exports_label),
        ) {
            Text(
                when {
                    list == null -> stringResource(R.string.loading)
                    running > 0 -> pluralString(R.plurals.exports_running, running)
                    else -> pluralString(R.plurals.exports_count, list.size)
                },
                style = CtrlType.Mono, color = if (error != null) CtrlColors.Error else CtrlColors.Muted,
            )
        }
        if (list == null) {
            Box(Modifier.fillMaxSize().padding(bottom = bottomPadding), Alignment.Center) {
                if (error != null) Text(error!!, color = CtrlColors.Error, modifier = Modifier.padding(24.dp))
                else CircularProgressIndicator(color = CtrlColors.AccentText)
            }
            return@Column
        }
        val cams = list.map { it.camera }.distinct().sorted()
        if (cams.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CtrlChip(stringResource(R.string.filter_all), camera == null, { camera = null })
                cams.forEach { c -> CtrlChip(AlertNotifier.cameraLabel(c), camera == c, { camera = c }) }
            }
        }
        val shown = list.filter { camera == null || it.camera == camera }
        if (shown.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 32.dp).padding(bottom = bottomPadding),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.VideoLibrary, null, Modifier.size(40.dp), tint = CtrlColors.Muted)
                Spacer(Modifier.size(12.dp))
                Text(stringResource(R.string.exports_empty), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.exports_empty_body), color = CtrlColors.Muted,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp),
                )
            }
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = bottomPadding + 16.dp),
        ) {
            items(shown, key = { it.id }) { e ->
                ExportRow(
                    e, busy = busyId == e.id,
                    onPlay = { if (!e.inProgress) ExportPlayer.current = e },
                    onSave = { download(e, false) },
                    onShare = { download(e, true) },
                    onRename = { renaming = e },
                    onDelete = { deleting = e },
                )
            }
        }
    }

    renaming?.let { e ->
        var name by remember(e.id) { mutableStateOf(e.name) }
        var saving by remember { mutableStateOf(false) }
        CtrlAlertDialog(
            onDismissRequest = { if (!saving) renaming = null },
            icon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
            title = { Text(stringResource(R.string.export_rename)) },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(100) }, singleLine = true,
                    label = { Text(stringResource(R.string.export_name)) },
                    shape = CtrlShape.Field, colors = ctrlFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton({
                    saving = true
                    scope.launch {
                        try {
                            App.api.renameExport(e.id, name)
                            renaming = null
                            reload++
                        } catch (ex: Exception) {
                            Toast.makeText(ctx, ctx.getString(R.string.update_failed, ex.message.orEmpty()), Toast.LENGTH_LONG).show()
                        } finally { saving = false }
                    }
                }, enabled = name.isNotBlank() && !saving) { Text(stringResource(R.string.export_save_name), color = CtrlColors.AccentText) }
            },
            dismissButton = { TextButton({ renaming = null }, enabled = !saving) { Text(stringResource(R.string.cancel), color = CtrlColors.Text) } },
        )
    }

    deleting?.let { e ->
        CtrlAlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text(stringResource(R.string.export_delete_title)) },
            text = { Text(stringResource(R.string.export_delete_body, e.name.ifBlank { e.id })) },
            confirmButton = {
                TextButton({
                    deleting = null
                    scope.launch {
                        try {
                            App.api.deleteExport(e.id)
                            exports = exports?.filter { it.id != e.id }
                            reload++
                        } catch (ex: Exception) {
                            Toast.makeText(ctx, ctx.getString(R.string.update_failed, ex.message.orEmpty()), Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text(stringResource(R.string.export_delete), color = CtrlColors.Error) }
            },
            dismissButton = { TextButton({ deleting = null }) { Text(stringResource(R.string.cancel), color = CtrlColors.Text) } },
        )
    }
}

@Composable
private fun pluralString(id: Int, n: Int): String = LocalContext.current.resources.getQuantityString(id, n, n)

@Composable
private fun ExportRow(
    e: ExportItem,
    busy: Boolean,
    onPlay: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val fmt = remember { SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault()) }
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().topDivider().clickable(enabled = !e.inProgress, onClick = onPlay)
            .heightIn(min = 76.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(CtrlColors.Raised),
            contentAlignment = Alignment.Center,
        ) {
            e.thumbPath?.let { RemoteImage(App.api.url(it), Modifier.fillMaxSize()) }
            if (e.inProgress) {
                Box(Modifier.fillMaxSize().background(CtrlColors.VideoScrim), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = CtrlColors.OnVideo)
                }
            } else {
                Icon(Icons.Outlined.PlayArrow, null, Modifier.size(26.dp), tint = Color.White.copy(alpha = 0.9f))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                e.name.ifBlank { AlertNotifier.cameraLabel(e.camera) }, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${AlertNotifier.cameraLabel(e.camera)} · ${if (e.date > 0) fmt.format(Date((e.date * 1000).toLong())) else ""}",
                style = CtrlType.Mono, fontSize = 11.sp, color = CtrlColors.Muted, maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                CtrlStatusDot(if (e.inProgress) CtrlColors.AccentText else CtrlColors.Inactive)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(if (e.inProgress) R.string.export_in_progress else R.string.export_ready).uppercase(),
                    style = CtrlType.Label, fontSize = 10.sp,
                    color = if (e.inProgress) CtrlColors.AccentText else CtrlColors.Muted,
                )
            }
        }
        if (busy) {
            CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.dp, color = CtrlColors.AccentText)
        } else Box {
            IconButton({ menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more), tint = CtrlColors.Muted) }
            DropdownMenu(
                menu, { menu = false },
                shape = CtrlShape.Field, containerColor = CtrlColors.Raised,
                border = BorderStroke(1.dp, CtrlColors.ControlOutline),
            ) {
                if (!e.inProgress) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.play)) },
                        leadingIcon = { Icon(Icons.Outlined.PlayArrow, null) },
                        onClick = { menu = false; onPlay() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.save_to_gallery)) },
                        leadingIcon = { Icon(Icons.Outlined.Download, null) },
                        onClick = { menu = false; onSave() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share)) },
                        leadingIcon = { Icon(Icons.Outlined.Share, null) },
                        onClick = { menu = false; onShare() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.export_rename)) },
                    leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                    onClick = { menu = false; onRename() },
                )
                if (!e.inProgress) DropdownMenuItem(
                    text = { Text(stringResource(R.string.export_delete), color = CtrlColors.Error) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = CtrlColors.Error) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

/** Plays a server export full screen, with the standard player controls. */
@OptIn(UnstableApi::class)
@Composable
fun ExportPlayerScreen(e: ExportItem, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember(e.id) {
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(App.api.http)))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(App.api.exportVideoUrl(e)))
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }
    Column(Modifier.fillMaxSize().background(CtrlColors.Background)) {
        CtrlSubTopBar(
            title = e.name.ifBlank { AlertNotifier.cameraLabel(e.camera) },
            onBack = onClose,
            backIcon = Icons.AutoMirrored.Outlined.ArrowBack,
            backDescription = stringResource(R.string.back),
            subtitle = {
                Text(AlertNotifier.cameraLabel(e.camera).uppercase(), style = CtrlType.Label, color = CtrlColors.Muted,
                    modifier = Modifier.padding(top = 2.dp))
            },
        )
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
            AndroidView(
                factory = { c -> PlayerView(c).apply { useController = true; setShowNextButton(false); setShowPreviousButton(false) } },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
