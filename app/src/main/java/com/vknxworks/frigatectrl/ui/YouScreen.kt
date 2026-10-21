package com.vknxworks.frigatectrl.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vknxworks.frigatectrl.App
import com.vknxworks.frigatectrl.BuildConfig
import com.vknxworks.frigatectrl.R
import com.vknxworks.frigatectrl.ui.settings.Group
import com.vknxworks.frigatectrl.ui.settings.GroupRow

private const val SPONSOR_URL = "https://github.com/sponsors/VoidedKN0X"
private const val REPO_URL = "https://github.com/VoidedKN0X/FrigateCtrl"
private const val ISSUES_URL = "$REPO_URL/issues"
private const val LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/** The Frigate login's first letter, or null without a login (Frigate has no profile pictures of its own). */
private fun loginInitial(): String? = App.prefs.username.trim().take(1).uppercase().ifEmpty { null }

/**
 * The round profile picture at the top right of the Cameras and Health tabs. Tapping it opens the You
 * page, the only way into Settings (like every Ctrl app). Shows the first letter of your Frigate login,
 * or the series' placeholder avatar when Frigate doesn't ask for one.
 */
@Composable
fun ProfileButton(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val label = stringResource(R.string.account_and_settings)
    val initial = loginInitial()
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            if (initial == null) {
                Icon(Icons.Filled.AccountCircle, null, tint = cs.primary)
            } else {
                Text(initial, style = MaterialTheme.typography.titleMedium, color = cs.primary)
            }
        }
    }
}

/**
 * Big left-aligned title for the main tabs with the profile picture at the end, as in every Ctrl app.
 * [subtitle] is the short status line under the title; [inset] is horizontal padding the parent already adds.
 */
@Composable
fun TabTitle(
    title: String,
    onYou: () -> Unit,
    modifier: Modifier = Modifier,
    inset: Dp = 0.dp,
    subtitle: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp - inset, end = (8.dp - inset).coerceAtLeast(0.dp), top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            ProfileButton(onYou)
        }
        if (subtitle != null) {
            Column(Modifier.padding(start = 20.dp - inset, end = (20.dp - inset).coerceAtLeast(0.dp), bottom = 8.dp), content = subtitle)
        }
    }
}

/**
 * The You page, opened from the profile picture: who you're signed in as on which Frigate server, the
 * server and login, the way into Settings, logging out, and the About section every Ctrl app ends with.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouScreen(onBack: () -> Unit, onSettings: () -> Unit, onServer: () -> Unit, onLogout: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var confirmLogout by remember { mutableStateOf(false) }
    var frigateVersion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { frigateVersion = runCatching { App.api.version() }.getOrNull() }
    val prefs = App.prefs
    val host = prefs.baseUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
    val username = prefs.username.trim().ifEmpty { null }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.you_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                scrollBehavior = scroll,
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            // Account card: the series' big profile picture, who you are and where.
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(96.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    val initial = loginInitial()
                    if (initial == null) {
                        Icon(Icons.Filled.AccountCircle, null, Modifier.size(56.dp), tint = cs.primary)
                    } else {
                        Text(initial, style = MaterialTheme.typography.displaySmall, color = cs.primary)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    username ?: host.ifBlank { stringResource(R.string.you_not_set) },
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    listOfNotNull(
                        if (username != null) host.ifBlank { null } else stringResource(R.string.you_no_login),
                        frigateVersion?.let { stringResource(R.string.you_frigate_version, it) },
                    ).joinToString(stringResource(R.string.list_separator_wide)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Group(stringResource(R.string.you_server_group), gutter = 16.dp) {
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

            Group(gutter = 16.dp) {
                GroupRow(
                    stringResource(R.string.settings),
                    stringResource(R.string.settings_subtitle),
                    icon = Icons.Filled.Settings,
                    showChevron = true,
                    onClick = onSettings,
                )
                GroupRow(
                    stringResource(R.string.log_out),
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    iconTint = cs.error,
                    titleColor = cs.error,
                    onClick = { confirmLogout = true },
                )
            }

            Group(stringResource(R.string.group_about), gutter = 16.dp) {
                GroupRow(stringResource(R.string.support_title), stringResource(R.string.support_subtitle), Icons.Filled.Favorite, showChevron = true, onClick = { openLink(ctx, SPONSOR_URL) })
                GroupRow(stringResource(R.string.bugs_title), stringResource(R.string.bugs_subtitle), Icons.Filled.BugReport, showChevron = true, onClick = { openLink(ctx, ISSUES_URL) })
                GroupRow(stringResource(R.string.source_title), stringResource(R.string.source_subtitle), Icons.Filled.Code, showChevron = true, onClick = { openLink(ctx, REPO_URL) })
                GroupRow(stringResource(R.string.licence_title), stringResource(R.string.licence_subtitle), Icons.Filled.Gavel, showChevron = true, onClick = { openLink(ctx, LICENCE_URL) })
            }
            Text(
                stringResource(R.string.about_footer, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            icon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
            title = { Text(stringResource(R.string.log_out_title)) },
            text = { Text(stringResource(R.string.log_out_body)) },
            confirmButton = { TextButton({ confirmLogout = false; onLogout() }) { Text(stringResource(R.string.log_out)) } },
            dismissButton = { TextButton({ confirmLogout = false }) { Text(stringResource(R.string.cancel)) } },
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
