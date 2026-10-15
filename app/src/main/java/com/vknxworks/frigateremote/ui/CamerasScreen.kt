package com.vknxworks.frigateremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vknxworks.frigateremote.App
import com.vknxworks.frigateremote.data.CameraInfo

@Composable
fun CamerasScreen(onOpen: (String) -> Unit, onSettings: () -> Unit, onLogout: () -> Unit) {
    var cameras by remember { mutableStateOf<List<CameraInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        error = null
        try { cameras = App.api.cameras() } catch (e: Exception) { error = e.message ?: "Couldn't load cameras" }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Cameras", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(App.prefs.baseUrl.removePrefix("https://").removePrefix("http://"),
                    color = Palette.Muted, style = MaterialTheme.typography.bodySmall)
            }
            IconButton({ reload++ }) { Icon(Icons.Default.Refresh, "Refresh") }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Server settings") }, onClick = { menu = false; onSettings() })
                    DropdownMenuItem(text = { Text("Log out") }, onClick = { menu = false; onLogout() })
                }
            }
        }

        val cams = cameras
        when {
            error != null && cams == null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text(error!!, color = Palette.Alert)
                Spacer(Modifier.size(12.dp))
                Button({ reload++ }) { Text("Retry") }
            }
            cams == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(320.dp),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) {
                items(cams, key = { it.name }) { cam -> CameraCard(cam) { onOpen(cam.name) } }
            }
        }
    }
}

@Composable
private fun CameraCard(cam: CameraInfo, onClick: () -> Unit) {
    // Label colour follows the picture: dark text on bright scenes, white text on dark ones.
    var bright by remember { mutableStateOf(false) }
    val textColor = if (bright) Color(0xFF0E1116) else Color.White
    val scrim = if (bright) Color(0x99FFFFFF) else Color(0xB3000000)
    Box(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))
            .background(Palette.Surface).clickable(onClick = onClick)
    ) {
        if (cam.enabled) {
            RemoteImage(
                App.api.latestJpg(cam.name, 360), Modifier.fillMaxSize(), refreshMs = 4000,
                onBitmap = { bright = bottomBrightness(it) > 0.62f },
            )
        } else {
            Icon(Icons.Default.VideocamOff, null, tint = Palette.Muted, modifier = Modifier.align(Alignment.Center))
        }
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, scrim)))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (cam.enabled) Palette.Accent else Palette.Muted))
            Text(cam.displayName, color = textColor, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp).weight(1f))
            if (cam.hasOnvif) Icon(Icons.Default.ControlCamera, "PTZ", tint = textColor.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
        }
    }
}
