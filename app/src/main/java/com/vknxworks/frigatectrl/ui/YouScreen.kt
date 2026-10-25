package com.vknxworks.frigatectrl.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vknxworks.frigatectrl.App
import com.vknxworks.frigatectrl.BuildConfig
import com.vknxworks.frigatectrl.R
import com.vknxworks.frigatectrl.ui.components.CtrlAlertDialog
import com.vknxworks.frigatectrl.ui.components.CtrlAvatar
import com.vknxworks.frigatectrl.ui.components.CtrlButton
import com.vknxworks.frigatectrl.ui.components.CtrlSectionLabel
import com.vknxworks.frigatectrl.ui.components.CtrlSquareButton
import com.vknxworks.frigatectrl.ui.components.CtrlWordmark
import com.vknxworks.frigatectrl.ui.settings.Group
import com.vknxworks.frigatectrl.ui.settings.GroupRow
import com.vknxworks.frigatectrl.ui.theme.CtrlColors
import com.vknxworks.frigatectrl.ui.theme.CtrlType

private const val SPONSOR_URL = "https://github.com/sponsors/VoidedKN0X"
private const val REPO_URL = "https://github.com/VoidedKN0X/FrigateCtrl"
private const val ISSUES_URL = "$REPO_URL/issues"
private const val LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/** The Frigate login's first letter, or null without a login (Frigate has no profile pictures of its own). */
private fun loginInitial(): String? = App.prefs.username.trim().take(1).uppercase().ifEmpty { null }

/**
 * The profile picture at the top right of the Cameras and Health tabs. Tapping it opens the You
 * page, the only way into Settings (like every Ctrl app). Shows the first letter of your Frigate login,
 * or a person icon when Frigate doesn't ask for one, with the accent badge while a server is set up.
 */
@Composable
fun ProfileButton(onClick: () -> Unit) {
    CtrlAvatar(
        initial = loginInitial(),
        contentDescription = stringResource(R.string.account_and_settings),
        onClick = onClick,
        connected = App.prefs.isConfigured,
    )
}

/**
 * Header of the main tabs: the FRIGATE/CTRL wordmark and the profile picture, then the big tab title
 * with a mono label above it. [subtitle] is the short status line under the title; [inset] is
 * horizontal padding the parent already adds.
 */
@Composable
fun TabTitle(
    title: String,
    onYou: () -> Unit,
    modifier: Modifier = Modifier,
    inset: Dp = 0.dp,
    label: String? = null,
    subtitle: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val side = (20.dp - inset).coerceAtLeast(0.dp)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = side, end = side, top = 10.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CtrlWordmark("Frigate", Modifier.weight(1f))
            ProfileButton(onYou)
        }
        Column(Modifier.padding(start = side, end = side, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (label != null) CtrlSectionLabel(label)
            Text(title, style = MaterialTheme.typography.headlineLarge, color = CtrlColors.Text)
            subtitle?.invoke(this)
        }
    }
}

/**
 * The You page, opened from the profile picture: who you're signed in as on which Frigate server, the
 * server and login, the way into Settings, logging out, and the About section every Ctrl app ends with.
 */
