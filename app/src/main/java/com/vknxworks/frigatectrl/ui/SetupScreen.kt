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
import kotlinx.coroutines.launch
import javax.net.ssl.SSLException

@Composable
fun SetupScreen(editing: Boolean, onDone: () -> Unit, onCancel: (() -> Unit)?) {
    val prefs = App.prefs
    val cs = MaterialTheme.colorScheme
    var url by remember { mutableStateOf(if (editing) prefs.baseUrl else "") }
    var user by remember { mutableStateOf(if (editing) prefs.username else "") }
    var pass by remember { mutableStateOf(if (editing) prefs.password else "") }
    var insecure by remember { mutableStateOf(prefs.allowInsecureTls) }
    var showPass by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun connect() {
        if (busy || url.isBlank()) return
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
                    ListItem(
                        headlineContent = { Text("Allow self-signed certificate") },
                        supportingContent = { Text("Only for servers on your own network") },
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
