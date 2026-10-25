package com.vknxworks.frigatectrl.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PersistableBundle
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vknxworks.frigatectrl.App
import com.vknxworks.frigatectrl.R
import com.vknxworks.frigatectrl.data.ReviewItem
import com.vknxworks.frigatectrl.notify.AlertNotifier
import com.vknxworks.frigatectrl.notify.AlertWorker
import com.vknxworks.frigatectrl.notify.Push
import com.vknxworks.frigatectrl.notify.PushStatus
import com.vknxworks.frigatectrl.notify.UnifiedPush
import com.vknxworks.frigatectrl.ui.components.CtrlSubTopBar
import com.vknxworks.frigatectrl.ui.lock.LockTrips
import com.vknxworks.frigatectrl.ui.settings.Group
import com.vknxworks.frigatectrl.ui.settings.GroupChoice
import com.vknxworks.frigatectrl.ui.settings.GroupRow
import com.vknxworks.frigatectrl.ui.settings.GroupSwitch
import com.vknxworks.frigatectrl.ui.theme.CtrlColors
import com.vknxworks.frigatectrl.ui.theme.CtrlType
import kotlinx.coroutines.launch

/** Alert notifications: the background check, wake-up pushes through ntfy and the filters. Opened from Settings. */
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val n = App.notify
    val scope = rememberCoroutineScope()

    // Permission and Android's own switch, re-read whenever FrigateCtrl comes back on screen.
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val granted = remember(tick) {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val canPost = remember(tick) { AlertNotifier.canPost(ctx) }
    val ntfyInstalled = remember(tick) { UnifiedPush.isNtfyInstalled(ctx) }
    val ntfyFree = remember(tick) { UnifiedPush.isNtfyUnrestricted(ctx) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        tick++
        if (ok && !n.enabled) turnOn(ctx)
    }

    fun ask() {
        LockTrips.expectReturn()
        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun openSystemSettings() {
        LockTrips.expectReturn()
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    // Cameras and labels for the filters.
    var cameras by remember { mutableStateOf<List<String>>(emptyList()) }
    var labels by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        cameras = runCatching { App.api.cameras().map { it.name } }.getOrDefault(emptyList())
        labels = App.api.labels()
    }

    val push by Push.status.collectAsState()
    LaunchedEffect(n.push, tick) { Push.load(ctx, n.push) }

    Column(Modifier.fillMaxSize().background(CtrlColors.Background)) {
        CtrlSubTopBar(
            title = stringResource(R.string.notifications),
            onBack = onBack,
            backIcon = Icons.AutoMirrored.Outlined.ArrowBack,
            backDescription = stringResource(R.string.back),
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 32.dp),
        ) {
            Text(
                stringResource(R.string.notif_intro),
                style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Muted,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )

            Group(stringResource(R.string.group_alerts), gutter = 0.dp, footer = lastCheckText(ctx, n.lastCheckMs, n.enabled)) {
                GroupSwitch(
                    stringResource(R.string.notif_enable), stringResource(R.string.notif_enable_subtitle),
                    n.enabled, Icons.Outlined.Notifications,
                ) { on ->
                    if (!on) turnOff(ctx)
                    else if (!granted) ask()
                    else turnOn(ctx)
                }
                if (n.enabled && !canPost) {
                    GroupRow(
                        stringResource(R.string.notif_blocked),
                        stringResource(R.string.notif_blocked_subtitle),
                        icon = Icons.Outlined.Warning, iconTint = CtrlColors.Error, showChevron = true,
                        onClick = { if (!granted) ask() else openSystemSettings() },
                    )
                }
                GroupChoice(
                    stringResource(R.string.notif_what),
                    listOf(false to stringResource(R.string.notif_alerts_only), true to stringResource(R.string.notif_alerts_detections)),
                    n.includeDetections, Icons.Outlined.NotificationsActive,
                ) { n.includeDetections = it }
                GroupChoice(
                    stringResource(R.string.notif_interval),
                    listOf(15, 30, 60, 120, 360).map { it to intervalLabel(ctx, it) },
                    n.intervalMinutes, Icons.Outlined.Schedule,
                    footer = stringResource(R.string.notif_interval_footer),
                ) {
                    n.intervalMinutes = it
                    AlertWorker.schedule(ctx)
                }
                GroupRow(
                    stringResource(R.string.notif_channels), stringResource(R.string.notif_channels_subtitle),
                    icon = Icons.Outlined.Tune, showChevron = true, onClick = ::openSystemSettings,
                )
                GroupRow(
                    stringResource(R.string.notif_test), stringResource(R.string.notif_test_subtitle),
                    icon = Icons.AutoMirrored.Outlined.Send, enabled = canPost,
                    onClick = { scope.launch { sendTest(ctx, cameras.firstOrNull()) } },
                )
            }

            Group(
                stringResource(R.string.group_quiet), gutter = 0.dp,
                footer = stringResource(R.string.quiet_footer),
            ) {
                GroupSwitch(
                    stringResource(R.string.quiet_hours), stringResource(R.string.quiet_hours_subtitle),
                    n.quietHours, Icons.Outlined.Bedtime,
                ) { n.quietHours = it }
                if (n.quietHours) {
                    val hours = (0..23).map { it to "%02d:00".format(it) }
                    GroupChoice(stringResource(R.string.quiet_from), hours, n.quietFrom) { n.quietFrom = it }
                    GroupChoice(stringResource(R.string.quiet_to), hours, n.quietTo) { n.quietTo = it }
                }
            }

            if (cameras.isNotEmpty()) {
                Group(stringResource(R.string.group_notif_cameras), gutter = 0.dp, footer = stringResource(R.string.notif_cameras_footer)) {
                    cameras.forEach { cam ->
                        GroupSwitch(AlertNotifier.cameraLabel(cam), null, cam !in n.mutedCameras, Icons.Outlined.Videocam) { on ->
                            n.mutedCameras = if (on) n.mutedCameras - cam else n.mutedCameras + cam
                        }
                    }
                }
            }
            if (labels.isNotEmpty()) {
                Group(stringResource(R.string.group_notif_labels), gutter = 0.dp, footer = stringResource(R.string.notif_labels_footer)) {
                    labels.forEach { label ->
                        GroupSwitch(
                            label.replace('_', ' ').replaceFirstChar { it.uppercase() }, null,
                            label !in n.mutedLabels, Icons.Outlined.Sell,
                        ) { on -> n.mutedLabels = if (on) n.mutedLabels - label else n.mutedLabels + label }
                    }
                }
            }

            Group(stringResource(R.string.group_push), gutter = 0.dp, footer = stringResource(R.string.push_footer)) {
                GroupSwitch(
                    stringResource(R.string.push_title), stringResource(R.string.push_subtitle),
                    n.push, Icons.Outlined.Bolt, enabled = n.enabled,
                ) { on ->
                    if (on) {
                        if (!ntfyInstalled) {
                            Toast.makeText(ctx, R.string.push_no_ntfy_short, Toast.LENGTH_LONG).show()
                        } else {
                            n.push = true
                            Push.register(ctx)
                        }
                    } else {
                        n.push = false
                        Push.unregister(ctx)
                    }
                }
                if (!ntfyInstalled) {
                    GroupRow(
                        stringResource(R.string.push_get_ntfy), stringResource(R.string.push_no_ntfy),
                        icon = Icons.Outlined.Warning, showChevron = true,
                        onClick = {
                            LockTrips.expectReturn()
                            runCatching {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UnifiedPush.NTFY_DOWNLOAD)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                    )
                }
                if (n.push) PushStatusRow(push, onRetry = { Push.register(ctx) })
                if (n.push && ntfyInstalled && !ntfyFree) {
                    GroupRow(
                        stringResource(R.string.push_battery), stringResource(R.string.push_battery_subtitle),
                        icon = Icons.Outlined.BatteryAlert, iconTint = CtrlColors.Error,
                    )
                }
            }

            if (App.vault.enabled) {
                Group(stringResource(R.string.group_privacy), gutter = 0.dp, footer = stringResource(R.string.alerts_locked_footer)) {
                    GroupSwitch(
                        stringResource(R.string.alerts_locked), stringResource(R.string.alerts_locked_subtitle),
                        App.prefs.alertsWhileLocked, Icons.Outlined.Lock,
                    ) { on ->
                        App.prefs.alertsWhileLocked = on
                        tick++
                    }
                }
            }
        }
    }
}

@Composable
private fun PushStatusRow(status: PushStatus, onRetry: () -> Unit) {
    val ctx = LocalContext.current
    when (status) {
        is PushStatus.Registered, is PushStatus.Unavailable -> {
            val endpoint = if (status is PushStatus.Registered) status.endpoint else (status as PushStatus.Unavailable).endpoint
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.push_endpoint).uppercase(), style = CtrlType.Label, color = CtrlColors.Muted)
                        SelectionContainer {
                            Text(endpoint, style = CtrlType.Mono, color = CtrlColors.Text, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton({ copySecret(ctx, endpoint) }) {
                        Icon(Icons.Outlined.ContentCopy, stringResource(R.string.push_copy), tint = CtrlColors.AccentText)
                    }
                }
                if (status is PushStatus.Unavailable) {
                    Text(stringResource(R.string.push_unavailable), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Error,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        PushStatus.Registering -> GroupRow(stringResource(R.string.push_registering), null)
        PushStatus.NoNtfy -> GroupRow(stringResource(R.string.push_no_ntfy_short), null, iconTint = CtrlColors.Error)
        is PushStatus.Failed -> GroupRow(
            stringResource(R.string.push_failed), stringResource(R.string.push_failed_subtitle, status.reason),
            icon = Icons.Outlined.Warning, iconTint = CtrlColors.Error, showChevron = true, onClick = onRetry,
        )
        PushStatus.Off -> Unit
    }
}

/** The address is a secret (anyone who knows it can post to it): copied as sensitive, so it isn't previewed. */
private fun copySecret(ctx: Context, text: String) {
    val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(ctx.getString(R.string.push_endpoint), text)
    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    cm.setPrimaryClip(clip)
}

private fun intervalLabel(ctx: Context, minutes: Int): String =
    if (minutes < 60) ctx.getString(R.string.every_minutes, minutes) else ctx.resources.getQuantityString(R.plurals.every_hours, minutes / 60, minutes / 60)

private fun lastCheckText(ctx: Context, ms: Long, enabled: Boolean): String = when {
    !enabled -> ctx.getString(R.string.notif_footer_off)
    ms <= 0 -> ctx.getString(R.string.notif_last_never)
    else -> ctx.getString(R.string.notif_last_check, DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS))
}

private fun turnOn(ctx: Context) {
    val n = App.notify
    // Only what happens from now on: no flood of the last hours' alerts.
    n.resetCheckState()
    n.markChecked(System.currentTimeMillis())
    n.enabled = true
    AlertWorker.schedule(ctx)
}

private fun turnOff(ctx: Context) {
    val n = App.notify
    n.enabled = false
    if (n.push) {
        n.push = false
        Push.unregister(ctx)
    }
    AlertWorker.schedule(ctx)
}

/** A notification like a real one, with the latest picture of a camera if it can be fetched. */
private suspend fun sendTest(ctx: Context, camera: String?) {
    val cam = camera ?: "camera"
    val r = ReviewItem(
        id = "test-${System.currentTimeMillis()}",
        camera = cam,
        start = System.currentTimeMillis() / 1000.0,
        end = null,
        severity = "alert",
        labels = listOf("person"),
        thumbPath = null,
    )
    val pic = camera?.let { c ->
        App.api.bytes(App.api.latestJpg(c, 720))?.let { runCatching { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
    }
    try {
        AlertNotifier.post(ctx, r, App.notify, test = true, picture = pic)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        // A test that can't be shown just doesn't appear.
    }
}
