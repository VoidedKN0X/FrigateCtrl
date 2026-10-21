package com.vknxworks.frigatectrl.ui.lock

import android.content.Context
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vknxworks.frigatectrl.App
import com.vknxworks.frigatectrl.data.lock.LockVault
import com.vknxworks.frigatectrl.ui.pressScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.vknxworks.frigatectrl.R

const val PIN_MIN = LockVault.PIN_MIN
const val PIN_MAX = LockVault.PIN_MAX

/** Only class-3 ("strong") biometrics — weak face unlock can be fooled by a photo. */
fun biometricAvailable(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS


/**
 * Trips the app itself starts to another app (share sheet, browser). Coming back from them within
 * the lock delay doesn't count as leaving the app, so you don't have to unlock again.
 */
object LockTrips {
    @Volatile private var expectedAt = 0L

    fun expectReturn() { expectedAt = SystemClock.elapsedRealtime() }

    /** True (once) when the app is being left within 5 seconds of [expectReturn]. */
    fun consume(): Boolean {
        val at = expectedAt
        expectedAt = 0L
        return at > 0 && SystemClock.elapsedRealtime() - at < 5_000
    }
}

/**
 * Shows the strong-biometric prompt for [cipher], a Keystore key from [LockVault] that only works
 * right after a real match. [onSuccess] gets the unlocked cipher, so a faked "success" can't open the app.
 */
fun showBiometricPrompt(
    activity: FragmentActivity,
    title: String,
    cipher: Cipher,
    subtitle: String? = null,
    onSuccess: (Cipher) -> Unit,
) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                result.cryptoObject?.cipher?.let(onSuccess)
            }
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .setNegativeButtonText(activity.getString(R.string.lock_use_pin))
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build(),
        BiometricPrompt.CryptoObject(cipher),
    )
}

/** Turns the app lock on with [pin] (off the main thread): new vault key, login encrypted with it. */
suspend fun enableAppLock(pin: String) = withContext(Dispatchers.Default) {
    App.vault.setUp(pin)
    App.prefs.sealCredentials()
}

/** Turns the app lock off (after the PIN was confirmed): the login goes back to Keystore-only encryption. */
fun disableAppLock() {
    App.prefs.unsealCredentials { App.vault.disable() }
}

/** Whether the app is locked. Survives rotation; a fresh process always starts locked. */
class AppLockViewModel : ViewModel() {
    val settings = App.lock
    val vault = App.vault

    /** A PIN is set (the new lock, or an old one from 3.2.x waiting to be upgraded). */
    val enabled: Boolean get() = vault.enabled || settings.legacyPinSet

    var locked by mutableStateOf(enabled)
        private set
    /** A string resource: why the last try didn't unlock. */
    var pinError by mutableStateOf<Int?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var lockoutRemaining by mutableLongStateOf(0L)
        private set

    /**
     * Unlocked with an old PIN shorter than 6 digits (or too easy to guess): a new PIN must be
     * chosen before the app opens.
     */
    var mustChoosePin by mutableStateOf(false)
        private set

    /** Set once the lock screen has shown the biometric prompt, so rotation doesn't show it twice. */
    var biometricShown = false

    /** Set when the biometric key was invalidated by a newly added fingerprint; the PIN is needed. */
    var biometricInvalidated by mutableStateOf(false)

    private var backgroundedAt = 0L
    private var ticker: kotlinx.coroutines.Job? = null

    init {
        // An old PIN without a server to protect (logged out) has nothing to upgrade.
        if (settings.legacyPinSet && !App.prefs.isConfigured) { settings.clearPin(); resetBiometricKey(); locked = false }
        refreshLockout()
    }

    /** The current trip away was started by the app itself (share sheet, a link). */
    private var exemptTrip = false

    fun onBackground() {
        backgroundedAt = SystemClock.elapsedRealtime()
        // The app opened the share sheet or a link itself: that isn't leaving (unless you stay away long).
        exemptTrip = LockTrips.consume()
        if (exemptTrip) return
        // "Immediately" locks before the app is hidden, so cameras aren't visible on return and the
        // key is gone while the app is in the background.
        if (enabled && settings.lockAfterSeconds == 0) lockNow()
    }

    fun onForeground() {
        refreshLockout()
        val since = backgroundedAt
        val exempt = exemptTrip
        backgroundedAt = 0L
        exemptTrip = false
        val after = settings.lockAfterSeconds
        if (!enabled || locked || since == 0L || after < 0) return
        // A trip the app started still locks if you stay away long: after 5 minutes, or the chosen time if longer.
        val limit = if (exempt) maxOf(after * 1000L, 5 * 60_000L) else after * 1000L
        if (SystemClock.elapsedRealtime() - since >= limit) lockNow()
    }

