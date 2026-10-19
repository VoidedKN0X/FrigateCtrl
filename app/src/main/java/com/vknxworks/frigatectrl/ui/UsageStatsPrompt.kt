package com.vknxworks.frigatectrl.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.vknxworks.frigatectrl.App

/** What's shared, in the words used by the first-run question and the Settings footer. */
const val USAGE_STATS_DESCRIPTION =
    "Which screens you open, whether you use live view, history, the timeline, PTZ and event playback, " +
        "the app version, Android version, phone model and language. Never your server address, username, " +
        "camera names, zones, what was detected, events or recording times. " +
        "Sent to VKNXWorks' own server, not to any analytics company."

/** Asked once, after connecting to a server. Nothing is sent until the user says yes; they can change it in Settings. */
@Composable
fun UsageStatsPrompt() {
    val t = App.telemetry
    // Settings are read from storage synchronously, so this is the stored answer, never a placeholder.
    if (!t.available || App.settings.usageStatsAsked) return
    AlertDialog(
        onDismissRequest = { },
        icon = { Icon(Icons.Outlined.Insights, null) },
        title = { Text("Help improve FrigateCtrl?") },
        text = {
            Text(
                "Share anonymous usage stats so I can see which features people use and which Android " +
                    "versions to support.\n\n$USAGE_STATS_DESCRIPTION\n\nYou can change this any time in Settings.",
            )
        },
        confirmButton = { TextButton(onClick = { t.setOptIn(true) }) { Text("Share stats") } },
        dismissButton = { TextButton(onClick = { t.setOptIn(false) }) { Text("No thanks") } },
    )
}
