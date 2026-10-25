package com.vknxworks.frigatectrl.notify

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vknxworks.frigatectrl.App

/** A review item to open in its camera's History, from a tapped notification. */
data class ReviewLink(val camera: String, val time: Double)

/**
 * Notification taps waiting to be opened. They wait while FrigateCtrl is locked and open once it's unlocked.
 * MainActivity is exported (it's the launcher), so only intents that carry the secret every FrigateCtrl
 * notification has are accepted; anything else just opens the app as usual.
 */
object DeepLinks {
    var pending by mutableStateOf<ReviewLink?>(null)

    private val cameraName = Regex("[A-Za-z0-9_-]{1,64}")

    fun handle(context: Context, intent: Intent?) {
        // Other apps can start the main screen with anything in it: a broken intent just opens the app.
        runCatching { handleLink(context, intent) }
    }

    private fun handleLink(context: Context, intent: Intent?) {
        if (intent == null || intent.action != AlertNotifier.ACTION_OPEN_REVIEW) return
        App.init(context)
        val secret = intent.getStringExtra(AlertNotifier.EXTRA_SECRET)
        if (secret.isNullOrEmpty() || secret != App.notify.linkSecret) return
        val camera = intent.getStringExtra(AlertNotifier.EXTRA_CAMERA)?.takeIf { cameraName.matches(it) } ?: return
        val time = intent.getDoubleExtra(AlertNotifier.EXTRA_TIME, 0.0).takeIf { it > 0 && !it.isNaN() } ?: return
        intent.getStringExtra(AlertNotifier.EXTRA_REVIEW)?.let { AlertNotifier.cancel(context, it) }
        pending = ReviewLink(camera, time)
        // Handled: a rotation or a return from the recents screen mustn't open it again.
        intent.action = Intent.ACTION_MAIN
    }
}
