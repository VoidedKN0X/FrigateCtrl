package com.vknxworks.frigatectrl.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vknxworks.frigatectrl.App
import com.vknxworks.frigatectrl.MainActivity
import com.vknxworks.frigatectrl.R
import com.vknxworks.frigatectrl.data.ReviewItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Notification channels (one per severity) and the rich alert notification itself. */
object AlertNotifier {
    const val CH_ALERTS = "alerts"
    const val CH_DETECTIONS = "detections"

    /** Tap action that opens a camera's History at a review item (see [MainActivity]). */
    const val ACTION_OPEN_REVIEW = "com.vknxworks.frigatectrl.OPEN_REVIEW"
    const val EXTRA_CAMERA = "camera"
    const val EXTRA_TIME = "time"
    const val EXTRA_REVIEW = "review"
    const val EXTRA_SECRET = "secret"

    private const val GROUP = "frigatectrl.reviews"
    private const val SUMMARY_ID = 1

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { createChannels(context, nm) }
    }

    private fun createChannels(context: Context, nm: NotificationManager) {
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_ALERTS, context.getString(R.string.ch_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.ch_alerts_desc)
                },
                NotificationChannel(CH_DETECTIONS, context.getString(R.string.ch_detections), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.ch_detections_desc)
                },
            ),
        )
    }

    fun canPost(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Camera names come from the server: shown as Frigate shows them, on one short line. */
    fun cameraLabel(camera: String) =
        camera.replace('_', ' ').replace(Regex("[\\p{Cntrl}\\s]+"), " ").trim().take(60).replaceFirstChar { it.uppercase() }

    private fun labelsText(context: Context, r: ReviewItem): String =
        r.labels.take(6).joinToString(context.getString(R.string.list_separator)) { it.replace('_', ' ').take(30) }
            .replaceFirstChar { it.uppercase() }
            .ifBlank { context.getString(if (r.isAlert) R.string.nt_alert else R.string.nt_detection) }

    /** The picture for [r]: the tracked object's snapshot, else the review item's thumbnail. */
    private suspend fun fetchPicture(r: ReviewItem): Bitmap? {
        val urls = buildList {
            r.detections.firstOrNull()?.let { add(App.api.eventSnapshotUrl(it, 720)) }
            r.thumbPath?.let { add(App.api.url(it)) }
        }
        for (u in urls) {
            val bytes = App.api.bytes(u) ?: continue
            decode(bytes)?.let { return it }
        }
        return null
    }

    private suspend fun decode(bytes: ByteArray): Bitmap? = withContext(Dispatchers.Default) {
        runCatching {
            val b = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
            // Keep notifications small: Android drops very large pictures.
            if (b.width > 1024) Bitmap.createScaledBitmap(b, 1024, (b.height * 1024f / b.width).toInt().coerceAtLeast(1), true) else b
        }.getOrNull()
    }

    private fun openIntent(context: Context, r: ReviewItem, secret: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_REVIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CAMERA, r.camera)
            putExtra(EXTRA_TIME, r.start)
            putExtra(EXTRA_REVIEW, r.id)
            putExtra(EXTRA_SECRET, secret)
        }
        return PendingIntent.getActivity(
            context, r.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Posts one notification for [r], with its snapshot when the server has one. */
    suspend fun post(context: Context, r: ReviewItem, settings: NotifySettings, test: Boolean = false, picture: Bitmap? = null) {
        if (!canPost(context)) return
        val channel = if (r.isAlert) CH_ALERTS else CH_DETECTIONS
        val img = picture ?: if (test) null else fetchPicture(r)
        val title = context.getString(
            if (r.isAlert) R.string.nt_title_alert else R.string.nt_title_detection,
            labelsText(context, r), cameraLabel(r.camera),
        )
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date((r.start * 1000).toLong()))
        val text = if (test) context.getString(R.string.nt_test_text) else context.getString(R.string.nt_text, cameraLabel(r.camera), time)
        val color = ContextCompat.getColor(context, R.color.notification_accent)
        // On a locked phone only this version shows: no camera, label or picture.
        val redacted = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(color)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(if (r.isAlert) R.string.nt_public_alert else R.string.nt_public_detection))
            .build()
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(color)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(redacted)
            .setContentTitle(title)
            .setContentText(text)
            .setWhen((r.start * 1000).toLong())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setGroup(GROUP)
            .setCategory(if (r.isAlert) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_EVENT)
            .setPriority(if (r.isAlert) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(settings.inQuietHours())
            .setContentIntent(openIntent(context, r, settings.linkSecret))
        if (img != null) {
            b.setLargeIcon(img)
            b.setStyle(NotificationCompat.BigPictureStyle().bigPicture(img).bigLargeIcon(null as Bitmap?).setSummaryText(text))
        }
        try {
            NotificationManagerCompat.from(context).notify(r.id, r.id.hashCode(), b.build())
            postSummary(context, settings)
        } catch (_: Exception) {
            // Permission taken away meanwhile, or Android refused the notification (a picture too large to send).
        }
    }

    /** Groups several alerts into one stack in the shade. */
    private fun postSummary(context: Context, settings: NotifySettings) {
        val n = NotificationCompat.Builder(context, CH_DETECTIONS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(context.getString(R.string.app_name))
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setSilent(true)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(SUMMARY_ID, n)
        } catch (_: Exception) {
        }
    }

    /** Removes the notification of a review item (opened from it, or marked reviewed). */
    fun cancel(context: Context, reviewId: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(reviewId, reviewId.hashCode()) }
    }

    fun cancelAll(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancelAll() }
    }
}
