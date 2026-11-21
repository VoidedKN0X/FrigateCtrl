package com.vknxworks.frigatectrl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VideocamOff
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
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.fragment.app.FragmentActivity
import com.vknxworks.frigatectrl.ui.lock.PIN_MAX
import com.vknxworks.frigatectrl.ui.lock.PIN_MIN
import com.vknxworks.frigatectrl.ui.lock.biometricAvailable
import com.vknxworks.frigatectrl.ui.lock.disableAppLock
import com.vknxworks.frigatectrl.ui.lock.enableAppLock
import com.vknxworks.frigatectrl.ui.lock.PinRequest
import com.vknxworks.frigatectrl.ui.lock.PinRequests
import com.vknxworks.frigatectrl.ui.lock.lockActivity
import com.vknxworks.frigatectrl.ui.settings.Group
import com.vknxworks.frigatectrl.ui.settings.GroupChoice
import com.vknxworks.frigatectrl.ui.settings.GroupRow
import com.vknxworks.frigatectrl.ui.settings.GroupSwitch
import android.widget.Toast
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import com.vknxworks.frigatectrl.data.lock.LockVault
import com.vknxworks.frigatectrl.ui.lock.showBiometricPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.sp
import com.vknxworks.frigatectrl.data.CrashReport
import com.vknxworks.frigatectrl.data.LiveReport
import com.vknxworks.frigatectrl.ui.components.CtrlAlertDialog
import com.vknxworks.frigatectrl.ui.theme.CtrlType
import com.vknxworks.frigatectrl.App
import androidx.compose.runtime.LaunchedEffect
import com.vknxworks.frigatectrl.notify.AlertNotifier
import com.vknxworks.frigatectrl.R
import com.vknxworks.frigatectrl.ui.components.CtrlSubTopBar
import com.vknxworks.frigatectrl.ui.theme.CtrlColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.automirrored.outlined.ArrowBack