@Composable
fun YouScreen(onBack: () -> Unit, onSettings: () -> Unit, onServer: () -> Unit, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    var confirmLogout by remember { mutableStateOf(false) }
    var frigateVersion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { frigateVersion = runCatching { App.api.version() }.getOrNull() }
    val prefs = App.prefs
    val host = prefs.baseUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
    val username = prefs.username.trim().ifEmpty { null }

    Column(Modifier.fillMaxSize().background(CtrlColors.Background)) {
        // Close button, centred mono label, as in every Ctrl app's profile page.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CtrlSquareButton(Icons.Outlined.Close, stringResource(R.string.back), onBack)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CtrlSectionLabel(stringResource(R.string.you_title))
            }
            Spacer(Modifier.width(44.dp))
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp),
        ) {
            // Account: the accent profile picture, who you are and where.
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(68.dp).clip(RoundedCornerShape(22.dp)).background(CtrlColors.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    val initial = loginInitial()
                    if (initial == null) {
                        Icon(Icons.Outlined.Person, null, Modifier.size(32.dp), tint = CtrlColors.OnAccent)
                    } else {
                        Text(initial, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = CtrlColors.OnAccent)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        username ?: host.ifBlank { stringResource(R.string.you_not_set) },
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            if (username != null) host.ifBlank { null } else stringResource(R.string.you_no_login),
                            frigateVersion?.let { stringResource(R.string.you_frigate_version, it) },
                        ).joinToString(stringResource(R.string.list_separator)),
                        style = CtrlType.Mono,
                        color = CtrlColors.Muted,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Group(stringResource(R.string.you_server_group), gutter = 20.dp) {
                GroupRow(
                    stringResource(R.string.server_and_login),
                    prefs.baseUrl.ifBlank { stringResource(R.string.you_not_set) } +
                        when {
                            prefs.isHttp -> "\n" + stringResource(R.string.server_not_encrypted)
                            prefs.allowInsecureTls && prefs.certPin.isNotBlank() ->
                                "\n" + stringResource(R.string.server_pinned_cert, prefs.certPin.take(23))
                            else -> ""
                        },
                    icon = Icons.Outlined.Dns,
                    showChevron = true,
                    onClick = onServer,
                )
            }

            Group(stringResource(R.string.group_app), gutter = 20.dp) {
                GroupRow(
                    stringResource(R.string.settings),
                    stringResource(R.string.settings_subtitle),
                    icon = Icons.Outlined.Tune,
                    showChevron = true,
                    onClick = onSettings,
                )
                GroupRow(
                    stringResource(R.string.log_out),
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    iconTint = CtrlColors.Error,
                    titleColor = CtrlColors.Error,
                    onClick = { confirmLogout = true },
                )
            }

            Group(stringResource(R.string.group_about), gutter = 20.dp) {
                GroupRow(stringResource(R.string.bugs_title), stringResource(R.string.bugs_subtitle), Icons.Outlined.BugReport, showChevron = true, onClick = { openLink(ctx, ISSUES_URL) })
                GroupRow(stringResource(R.string.source_title), stringResource(R.string.source_subtitle), Icons.Outlined.Code, showChevron = true, onClick = { openLink(ctx, REPO_URL) })
                GroupRow(stringResource(R.string.licence_title), stringResource(R.string.licence_subtitle), Icons.Outlined.Gavel, showChevron = true, onClick = { openLink(ctx, LICENCE_URL) })
            }

            // Support, accent-filled at the bottom like every Ctrl app's profile page.
            CtrlButton(
                stringResource(R.string.support_title),
                onClick = { openLink(ctx, SPONSOR_URL) },
                icon = Icons.Outlined.FavoriteBorder,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp),
            )
            Text(
                stringResource(R.string.support_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = CtrlColors.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Text(
                stringResource(R.string.about_footer, BuildConfig.VERSION_NAME).uppercase(),
                style = CtrlType.Label, letterSpacing = 1.4.sp,
                color = CtrlColors.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Text(
                stringResource(R.string.series_footer),
                style = CtrlType.Label, letterSpacing = 1.4.sp,
                color = CtrlColors.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }

    if (confirmLogout) {
        CtrlAlertDialog(
            onDismissRequest = { confirmLogout = false },
            icon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
            title = { Text(stringResource(R.string.log_out_title)) },
            text = { Text(stringResource(R.string.log_out_body)) },
            confirmButton = { TextButton({ confirmLogout = false; onLogout() }) { Text(stringResource(R.string.log_out), color = CtrlColors.Error) } },
            dismissButton = { TextButton({ confirmLogout = false }) { Text(stringResource(R.string.cancel), color = CtrlColors.Text) } },
        )
    }
}

private fun openLink(ctx: Context, url: String) {
    try {
        // Opening the browser from here doesn't count as leaving FrigateCtrl for the app lock.
        com.vknxworks.frigatectrl.ui.lock.LockTrips.expectReturn()
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}
