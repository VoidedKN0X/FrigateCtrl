package com.vknxworks.frigatectrl.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer

private fun Context.activity(): ComponentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    return null
}

/** True while the app is shown as a floating picture-in-picture window. */
@Composable
fun rememberIsInPip(): Boolean {
    val activity = LocalContext.current.activity()
    var inPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        activity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity?.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}

/** Whether the live view floats by itself when you leave the app (a camera is open with auto picture-in-picture on). */
object PipState {
    @Volatile var autoArmed = false
}

private fun pipParams(autoEnter: Boolean) = PictureInPictureParams.Builder()
    .setAspectRatio(Rational(16, 9))
    .setAutoEnterEnabled(autoEnter)
    .setSeamlessResizeEnabled(true)
    .build()

/**
 * While composed, lets the live view float as picture-in-picture: automatically when you leave the
 * app (if [autoEnter]), or on demand via the returned function.
 */
@Composable
fun rememberPipController(autoEnter: Boolean): () -> Unit {
    val activity: Activity? = LocalContext.current.activity()
    DisposableEffect(activity, autoEnter) {
        runCatching { activity?.setPictureInPictureParams(pipParams(autoEnter)) }
        PipState.autoArmed = autoEnter
        onDispose {
            PipState.autoArmed = false
            runCatching { activity?.setPictureInPictureParams(pipParams(false)) }
        }
    }
    return remember(activity) { { runCatching { activity?.enterPictureInPictureMode(pipParams(autoEnter)) } } }
}