/** Settings, opened from the You page (tap your profile picture). Server, log out and About are on the You page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onNotifications: () -> Unit = {}) {
    val s = App.settings
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val lock = App.lock
    val bioAvailable = remember { biometricAvailable(ctx) }
    var crash by remember { mutableStateOf(CrashReport.read(ctx)) }
    val crashTime = remember(crash) {
        CrashReport.time(ctx).takeIf { it > 0 }?.let {
            java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(it))
        }
    }
    var showCrash by remember { mutableStateOf(false) }
    var liveReport by remember { mutableStateOf(LiveReport.read(ctx)) }
    val liveReportTime = remember(liveReport) {
        LiveReport.time(ctx).takeIf { it > 0 }?.let {
            java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(it))
        }
    }
    var showLiveReport by remember { mutableStateOf(false) }

    // The Appearance group (theme and wallpaper colours) is gone: every Ctrl app is dark only now.
    Column(Modifier.fillMaxSize().background(CtrlColors.Background)) {
        CtrlSubTopBar(
            title = stringResource(R.string.settings),
            onBack = onBack,
            backIcon = Icons.AutoMirrored.Outlined.ArrowBack,
            backDescription = stringResource(R.string.back),
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 32.dp),
        ) {

            Section(stringResource(R.string.notifications)) {
                GroupRow(
                    stringResource(R.string.notifications),
                    stringResource(if (App.notify.enabled) R.string.notifications_on_subtitle else R.string.notifications_off_subtitle),
                    icon = Icons.Outlined.Notifications,
                    showChevron = true,
                    onClick = onNotifications,
                )
            }

            Section(
                stringResource(R.string.group_privacy),
                footer = stringResource(R.string.privacy_footer),
            ) {
                val vault = App.vault
                Toggle(
                    Icons.Outlined.Lock, stringResource(R.string.lock_app),
                    stringResource(R.string.lock_app_subtitle),
                    vault.enabled,
                ) { on -> PinRequests.request = if (on) PinRequest.ENABLE else PinRequest.DISABLE }
                if (vault.enabled) {
                    GroupRow(
                        stringResource(R.string.lock_change_pin),
                        stringResource(R.string.lock_change_pin_subtitle, PIN_MIN),
                        icon = Icons.Outlined.Password,
                        showChevron = true,
                        onClick = { PinRequests.request = PinRequest.CHANGE },
                    )
                    Toggle(
                        Icons.Outlined.Fingerprint, stringResource(R.string.lock_fingerprint),
                        stringResource(if (bioAvailable || vault.biometricEnabled) R.string.lock_fingerprint_settings_subtitle else R.string.lock_fingerprint_unavailable),
                        vault.biometricEnabled, enabled = bioAvailable || vault.biometricEnabled,
                    ) { on ->
                        val act = ctx.lockActivity() as? FragmentActivity
                        if (on && act != null) {
                            // A fresh key each time, bound to the fingerprints enrolled now.
                            val cipher = vault.biometricEnrolCipher()
                            if (cipher == null) Toast.makeText(ctx, R.string.lock_fingerprint_setup_failed, Toast.LENGTH_SHORT).show()
                            // Android can refuse to show the prompt (for example while the app is being left): no crash.
                            else runCatching {
                                showBiometricPrompt(act, ctx.getString(R.string.lock_fingerprint_enable_title), cipher, subtitle = ctx.getString(R.string.lock_fingerprint_enable_subtitle)) {
                                    if (!vault.finishBiometricEnrol(it)) Toast.makeText(ctx, R.string.lock_fingerprint_setup_failed, Toast.LENGTH_SHORT).show()
                                }
                            }.onFailure { Toast.makeText(ctx, R.string.lock_fingerprint_setup_failed, Toast.LENGTH_SHORT).show() }
                        } else if (!on) {
                            vault.disableBiometric()
                        }
                    }
                    // Seconds; -1 = only when the app restarts.
                    val afters = listOf(0, 60, 300, 900, 3600, -1)
                    Choice(
                        Icons.Outlined.Timer, stringResource(R.string.lock_again),
                        listOf(
                            R.string.lock_again_immediately, R.string.lock_again_1m, R.string.lock_again_5m,
                            R.string.lock_again_15m, R.string.lock_again_1h, R.string.lock_again_restart,
                        ).map { stringResource(it) },
                        afters.indexOf(lock.lockAfterSeconds).coerceAtLeast(0),
                    ) { lock.lockAfterSeconds = afters[it] }
                }
                Toggle(
                    Icons.Outlined.VisibilityOff, stringResource(R.string.hide_screenshots),
                    stringResource(R.string.hide_screenshots_subtitle), lock.hideScreenshots,
                ) { lock.hideScreenshots = it }
                Toggle(
                    Icons.Outlined.Layers, stringResource(R.string.hide_recents),
                    stringResource(R.string.hide_recents_subtitle), lock.hideRecents,
                ) { lock.hideRecents = it }
            }

            Section(stringResource(R.string.tab_cameras)) {
                Choice(
                    Icons.Outlined.GridView, stringResource(R.string.grid_columns),
                    listOf(stringResource(R.string.auto), "1", "2", "3"),
                    s.gridColumns.coerceIn(0, 3),
                ) { s.gridColumns = it }
                Divider()
                val refreshOptions = listOf(0, 2, 4, 10)
                Choice(
                    Icons.Outlined.Refresh, stringResource(R.string.thumb_refresh),
                    listOf(stringResource(R.string.off), "2 s", "4 s", "10 s"),
                    refreshOptions.indexOf(s.thumbRefreshSec).coerceAtLeast(0),
                ) { s.thumbRefreshSec = refreshOptions[it] }
                Divider()
                Toggle(Icons.Outlined.VideocamOff, stringResource(R.string.show_disabled), null, s.showDisabled) { s.showDisabled = it }
            }

            Section(stringResource(R.string.group_live_view)) {
                Toggle(Icons.Outlined.HighQuality, stringResource(R.string.prefer_sub), stringResource(R.string.prefer_sub_subtitle), s.preferSubStream) {
                    s.preferSubStream = it
                }
                Divider()
                Toggle(Icons.AutoMirrored.Outlined.VolumeOff, stringResource(R.string.start_muted), null, s.startMuted) { s.startMuted = it }
                Divider()
                Toggle(
                    Icons.Outlined.PictureInPictureAlt, stringResource(R.string.pip_setting),
                    stringResource(R.string.pip_setting_subtitle), s.autoPip,
                ) { s.autoPip = it }
                Divider()
                Toggle(Icons.Outlined.ScreenLockPortrait, stringResource(R.string.keep_screen_on), stringResource(R.string.keep_screen_on_subtitle), s.keepScreenOn) {
                    s.keepScreenOn = it
                }
            }

            Section(stringResource(R.string.history)) {
                val spans = listOf(5, 15, 60, 360)
                Choice(
                    Icons.Outlined.Timeline, stringResource(R.string.timeline_span),
                    listOf("5 min", "15 min", "1 h", "6 h"),
                    spans.indexOf(s.timelineMinutes).coerceAtLeast(0),
                ) { s.timelineMinutes = spans[it] }
                Divider()
                val pre = listOf(0, 3, 5, 10)
                Choice(
                    Icons.Outlined.SkipPrevious, stringResource(R.string.event_preroll),
                    listOf("0 s", "3 s", "5 s", "10 s"),
                    pre.indexOf(s.eventPreroll).coerceAtLeast(0),
                ) { s.eventPreroll = pre[it] }
                Divider()
                Toggle(Icons.Outlined.Vibration, stringResource(R.string.haptic_ticks), stringResource(R.string.haptic_ticks_subtitle), s.timelineHaptics) {
                    s.timelineHaptics = it
                }
            }

            SettingsBackupSection()

            // Only there after FrigateCtrl stopped because of an error, or after a live view: reports stay on this phone.
            if (crash != null || liveReport != null) {
                Section(
                    stringResource(R.string.group_troubleshooting),
                    footer = stringResource(if (crash != null) R.string.crash_footer else R.string.live_report_footer),
                ) {
                    if (crash != null) {
                        GroupRow(
                            stringResource(R.string.crash_report),
                            crashTime,
                            icon = Icons.Outlined.BugReport,
                            showChevron = true,
                            onClick = { showCrash = true },
                        )
                    }
                    if (liveReport != null) {
                        GroupRow(
                            stringResource(R.string.live_report),
                            liveReportTime,
                            icon = Icons.Outlined.BugReport,
                            showChevron = true,
                            onClick = { showLiveReport = true },
                        )
                    }
                }
            }
        }
    }

    val report = crash
    if (showCrash && report != null) {
        CrashReportDialog(
            report,
            onCopy = { copySensitive(ctx, report); Toast.makeText(ctx, R.string.crash_copied, Toast.LENGTH_SHORT).show() },
            onDelete = { CrashReport.delete(ctx); crash = null; showCrash = false },
            onDismiss = { showCrash = false },
        )
    }
    val live = liveReport
    if (showLiveReport && live != null) {
        CrashReportDialog(
            live,
            onCopy = { copySensitive(ctx, live, R.string.live_report); Toast.makeText(ctx, R.string.live_report_copied, Toast.LENGTH_SHORT).show() },
            onDelete = { LiveReport.delete(ctx); liveReport = null; showLiveReport = false },
            onDismiss = { showLiveReport = false },
            title = R.string.live_report,
            intro = R.string.live_report_intro,
        )
    }
}

/** The last crash report in full, to copy into a bug report, or delete. */
@Composable
private fun CrashReportDialog(
    report: String,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    title: Int = R.string.crash_report,
    intro: Int = R.string.crash_intro,
) {
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.BugReport, null) },
        title = { Text(stringResource(title)) },
        text = {
            Column {
                Text(stringResource(intro), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                SelectionContainer(
                    Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                ) {
                    Text(report, style = CtrlType.Mono, fontSize = 10.sp, lineHeight = 13.sp, color = CtrlColors.Text, softWrap = false)
                }
            }
        },
        confirmButton = { TextButton(onCopy) { Text(stringResource(R.string.crash_copy), color = CtrlColors.AccentText) } },
        dismissButton = {
            Row {
                TextButton(onDelete) { Text(stringResource(R.string.crash_delete), color = CtrlColors.Error) }
                TextButton(onDismiss) { Text(stringResource(R.string.close), color = CtrlColors.Text) }
            }
        },
    )
}

