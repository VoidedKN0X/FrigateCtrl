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

const val PIN_MIN = LockVault.PIN_MIN
const val PIN_MAX = LockVault.PIN_MAX

/** Only class-3 ("strong") biometrics — weak face unlock can be fooled by a photo. */
fun biometricAvailable(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

/** PINs that are too easy to guess: one repeated digit, or a straight run like 123456 or 987654. */
fun isObviousPin(pin: String): Boolean =
    pin.toSet().size == 1 || "0123456789012".contains(pin) || "9876543210987".contains(pin)

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
            .setNegativeButtonText("Use PIN")
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
    var pinError by mutableStateOf<String?>(null)
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

    fun onBackground() {
        // The app opened the share sheet or a link itself: that isn't leaving.
        if (LockTrips.consume()) { backgroundedAt = 0L; return }
        backgroundedAt = SystemClock.elapsedRealtime()
        // "Immediately" locks before the app is hidden, so cameras aren't visible on return and the
        // key is gone while the app is in the background.
        if (enabled && settings.lockAfterSeconds == 0) lockNow()
    }

    fun onForeground() {
        refreshLockout()
        val since = backgroundedAt
        backgroundedAt = 0L
        val after = settings.lockAfterSeconds
        if (!enabled || locked || since == 0L || after < 0) return
        if (SystemClock.elapsedRealtime() - since >= after * 1000L) lockNow()
    }

    fun lockNow() {
        if (enabled && !locked) {
            locked = true
            biometricShown = false
            mustChoosePin = false
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
            pinError = "Wrong PIN"
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

    fun unlockWithFingerprint(cipher: Cipher) {
        if (vault.unlockWithBiometric(cipher)) unlock()
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

@Composable
fun LockScreen(vm: AppLockViewModel, onForgot: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    var pin by remember { mutableStateOf("") }
    var confirmForgot by remember { mutableStateOf(false) }
    // Old PINs from 3.2.x could be 4 digits.
    val minLen = if (vm.vault.enabled) PIN_MIN else 4
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
                showBiometricPrompt(activity, "Unlock FrigateCtrl", cipher) { vm.unlockWithFingerprint(it) }
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
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, androidx.compose.animation.core.tween(45))
        }
    }

    fun press(d: Char) {
        if (vm.lockoutRemaining > 0 || vm.checking || pin.length >= PIN_MAX) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        pin += d
        val len = if (vm.vault.enabled) vm.vault.pinLength else vm.settings.pinLength
        if (pin.length == len) vm.submitPin(pin, wrong)
    }

    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(cs.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Lock, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(34.dp)) }
            Spacer(Modifier.height(18.dp))
            Text("FrigateCtrl is locked", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            val err = vm.pinError != null || vm.lockoutRemaining > 0 || vm.biometricInvalidated
            Text(
                when {
                    vm.lockoutRemaining > 0 -> "Too many tries. Try again in ${(vm.lockoutRemaining + 999) / 1000} s"
                    vm.biometricInvalidated -> "Fingerprint unlock needs to be set up again (fingerprints changed). Enter your PIN, then turn it on again in Settings."
                    vm.pinError != null -> vm.pinError!!
                    vm.vault.biometricEnabled && pinDue -> "Enter your PIN. It's needed after a restart and every 72 hours."
                    else -> "Enter your PIN"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (err) cs.error else cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Box(Modifier.offset { IntOffset(shake.value.roundToInt(), 0) }) { PinDots(pin.length, minLen) }
            Spacer(Modifier.height(32.dp))
            PinPad(
                onDigit = ::press,
                onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                onConfirm = { if (pin.length >= minLen) vm.submitPin(pin, wrong) },
                confirmEnabled = pin.length >= minLen && !vm.checking && vm.lockoutRemaining == 0L,
            )
            Spacer(Modifier.height(20.dp))
            if (canBio) {
                FilledTonalButton(onClick = askBiometric) {
                    Icon(Icons.Outlined.Fingerprint, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Use fingerprint")
                }
            }
            TextButton(onClick = { confirmForgot = true }) { Text("Forgot PIN? Log out") }
        }
    }
    if (confirmForgot) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmForgot = false },
            title = { Text("Forgot your PIN?") },
            text = {
                Text(
                    "The PIN can't be recovered, because your login is encrypted with it. FrigateCtrl will log out " +
                        "and forget the server, the app lock and anything kept from the server. Your Frigate server isn't changed."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmForgot = false; onForgot() }) { Text("Log out", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirmForgot = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun PinDots(count: Int, min: Int = PIN_MIN) {
    val cs = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(maxOf(count, min)) { i ->
            Box(
                Modifier.size(14.dp).clip(CircleShape)
                    .background(if (i < count) cs.primary else cs.surfaceContainerHighest)
            )
        }
    }
}

@Composable
fun PinPad(onDigit: (Char) -> Unit, onBackspace: () -> Unit, onConfirm: () -> Unit, confirmEnabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) { row.forEach { d -> DigitKey(d.toString()) { onDigit(d) } } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackspace, modifier = Modifier.size(72.dp)) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete")
            }
            DigitKey("0") { onDigit('0') }
            TextButton(onClick = onConfirm, enabled = confirmEnabled, modifier = Modifier.size(72.dp)) { Text("OK") }
        }
    }
}

@Composable
private fun DigitKey(label: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    FilledIconButton(
        onClick = onClick,
        interactionSource = source,
        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.size(72.dp).pressScale(source, 0.9f),
    ) { Text(label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium) }
}

/**
 * Shown after unlocking with an old PIN that's shorter than 6 digits or too easy to guess:
 * a new PIN has to be chosen (twice) before the app opens.
 */
@Composable
fun ChooseNewPinScreen(vm: AppLockViewModel) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    var pin by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    BackHandler { activity?.moveTaskToBack(true) }

    fun wrong(msg: String) {
        error = msg
        pin = ""
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch { for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, androidx.compose.animation.core.tween(45)) }
    }

    fun confirm() {
        if (saving || pin.length < PIN_MIN) return
        val f = first
        when {
            f == null && isObviousPin(pin) -> wrong("Too easy to guess. Try a less obvious PIN.")
            f == null -> { first = pin; pin = ""; error = null }
            f != pin -> { first = null; wrong("The PINs didn't match. Start again.") }
            else -> {
                saving = true
                scope.launch { vm.chooseNewPin(pin) }
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(cs.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Lock, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(34.dp)) }
            Spacer(Modifier.height(18.dp))
            Text("Choose a new PIN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                error ?: if (first == null) "The app lock now needs at least $PIN_MIN digits, and your login is encrypted with it."
                else "Enter the same PIN again",
                style = MaterialTheme.typography.bodyMedium,
                color = if (error != null) cs.error else cs.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Box(Modifier.offset { IntOffset(shake.value.roundToInt(), 0) }) { PinDots(pin.length) }
            Spacer(Modifier.height(32.dp))
            PinPad(
                onDigit = { d ->
                    if (pin.length < PIN_MAX && !saving) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        error = null
                        pin += d
                    }
                },
                onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                onConfirm = ::confirm,
                confirmEnabled = pin.length >= PIN_MIN && !saving,
            )
        }
    }
}
