package com.vknxworks.frigatectrl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vknxworks.frigatectrl.App
import com.vknxworks.frigatectrl.data.AuthException
import com.vknxworks.frigatectrl.data.Tls
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException

@Composable
fun SetupScreen(editing: Boolean, onDone: () -> Unit, onCancel: (() -> Unit)?) {
    // The password is typed here: never in screenshots, recordings or casting.
    com.vknxworks.frigatectrl.SecureWindow()
    val prefs = App.prefs
    val cs = MaterialTheme.colorScheme
    var url by remember { mutableStateOf(if (editing) prefs.baseUrl else "") }
    var user by remember { mutableStateOf(if (editing) prefs.username else "") }
    // The saved password is never put back into the field (where it could be revealed); leaving
    // it empty keeps it, but only for the same server and username.
    var pass by remember { mutableStateOf("") }
    val hasSavedPass = editing && prefs.password.isNotEmpty()
    var insecure by remember { mutableStateOf(prefs.allowInsecureTls) }
    var showPass by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Security prompts: a self-signed certificate to confirm, or an http address to confirm.
    var certPrompt by remember { mutableStateOf<Pair<String, CertInfo>?>(null) }
    var certChanged by remember { mutableStateOf(false) }
    var httpPrompt by remember { mutableStateOf<String?>(null) }

    /** The password to use for [base]: what was typed, or the saved one for the same server (scheme, host, port) and user. */
    fun passwordFor(base: String): String {
        if (pass.isNotEmpty() || !hasSavedPass) return pass
        val old = prefs.baseUrl.toHttpUrlOrNull() ?: return ""
        val new = base.toHttpUrlOrNull() ?: return ""
        // Same scheme, host and port: switching to http or to another port on the same machine
        // needs the password typed again.
        val same = old.scheme == new.scheme && old.host.equals(new.host, ignoreCase = true) &&
            old.port == new.port && user.trim() == prefs.username
        return if (same) prefs.password else ""
    }

    fun save(base: String, tok: String, pin: String, password: String) {
        prefs.baseUrl = base
        prefs.username = user.trim()
        prefs.password = password
        prefs.token = tok
        prefs.allowInsecureTls = insecure && pin.isNotBlank()
        prefs.certPin = if (insecure) pin else ""
        prefs.httpConfirmed = base.startsWith("http://", ignoreCase = true)
        App.api.reload()
    }

    /** Asks the user to confirm the certificate [base] presents (nothing is sent to it first). */
    fun promptCert(base: String, changed: Boolean, httpFallback: String? = null) {
        busy = true
        scope.launch {
            try {
                certChanged = changed
                certPrompt = base to fetchCertInfo(base)
            } catch (e: Exception) {
                if (httpFallback != null && e is IOException && e !is SSLException) httpPrompt = httpFallback
                else error = "Couldn't read the server's certificate: " + (e.message ?: e.javaClass.simpleName)
            }
            busy = false
        }
    }

    /**
     * Logs in to [base]. [pin] is the confirmed certificate fingerprint ("" = normal checks),
     * [httpOk] means the user confirmed plain http. When the address had no scheme, [httpFallback]
     * is offered (after an explicit warning) only if https couldn't connect at all — never after
     * a certificate error.
     */
    fun attempt(base: String, pin: String, httpOk: Boolean, httpFallback: String?) {
        error = null
        busy = true
        scope.launch {
            try {
                val password = passwordFor(base)
                val tok = App.api.login(base, user.trim(), password, pin, httpOk)
                save(base, tok, pin, password)
                busy = false
                onDone()
                return@launch
            } catch (e: Exception) {
                busy = false
                val certError = Tls.isCertificateError(e)
                when {
                    e is AuthException -> error = e.message
                    // The pinned certificate no longer matches: show the new one and let the user decide.
                    insecure && pin.isNotBlank() && certError -> promptCert(base, changed = true)
                    certError || e is SSLPeerUnverifiedException -> error = "Certificate not trusted. If Frigate uses its " +
                        "default self-signed certificate, turn on \"Allow self-signed certificate\"."
                    e is SSLException -> error = "Secure connection failed (" + (e.message ?: e.javaClass.simpleName) +
                        "). If your server only serves plain http (e.g. port 5000), type http:// in front of the address."
                    httpFallback != null && e is IOException -> httpPrompt = httpFallback
                    else -> error = e.message ?: e.javaClass.simpleName
                }
            }
        }
    }

    /** A pin the user already confirmed for this same server (scheme, host and port). */
    fun savedPinFor(base: String): String {
        val old = prefs.baseUrl.toHttpUrlOrNull() ?: return ""
        val new = base.toHttpUrlOrNull() ?: return ""
        val same = old.scheme == new.scheme && old.host.equals(new.host, true) && old.port == new.port
        return if (same && prefs.allowInsecureTls) prefs.certPin else ""
    }

    fun connectHttps(base: String, httpFallback: String?) {
        if (!insecure) { attempt(base, "", false, httpFallback); return }
        val pin = savedPinFor(base)
        if (pin.isNotBlank()) attempt(base, pin, false, httpFallback)
        else promptCert(base, changed = false, httpFallback = httpFallback)
    }

    fun connect() {
        if (busy || url.isBlank()) return
        error = null
        val raw = url.trim().trimEnd('/')
        when {
            raw.startsWith("http://", ignoreCase = true) -> {
                // Explicit http: always confirm, unless it's the server the user already confirmed.
                val sameAsSaved = prefs.httpConfirmed && raw.equals(prefs.baseUrl, ignoreCase = true)
                if (sameAsSaved) attempt(raw, "", true, null) else httpPrompt = raw
            }
            raw.startsWith("https://", ignoreCase = true) -> connectHttps(raw, null)
            // No scheme: https first; http only behind an explicit confirm if https can't connect.
            else -> connectHttps("https://$raw", "http://$raw")
        }
    }

    certPrompt?.let { (base, cert) ->
        CertTrustDialog(
            cert = cert, changed = certChanged,
            onTrust = { certPrompt = null; attempt(base, cert.fingerprint, false, null) },
            onCancel = { certPrompt = null },
        )
    }
    httpPrompt?.let { base ->
        HttpWarningDialog(
            host = base.toHttpUrlOrNull()?.host ?: base,
            intro = if (!url.trim().startsWith("http", ignoreCase = true))
                "Couldn't connect to ${base.removePrefix("http://")} over https." else null,
            onConfirm = { httpPrompt = null; attempt(base, "", true, null) },
            onCancel = { httpPrompt = null },
        )
    }

    Box(Modifier.fillMaxSize().background(cs.background), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxSize().safeDrawingPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.size(84.dp).clip(CircleShape).background(cs.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Videocam, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                if (editing) "Server settings" else "Welcome to FrigateCtrl",
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                "Enter the address you use to open Frigate in a browser.",
                color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        url, { url = it }, label = { Text("Frigate URL") }, singleLine = true,
                        placeholder = { Text("https://192.168.1.20:8971") },
                        leadingIcon = { Icon(Icons.Outlined.Dns, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        user, { user = it }, label = { Text("Username") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Person, null) },
                        supportingText = { Text("Leave empty if authentication is off (port 5000)") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        pass, { pass = it }, label = { Text("Password") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                        supportingText = if (hasSavedPass) ({ Text("Leave empty to keep the saved password") }) else null,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { connect() }),
                        trailingIcon = {
                            IconButton({ showPass = !showPass }) {
                                Icon(if (showPass) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    if (showPass) "Hide password" else "Show password")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (url.trim().startsWith("http://", ignoreCase = true)) NotEncryptedBadge()
                    ListItem(
                        headlineContent = { Text("Allow self-signed certificate") },
                        supportingContent = { Text("You confirm the certificate's fingerprint once; only that certificate is accepted") },
                        trailingContent = { Switch(insecure, { insecure = it }) },
                        colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerLow),
                    )
                }
            }

            error?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = cs.errorContainer, contentColor = cs.onErrorContainer),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ErrorOutline, null)
                        Spacer(Modifier.width(12.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { connect() },
                enabled = !busy && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = cs.onPrimary)
                else Text(if (editing) "Save & reconnect" else "Connect", style = MaterialTheme.typography.titleMedium)
            }
            if (onCancel != null) TextButton(onCancel, Modifier.fillMaxWidth().padding(top = 4.dp)) { Text("Cancel") }
        }
    }
}
