package com.vknxworks.frigatectrl

import android.content.Intent
import android.content.res.Configuration
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
import com.vknxworks.frigatectrl.ui.components.CtrlBottomBar
import com.vknxworks.frigatectrl.ui.components.CtrlBottomBarHeight
import com.vknxworks.frigatectrl.ui.components.CtrlBottomBarMargin
import com.vknxworks.frigatectrl.ui.components.CtrlTab
import com.vknxworks.frigatectrl.ui.theme.CtrlColors
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vknxworks.frigatectrl.ui.lock.AppLockViewModel
import com.vknxworks.frigatectrl.ui.lock.ChooseNewPinScreen
import com.vknxworks.frigatectrl.ui.lock.LockScreen
import com.vknxworks.frigatectrl.ui.lock.PinRequestScreen
import com.vknxworks.frigatectrl.ui.lock.PinRequests
import com.vknxworks.frigatectrl.ui.lock.resetBiometricKey
import com.vknxworks.frigatectrl.ui.clearImageCache
import com.vknxworks.frigatectrl.data.Clips
import com.vknxworks.frigatectrl.ui.CameraScreen
import com.vknxworks.frigatectrl.ui.PipState
import com.vknxworks.frigatectrl.ui.CamerasScreen
import com.vknxworks.frigatectrl.ui.FrigateTheme
import com.vknxworks.frigatectrl.ui.HealthScreen
import com.vknxworks.frigatectrl.ui.SettingsScreen
import com.vknxworks.frigatectrl.ui.SetupScreen
import com.vknxworks.frigatectrl.ui.YouScreen
import com.vknxworks.frigatectrl.ui.ConnectionSecurityCheck
import com.vknxworks.frigatectrl.ui.needsConnectionCheck
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.outlined.VideoLibrary
import com.vknxworks.frigatectrl.notify.AlertNotifier
import com.vknxworks.frigatectrl.notify.AlertWorker
import com.vknxworks.frigatectrl.notify.DeepLinks
import com.vknxworks.frigatectrl.notify.Push
import com.vknxworks.frigatectrl.ui.ExportPlayer
import com.vknxworks.frigatectrl.ui.ExportPlayerScreen
import com.vknxworks.frigatectrl.ui.ExportsScreen
import com.vknxworks.frigatectrl.ui.NotificationsScreen

// FragmentActivity (a ComponentActivity) is required by the fingerprint prompt.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App.init(this)
        // Follows the phone's dark / light setting (the activity restarts when it changes): transparent bars over
        // the app's own background, light icons in dark mode and dark icons in light mode, edge to edge.
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        CtrlColors.palette = if (dark) CtrlColors.Dark else CtrlColors.Light
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // Applied before the first frame is drawn, not only once Compose has started.
        if (App.lock.hideScreenshots) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // Remove temporary shared clips left over from an earlier session.
        val appCtx = applicationContext
        Thread { Clips.cleanup(appCtx) }.start()
        // Alert notifications: the background check and the registration with ntfy, as they were left.
        if (App.prefs.isConfigured) {
            AlertWorker.schedule(appCtx)
            Push.start(appCtx, App.notify.enabled && App.notify.push)
        }
        // A tapped alert notification opens that camera's History (after unlocking, if the app is locked).
        DeepLinks.handle(this, intent)
        setContent {
            FrigateTheme {
                // "Hide in screenshots" (off by default, independent of the app lock). Sign-in, the lock screen and
                // PIN screens are always kept out of screenshots.
                val secure = App.lock.hideScreenshots || SecureScreens.active > 0
                // "Hide in recent apps", and always with the app lock on: a blank card instead of a snapshot.
                val blankRecents = App.lock.hideRecents || App.vault.enabled
                LaunchedEffect(blankRecents) { setRecentsScreenshotEnabled(!blankRecents) }
                LaunchedEffect(secure) {
                    if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Root(this) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLinks.handle(this, intent)
    }

    private val lockVm: AppLockViewModel get() = ViewModelProvider(this)[AppLockViewModel::class.java]

    // Picture-in-picture: the activity is only paused, never stopped, so the lifecycle alone never sees you
    // leave. Going home while the live view is about to float, and entering picture-in-picture, count as
    // leaving; expanding the floating window back to full screen counts as returning, with the usual lock check.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (PipState.autoArmed && !isChangingConfigurations) lockVm.onBackground()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) lockVm.onBackground() else lockVm.onForeground()
    }
}

