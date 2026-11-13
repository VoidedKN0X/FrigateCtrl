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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.vknxworks.frigatectrl.R
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import com.vknxworks.frigatectrl.ui.components.CtrlButton
import com.vknxworks.frigatectrl.ui.components.CtrlCard
import com.vknxworks.frigatectrl.ui.components.CtrlSectionLabel
import com.vknxworks.frigatectrl.ui.components.CtrlSwitch
import com.vknxworks.frigatectrl.ui.components.CtrlWordmark
import com.vknxworks.frigatectrl.ui.components.ctrlFieldColors
import com.vknxworks.frigatectrl.ui.theme.CtrlColors
import com.vknxworks.frigatectrl.ui.theme.CtrlShape

@Composable
fun SetupScreen(editing: Boolean, onDone: () -> Unit, onCancel: (() -> Unit)?) {
    // The password is typed here: never in screenshots, recordings or casting.
    com.vknxworks.frigatectrl.SecureWindow()
    val prefs = App.prefs
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
    val ctx = LocalContext.current

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
                else error = ctx.getString(R.string.cert_read_failed, e.message ?: e.javaClass.simpleName)
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
                    certError || e is SSLPeerUnverifiedException -> error = ctx.getString(R.string.cert_not_trusted)
                    e is SSLException -> error = ctx.getString(R.string.tls_failed, e.message ?: e.javaClass.simpleName)
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
                stringResource(R.string.https_failed, base.removePrefix("http://")) else null,
            onConfirm = { httpPrompt = null; attempt(base, "", true, null) },
            onCancel = { httpPrompt = null },
        )
    }

    Box(Modifier.fillMaxSize().background(CtrlColors.Background), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxSize().safeDrawingPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CtrlWordmark("Frigate", Modifier.fillMaxWidth().padding(bottom = 28.dp))
            Box(
                Modifier.align(Alignment.Start).size(72.dp).clip(RoundedCornerShape(24.dp)).background(CtrlColors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Videocam, null, tint = CtrlColors.OnAccent, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(22.dp))
            CtrlSectionLabel(stringResource(R.string.setup_label), Modifier.fillMaxWidth())
            Text(
                stringResource(if (editing) R.string.server_settings else R.string.setup_welcome),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            Text(
                stringResource(R.string.setup_intro),
                color = CtrlColors.Muted, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
            )

            CtrlCard(padding = 16.dp, spacing = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        url, { url = it }, label = { Text(stringResource(R.string.setup_url)) }, singleLine = true,
                        placeholder = { Text("https://192.168.1.20:8971") },
                        leadingIcon = { Icon(Icons.Outlined.Dns, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        shape = CtrlShape.Field,
                        colors = ctrlFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        user, { user = it }, label = { Text(stringResource(R.string.setup_username)) }, singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Person, null) },
                        supportingText = { Text(stringResource(R.string.setup_username_hint)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        shape = CtrlShape.Field,
                        colors = ctrlFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        pass, { pass = it }, label = { Text(stringResource(R.string.setup_password)) }, singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                        supportingText = if (hasSavedPass) ({ Text(stringResource(R.string.setup_password_keep)) }) else null,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { connect() }),
                        trailingIcon = {
                            IconButton({ showPass = !showPass }) {
                                Icon(if (showPass) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    stringResource(if (showPass) R.string.hide_password else R.string.show_password))
                            }
                        },
                        shape = CtrlShape.Field,
                        colors = ctrlFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (url.trim().startsWith("http://", ignoreCase = true)) NotEncryptedBadge()
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.setup_self_signed), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.setup_self_signed_subtitle), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
                        }
                        Spacer(Modifier.width(12.dp))
                        CtrlSwitch(insecure, { insecure = it })
                    }
                }
            }

            error?.let {
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp).clip(CtrlShape.Field).background(CtrlColors.ErrorContainer)
                        .border(1.dp, CtrlColors.Error.copy(alpha = 0.4f), CtrlShape.Field).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = CtrlColors.Error)
                    Spacer(Modifier.width(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Error)
                }
            }

            Spacer(Modifier.height(20.dp))
            CtrlButton(
                stringResource(if (editing) R.string.setup_save_reconnect else R.string.setup_connect),
                onClick = { connect() },
                enabled = !busy && url.isNotBlank(),
                loading = busy,
                modifier = Modifier.fillMaxWidth(),
            )
            if (onCancel != null) TextButton(onCancel, Modifier.fillMaxWidth().padding(top = 4.dp)) { Text(stringResource(R.string.cancel), color = CtrlColors.Muted) }
        }
    }
}
