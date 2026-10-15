package com.vknxworks.frigateremote

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vknxworks.frigateremote.ui.CameraScreen
import com.vknxworks.frigateremote.ui.CamerasScreen
import com.vknxworks.frigateremote.ui.FrigateTheme
import com.vknxworks.frigateremote.ui.SettingsScreen
import com.vknxworks.frigateremote.ui.SetupScreen
import com.vknxworks.frigateremote.ui.isAppInDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App.init(this)
        enableEdgeToEdge()
        setContent {
            FrigateTheme {
                val dark = isAppInDarkTheme()
                LaunchedEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Root() }
            }
        }
    }
}

/** Routes: "setup", "setup-edit", "cameras", "settings", "camera:<name>" */
@Composable
private fun Root() {
    var route by rememberSaveable { mutableStateOf(if (App.prefs.isConfigured) "cameras" else "setup") }

    fun logout() {
        App.prefs.clear()
        App.api.reload()
        route = "setup"
    }

    AnimatedContent(
        targetState = route,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "route",
    ) { r ->
        when {
            r == "setup" || r == "setup-edit" -> {
                if (r == "setup-edit") BackHandler { route = "settings" }
                SetupScreen(
                    editing = r == "setup-edit",
                    onDone = { route = "cameras" },
                    onCancel = if (r == "setup-edit") ({ route = "settings" }) else null,
                )
            }
            r == "cameras" -> CamerasScreen(
                onOpen = { route = "camera:$it" },
                onSettings = { route = "settings" },
            )
            r == "settings" -> {
                BackHandler { route = "cameras" }
                SettingsScreen(
                    onBack = { route = "cameras" },
                    onServer = { route = "setup-edit" },
                    onLogout = ::logout,
                )
            }
            r.startsWith("camera:") -> {
                BackHandler { route = "cameras" }
                CameraScreen(camera = r.removePrefix("camera:"), onBack = { route = "cameras" })
            }
        }
    }
}