/** The settings as they are now, by the keys of [com.vknxworks.frigatectrl.data.SettingsBackup]. */
private fun currentSettings(): Map<String, Any> {
    val s = App.settings
    val n = App.notify
    val b = com.vknxworks.frigatectrl.data.SettingsBackup
    return mapOf(
        b.GRID_COLUMNS to s.gridColumns.coerceIn(0, 3),
        b.THUMB_REFRESH to s.thumbRefreshSec,
        b.SHOW_DISABLED to s.showDisabled,
        b.PREFER_SUB to s.preferSubStream,
        b.START_MUTED to s.startMuted,
        b.KEEP_AWAKE to s.keepScreenOn,
        b.AUTO_PIP to s.autoPip,
        b.TIMELINE_MINUTES to s.timelineMinutes,
        b.TIMELINE_HAPTICS to s.timelineHaptics,
        b.EVENT_PREROLL to s.eventPreroll,
        b.NOTIFY_DETECTIONS to n.includeDetections,
        b.NOTIFY_INTERVAL to n.intervalMinutes,
        b.QUIET_HOURS to n.quietHours,
        b.QUIET_FROM to n.quietFrom,
        b.QUIET_TO to n.quietTo,
        b.MUTED_CAMERAS to n.mutedCameras,
        b.MUTED_LABELS to n.mutedLabels,
        b.HIDE_SCREENSHOTS to App.lock.hideScreenshots,
        b.HIDE_RECENTS to App.lock.hideRecents,
    )
}

