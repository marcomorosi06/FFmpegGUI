package com.cuscus.ffmpeggui.export

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.cuscus.ffmpeggui.R

/**
 * Before every export: an export shows how far it is, and that it is done, in
 * a notification (AndroidExportNotifier). When notifications are not allowed
 * now, they are asked for first, every time, and the export starts once the
 * answer is given, whatever it is (an export never waits on them):
 *
 *  - Android 13 and later, the permission not refused for good: Android's
 *    own question (POST_NOTIFICATIONS).
 *  - Refused for good (Android no longer asks: after a second no, or "don't
 *    ask again"), turned off in Android's settings, or the export's channels
 *    turned off (or Android 12 and earlier, which have no question): a dialog
 *    of the app's own says why, and leads to Android's notification settings;
 *    back from there, the export starts. "Export without" starts it at once.
 *
 * Returns what starts an export: give it the export to start.
 */
@Composable
fun rememberNotificationsFirst(): (start: () -> Unit) -> Unit {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val prefs = remember { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var explaining by remember { mutableStateOf(false) }
    var inSettings by remember { mutableStateOf(false) }

    fun go() {
        val start = pending
        pending = null
        explaining = false
        start?.invoke()
    }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Asked once: a later "no" with no reason to show again means Android will not ask any more.
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        go()
    }
    // Back from Android's settings: the export goes on, notifications on or not.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (inSettings) {
            inSettings = false
            go()
        }
    }

    if (explaining) {
        AlertDialog(
            onDismissRequest = { go() },
            icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
            title = { Text(stringResource(R.string.export_notify_title)) },
            text = { Text(stringResource(R.string.export_notify_text)) },
            confirmButton = {
                TextButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    explaining = false
                    inSettings = openNotificationSettings(context)
                    if (!inSettings) go()
                }) { Text(stringResource(R.string.export_notify_settings)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    go()
                }) { Text(stringResource(R.string.export_notify_without)) }
            },
        )
    }

    return remember(context) {
        { start: () -> Unit ->
            when {
                // An export already waiting on the answer: this one is the same tap, again.
                pending != null -> Unit
                notificationsShown(context) -> start()
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionGranted(context) && canAsk(context, prefs.getBoolean(KEY_ASKED, false)) -> {
                    pending = start
                    ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    pending = start
                    explaining = true
                }
            }
            Unit
        }
    }
}

/** True when the export's notifications can show: the app's allowed, and neither of its channels turned off. */
private fun notificationsShown(context: Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    val manager = context.getSystemService(NotificationManager::class.java) ?: return true
    // A channel not made yet (no export so far) is not off.
    return listOf(AndroidExportNotifier.PROGRESS_CHANNEL, AndroidExportNotifier.RESULT_CHANNEL).none { id ->
        manager.getNotificationChannel(id)?.importance == NotificationManager.IMPORTANCE_NONE
    }
}

private fun permissionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

/**
 * Whether Android would still show its question: never asked yet, or refused
 * once (it then says there is a reason to show). Asked and no reason to show:
 * refused for good, only its settings can say yes.
 */
private fun canAsk(context: Context, asked: Boolean): Boolean {
    if (!asked) return true
    val activity = context.findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
}

/** Opens Android's notification settings of this app; false when it could not. */
private fun openNotificationSettings(context: Context): Boolean {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent) }.isSuccess
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val PREFS = "notifications_first"
private const val KEY_ASKED = "asked"
