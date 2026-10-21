package com.vknxworks.frigatectrl.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Dns
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.vknxworks.frigatectrl.data.lock.LockVault
import com.vknxworks.frigatectrl.ui.lock.showBiometricPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vknxworks.frigatectrl.App
import com.vknxworks.frigatectrl.BuildConfig
import com.vknxworks.frigatectrl.R
import com.vknxworks.frigatectrl.data.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onServer: () -> Unit, onLogout: () -> Unit) {
    val s = App.settings
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var confirmLogout by remember { mutableStateOf(false) }
    val lock = App.lock
    val bioAvailable = remember { biometricAvailable(ctx) }
    var frigateVersion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { frigateVersion = App.api.version() }

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
                    Icons.Outlined.DarkMode, "Theme",
                    THEMES.map { it.label },
                    THEMES.indexOf(s.theme).coerceAtLeast(0),
                ) { s.theme = THEMES[it] }
                Toggle(
                    Icons.Outlined.Palette, "Use wallpaper colours", "Replaces the default colours with ones from your wallpaper", s.dynamicColor,
                ) { s.dynamicColor = it }
            }

            Section(
                "Privacy and security",
                footer = "FrigateCtrl has no analytics or tracking. It only talks to your own Frigate server.",
            ) {
                val vault = App.vault
                Toggle(
                    Icons.Outlined.Lock, "App lock",
                    "Ask for an app PIN (and optionally your fingerprint) when opening FrigateCtrl. While locked, your login " +
                        "stays encrypted with the PIN, and no camera is shown or streamed.",
                    vault.enabled,
                ) { on -> PinRequests.request = if (on) PinRequest.ENABLE else PinRequest.DISABLE }
                if (vault.enabled) {
                    GroupRow(
                        "Change app PIN",
                        "At least $PIN_MIN digits, separate from your phone's PIN",
                        icon = Icons.Outlined.Password,
                        showChevron = true,
                        onClick = { PinRequests.request = PinRequest.CHANGE },
                    )
                    Toggle(
                        Icons.Outlined.Fingerprint, "Unlock with fingerprint",
                        if (bioAvailable || vault.biometricEnabled) "Only strong biometrics such as your fingerprint, checked by the phone's " +
                            "secure hardware. The PIN is still needed after a restart and every 72 hours."
                        else "Set up a fingerprint on this phone first",
                        vault.biometricEnabled, enabled = bioAvailable || vault.biometricEnabled,
                    ) { on ->
                        val act = ctx.lockActivity() as? FragmentActivity
                        if (on && act != null) {
                            // A fresh key each time, bound to the fingerprints enrolled now.
                            val cipher = vault.biometricEnrolCipher()
                            if (cipher == null) Toast.makeText(ctx, "Couldn't set up fingerprint unlock", Toast.LENGTH_SHORT).show()
                            else showBiometricPrompt(act, "Turn on fingerprint unlock", cipher, subtitle = "Confirm your fingerprint") {
                                if (!vault.finishBiometricEnrol(it)) Toast.makeText(ctx, "Couldn't set up fingerprint unlock", Toast.LENGTH_SHORT).show()
                            }
                        } else if (!on) {
                            vault.disableBiometric()
                        }
                    }
                    // Seconds; -1 = only when the app restarts.
                    val afters = listOf(0, 60, 300, 900, 3600, -1)
                    Choice(
                        Icons.Outlined.Timer, "Lock again",
                        listOf(
                            "As soon as you leave the app", "After 1 minute away", "After 5 minutes away",
                            "After 15 minutes away", "After 1 hour away", "Only when the app restarts",
                        ),
                        afters.indexOf(lock.lockAfterSeconds).coerceAtLeast(0),
                    ) { lock.lockAfterSeconds = afters[it] }
                }
                Toggle(
                    Icons.Outlined.VisibilityOff, "Hide in screenshots and recent apps",
                    "Screenshots, screen recordings and the recent apps view show a blank screen. Sign-in and the lock screen are always hidden", lock.hideContent,
                ) { lock.hideContent = it }
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

            Section("Server") {
                GroupRow(
                    "Server & login",
                    App.prefs.baseUrl.ifBlank { "Not set" } + (frigateVersion?.let { " · Frigate $it" } ?: "") +
                        when {
                            App.prefs.isHttp -> "\nNot encrypted (HTTP)"
                            App.prefs.allowInsecureTls && App.prefs.certPin.isNotBlank() ->
                                "\nPinned certificate SHA-256 ${App.prefs.certPin.take(23)}…"
                            else -> ""
                        },
                    icon = Icons.Outlined.Dns,
                    showChevron = true,
                    onClick = onServer,
                )
                GroupRow(
                    "Log out",
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    iconTint = cs.error,
                    titleColor = cs.error,
                    onClick = { confirmLogout = true },
                )
            }

            Section(stringResource(R.string.group_about)) {
                AboutRow(Icons.Filled.Favorite, R.string.support_title, R.string.support_subtitle) { openLink(ctx, SPONSOR_URL) }
                Divider()
                AboutRow(Icons.Filled.BugReport, R.string.bugs_title, R.string.bugs_subtitle) { openLink(ctx, ISSUES_URL) }
                Divider()
                AboutRow(Icons.Filled.Code, R.string.source_title, R.string.source_subtitle) { openLink(ctx, REPO_URL) }
                Divider()
                AboutRow(Icons.Filled.Gavel, R.string.licence_title, R.string.licence_subtitle) { openLink(ctx, LICENCE_URL) }
            }
            Text(
                stringResource(R.string.about_footer, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }


    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            icon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
            title = { Text("Log out?") },
            text = { Text("The server address and saved login will be removed from this phone, and the app lock is turned off.") },
            confirmButton = { TextButton({ confirmLogout = false; onLogout() }) { Text("Log out") } },
            dismissButton = { TextButton({ confirmLogout = false }) { Text("Cancel") } },
        )
    }
}

private const val SPONSOR_URL = "https://github.com/sponsors/VoidedKN0X"
private const val REPO_URL = "https://github.com/VoidedKN0X/FrigateCtrl"
private const val ISSUES_URL = "$REPO_URL/issues"
private const val LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

private fun openLink(ctx: Context, url: String) {
    try {
        com.vknxworks.frigatectrl.ui.lock.LockTrips.expectReturn()
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}

/** In the same order as every Ctrl app. */
private val THEMES = listOf(ThemeMode.System, ThemeMode.Dark, ThemeMode.Light, ThemeMode.Black)

@Composable
private fun AboutRow(icon: ImageVector, title: Int, subtitle: Int, onClick: () -> Unit) =
    GroupRow(stringResource(title), stringResource(subtitle), icon, showChevron = true, onClick = onClick)

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
