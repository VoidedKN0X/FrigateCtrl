package com.vknxworks.frigatectrl.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vknxworks.frigatectrl.App
import java.util.concurrent.TimeUnit

/**
 * The background check for new alerts: every 15, 30 or 60 minutes (or more) while notifications are on, only
 * with a network connection, and once right away when a wake-up push from ntfy arrives.
 */
class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        App.init(applicationContext)
        var hint = inputData.getString(KEY_HINT)
        var outcome: AlertCheck.Outcome
        var rounds = 0
        // A push that arrived while this check was running asks for one more round, so nothing slips between them.
        do {
            AlertCheck.again = false
            outcome = AlertCheck.run(applicationContext, hint)
            hint = null
        } while (AlertCheck.again && outcome == AlertCheck.Outcome.Done && ++rounds < 3)
        // A push that couldn't reach the server gets one more try a little later; the periodic check just waits.
        return if (outcome == AlertCheck.Outcome.Failed && inputData.getBoolean(KEY_ONCE, false) && runAttemptCount < 3) Result.retry()
        else Result.success()
    }

    companion object {
        private const val PERIODIC = "alert-check"
        private const val ONCE = "alert-check-now"
        private const val KEY_HINT = "hint"
        private const val KEY_ONCE = "once"

        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Starts, changes or stops the periodic check to match the settings. */
        fun schedule(context: Context) {
            App.init(context)
            val s = App.notify
            val wm = WorkManager.getInstance(context)
            if (!s.enabled || !App.prefs.isConfigured) {
                wm.cancelUniqueWork(PERIODIC)
                wm.cancelUniqueWork(ONCE)
                return
            }
            val request = PeriodicWorkRequestBuilder<AlertWorker>(s.intervalMinutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(online)
                .build()
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** One check as soon as possible (after a push, or on unlock). */
        fun runNow(context: Context, hint: String? = null) {
            AlertCheck.again = true
            val data = if (hint != null) workDataOf(KEY_ONCE to true, KEY_HINT to hint) else workDataOf(KEY_ONCE to true)
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONCE,
                // Pushes in a burst (several cameras at once) fold into one check that sees them all.
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<AlertWorker>()
                    .setConstraints(online)
                    .setInputData(data)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build(),
            )
        }
    }
}
