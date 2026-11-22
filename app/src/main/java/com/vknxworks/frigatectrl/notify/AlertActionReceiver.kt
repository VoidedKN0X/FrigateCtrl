package com.vknxworks.frigatectrl.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.vknxworks.frigatectrl.App
import com.vknxworks.frigatectrl.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The two buttons on an alert notification: "Mark reviewed" (tells Frigate the review item was seen and
 * removes the notification) and "Snooze 1 h" (no notifications from that camera for an hour, on this phone
 * only; nothing changes on the server). Not exported: only FrigateCtrl's own notifications can reach it,
 * and every intent must also carry the notifications' secret.
 */
class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        runCatching { handle(context.applicationContext, intent) }
    }

    private fun handle(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != ACTION_REVIEWED && action != ACTION_SNOOZE) return
        App.init(context)
        val secret = intent.getStringExtra(AlertNotifier.EXTRA_SECRET)
        if (secret.isNullOrEmpty() || secret != App.notify.linkSecret) return
        val reviewId = intent.getStringExtra(AlertNotifier.EXTRA_REVIEW)?.takeIf { reviewIdOk(it) } ?: return
        when (action) {
            ACTION_SNOOZE -> {
                // FrigateCtrl's own app lock is on and locked: silencing a camera needs it unlocked too.
                if (App.vault.enabled && !App.vault.isOpen) {
                    toast(context, context.getString(R.string.nt_snooze_locked))
                    return
                }
                val camera = intent.getStringExtra(AlertNotifier.EXTRA_CAMERA)?.takeIf { cameraName.matches(it) } ?: return
                App.notify.snooze(camera, System.currentTimeMillis() + Snooze.DURATION_MS)
                AlertNotifier.cancel(context, reviewId)
                toast(context, context.getString(R.string.nt_snoozed, AlertNotifier.cameraLabel(camera)))
            }
            ACTION_REVIEWED -> {
                // The login is sealed with the app lock and FrigateCtrl is locked: Frigate can't be told yet.
                if (App.prefs.loginSealedAway) {
                    toast(context, context.getString(R.string.nt_reviewed_locked))
                    return
                }
                val pending = goAsync()
                scope.launch {
                    try {
                        val ok = withTimeoutOrNull(8_000) {
                            runCatching { App.api.setReviewed(listOf(reviewId), true) }.isSuccess
                        } ?: false
                        withContext(Dispatchers.Main) {
                            if (ok) AlertNotifier.cancel(context, reviewId)
                            else toast(context, context.getString(R.string.nt_reviewed_failed))
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private fun toast(context: Context, text: String) {
        runCatching { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        const val ACTION_REVIEWED = "com.vknxworks.frigatectrl.MARK_REVIEWED"
        const val ACTION_SNOOZE = "com.vknxworks.frigatectrl.SNOOZE_CAMERA"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val cameraName = Regex("[A-Za-z0-9_-]{1,64}")

        /** Review ids look like "1718000000.123456-abc123": only those characters are passed on to the server. */
        private fun reviewIdOk(id: String) = id.length <= 64 && id.all { c -> c.isLetterOrDigit() || c in ".-_" }

        fun intent(context: Context, action: String, camera: String, reviewId: String, secret: String): PendingIntent {
            val i = Intent(context, AlertActionReceiver::class.java).apply {
                this.action = action
                putExtra(AlertNotifier.EXTRA_CAMERA, camera)
                putExtra(AlertNotifier.EXTRA_REVIEW, reviewId)
                putExtra(AlertNotifier.EXTRA_SECRET, secret)
            }
            // One pending intent per review item and action, so the buttons of two notifications never mix.
            return PendingIntent.getBroadcast(
                context, (action + reviewId).hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
