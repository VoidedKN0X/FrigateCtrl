package com.vknxworks.frigateremote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vknxworks.frigateremote.App
import com.vknxworks.frigateremote.data.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onServer: () -> Unit, onLogout: () -> Unit) {
    val s = App.settings
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var confirmLogout by remember { mutableStateOf(false) }
    var frigateVersion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { frigateVersion = App.api.version() }
    val appVersion = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Exception) { null } ?: "?"
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                scrollBehavior = scroll,
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 32.dp),
        ) {
            Section("Appearance") {
                Choice(
                    icon = when (s.theme) {
                        ThemeMode.Light -> Icons.Outlined.LightMode
                        ThemeMode.Dark -> Icons.Outlined.DarkMode
                        ThemeMode.System -> Icons.Outlined.Contrast
                    },
                    title = "Theme",
                    options = listOf("System", "Light", "Dark"),
                    selected = s.theme.ordinal,
                    onSelect = { s.theme = ThemeMode.entries[it] },
                )
                Divider()
                Toggle(
                    Icons.Outlined.Palette, "Material You colours", "Match the app to your wallpaper", s.dynamicColor,
                ) { s.dynamicColor = it }
                Divider()
                Toggle(Icons.Outlined.DarkMode, "Pure black", "True black backgrounds in dark mode (saves power on OLED)", s.pureBlack) {
                    s.pureBlack = it
                }
            }

            Section("Cameras") {
                Choice(
                    Icons.Outlined.GridView, "Grid columns",
                    listOf("Auto", "1", "2", "3"),
                    s.gridColumns.coerceIn(0, 3),
                ) { s.gridColumns = it }
                Divider()
                val refreshOptions = listOf(0, 2, 4, 10)
                Choice(
                    Icons.Outlined.Refresh, "Thumbnail refresh",
                    listOf("Off", "2 s", "4 s", "10 s"),
                    refreshOptions.indexOf(s.thumbRefreshSec).coerceAtLeast(0),
                ) { s.thumbRefreshSec = refreshOptions[it] }
                Divider()
                Toggle(Icons.Outlined.VideocamOff, "Show disabled cameras", null, s.showDisabled) { s.showDisabled = it }
            }

            Section("Live view") {
                Toggle(Icons.Outlined.HighQuality, "Prefer sub stream", "Lower quality, loads faster on mobile data", s.preferSubStream) {
                    s.preferSubStream = it
                }
                Divider()
                Toggle(Icons.AutoMirrored.Outlined.VolumeOff, "Start muted", null, s.startMuted) { s.startMuted = it }
                Divider()
                Toggle(Icons.Outlined.ScreenLockPortrait, "Keep screen on", "While watching live or history", s.keepScreenOn) {
                    s.keepScreenOn = it
                }
            }

            Section("History") {
                val spans = listOf(5, 15, 60, 360)
                Choice(
                    Icons.Outlined.Timeline, "Timeline span when opened",
                    listOf("5 min", "15 min", "1 h", "6 h"),
                    spans.indexOf(s.timelineMinutes).coerceAtLeast(0),
                ) { s.timelineMinutes = spans[it] }
                Divider()
                val pre = listOf(0, 3, 5, 10)
                Choice(
                    Icons.Outlined.SkipPrevious, "Start events early by",
                    listOf("0 s", "3 s", "5 s", "10 s"),
                    pre.indexOf(s.eventPreroll).coerceAtLeast(0),
                ) { s.eventPreroll = pre[it] }
                Divider()
                Toggle(Icons.Outlined.Vibration, "Haptic ticks", "Feel each time mark while scrubbing", s.timelineHaptics) {
                    s.timelineHaptics = it
                }
            }

            Section("Server") {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Dns, null) },
                    headlineContent = { Text("Server & login") },
                    supportingContent = {
                        Text(App.prefs.baseUrl.ifBlank { "Not set" } +
                                (frigateVersion?.let { " · Frigate $it" } ?: ""))
                    },
                    colors = itemColors(),
                    modifier = Modifier.clickableItem(onServer),
                )
                Divider()
                ListItem(
                    leadingContent = { Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = cs.error) },
                    headlineContent = { Text("Log out", color = cs.error) },
                    colors = itemColors(),
                    modifier = Modifier.clickableItem { confirmLogout = true },
                )
            }

            Section("About") {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Info, null) },
                    headlineContent = { Text("FrigateCtrl $appVersion") },
                    supportingContent = { Text("VKNXWorks") },
                    colors = itemColors(),
                )
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            icon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
            title = { Text("Log out?") },
            text = { Text("The server address and saved login will be removed from this phone.") },
            confirmButton = { TextButton({ confirmLogout = false; onLogout() }) { Text("Log out") } },
            dismissButton = { TextButton({ confirmLogout = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(20.dp))
    Text(
        title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
    )
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) { Column { content() } }
}

@Composable
private fun itemColors() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

@Composable
private fun Divider() = HorizontalDivider(
    Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
)

private fun Modifier.clickableItem(onClick: () -> Unit) = this.clickable(onClick = onClick)

@Composable
private fun Toggle(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked, onChange, enabled = enabled) },
        colors = itemColors(),
        modifier = Modifier.clickable(enabled) { onChange(!checked) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Choice(
    icon: ImageVector,
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.padding(bottom = 12.dp)) {
        ListItem(
            leadingContent = { Icon(icon, null) },
            headlineContent = { Text(title) },
            colors = itemColors(),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            options.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = i == selected,
                    onClick = { onSelect(i) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(label, maxLines = 1) }
            }
        }
    }
}
