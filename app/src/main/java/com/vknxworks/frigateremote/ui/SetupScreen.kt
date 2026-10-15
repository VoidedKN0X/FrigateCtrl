package com.vknxworks.frigateremote.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vknxworks.frigateremote.App
import com.vknxworks.frigateremote.data.AuthException
import kotlinx.coroutines.launch
import javax.net.ssl.SSLException

@Composable
fun SetupScreen(editing: Boolean, onDone: () -> Unit, onCancel: (() -> Unit)?) {
    val prefs = App.prefs
    var url by remember { mutableStateOf(if (editing) prefs.baseUrl else "") }
    var user by remember { mutableStateOf(if (editing) prefs.username else "") }
    var pass by remember { mutableStateOf(if (editing) prefs.password else "") }
    var insecure by remember { mutableStateOf(prefs.allowInsecureTls) }
    var showPass by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun connect() {
        error = null
        busy = true
        scope.launch {
            val raw = url.trim().trimEnd('/')
            val candidates = if (raw.startsWith("http://") || raw.startsWith("https://")) listOf(raw)
            else listOf("https://$raw", "http://$raw")
            var lastErr: Exception? = null
            for (base in candidates) {
                try {
                    val tok = App.api.login(base, user.trim(), pass, insecure)
                    prefs.baseUrl = base
                    prefs.username = user.trim()
                    prefs.password = pass
                    prefs.token = tok
                    prefs.allowInsecureTls = insecure
                    App.api.reload()
                    busy = false
                    onDone()
                    return@launch
                } catch (e: AuthException) {
                    lastErr = e; break
                } catch (e: Exception) {
                    lastErr = e
                }
            }
            busy = false
            error = when (val e = lastErr) {
                is AuthException -> e.message
                is SSLException -> "Certificate not trusted. If Frigate uses its default self-signed " +
                        "certificate, turn on \"Allow self-signed certificate\"."
                null -> "Couldn't connect"
                else -> e.message ?: e.javaClass.simpleName
            }
        }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Icon(Icons.Default.Videocam, null, tint = Palette.Accent, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            if (editing) "Server settings" else "Connect to Frigate",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Use the same address you open Frigate with in a browser — e.g. https://192.168.1.20:8971",
            color = Palette.Muted, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
        )
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                url, { url = it }, label = { Text("Frigate URL") }, singleLine = true,
                placeholder = { Text("https://frigate.local:8971") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                user, { user = it }, label = { Text("Username") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                pass, { pass = it }, label = { Text("Password") }, singleLine = true,
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                trailingIcon = {
                    IconButton({ showPass = !showPass }) {
                        Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Leave username empty if your Frigate has authentication disabled (port 5000).",
                color = Palette.Muted, style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Allow self-signed certificate")
                    Text(
                        "Only for servers on your own network",
                        color = Palette.Muted, style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(insecure, { insecure = it })
            }
            error?.let { Text(it, color = Palette.Alert) }
            Button(
                onClick = { connect() },
                enabled = !busy && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text(if (editing) "Save & reconnect" else "Connect")
            }
            if (onCancel != null) TextButton(onCancel, Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}
