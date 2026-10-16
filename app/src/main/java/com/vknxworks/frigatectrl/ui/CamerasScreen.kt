package com.vknxworks.frigatectrl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ControlCamera
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vknxworks.frigatectrl.App
import com.vknxworks.frigatectrl.data.CameraGroup
import com.vknxworks.frigatectrl.data.CameraInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CamerasScreen(onOpen: (String) -> Unit, onSettings: () -> Unit, bottomPadding: Dp = 0.dp) {
    val settings = App.settings
    val cs = MaterialTheme.colorScheme
    var cameras by remember { mutableStateOf<List<CameraInfo>?>(null) }
    var groups by remember { mutableStateOf<List<CameraGroup>>(emptyList()) }
    var alerts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        error = null
        try {
            cameras = App.api.cameras()
            groups = App.api.groups
        } catch (e: Exception) { error = e.message ?: "Couldn't load cameras" }
        alerts = try {
            App.api.reviews("all", nowSec() - 86400, nowSec())
                .filter { it.isAlert && !it.reviewed }.groupingBy { it.camera }.eachCount()
        } catch (_: Exception) { alerts }
        refreshing = false
    }

    val inGroup = groups.firstOrNull { it.name == group }?.cameras
    val visible = cameras
        ?.filter { settings.showDisabled || it.enabled }
        ?.filter { inGroup == null || it.name in inGroup }
    val totalAlerts = alerts.values.sum()

    val bars = WindowInsets.statusBars.asPaddingValues()

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refreshing = true; reload++ },
        modifier = Modifier.fillMaxSize().background(cs.background),
    ) {
        LazyVerticalGrid(
            columns = if (settings.gridColumns == 0) GridCells.Adaptive(300.dp) else GridCells.Fixed(settings.gridColumns),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = bars.calculateTopPadding() + 8.dp,
                bottom = bottomPadding + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // ---- compact header ----
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                Header(
                    host = App.prefs.baseUrl.removePrefix("https://").removePrefix("http://"),
                    cameraCount = visible?.size,
                    alertCount = totalAlerts,
                    onSettings = onSettings,
                )
            }
            if (groups.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "groups") {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(group == null, { group = null }, label = { Text("All") })
                        groups.forEach { g ->
                            FilterChip(
                                group == g.name, { group = if (group == g.name) null else g.name },
                                label = { Text(g.name.replace('_', ' ').replaceFirstChar { it.uppercase() }) },
                            )
                        }
                    }
                }
            }

            when {
                error != null && cameras == null -> item(span = { GridItemSpan(maxLineSpan) }, key = "err") {
                    EmptyState("Can't reach Frigate", error!!, "Retry") { reload++ }
                }
                visible == null -> item(span = { GridItemSpan(maxLineSpan) }, key = "loading") {
                    Box(Modifier.fillMaxWidth().height(320.dp), Alignment.Center) { CircularProgressIndicator() }
                }
                visible.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    EmptyState("No cameras here", "Nothing matches this view.", null) {}
                }
                else -> items(visible, key = { it.name }) { cam ->
                    CameraCard(
                        cam, alerts[cam.name] ?: 0, compact = settings.gridColumns >= 2,
                        modifier = Modifier.animateItem(),
                    ) { onOpen(cam.name) }
                }
            }
        }
    }
}

@Composable
private fun Header(host: String, cameraCount: Int?, alertCount: Int, onSettings: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(cs.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Videocam, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Cameras", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val parts = buildList {
                add(host)
                cameraCount?.let { add("$it cams") }
                if (alertCount > 0) add("$alertCount new alerts")
            }
            Text(
                parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (App.prefs.isHttp) NotEncryptedBadge(Modifier.padding(top = 2.dp))
        }
        FilledTonalIconButton(onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
    }
}

@Composable
private fun EmptyState(title: String, body: String, action: String?, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 64.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.CloudOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp))
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            Button(onAction) { Text(action) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraCard(cam: CameraInfo, alerts: Int, compact: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    // Label colour follows the picture: dark text on bright scenes, white text on dark ones.
    var bright by remember { mutableStateOf(false) }
    val textColor = if (bright) Color(0xFF111111) else Color.White
    val scrim = if (bright) Color(0x99FFFFFF) else Color(0xB3000000)
    val refresh = App.settings.thumbRefreshSec
    val source = remember { MutableInteractionSource() }

    Card(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(if (compact) 20.dp else 28.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHighest),
        modifier = modifier.pressScale(source),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            if (cam.enabled) {
                RemoteImage(
                    App.api.latestJpg(cam.name, if (compact) 240 else 360), Modifier.fillMaxSize(),
                    refreshMs = refresh * 1000L,
                    onBitmap = { bright = bottomBrightness(it) > 0.62f },
                    placeholder = {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    },
                )
            } else {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.VideocamOff, null, tint = cs.onSurfaceVariant)
                    Text("Disabled", color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
            }

            // Top-right badges
            Row(
                Modifier.align(Alignment.TopEnd).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (alerts > 0) Badge(Status.alert, Color.White) {
                    Icon(Icons.Outlined.NotificationsActive, null, Modifier.size(12.dp), tint = Color.White)
                    Text(" $alerts", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                if (cam.hasOnvif) Badge(Color(0x99000000), Color.White) {
                    Icon(Icons.Outlined.ControlCamera, "PTZ", Modifier.size(12.dp), tint = Color.White)
                }
            }

            // Name
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, scrim)))
                    .padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (cam.enabled) Status.recording else Color.Gray))
                Spacer(Modifier.width(8.dp))
                Text(
                    cam.displayName, color = textColor, fontWeight = FontWeight.SemiBold,
                    fontSize = if (compact) 13.sp else 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Badge(bg: Color, fg: Color, content: @Composable () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
