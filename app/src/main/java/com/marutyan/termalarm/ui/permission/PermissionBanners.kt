package com.marutyan.termalarm.ui.permission

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
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

/**
 * 通知権限(POST_NOTIFICATIONS)の実行時権限を初回起動時に要求する。API33未満では権限自体が無いため常に許可扱い。
 * 拒否されている間はアラーム通知が出せない旨をアラーム一覧の上に案内する(docs/SPEC.md「権限」)。
 * 判定自体はalarm.NotificationPermission(担当C実装)を使い、UI固有のダイアログ要求とバナー表示だけをここで行う。
 */
@Composable
fun NotificationPermissionBanner() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(NotificationPermission.isGranted(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted -> granted = isGranted }

    // 初回表示時に1度だけ要求する。既に許可済み・拒否済み(表示不可)の場合はOSが即座に結果を返すため実質no-op
    LaunchedEffect(Unit) {
        if (!granted && NotificationPermission.isRuntimeRequestRequired()) {
            launcher.launch(NotificationPermission.PERMISSION)
        }
    }

    if (!granted) {
        PermissionBanner(
            message = stringResource(R.string.notification_permission_banner),
            actionLabel = stringResource(R.string.open_settings),
            onAction = { context.startActivity(appSettingsIntent(context.packageName)) },
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
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

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