    fun lockNow() {
        if (enabled && !locked) {
            locked = true
            biometricShown = false
            mustChoosePin = false
            PinRequests.request = null
            // Wipe the key from memory and stop everything that holds the login.
            vault.close()
            App.api.forgetSession()
        }
    }

    fun unlock() {
        pinError = null
        biometricInvalidated = false
        locked = false
        App.api.reload()
    }

    fun submitPin(pin: String, onWrong: () -> Unit) {
        if (checking || lockoutRemaining > 0) return
        viewModelScope.launch {
            checking = true
            // Slow on purpose (it makes guessing slow too); keep it off the main thread.
            val ok = withContext(Dispatchers.Default) {
                if (vault.enabled) {
                    vault.unlockWithPin(pin) == LockVault.PinResult.Ok
                } else {
                    // The old lock from 3.2.x, with its own wrong-PIN counter and lockout.
                    if (settings.lockoutRemainingMs() > 0) false
                    else if (settings.checkPin(pin)) {
                        settings.failedAttempts = 0
                        if (pin.length >= PIN_MIN && !isObviousPin(pin)) upgrade(pin) else mustChoosePin = true
                        true
                    } else {
                        settings.recordWrongPin()
                        false
                    }
                }
            }
            checking = false
            refreshLockout()
            if (ok) { unlock(); return@launch }
            pinError = R.string.lock_wrong_pin
            onWrong()
        }
    }

    /** Upgrades an old 3.2.x PIN to the new lock: new vault key, login encrypted, old hash and key gone. */
    private fun upgrade(pin: String) {
        vault.setUp(pin)
        App.prefs.sealCredentials()
        settings.clearPin()
        resetBiometricKey()
    }

    /** The mandatory new PIN after unlocking with a short old one. */
    suspend fun chooseNewPin(pin: String) {
        withContext(Dispatchers.Default) { upgrade(pin) }
        mustChoosePin = false
    }

    fun unlockWithFingerprint(cipher: Cipher): Boolean {
        if (!vault.unlockWithBiometric(cipher)) return false
        unlock()
        return true
    }

    /** Typing again clears "Wrong PIN". */
    fun clearError() {
        pinError = null
    }

    // Uses the monotonic clock, so changing the phone's time can't end it.
    private fun refreshLockout() {
        lockoutRemaining = if (vault.enabled) vault.lockoutRemaining() else settings.lockoutRemainingMs()
        if (lockoutRemaining <= 0 || ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (true) {
                val left = if (vault.enabled) vault.lockoutRemaining() else settings.lockoutRemainingMs()
                lockoutRemaining = left
                if (left <= 0) break
                delay(500)
            }
        }
    }
}

/** Forgets the old (3.2.x) fingerprint key. */
fun resetBiometricKey() {
    runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("frigatectrl_biometric_unlock_key") }
}

