package com.marutyan.termalarm.ui.permission

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.marutyan.termalarm.R
import com.marutyan.termalarm.alarm.AlarmScheduler
import com.marutyan.termalarm.alarm.ExactAlarmPermission
import com.marutyan.termalarm.alarm.NotificationPermission
import kotlinx.coroutines.launch

/** 通知権限の設定状態を永続化するためのSharedPreferences名 */
private const val PERMISSION_PREFS_NAME = "permission_prefs"

/** 通知権限を一度でもユーザーに要求したかどうかを記録するキー */
private const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"

/**
 * 通知権限の要求ダイアログを過去に一度でも表示したかを確認する。
 * アプリ起動直後に不意に権限を求めないようにし、一度断られた後は設定画面への案内に切り替えるために用いる。
 */
fun isNotificationPermissionRequested(context: Context): Boolean =
    context.getSharedPreferences(PERMISSION_PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)

/**
 * 通知権限の要求ダイアログを表示したことを記録する。
 * ユーザーに勝手に繰り返し権限要求ダイアログを出さないようにするために用いる。
 */
fun setNotificationPermissionRequested(context: Context) {
    context.getSharedPreferences(PERMISSION_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true)
        .apply()
}

/**
 * 通知権限（POST_NOTIFICATIONS）が未許可の場合にホーム画面上部で案内するバナー。
 * アプリ起動直後に勝手に要求せず、バナーのボタン操作時に要求する。一度断られた後は設定画面へ案内する。
 */
@Composable
fun NotificationPermissionBanner() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(NotificationPermission.isGranted(context)) }
    var hasRequested by remember { mutableStateOf(isNotificationPermissionRequested(context)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        granted = isGranted
        hasRequested = true
        setNotificationPermissionRequested(context)
    }

    // 設定画面などから戻ってきたときに最新の許可状態を反映できるよう、画面が再開するたびに確認する
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = NotificationPermission.isGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!granted) {
        val actionLabel = if (!hasRequested) {
            stringResource(R.string.notification_permission_action_request)
        } else {
            stringResource(R.string.open_settings)
        }
        PermissionBanner(
            message = stringResource(R.string.notification_permission_banner),
            actionLabel = actionLabel,
            onAction = {
                if (!hasRequested && NotificationPermission.isRuntimeRequestRequired()) {
                    launcher.launch(NotificationPermission.PERMISSION)
                } else {
                    context.startActivity(appSettingsIntent(context.packageName))
                }
            },
        )
    }
}

/**
 * 正確なアラーム権限(SCHEDULE_EXACT_ALARM系)が許可されていない場合に設定画面へ誘導するバナー。
 * USE_EXACT_ALARM宣言済みでも端末設定で無効化され得るため、alarm.ExactAlarmPermission.isGranted()を都度確認する。
 * 未許可→許可への遷移(設定画面から戻ってきたタイミング)を検知したら、その間に予約できなかったアラームを
 * まとめて登録し直すためAlarmScheduler.rescheduleAll()を呼ぶ(docs/SPEC.md「権限」)。
 */
@Composable
fun ExactAlarmPermissionBanner() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(ExactAlarmPermission.isGranted(context)) }

    // 設定画面から戻ってきたときに再判定できるよう、画面が再開するたびに確認する
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowAllowed = ExactAlarmPermission.isGranted(context)
                if (!allowed && nowAllowed) {
                    // 未許可の間に保存されて予約できなかったアラームをまとめて登録し直す
                    coroutineScope.launch { AlarmScheduler.rescheduleAll(context) }
                }
                allowed = nowAllowed
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!allowed) {
        PermissionBanner(
            message = stringResource(R.string.exact_alarm_permission_banner),
            actionLabel = stringResource(R.string.open_settings),
            onAction = { context.startActivity(ExactAlarmPermission.settingsIntent(context)) },
        )
    }
}

private fun appSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())

@Composable
private fun PermissionBanner(message: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}