/** Applies the [keys] of [incoming] (exactly the changes the confirmation listed). */
@Suppress("UNCHECKED_CAST")
private fun applySettings(ctx: android.content.Context, incoming: Map<String, Any>, keys: List<String>) {
    val s = App.settings
    val n = App.notify
    val b = com.vknxworks.frigatectrl.data.SettingsBackup
    for (key in keys) {
        val v = incoming[key] ?: continue
        when (key) {
            b.GRID_COLUMNS -> s.gridColumns = v as Int
            b.THUMB_REFRESH -> s.thumbRefreshSec = v as Int
            b.SHOW_DISABLED -> s.showDisabled = v as Boolean
            b.PREFER_SUB -> s.preferSubStream = v as Boolean
            b.START_MUTED -> s.startMuted = v as Boolean
            b.KEEP_AWAKE -> s.keepScreenOn = v as Boolean
            b.AUTO_PIP -> s.autoPip = v as Boolean
            b.TIMELINE_MINUTES -> s.timelineMinutes = v as Int
            b.TIMELINE_HAPTICS -> s.timelineHaptics = v as Boolean
            b.EVENT_PREROLL -> s.eventPreroll = v as Int
            b.NOTIFY_DETECTIONS -> n.includeDetections = v as Boolean
            b.NOTIFY_INTERVAL -> n.intervalMinutes = v as Int
            b.QUIET_HOURS -> n.quietHours = v as Boolean
            b.QUIET_FROM -> n.quietFrom = v as Int
            b.QUIET_TO -> n.quietTo = v as Int
            b.MUTED_CAMERAS -> n.mutedCameras = v as Set<String>
            b.MUTED_LABELS -> n.mutedLabels = v as Set<String>
            // Only ever switched on by a file.
            b.HIDE_SCREENSHOTS -> App.lock.hideScreenshots = true
            b.HIDE_RECENTS -> App.lock.hideRecents = true
        }
    }
    // The background check follows the interval that may have changed.
    if (b.NOTIFY_INTERVAL in keys) runCatching { com.vknxworks.frigatectrl.notify.AlertWorker.schedule(ctx.applicationContext) }
}

private fun settingLabel(key: String): Int {
    val b = com.vknxworks.frigatectrl.data.SettingsBackup
    return when (key) {
        b.GRID_COLUMNS -> R.string.grid_columns
        b.THUMB_REFRESH -> R.string.thumb_refresh
        b.SHOW_DISABLED -> R.string.show_disabled
        b.PREFER_SUB -> R.string.prefer_sub
        b.START_MUTED -> R.string.start_muted
        b.KEEP_AWAKE -> R.string.keep_screen_on
        b.AUTO_PIP -> R.string.pip_setting
        b.TIMELINE_MINUTES -> R.string.timeline_span
        b.TIMELINE_HAPTICS -> R.string.haptic_ticks
        b.EVENT_PREROLL -> R.string.event_preroll
        b.NOTIFY_DETECTIONS -> R.string.notif_what
        b.NOTIFY_INTERVAL -> R.string.notif_interval
        b.QUIET_HOURS -> R.string.quiet_hours
        b.QUIET_FROM -> R.string.quiet_from
        b.QUIET_TO -> R.string.quiet_to
        b.MUTED_CAMERAS -> R.string.backup_muted_cameras
        b.MUTED_LABELS -> R.string.backup_muted_labels
        b.HIDE_SCREENSHOTS -> R.string.hide_screenshots
        else -> R.string.hide_recents
    }
}

private fun settingValue(ctx: android.content.Context, v: Any?): String = when (v) {
    is Boolean -> ctx.getString(if (v) R.string.backup_on else R.string.backup_off)
    is Set<*> -> if (v.isEmpty()) ctx.getString(R.string.backup_none) else v.filterIsInstance<String>().sorted().joinToString(", ")
    null -> ctx.getString(R.string.backup_none)
    else -> v.toString()
}