/** Screens that must never be captured (sign-in, the lock screen, PIN screens) bump [active] while shown. */
object SecureScreens {
    var active by androidx.compose.runtime.mutableIntStateOf(0)
}

/** Keeps FLAG_SECURE on while the calling screen is showing, whatever the user's setting. */
@Composable
fun SecureWindow() {
    DisposableEffect(Unit) {
        SecureScreens.active++
        onDispose { SecureScreens.active-- }
    }
}

/** Routes: "setup", "setup-edit", "cameras", "you" (the profile picture), "settings" (from You), "camera:<name>" */
@Composable
private fun Root(activity: FragmentActivity, lockVm: AppLockViewModel = viewModel()) {
    var route by rememberSaveable { mutableStateOf(if (App.prefs.isConfigured) "cameras" else "setup") }
    var homeTab by rememberSaveable { mutableStateOf(0) }
    var securityChecked by rememberSaveable { mutableStateOf(false) }

    fun logout() {
        // The app lock protects the saved login; with no login left it's turned off too (it would
        // otherwise have no key to encrypt the next login with while locked).
        App.vault.disable()
        App.lock.clearPin()
        resetBiometricKey()
        App.prefs.clear()
        App.api.reload()
        // Alerts were about this server: notifications off, ntfy registration and shown alerts gone.
        val ctx = activity.applicationContext
        Push.unregister(ctx)
        App.notify.clear()
        AlertWorker.schedule(ctx)
        AlertNotifier.cancelAll(ctx)
        ExportPlayer.current = null
        // Nothing from the old server stays behind: cached thumbnails and temporary clips.
        clearImageCache()
        val appCtx = activity.applicationContext
        Thread { Clips.cleanup(appCtx, all = true) }.start()
        route = "setup"
    }

    val inPip = com.vknxworks.frigatectrl.ui.rememberIsInPip()

    // Lock when the app goes to the background (immediately, or after the chosen delay).
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (!activity.isChangingConfigurations) lockVm.onBackground()
                Lifecycle.Event.ON_START -> lockVm.onForeground()
                // Also back from something that only paused the app (picture-in-picture expanded to full
                // screen, or a see-through screen such as the share sheet). Nothing happens if it never left.
                Lifecycle.Event.ON_RESUME -> if (!activity.isInPictureInPictureMode) lockVm.onForeground()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // While locked nothing else is composed, so live streams and audio stop too.
    if (lockVm.locked && App.prefs.isConfigured && inPip) {
        // Locked while floating: no camera, only a lock. Expanding the window shows the lock screen.
        Box(Modifier.fillMaxSize().background(CtrlColors.Background), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(
                Icons.Outlined.Lock,
                stringResource(R.string.pip_locked),
                tint = CtrlColors.AccentText,
            )
        }
        return
    }
    if (lockVm.locked && App.prefs.isConfigured) {
        LockScreen(
            vm = lockVm,
            onForgot = {
                // The PIN encrypts the saved login, so forgetting it means logging out: the lock,
                // the login and everything kept from the server are wiped.
                logout()
                lockVm.unlock()
            },
        )
        return
    }
    if (lockVm.mustChoosePin) {
        ChooseNewPinScreen(lockVm)
        return
    }

    // Only reached while unlocked: a tapped notification opens its camera's History at the alert.
    val link = DeepLinks.pending
    LaunchedEffect(link) {
        if (link != null && App.prefs.isConfigured) {
            ExportPlayer.current = null
            route = "review:${link.camera}:${link.time}"
            DeepLinks.pending = null
        }
    }
    // With the login sealed by the app lock, background checks wait while locked: catch up after unlocking.
    LaunchedEffect(Unit) {
        if (App.notify.enabled && App.vault.enabled && !App.prefs.alertsWhileLocked) AlertWorker.runNow(activity.applicationContext)
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

    // A server export playing full screen, in place of the tabs (they come back as they were).
    ExportPlayer.current?.let { e ->
        BackHandler { ExportPlayer.current = null }
        ExportPlayerScreen(e, onClose = { ExportPlayer.current = null })
        PinRequests.request?.let { PinRequestScreen(it) }
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
                if (r == "setup-edit") BackHandler { route = "you" }
                SetupScreen(
                    editing = r == "setup-edit",
                    onDone = { route = "cameras" },
                    onCancel = if (r == "setup-edit") ({ route = "you" }) else null,
                )
            }
            r == "cameras" -> HomeScreen(
                tab = homeTab,
                onTab = { homeTab = it },
                onOpen = { route = "camera:$it" },
                onYou = { route = "you" },
            )
            r == "you" -> {
                BackHandler { route = "cameras" }
                YouScreen(
                    onBack = { route = "cameras" },
                    onSettings = { route = "settings" },
                    onServer = { route = "setup-edit" },
                    onLogout = ::logout,
                )
            }
            r == "settings" -> {
                BackHandler { route = "you" }
                SettingsScreen(onBack = { route = "you" }, onNotifications = { route = "notifications" })
            }
            r == "notifications" -> {
                BackHandler { route = "settings" }
                NotificationsScreen(onBack = { route = "settings" })
            }
            r.startsWith("camera:") -> {
                BackHandler { route = "cameras" }
                CameraScreen(camera = r.removePrefix("camera:"), onBack = { route = "cameras" })
            }
            r.startsWith("review:") -> {
                // review:<camera>:<start time>, from a notification: History at that alert.
                val rest = r.removePrefix("review:")
                val cam = rest.substringBeforeLast(':')
                val at = rest.substringAfterLast(':').toDoubleOrNull()
                BackHandler { route = "cameras" }
                CameraScreen(camera = cam, onBack = { route = "cameras" }, historyAt = at)
            }
        }
    }
    // Turning the lock on or off, or changing the PIN (from Settings, behind the profile picture), on top of the app.
    PinRequests.request?.let { PinRequestScreen(it) }
}

