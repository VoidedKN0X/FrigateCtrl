package com.vknxworks.frigateremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vknxworks.frigateremote.ui.CameraScreen
import com.vknxworks.frigateremote.ui.CamerasScreen
import com.vknxworks.frigateremote.ui.FrigateTheme
import com.vknxworks.frigateremote.ui.Palette
import com.vknxworks.frigateremote.ui.SetupScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App.init(this)
        enableEdgeToEdge()
        setContent {
            FrigateTheme {
                Box(Modifier.fillMaxSize().background(Palette.Bg)) { Root() }
            }
        }
    }
}

/** Routes: "setup", "setup-edit", "cameras", "camera:<name>" */
@Composable
private fun Root() {
    var route by rememberSaveable { mutableStateOf(if (App.prefs.isConfigured) "cameras" else "setup") }

    when {
        route == "setup" || route == "setup-edit" -> {
            if (route == "setup-edit") BackHandler { route = "cameras" }
            SetupScreen(
                editing = route == "setup-edit",
                onDone = { route = "cameras" },
                onCancel = if (route == "setup-edit") ({ route = "cameras" }) else null,
            )
        }
        route == "cameras" -> CamerasScreen(
            onOpen = { route = "camera:$it" },
            onSettings = { route = "setup-edit" },
            onLogout = {
                App.prefs.clear()
                App.api.reload()
                route = "setup"
            },
        )
        route.startsWith("camera:") -> {
            BackHandler { route = "cameras" }
            CameraScreen(camera = route.removePrefix("camera:"), onBack = { route = "cameras" })
        }
    }
}