/**
 * "Back up and restore": saves FrigateCtrl's own settings to a file and reads them back. The file has no
 * server address, login or PIN in it; reading one lists what would change and asks first.
 */
@Composable
private fun SettingsBackupSection() {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<Map<String, Any>?>(null) }
    fun toast(id: Int) = android.widget.Toast.makeText(ctx, id, android.widget.Toast.LENGTH_LONG).show()

    val exporter = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            val text = com.vknxworks.frigatectrl.data.SettingsBackup.toJson(currentSettings())
            ctx.contentResolver.openOutputStream(uri, "w")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("no stream")
        }.isSuccess
        toast(if (ok) R.string.backup_exported else R.string.backup_export_failed)
    }
    val importer = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val max = com.vknxworks.frigatectrl.data.SettingsBackup.MAX_BYTES
        val text = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                // A settings file is tiny; stop reading anything that isn't.
                val bytes = input.readNBytes(max + 1)
                if (bytes.size > max) null else String(bytes, Charsets.UTF_8)
            }
        }.getOrNull()
        val parsed = text?.let { com.vknxworks.frigatectrl.data.SettingsBackup.parse(it) }
        when {
            parsed == null -> toast(R.string.backup_not_a_file)
            com.vknxworks.frigatectrl.data.SettingsBackup.changes(currentSettings(), parsed).isEmpty() -> toast(R.string.backup_nothing_to_change)
            else -> pending = parsed
        }
    }

    Section(stringResource(R.string.backup_group), footer = stringResource(R.string.backup_footer)) {
        GroupRow(
            stringResource(R.string.backup_export),
            stringResource(R.string.backup_export_subtitle),
            icon = Icons.Outlined.FileUpload,
            showChevron = true,
            onClick = {
                // Android's "save as" screen opens: that doesn't count as leaving the app.
                com.vknxworks.frigatectrl.ui.lock.LockTrips.expectReturn()
                exporter.launch("frigatectrl-settings.json")
            },
        )
        GroupRow(
            stringResource(R.string.backup_import),
            stringResource(R.string.backup_import_subtitle),
            icon = Icons.Outlined.FileDownload,
            showChevron = true,
            onClick = {
                com.vknxworks.frigatectrl.ui.lock.LockTrips.expectReturn()
                importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            },
        )
    }

    pending?.let { incoming ->
        val now = currentSettings()
        val keys = com.vknxworks.frigatectrl.data.SettingsBackup.changes(now, incoming)
        com.vknxworks.frigatectrl.GuardedWindow()
        com.vknxworks.frigatectrl.ui.components.CtrlAlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = {
                androidx.compose.foundation.layout.Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                ) {
                    Text(stringResource(R.string.backup_confirm_body))
                    keys.forEach { key ->
                        Text(
                            stringResource(R.string.backup_change_line, stringResource(settingLabel(key)), settingValue(ctx, now[key]), settingValue(ctx, incoming[key])),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton({
                    applySettings(ctx, incoming, keys)
                    pending = null
                    toast(R.string.backup_imported)
                }) { Text(stringResource(R.string.backup_replace), color = CtrlColors.AccentText) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton({ pending = null }) { Text(stringResource(R.string.cancel), color = CtrlColors.Text) }
            },
        )
    }
}

/** Copied as sensitive, so Android doesn't preview it. */
private fun copySensitive(ctx: android.content.Context, text: String, label: Int = R.string.crash_report) {
    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java) ?: return
    val clip = android.content.ClipData.newPlainText(ctx.getString(label), text)
    clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    runCatching { cm.setPrimaryClip(clip) }
}

// Settings rows look the same as in every Ctrl app (see ui/settings/SettingsKit.kt).

@Composable
private fun Section(title: String, footer: String? = null, content: @Composable () -> Unit) {
    Group(title, footer = footer, gutter = 0.dp) { content() }
}

/** Rows in a group are set apart by their spacing, as in the other Ctrl apps. */
@Composable
private fun Divider() = Unit

@Composable
private fun Toggle(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) = GroupSwitch(title, subtitle, checked, icon, enabled, onChange)

@Composable
private fun Choice(
    icon: ImageVector,
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) = GroupChoice(title, options.mapIndexed { i, label -> i to label }, selected, icon) { onSelect(it) }
