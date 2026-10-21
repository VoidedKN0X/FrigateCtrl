package com.vknxworks.frigatectrl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.NoEncryption
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vknxworks.frigatectrl.App
import com.vknxworks.frigatectrl.data.Tls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import androidx.compose.ui.res.stringResource
import com.vknxworks.frigatectrl.R

/** What the user is asked to trust: the server's certificate as fetched (nothing was sent to it). */
data class CertInfo(val host: String, val subject: String, val fingerprint: String)

/** Fetches the certificate [baseUrl] presents, without trusting it or sending anything to it. */
suspend fun fetchCertInfo(baseUrl: String): CertInfo = withContext(Dispatchers.IO) {
    val u = baseUrl.toHttpUrlOrNull() ?: throw Exception(App.str(R.string.error_invalid_address))
    val leaf = Tls.fetchLeaf(u.host, u.port)
    CertInfo(u.host, leaf.subjectX500Principal.name, Tls.fingerprint(leaf))
}

/** Asks the user to confirm a self-signed certificate by its SHA-256 fingerprint (trust on first use). */
@Composable
fun CertTrustDialog(cert: CertInfo, changed: Boolean, intro: String? = null, onTrust: () -> Unit, onCancel: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Outlined.VerifiedUser, null) },
        title = { Text(stringResource(if (changed) R.string.cert_changed_title else R.string.cert_trust_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                intro?.let { Text(it) }
                if (changed) Text(
                    stringResource(R.string.cert_changed_body, cert.host),
                    color = cs.error,
                )
                Text(stringResource(R.string.cert_check_fingerprint, cert.host))
                Text(
                    cert.fingerprint,
                    fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(cs.surfaceContainerHighest).padding(10.dp),
                )
                if (cert.subject.isNotBlank()) Text(
                    stringResource(R.string.cert_issued_to, cert.subject), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.cert_pinned_note),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onTrust) { Text(stringResource(R.string.cert_trust)) } },
        dismissButton = { TextButton(onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Explicit confirmation before using a plain-http (unencrypted) server. */
@Composable
fun HttpWarningDialog(host: String, intro: String? = null, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Outlined.NoEncryption, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.http_warning_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                intro?.let { Text(it) }
                Text(stringResource(R.string.http_warning_body, host))
            }
        },
        confirmButton = { TextButton(onConfirm) { Text(stringResource(R.string.http_warning_confirm)) } },
        dismissButton = { TextButton(onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Small red "Not encrypted (HTTP)" label shown next to an http server. */
@Composable
fun NotEncryptedBadge(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).background(cs.errorContainer).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.LockOpen, null, tint = cs.onErrorContainer, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.server_not_encrypted), color = cs.onErrorContainer,
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium,
        )
    }
}

/** Whether the saved server needs a one-time security confirmation (after updating from an older version). */
fun needsConnectionCheck(): Boolean {
    val p = App.prefs
    return p.isConfigured && (p.needsCertPin || (p.isHttp && !p.httpConfirmed))
}

/**
 * One-time check for installs from before certificate pinning: an http server must be confirmed,
 * and a "self-signed" server (which used to trust any certificate) must have its certificate pinned.
 * Until then nothing is sent to the server with the old, insecure settings.
 */
@Composable
fun ConnectionSecurityCheck(onDone: () -> Unit, onServerSettings: () -> Unit) {
    val prefs = App.prefs
    val cs = MaterialTheme.colorScheme
    val host = remember { prefs.baseUrl.toHttpUrlOrNull()?.host ?: prefs.baseUrl }

    Box(Modifier.fillMaxSize().background(cs.background), contentAlignment = Alignment.Center) {
        if (prefs.isHttp) {
            HttpWarningDialog(
                host = host,
                intro = stringResource(R.string.http_check_intro),
                onConfirm = {
                    prefs.httpConfirmed = true
                    App.api.reload()
                    onDone()
                },
                onCancel = onServerSettings,
            )
        } else {
            CertPinCheck(host, onDone, onServerSettings)
        }
    }
}

@Composable
private fun CertPinCheck(host: String, onDone: () -> Unit, onServerSettings: () -> Unit) {
    val prefs = App.prefs
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        var attempt by remember { mutableIntStateOf(0) }
        var cert by remember { mutableStateOf<CertInfo?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(attempt) {
            error = null
            cert = null
            try { cert = fetchCertInfo(prefs.baseUrl) } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
        }
        val c = cert
        val err = error
        when {
            c != null -> CertTrustDialog(
                cert = c, changed = false,
                intro = stringResource(R.string.cert_check_intro),
                onTrust = {
                    prefs.certPin = c.fingerprint
                    App.api.reload()
                    onDone()
                },
                onCancel = onServerSettings,
            )
            err != null -> AlertDialog(
                onDismissRequest = onServerSettings,
                icon = { Icon(Icons.Outlined.VerifiedUser, null) },
                title = { Text(stringResource(R.string.cert_check_failed_title)) },
                text = { Text(stringResource(R.string.cert_check_failed_body, host, err)) },
                confirmButton = { TextButton({ attempt++ }) { Text(stringResource(R.string.retry)) } },
                dismissButton = { TextButton(onServerSettings) { Text(stringResource(R.string.server_settings)) } },
            )
            else -> CircularProgressIndicator()
        }
    }
}