/** The lock screen: PIN pad, fingerprint, and "Forgot PIN?" (the same look and flow as every Ctrl app). */
@Composable
fun LockScreen(vm: AppLockViewModel, onForgot: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.lockActivity() as? FragmentActivity }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    var pin by remember { mutableStateOf("") }
    var bioMessage by remember { mutableStateOf<String?>(null) }
    var confirmForgot by remember { mutableStateOf(false) }
    // Old PINs from 3.2.x could be 4 digits.
    val minLen = if (vm.vault.enabled) PIN_MIN else 4
    val savedLength = if (vm.vault.enabled) vm.vault.pinLength else vm.settings.pinLength
    // The PIN is needed after a restart and every 72 hours, like Android's own screen lock.
    val pinDue = remember(vm.locked) { vm.vault.pinRequired() }
    val canBio = !vm.biometricInvalidated && vm.vault.biometricEnabled && !pinDue && activity != null && biometricAvailable(context)

    val askBiometric: () -> Unit = {
        if (canBio && activity != null) runCatching {
            val cipher = vm.vault.biometricUnlockCipher()
            if (cipher == null) {
                // Fingerprints changed (the vault turned fingerprint unlock off): the PIN is needed.
                if (!vm.vault.biometricEnabled) vm.biometricInvalidated = true
            } else {
                showBiometricPrompt(activity, context.getString(R.string.lock_fingerprint_title), cipher, subtitle = context.getString(R.string.lock_fingerprint_subtitle)) {
                    if (!vm.unlockWithFingerprint(it)) bioMessage = context.getString(R.string.lock_fingerprint_failed)
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        if (!vm.biometricShown) { vm.biometricShown = true; askBiometric() }
    }
    // Back leaves the app rather than revealing anything underneath.
    BackHandler { activity?.moveTaskToBack(true) }

    val wrong: () -> Unit = {
        pin = ""
        scope.shakePin(shake, haptics)
    }

    PinPadScreen(
        title = stringResource(R.string.lock_title),
        message = when {
            vm.lockoutRemaining > 0 -> lockoutText(vm.lockoutRemaining)
            vm.pinError != null -> stringResource(vm.pinError!!)
            bioMessage != null -> bioMessage!!
            vm.biometricInvalidated -> stringResource(R.string.lock_fingerprint_changed)
            !vm.vault.enabled -> stringResource(R.string.lock_legacy_pin)
            vm.vault.biometricEnabled && pinDue -> stringResource(R.string.lock_pin_due)
            else -> stringResource(R.string.lock_enter_pin)
        },
        isError = vm.pinError != null || vm.lockoutRemaining > 0 || vm.biometricInvalidated || bioMessage != null,
        pin = pin,
        shake = shake.value,
        checking = vm.checking,
        enabled = vm.lockoutRemaining == 0L && !vm.checking,
        onDigit = { d ->
            if (pin.length < PIN_MAX) {
                bioMessage = null
                vm.clearError()
                pin += d
                if (pin.length == savedLength) vm.submitPin(pin, wrong)
            }
        },
        onBackspace = { pin = pin.dropLast(1) },
        onConfirm = { if (pin.length >= minLen) vm.submitPin(pin, wrong) },
        showConfirm = pin.length >= minLen && pin.length != savedLength,
        minDots = if (vm.vault.enabled) PIN_MIN else maxOf(minLen, savedLength),
        bottom = { LockScreenButtons(showFingerprint = canBio, onFingerprint = askBiometric, onForgot = { confirmForgot = true }) },
    )
    if (confirmForgot) {
        ForgotPinDialog(
            body = stringResource(R.string.lock_forgot_body),
            confirm = stringResource(R.string.lock_forgot_confirm),
            onDismiss = { confirmForgot = false },
        ) {
            confirmForgot = false
            onForgot()
        }
    }
}

/**
 * Shown after unlocking with an old PIN that's shorter than 6 digits or too easy to guess:
 * a new PIN has to be chosen (twice) before the app opens.
 */
@Composable
fun ChooseNewPinScreen(vm: AppLockViewModel) {
    PinSetupScreen(
        vault = vm.vault,
        title = stringResource(R.string.lock_choose_title),
        intro = stringResource(R.string.lock_migrate_intro, PIN_MIN),
        verifyCurrent = false,
        cancellable = false,
        onDone = { pin -> vm.chooseNewPin(pin) },
        onCancel = {},
    )
}

/** Turning the lock on or off, or changing the PIN, from Settings: a PIN screen on top of the app. */
@Composable
fun PinRequestScreen(request: PinRequest) {
    val vault = App.vault
    val done = { PinRequests.request = null }
    when (request) {
        PinRequest.ENABLE -> PinSetupScreen(
            vault = vault,
            title = stringResource(R.string.lock_choose_title),
            intro = stringResource(R.string.lock_choose_intro, PIN_MIN),
            verifyCurrent = false,
            cancellable = true,
            onDone = { pin -> enableAppLock(pin); done() },
            onCancel = done,
        )
        PinRequest.CHANGE -> PinSetupScreen(
            vault = vault,
            title = stringResource(R.string.lock_change_title),
            intro = stringResource(R.string.lock_change_intro, PIN_MIN),
            verifyCurrent = true,
            cancellable = true,
            // Same key, new PIN: fingerprint unlock keeps working.
            onDone = { pin -> withContext(Dispatchers.Default) { check(vault.changePin(pin)) }; done() },
            onCancel = done,
        )
        PinRequest.DISABLE -> PinSetupScreen(
            vault = vault,
            title = stringResource(R.string.lock_disable_title),
            intro = stringResource(R.string.lock_disable_intro),
            verifyCurrent = true,
            verifyOnly = true,
            cancellable = true,
            onDone = { withContext(Dispatchers.Default) { disableAppLock() }; done() },
            onCancel = done,
        )
    }
}

/** The lock and PIN screens are always kept out of screenshots, recordings and casting, like sign-in. */
@Composable
internal fun KeepOutOfScreenshots() = com.vknxworks.frigatectrl.SecureWindow()
