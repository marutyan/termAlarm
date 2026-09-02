package com.marutyan.termalarm.alarm

import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marutyan.termalarm.R
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.domain.nextTrigger
import com.marutyan.termalarm.domain.remainingOccurrenceCount
import com.marutyan.termalarm.ui.theme.TermAlarmTheme
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 鳴動画面。design/Ringing.dc.htmlの見た目に合わせ、色はすべてMaterialTheme.colorSchemeから取る。
 * 全画面インテント経由でロック画面の上に起動されるため、setShowWhenLocked等でロック解除なしの表示を行う。
 * ボタン操作はRingingServiceへIntentで送るだけで、状態遷移（次回予約など）の実体はサービス側に置く。
 */
class RingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLockScreenDisplay()

        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        val triggerAtMillis = intent.getLongExtra(EXTRA_TRIGGER_AT_MILLIS, System.currentTimeMillis())

        setContent {
            TermAlarmTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RingingScreen(
                        alarmId = alarmId,
                        triggerAtMillis = triggerAtMillis,
                        onFinish = { finish() },
                    )
                }
            }
        }
    }

    // ロック画面の上に鳴動画面を表示するためのウィンドウ設定。
    // setShowWhenLocked/setTurnScreenOnはAPI27(O_MR1)以降のみ存在するため、
    // minSdk26の端末向けだけに旧来のWindowManager.LayoutParamsフラグへフォールバックする
    private fun setupLockScreenDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        // RingingServiceが全画面通知に載せる、このActivityを開くIntentを組み立てる
        private fun launchIntent(context: Context, alarmId: Long, triggerAtMillis: Long): Intent =
            Intent(context, RingingActivity::class.java).apply {
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_TRIGGER_AT_MILLIS, triggerAtMillis)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            }

        // 通知のfullScreenIntent/contentIntentに使うPendingIntent。idごとに一意にする
        fun fullScreenPendingIntent(context: Context, alarmId: Long, triggerAtMillis: Long): PendingIntent =
            PendingIntent.getActivity(
                context,
                alarmId.toInt(),
                launchIntent(context, alarmId, triggerAtMillis),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}

@Composable
private fun RingingScreen(alarmId: Long, triggerAtMillis: Long, onFinish: () -> Unit) {
    val context = LocalContext.current

    // 鳴動中のoccurrenceの実時刻。予約時に意図していた時刻を使うことで、サービス起動の遅延に影響されない
    val occurrenceAt = remember(triggerAtMillis) {
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(triggerAtMillis), ZoneId.systemDefault())
    }

    var schedule by remember { mutableStateOf<AlarmSchedule?>(null) }
    LaunchedEffect(alarmId) {
        schedule = AlarmRepository(AlarmDatabase.getInstance(context).alarmDao()).getById(alarmId)
    }

    // 画面上部に表示する現在時刻。1秒ごとに更新する
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(1000)
        }
    }

    // サービス側の無操作タイムアウトと同じ時間で画面も閉じる（サービス自体の停止・次回予約はサービス側が行う）
    LaunchedEffect(Unit) {
        delay(RINGING_AUTO_STOP_TIMEOUT_MILLIS)
        onFinish()
    }

    val currentSchedule = schedule ?: return

    RingingContent(
        now = now,
        label = currentSchedule.label,
        remainingCount = remainingOccurrenceCount(currentSchedule, occurrenceAt),
        nextTriggerTime = nextTrigger(currentSchedule, occurrenceAt),
        snoozeMinutes = currentSchedule.snoozeMinutes,
        skipRequiresApp = currentSchedule.skipRequiresApp,
        onStop = {
            context.startService(RingingService.stopIntent(context))
            onFinish()
        },
        onSnooze = { minutes ->
            context.startService(RingingService.snoozeIntent(context, minutes))
            onFinish()
        },
        onSkipToday = {
            context.startService(RingingService.skipIntent(context))
            onFinish()
        },
    )
}

@Composable
private fun RingingContent(
    now: ZonedDateTime,
    label: String,
    remainingCount: Int,
    nextTriggerTime: ZonedDateTime?,
    snoozeMinutes: Int?,
    skipRequiresApp: Boolean,
    onStop: () -> Unit,
    onSnooze: (Int) -> Unit,
    onSkipToday: () -> Unit,
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("H:mm") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPANESE) }

    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 48.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AlarmGlyph(tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = now.format(timeFormatter),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Medium,
                    // 数字の桁が変わるたびに幅が動いてちらつかないよう等幅数字を使う（docs/SPEC.md「フォント」）
                    style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                )
                Text(text = now.format(dateFormatter), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp)
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(20.dp)) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.ringing_remaining_count, remainingCount),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            if (nextTriggerTime != null) {
                Text(
                    text = stringResource(R.string.ringing_next_time, nextTriggerTime.format(timeFormatter)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StopButton(onClick = onStop)
            if (snoozeMinutes != null) {
                SnoozeButton(minutes = snoozeMinutes, onClick = { onSnooze(snoozeMinutes) })
            }
            if (!skipRequiresApp) {
                SkipTodayRow(onClick = onSkipToday)
            } else {
                // 寝ぼけたまま押せてしまう事故を防ぐため、当日終了はボタンにせず案内文だけを出す
                // （docs/SPEC.md「誤操作の防止と当日終了」）
                Text(
                    text = stringResource(R.string.ringing_skip_requires_app_notice),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StopButton(onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(48.dp),
        modifier = Modifier.fillMaxWidth().height(96.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.ringing_stop),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SnoozeButton(minutes: Int, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = "${stringResource(R.string.ringing_snooze)}（${minutes}分）",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SkipTodayRow(onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CancelGlyph(tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(R.string.ringing_skip_today),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// 上部の丸いアイコン内に描く簡易な時計の図柄(文字盤の輪+2本の針)。design/Ringing.dc.htmlのSVGを模す
@Composable
private fun AlarmGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.1f
        drawCircle(color = tint, radius = size.minDimension / 2 - strokeWidth, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        val center = this.center
        drawLine(
            color = tint,
            start = center,
            end = Offset(center.x, center.y - size.minDimension * 0.28f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = center,
            end = Offset(center.x + size.minDimension * 0.22f, center.y + size.minDimension * 0.06f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

// 「今日はもう止める」の丸に斜線の図柄。design/Ringing.dc.htmlのSVGを模す
@Composable
private fun CancelGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.12f
        drawCircle(color = tint, radius = size.minDimension / 2 - strokeWidth, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        drawLine(
            color = tint,
            start = Offset(size.width * 0.22f, size.height * 0.78f),
            end = Offset(size.width * 0.78f, size.height * 0.22f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
