package com.vknxworks.frigatectrl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vknxworks.frigatectrl.App
import com.vknxworks.frigatectrl.R
import com.vknxworks.frigatectrl.data.CameraInfo
import com.vknxworks.frigatectrl.ui.components.CtrlStatusDot
import com.vknxworks.frigatectrl.ui.components.CtrlSubTopBar
import com.vknxworks.frigatectrl.ui.theme.CtrlColors
import com.vknxworks.frigatectrl.ui.theme.CtrlFonts
import com.vknxworks.frigatectrl.ui.theme.CtrlShape
import com.vknxworks.frigatectrl.ui.theme.CtrlType

/**
 * How many cameras play live video on the wall at once. Each one is a hardware video decoder, and phones
 * only have a handful; the cameras after these show a snapshot that refreshes every second instead.
 */
private const val WALL_LIVE_MAX = 6

/** The stream to use on the wall: the camera's sub-stream when its live streams name one, else its first. */
internal fun wallStream(cam: CameraInfo): String {
    val low = cam.streams.firstOrNull { s ->
        val text = (s.label + " " + s.streamName).lowercase()
        "sub" in text || "low" in text || "sd" in text
    }
    return (low ?: cam.streams.firstOrNull())?.streamName ?: cam.name
}

/**
 * The live wall: every camera (of [group], if one is given) playing live side by side, without sound.
 * With [birdseye], Frigate's own Birdseye picture instead, played like one camera. Tap a camera to open it.
 */
@Composable
fun WallScreen(birdseye: Boolean, group: String?, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var cameras by remember { mutableStateOf<List<CameraInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(group, birdseye) {
        if (birdseye) return@LaunchedEffect
        try {
            val all = App.api.cameras()
            val inGroup = App.api.groups.firstOrNull { it.name == group }?.cameras
            cameras = all.filter { it.enabled && (inGroup == null || it.name in inGroup) }
        } catch (e: Exception) {
            error = e.message ?: ctx.getString(R.string.cameras_load_failed)
        }
    }
    KeepScreenOn(App.settings.keepScreenOn)
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxSize().background(CtrlColors.Background)) {
        CtrlSubTopBar(
            title = stringResource(if (birdseye) R.string.wall_birdseye else R.string.wall_title),
            onBack = onBack,
            backIcon = Icons.AutoMirrored.Outlined.ArrowBack,
            backDescription = stringResource(R.string.back),
            subtitle = {
                Text(stringResource(R.string.chip_live).uppercase(), style = CtrlType.Label, color = CtrlColors.Muted, modifier = Modifier.padding(top = 2.dp))
            },
        )
        if (birdseye) {
            // Birdseye is one go2rtc stream named "birdseye" (only when it is restreamed).
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(16f / 9f).clip(CtrlShape.Tile)) {
                LiveView("birdseye", "birdseye", muted = true, modifier = Modifier.fillMaxSize())
            }
            return@Column
        }
        val list = cameras
        when {
            error != null && list == null -> Text(error.orEmpty(), color = CtrlColors.Muted, modifier = Modifier.padding(24.dp))
            list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = CtrlColors.AccentText) }
            list.isEmpty() -> Text(stringResource(R.string.no_cameras_body), color = CtrlColors.Muted, modifier = Modifier.padding(24.dp))
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(280.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = navBottom + 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(list, key = { _, cam -> cam.name }) { index, cam ->
                    WallTile(cam, live = index < WALL_LIVE_MAX) { onOpen(cam.name) }
                }
                if (list.size > WALL_LIVE_MAX) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "note") {
                        Text(
                            stringResource(R.string.wall_snapshots_note, WALL_LIVE_MAX),
                            style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WallTile(cam: CameraInfo, live: Boolean, onClick: () -> Unit) {
    val shape = CtrlShape.Tile
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(Color.Black)
            .border(1.dp, CtrlColors.Outline, shape),
    ) {
        if (live) {
            LiveView(cam.name, wallStream(cam), muted = true, modifier = Modifier.fillMaxSize(), showNotes = false)
        } else {
            RemoteImage(App.api.latestJpg(cam.name, 360), Modifier.fillMaxSize(), contentScale = ContentScale.Fit, refreshMs = 1000)
        }
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, CtrlColors.VideoBackdrop.copy(alpha = 0.85f))))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CtrlStatusDot(CtrlColors.Accent)
            Spacer(Modifier.width(8.dp))
            Text(
                cam.displayName.uppercase(), color = CtrlColors.OnVideo,
                fontFamily = CtrlFonts.Mono, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
                fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        // On top of the video, so a tap anywhere on the tile opens the camera.
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}
