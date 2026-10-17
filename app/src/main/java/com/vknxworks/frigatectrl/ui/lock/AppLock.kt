package com.vknxworks.frigatectrl.ui.lock

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
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
import com.vknxworks.frigatectrl.ui.pressScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.math.roundToInt

const val PIN_MIN = 4
const val PIN_MAX = 8

/** Only class-3 ("strong") biometrics — weak face unlock can be fooled by a photo. */
fun biometricAvailable(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

/**
 * A Keystore key that can only be used right after a strong (class 3) biometric match. Unlocking
 * goes through it, so a hooked or faked "success" callback without a real match can't open the app.
 * It is separate from the key that encrypts the password and token.
 */
private object UnlockKey {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "frigatectrl_biometric_unlock_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun keyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun delete() {
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    private fun key(): SecretKey {
        (keyStore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                // 0 = every use needs its own biometric match; only class 3 biometrics count.
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                .setInvalidatedByBiometricEnrollment(true)
                .build(),
        )
        return generator.generateKey()
    }

    /** A cipher ready to be unlocked by the prompt, or null if the key was invalidated (new fingerprint added). */
    fun cipher(): Cipher? = try {
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
    } catch (e: KeyPermanentlyInvalidatedException) {
        delete()
        null
    } catch (e: Exception) {
        // E.g. no strong biometric enrolled any more, so the key can't be made.
        delete()
        null
    }
}

/** Forgets the biometric unlock key, so turning fingerprint unlock on again makes a fresh one. */
fun resetBiometricKey() = UnlockKey.delete()

/**
 * Shows a strong-biometric prompt bound to [UnlockKey]. [onSuccess] runs only if the key could
 * actually be used afterwards. [onInvalidated] runs when fingerprints changed since fingerprint
 * unlock was turned on, so the PIN is needed again.
 */
fun showBiometricPrompt(
    activity: FragmentActivity,
    title: String,
    onInvalidated: () -> Unit = {},
    onSuccess: () -> Unit,
) {
    val cipher = UnlockKey.cipher()
    if (cipher == null) {
        onInvalidated()
        return
    }
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val unlocked = result.cryptoObject?.cipher ?: return
                // Fails unless the Keystore saw a real strong-biometric match for this cipher.
                val ok = runCatching {
                    unlocked.doFinal(ByteArray(16).also { SecureRandom().nextBytes(it) })
                }.isSuccess
                if (ok) onSuccess()
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

/** Whether the app is locked. Survives rotation; a fresh process always starts locked. */
class AppLockViewModel : ViewModel() {
    val settings = App.lock

    var locked by mutableStateOf(settings.pinSet)
        private set
    var pinError by mutableStateOf<String?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var lockoutRemaining by mutableLongStateOf(0L)
        private set

    /** Set once the lock screen has shown the biometric prompt, so rotation doesn't show it twice. */
    var biometricShown = false

    /** Set when the biometric key was invalidated by a newly added fingerprint; the PIN is needed. */
    var biometricInvalidated by mutableStateOf(false)

    private var backgroundedAt = 0L

    init { refreshLockout() }

    fun onBackground() {
        backgroundedAt = SystemClock.elapsedRealtime()
        // "Immediately" locks before the app is hidden, so cameras aren't visible on return.
        if (settings.pinSet && settings.lockAfterSeconds == 0) lockNow()
    }

    fun onForeground() {
        val since = backgroundedAt
        backgroundedAt = 0L
        if (!settings.pinSet || locked || since == 0L) return
        if (SystemClock.elapsedRealtime() - since >= settings.lockAfterSeconds * 1000L) lockNow()
    }

    fun lockNow() {
        if (settings.pinSet && !locked) {
            locked = true
            biometricShown = false
        }
    }

    fun unlock() {
        settings.failedAttempts = 0
        pinError = null
        biometricInvalidated = false
        locked = false
    }

    fun submitPin(pin: String, onWrong: () -> Unit) {
        if (checking || lockoutRemaining > 0) return
        viewModelScope.launch {
            checking = true
            // PBKDF2 takes a moment; keep it off the main thread.
            val ok = withContext(Dispatchers.Default) { settings.checkPin(pin) }
            checking = false
            if (ok) { unlock(); return@launch }
            settings.recordWrongPin()
            refreshLockout()
            pinError = "Wrong PIN"
            onWrong()
        }
    }

    private fun refreshLockout() {
        lockoutRemaining = settings.lockoutRemainingMs()
        if (lockoutRemaining <= 0) return
        viewModelScope.launch {
            while (true) {
                val left = settings.lockoutRemainingMs()
                lockoutRemaining = left
                if (left <= 0) break
                delay(500)
            }
        }
    }
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
    val canBio = !vm.biometricInvalidated && vm.settings.biometricEnabled && activity != null && biometricAvailable(context)

    val askBiometric: () -> Unit = {
        if (canBio && activity != null) runCatching {
            showBiometricPrompt(
                activity,
                "Unlock FrigateCtrl",
                onInvalidated = {
                    // Fingerprints changed: ask for the PIN and turn fingerprint unlock off.
                    vm.settings.biometricEnabled = false
                    vm.biometricInvalidated = true
                },
            ) { vm.unlock() }
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
        if (pin.length == vm.settings.pinLength) vm.submitPin(pin, wrong)
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
                    vm.biometricInvalidated -> "Fingerprints changed. Enter your PIN, then turn fingerprint unlock on again in Settings."
                    vm.pinError != null -> vm.pinError!!
                    else -> "Enter your PIN"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (err) cs.error else cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Box(Modifier.offset { IntOffset(shake.value.roundToInt(), 0) }) { PinDots(pin.length) }
            Spacer(Modifier.height(32.dp))
            PinPad(
                onDigit = ::press,
                onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                onConfirm = { if (pin.length >= PIN_MIN) vm.submitPin(pin, wrong) },
                confirmEnabled = pin.length >= PIN_MIN && !vm.checking && vm.lockoutRemaining == 0L,
            )
            Spacer(Modifier.height(20.dp))
            if (canBio) {
                FilledTonalButton(onClick = askBiometric) {
                    Icon(Icons.Outlined.Fingerprint, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Use fingerprint")
                }
            }
            TextButton(onClick = onForgot) { Text("Forgot PIN? Log out") }
        }
    }
}

@Composable
fun PinDots(count: Int) {
    val cs = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(maxOf(count, PIN_MIN)) { i ->
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
