package com.vknxworks.frigatectrl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ControlCamera
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ripple
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import com.vknxworks.frigatectrl.R
import com.vknxworks.frigatectrl.ui.components.CtrlButton
import com.vknxworks.frigatectrl.ui.components.CtrlChip
import com.vknxworks.frigatectrl.ui.components.CtrlHeroCard
import com.vknxworks.frigatectrl.ui.components.CtrlStatusDot
import com.vknxworks.frigatectrl.ui.theme.CtrlColors
import com.vknxworks.frigatectrl.ui.theme.CtrlFonts
import com.vknxworks.frigatectrl.ui.theme.CtrlShape
import com.vknxworks.frigatectrl.ui.theme.CtrlType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CamerasScreen(onOpen: (String) -> Unit, onYou: () -> Unit, bottomPadding: Dp = 0.dp) {
    val settings = App.settings
    var cameras by remember { mutableStateOf<List<CameraInfo>?>(null) }
    var groups by remember { mutableStateOf<List<CameraGroup>>(emptyList()) }
    var alerts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current

    LaunchedEffect(reload) {
        error = null
        try {
            cameras = App.api.cameras()
            groups = App.api.groups
        } catch (e: Exception) { error = e.message ?: ctx.getString(R.string.cameras_load_failed) }
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
        modifier = Modifier.fillMaxSize().background(CtrlColors.Background),
    ) {
        LazyVerticalGrid(
            columns = if (settings.gridColumns == 0) GridCells.Adaptive(300.dp) else GridCells.Fixed(settings.gridColumns),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = bars.calculateTopPadding(),
                bottom = bottomPadding + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // ---- compact header ----
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                Header(
                    host = App.prefs.baseUrl.removePrefix("https://").removePrefix("http://"),
                    cameraCount = visible?.size,
                    alertCount = totalAlerts,
                    onYou = onYou,
                )
            }
            // The hero: unreviewed alerts from the last 24 hours, the one number that matters most here.
            if (visible != null) item(span = { GridItemSpan(maxLineSpan) }, key = "hero") {
                CtrlHeroCard(
                    label = stringResource(R.string.hero_alerts_label),
                    trailingLabel = pluralStringResource(R.plurals.cameras_count, visible.size, visible.size),
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "%02d".format(totalAlerts.coerceAtMost(99)),
                            style = CtrlType.HeroNumber, color = CtrlColors.OnAccent,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            pluralStringResource(R.plurals.hero_alerts_unit, totalAlerts),
                            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = CtrlColors.OnAccent,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }
            if (groups.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "groups") {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CtrlChip(stringResource(R.string.filter_all), group == null, { group = null })
                        groups.forEach { g ->
                            CtrlChip(
                                g.name.replace('_', ' ').replaceFirstChar { it.uppercase() },
                                group == g.name, { group = if (group == g.name) null else g.name },
                            )
                        }
                    }
                }
            }

            when {
                error != null && cameras == null -> item(span = { GridItemSpan(maxLineSpan) }, key = "err") {
                    EmptyState(stringResource(R.string.cant_reach_frigate), error!!, stringResource(R.string.retry)) { reload++ }
                }
                visible == null -> item(span = { GridItemSpan(maxLineSpan) }, key = "loading") {
                    Box(Modifier.fillMaxWidth().height(320.dp), Alignment.Center) { CircularProgressIndicator(color = CtrlColors.Accent) }
                }
                visible.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    EmptyState(stringResource(R.string.no_cameras), stringResource(R.string.no_cameras_body), null) {}
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
private fun Header(host: String, cameraCount: Int?, alertCount: Int, onYou: () -> Unit) {
    // The parent grid already pads 16 dp on each side.
    TabTitle(
        stringResource(R.string.tab_cameras), onYou, inset = 16.dp,
        label = stringResource(if (cameraCount == null) R.string.nvr_connecting else R.string.nvr_online),
    ) {
        val camsText = cameraCount?.let { pluralStringResource(R.plurals.cameras_count, it, it) }
        val alertsText = if (alertCount > 0) pluralStringResource(R.plurals.new_alerts_count, alertCount, alertCount) else null
        Text(
            listOfNotNull(host, camsText, alertsText).joinToString(stringResource(R.string.list_separator)),
            style = CtrlType.Mono, color = CtrlColors.Muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (App.prefs.isHttp) NotEncryptedBadge(Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun EmptyState(title: String, body: String, action: String?, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.CloudOff, null, Modifier.size(40.dp), tint = CtrlColors.Muted)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, color = CtrlColors.Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp))
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            CtrlButton(action, onAction, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CameraCard(cam: CameraInfo, alerts: Int, compact: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val refresh = App.settings.thumbRefreshSec
    val source = remember { MutableInteractionSource() }
    val shape = CtrlShape.Tile

    Box(
        modifier
            .pressScale(source)
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.Outline, shape)
            .clickable(interactionSource = source, indication = ripple(), onClick = onClick),
    ) {
        if (cam.enabled) {
            RemoteImage(
                App.api.latestJpg(cam.name, if (compact) 240 else 360), Modifier.fillMaxSize(),
                refreshMs = refresh * 1000L,
                placeholder = {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = CtrlColors.Accent)
                    }
                },
            )
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.VideocamOff, null, Modifier.size(22.dp), tint = CtrlColors.Inactive)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.camera_disabled).uppercase(), style = CtrlType.Label, color = CtrlColors.Inactive)
            }
        }

        // Top-right badges
        Row(
            Modifier.align(Alignment.TopEnd).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (alerts > 0) Badge(CtrlColors.Accent) {
                Icon(Icons.Outlined.NotificationsActive, null, Modifier.size(12.dp), tint = CtrlColors.OnAccent)
                Spacer(Modifier.width(4.dp))
                Text("$alerts", color = CtrlColors.OnAccent, fontFamily = CtrlFonts.Mono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            if (cam.hasOnvif) Badge(CtrlColors.VideoScrim) {
                Icon(Icons.Outlined.ControlCamera, "PTZ", Modifier.size(12.dp), tint = CtrlColors.Text)
            }
        }

        // Name: mono label on a dark scrim, with the live dot.
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, CtrlColors.Background.copy(alpha = 0.85f))))
                .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CtrlStatusDot(if (cam.enabled) CtrlColors.Accent else CtrlColors.Inactive)
            Spacer(Modifier.width(8.dp))
            Text(
                cam.displayName.uppercase(), color = CtrlColors.Text,
                fontFamily = CtrlFonts.Mono, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
                fontSize = if (compact) 11.sp else 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (cam.enabled) Text(
                stringResource(R.string.chip_live), color = CtrlColors.Accent,
                style = CtrlType.Label, fontSize = if (compact) 10.sp else 11.sp,
            )
        }
    }
}

@Composable
private fun Badge(bg: Color, content: @Composable () -> Unit) {
    Row(
        Modifier.clip(CtrlShape.Badge).background(bg).padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