private fun depth(route: String): Int = when {
    route == "setup" -> 0
    route == "cameras" -> 1
    route == "you" || route.startsWith("camera:") || route.startsWith("review:") -> 2
    route == "notifications" -> 4
    else -> 3 // "settings" and "setup-edit", both opened from You
}

/** Dashboard: Cameras, Exports and System health as bottom tabs. */
@Composable
private fun HomeScreen(tab: Int, onTab: (Int) -> Unit, onOpen: (String) -> Unit, onYou: () -> Unit) {
    // Back on the Exports or Health tab returns to Cameras first.
    if (tab != 0) BackHandler { onTab(0) }
    val camerasLabel = stringResource(R.string.tab_cameras)
    val healthLabel = stringResource(R.string.tab_health)
    val exportsLabel = stringResource(R.string.tab_exports)
    val tabs = remember(camerasLabel, healthLabel, exportsLabel) {
        listOf(
            CtrlTab(camerasLabel, Icons.Outlined.Videocam),
            CtrlTab(exportsLabel, Icons.Outlined.VideoLibrary),
            CtrlTab(healthLabel, Icons.Outlined.MonitorHeart),
        )
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Lists scroll underneath the floating bar, so they only need room to clear it at the end.
    val contentBottom = navBottom + CtrlBottomBarMargin + CtrlBottomBarHeight + 8.dp

    Box(Modifier.fillMaxSize().background(CtrlColors.Background)) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "tab",
            ) { t ->
                when (t) {
                    0 -> CamerasScreen(onOpen = onOpen, onYou = onYou, bottomPadding = contentBottom)
                    1 -> ExportsScreen(onYou = onYou, bottomPadding = contentBottom)
                    else -> HealthScreen(onYou = onYou, bottomPadding = contentBottom)
                }
            }
        }
        CtrlBottomBar(
            tabs = tabs,
            selected = tab,
            onSelect = onTab,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = navBottom + CtrlBottomBarMargin),
        )
    }
}

