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
import androidx.compose.material.icons.outlined.Notifications
import com.vknxworks.frigatectrl.data.lock.LockVault
import com.vknxworks.frigatectrl.ui.lock.showBiometricPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vknxworks.frigatectrl.App
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
        }
    }
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
