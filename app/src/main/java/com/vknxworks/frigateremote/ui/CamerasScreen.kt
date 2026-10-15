package com.vknxworks.frigateremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ControlCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vknxworks.frigateremote.App
import com.vknxworks.frigateremote.data.CameraInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CamerasScreen(onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val settings = App.settings
    var cameras by remember { mutableStateOf<List<CameraInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        error = null
        try { cameras = App.api.cameras() } catch (e: Exception) { error = e.message ?: "Couldn't load cameras" }
        refreshing = false
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val visible = cameras?.filter { settings.showDisabled || it.enabled }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Cameras", fontWeight = FontWeight.SemiBold)
                        val host = App.prefs.baseUrl.removePrefix("https://").removePrefix("http://")
                        val count = visible?.size?.let { " · $it cameras" } ?: ""
                        Text("$host$count", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                },
                scrollBehavior = scroll,
            )
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; reload++ },
            modifier = Modifier.fillMaxSize().padding(pad),
        ) {
            when {
                error != null && cameras == null -> EmptyState(
                    title = "Can't reach Frigate",
                    body = error!!,
                    action = "Retry",
                    onAction = { reload++ },
                )
                visible == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                visible.isEmpty() -> EmptyState("No cameras", "Frigate didn't report any cameras.", null, {})
                else -> LazyVerticalGrid(
                    columns = if (settings.gridColumns == 0) GridCells.Adaptive(300.dp) else GridCells.Fixed(settings.gridColumns),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(visible, key = { it.name }) { cam ->
                        CameraCard(cam, compact = settings.gridColumns >= 2) { onOpen(cam.name) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String, action: String?, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
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
private fun CameraCard(cam: CameraInfo, compact: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    // Label colour follows the picture: dark text on bright scenes, white text on dark ones.
    var bright by remember { mutableStateOf(false) }
    val textColor = if (bright) Color(0xFF111111) else Color.White
    val scrim = if (bright) Color(0x99FFFFFF) else Color(0xB3000000)
    val refresh = App.settings.thumbRefreshSec

    ElevatedCard(onClick = onClick, shape = MaterialTheme.shapes.large) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(cs.surfaceContainerHighest)) {
            if (cam.enabled) {
                RemoteImage(
                    App.api.latestJpg(cam.name, if (compact) 240 else 360), Modifier.fillMaxSize(),
                    refreshMs = refresh * 1000L,
                    onBitmap = { bright = bottomBrightness(it) > 0.62f },
                    placeholder = {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                        }
                    },
                )
            } else {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.VideocamOff, null, tint = cs.onSurfaceVariant)
                    Text("Disabled", color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, scrim)))
                    .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (cam.enabled) Status.recording else Color.Gray))
                Spacer(Modifier.width(8.dp))
                Text(
                    cam.displayName, color = textColor, fontWeight = FontWeight.SemiBold,
                    fontSize = if (compact) 13.sp else 16.sp, maxLines = 1, modifier = Modifier.weight(1f),
                )
                if (cam.hasOnvif) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(textColor.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ControlCamera, null, tint = textColor, modifier = Modifier.size(14.dp))
                            if (!compact) Text(" PTZ", color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
