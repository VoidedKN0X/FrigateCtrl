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

/** What the user is asked to trust: the server's certificate as fetched (nothing was sent to it). */
data class CertInfo(val host: String, val subject: String, val fingerprint: String)

/** Fetches the certificate [baseUrl] presents, without trusting it or sending anything to it. */
suspend fun fetchCertInfo(baseUrl: String): CertInfo = withContext(Dispatchers.IO) {
    val u = baseUrl.toHttpUrlOrNull() ?: throw Exception("That isn't a valid address")
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
        title = { Text(if (changed) "Certificate changed" else "Trust this certificate?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                intro?.let { Text(it) }
                if (changed) Text(
                    "${cert.host} now presents a different certificate from the one you trusted. " +
                        "Only continue if you replaced it yourself.",
                    color = cs.error,
                )
                Text("Check that this SHA-256 fingerprint matches the certificate on ${cert.host}:")
                Text(
                    cert.fingerprint,
                    fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(cs.surfaceContainerHighest).padding(10.dp),
                )
                if (cert.subject.isNotBlank()) Text(
                    "Issued to: ${cert.subject}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
                Text(
                    "From now on FrigateCtrl only accepts this exact certificate from this server.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onTrust) { Text("Trust") } },
        dismissButton = { TextButton(onCancel) { Text("Cancel") } },
    )
}

/** Explicit confirmation before using a plain-http (unencrypted) server. */
@Composable
fun HttpWarningDialog(host: String, intro: String? = null, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Outlined.NoEncryption, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Connect without encryption?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                intro?.let { Text(it) }
                Text(
                    "$host is reached over plain http. Your password, login token and camera video are sent " +
                        "unencrypted and can be read by anyone on the same network. Only continue on a network you trust."
                )
            }
        },
        confirmButton = { TextButton(onConfirm) { Text("Connect anyway") } },
        dismissButton = { TextButton(onCancel) { Text("Cancel") } },
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
            "Not encrypted (HTTP)", color = cs.onErrorContainer,
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
                intro = "FrigateCtrl now asks before using a server without encryption.",
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
                intro = "\"Allow self-signed certificate\" used to accept any certificate. FrigateCtrl now " +
                    "pins your server's certificate instead, so confirm it once.",
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
                title = { Text("Couldn't check the certificate") },
                text = { Text("FrigateCtrl needs to confirm your server's certificate once, but couldn't reach $host: $err") },
                confirmButton = { TextButton({ attempt++ }) { Text("Retry") } },
                dismissButton = { TextButton(onServerSettings) { Text("Server settings") } },
            )
            else -> CircularProgressIndicator()
        }
    }
}
