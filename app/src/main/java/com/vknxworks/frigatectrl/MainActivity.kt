package com.vknxworks.frigatectrl

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vknxworks.frigatectrl.ui.lock.AppLockViewModel
import com.vknxworks.frigatectrl.ui.lock.LockScreen
import com.vknxworks.frigatectrl.ui.CameraScreen
import com.vknxworks.frigatectrl.ui.CamerasScreen
import com.vknxworks.frigatectrl.ui.FrigateTheme
import com.vknxworks.frigatectrl.ui.HealthScreen
import com.vknxworks.frigatectrl.ui.SettingsScreen
import com.vknxworks.frigatectrl.ui.SetupScreen
import com.vknxworks.frigatectrl.ui.isAppInDarkTheme

// FragmentActivity (a ComponentActivity) is required by the fingerprint prompt.
class MainActivity : FragmentActivity() {
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
                // With the lock on, keep cameras out of the recent-apps preview, screenshots and recordings.
                val secure = App.lock.pinSet && App.lock.hideContent
                LaunchedEffect(secure) {
                    if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Root(this) }
            }
        }
    }
}

/** Routes: "setup", "setup-edit", "cameras", "settings", "camera:<name>" */
@Composable
private fun Root(activity: FragmentActivity, lockVm: AppLockViewModel = viewModel()) {
    var route by rememberSaveable { mutableStateOf(if (App.prefs.isConfigured) "cameras" else "setup") }

    fun logout() {
        App.prefs.clear()
        App.api.reload()
        route = "setup"
    }

    // Lock when the app goes to the background (immediately, or after the chosen delay).
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (!activity.isChangingConfigurations) lockVm.onBackground()
                Lifecycle.Event.ON_START -> lockVm.onForeground()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // While locked nothing else is composed, so live streams and audio stop too.
    if (lockVm.locked && App.prefs.isConfigured) {
        LockScreen(
            vm = lockVm,
            onForgot = {
                // Forgetting the PIN wipes the lock and the saved login, so the cameras stay protected.
                App.lock.clearPin()
                lockVm.unlock()
                logout()
            },
        )
        return
    }

    AnimatedContent(
        targetState = route,
        transitionSpec = {
            // Slide deeper screens in from the right, back out to the left — with a soft fade.
            val forward = depth(targetState) >= depth(initialState)
            val dir = if (forward) 1 else -1
            (slideInHorizontally(tween(320)) { it / 5 * dir } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(320)) { -it / 8 * dir } + fadeOut(tween(160)))
        },
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
                    onHealth = { route = "health" },
                )
            }
            r == "health" -> {
                BackHandler { route = "settings" }
                HealthScreen(onBack = { route = "settings" })
            }
            r.startsWith("camera:") -> {
                BackHandler { route = "cameras" }
                CameraScreen(camera = r.removePrefix("camera:"), onBack = { route = "cameras" })
            }
        }
    }
}

private fun depth(route: String): Int = when {
    route == "setup" -> 0
    route == "cameras" -> 1
    route == "settings" || route.startsWith("camera:") -> 2
    route == "health" -> 3
    else -> 3
}
