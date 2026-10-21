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
import com.vknxworks.frigatectrl.data.lock.LockVault
import com.vknxworks.frigatectrl.ui.lock.showBiometricPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vknxworks.frigatectrl.App
import com.vknxworks.frigatectrl.R
import com.vknxworks.frigatectrl.data.ThemeMode

/** Settings, opened from the You page (tap your profile picture). Server, log out and About are on the You page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val s = App.settings
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lock = App.lock
    val bioAvailable = remember { biometricAvailable(ctx) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                scrollBehavior = scroll,
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 32.dp),
        ) {
            Section(stringResource(R.string.group_appearance)) {
                Choice(
                    Icons.Outlined.DarkMode, stringResource(R.string.theme),
                    THEMES.map { stringResource(it.label) },
                    THEMES.indexOf(s.theme).coerceAtLeast(0),
                ) { s.theme = THEMES[it] }
                Toggle(
                    Icons.Outlined.Palette, stringResource(R.string.dynamic_colour), stringResource(R.string.dynamic_colour_subtitle), s.dynamicColor,
                ) { s.dynamicColor = it }
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
                            else showBiometricPrompt(act, ctx.getString(R.string.lock_fingerprint_enable_title), cipher, subtitle = ctx.getString(R.string.lock_fingerprint_enable_subtitle)) {
                                if (!vault.finishBiometricEnrol(it)) Toast.makeText(ctx, R.string.lock_fingerprint_setup_failed, Toast.LENGTH_SHORT).show()
                            }
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
                Toggle(
                    Icons.Outlined.PictureInPictureAlt, "Picture-in-picture",
                    "Keep the live view floating when you leave the app", s.autoPip,
                ) { s.autoPip = it }
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
        }
    }
}

/** In the same order as every Ctrl app. */
private val THEMES = listOf(ThemeMode.System, ThemeMode.Dark, ThemeMode.Light, ThemeMode.Black)

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
