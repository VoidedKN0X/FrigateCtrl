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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.vknxworks.frigatectrl.ui.GlassTab
import com.vknxworks.frigatectrl.ui.GlassTabBar
import com.vknxworks.frigatectrl.ui.GlassTabBarHeight
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Videocam
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
import com.vknxworks.frigatectrl.ui.lock.resetBiometricKey
import com.vknxworks.frigatectrl.ui.clearImageCache
import com.vknxworks.frigatectrl.data.Clips
import com.vknxworks.frigatectrl.ui.CameraScreen
import com.vknxworks.frigatectrl.ui.CamerasScreen
import com.vknxworks.frigatectrl.ui.FrigateTheme
import com.vknxworks.frigatectrl.ui.HealthScreen
import com.vknxworks.frigatectrl.ui.SettingsScreen
import com.vknxworks.frigatectrl.ui.SetupScreen
import com.vknxworks.frigatectrl.ui.ConnectionSecurityCheck
import com.vknxworks.frigatectrl.ui.needsConnectionCheck
import com.vknxworks.frigatectrl.ui.isAppInDarkTheme

// FragmentActivity (a ComponentActivity) is required by the fingerprint prompt.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App.init(this)
        enableEdgeToEdge()
        // Never show a snapshot of the cameras in the recent-apps switcher.
        setRecentsScreenshotEnabled(false)
        // Applied before the first frame is drawn, not only once Compose has started.
        if (App.lock.hideContent) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // Remove temporary shared clips left over from an earlier session.
        val appCtx = applicationContext
        Thread { Clips.cleanup(appCtx) }.start()
        setContent {
            FrigateTheme {
                val dark = isAppInDarkTheme()
                LaunchedEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                // Keep cameras out of the recent-apps preview, screenshots and recordings (on by default,
                // independent of the app lock).
                val secure = App.lock.hideContent
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
    var homeTab by rememberSaveable { mutableStateOf(0) }
    var securityChecked by rememberSaveable { mutableStateOf(false) }

    fun logout() {
        App.prefs.clear()
        App.api.reload()
        // Nothing from the old server stays behind: cached thumbnails and temporary clips.
        clearImageCache()
        val appCtx = activity.applicationContext
        Thread { Clips.cleanup(appCtx, all = true) }.start()
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
                resetBiometricKey()
                lockVm.unlock()
                logout()
            },
        )
        return
    }

    // After updating from a version that trusted any certificate (or used http without asking),
    // confirm the server's security once before anything is sent to it.
    if (!securityChecked && route != "setup" && route != "setup-edit" && needsConnectionCheck()) {
        ConnectionSecurityCheck(
            onDone = { securityChecked = true },
            onServerSettings = { securityChecked = true; route = "setup-edit" },
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
            r == "cameras" -> HomeScreen(
                tab = homeTab,
                onTab = { homeTab = it },
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

private fun depth(route: String): Int = when {
    route == "setup" -> 0
    route == "cameras" -> 1
    route == "settings" || route.startsWith("camera:") -> 2
    else -> 3
}

/** Dashboard: Cameras and System health as bottom tabs. */
@Composable
private fun HomeScreen(tab: Int, onTab: (Int) -> Unit, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    // Back on the Health tab returns to Cameras first.
    if (tab != 0) BackHandler { onTab(0) }
    val haze = remember { HazeState() }
    val tabs = remember {
        listOf(
            GlassTab("Cameras", Icons.Outlined.Videocam, Icons.Filled.Videocam),
            GlassTab("Health", Icons.Outlined.MonitorHeart, Icons.Filled.MonitorHeart),
        )
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Lists scroll underneath the floating bar, so they only need room to clear it at the end.
    val contentBottom = navBottom + GlassTabBarHeight + 24.dp

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "tab",
            ) { t ->
                when (t) {
                    0 -> CamerasScreen(onOpen = onOpen, onSettings = onSettings, bottomPadding = contentBottom)
                    else -> HealthScreen(bottomPadding = contentBottom)
                }
            }
        }
        GlassTabBar(
            tabs = tabs,
            selected = tab,
            onSelect = onTab,
            hazeState = haze,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = navBottom + 12.dp),
        )
    }
}

