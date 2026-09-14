package com.marutyan.termalarm.alarm

import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marutyan.termalarm.MainActivity
import com.marutyan.termalarm.R
import com.marutyan.termalarm.data.Repositories
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.domain.AppSettings
import com.marutyan.termalarm.domain.challengeQuestionCount
import com.marutyan.termalarm.domain.nextTrigger
import com.marutyan.termalarm.domain.occurrenceCount
import com.marutyan.termalarm.domain.remainingOccurrenceCount
import com.marutyan.termalarm.ui.common.clockTimePattern
import com.marutyan.termalarm.ui.common.formatClockMinutes
import com.marutyan.termalarm.ui.navigation.EXTRA_DEEPLINK_END_TERM_ID
import com.marutyan.termalarm.ui.skipgame.SkipGameScreen
import com.marutyan.termalarm.ui.skipgame.SkipGameViewModel
import com.marutyan.termalarm.ui.skipgame.SkipGameViewModelFactory
import com.marutyan.termalarm.ui.skipgame.hasShakeSensor
import com.marutyan.termalarm.ui.theme.TermAlarmTheme
import com.marutyan.termalarm.ui.theme.customColors
import com.marutyan.termalarm.ui.theme.ibmPlexMonoFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 鳴動画面Activity。design/Ringing.dc.htmlの設計に基づき、鳴動情報表示と停止・終了操作を提供する。
 * ロック画面の上への全画面インテント表示を維持し、ストップ時はサービスへ停止通知、ターム終了時はMainActivityへ誘導する。
 */
class RingingActivity : ComponentActivity() {

    // 鳴動セッション開始時に一度だけ読み込む設定値。鳴動中に設定画面から値が変わることは想定しないため起動時の1回読みとする
    private var settings by mutableStateOf(AppSettings())

    /**
     * 通知の操作ボタンからアラームを止めたときに、この画面を閉じるための受け口。
     * 画面上のボタンで止めた場合は自分でfinish()するため、この経路は通知側からの操作だけに使う。
     */
    private val ringingFinishedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLockScreenDisplay()

        ContextCompat.registerReceiver(
            this,
            ringingFinishedReceiver,
            IntentFilter(RingingService.ACTION_RINGING_FINISHED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        val triggerAtMillis = intent.getLongExtra(EXTRA_TRIGGER_AT_MILLIS, System.currentTimeMillis())

        lifecycleScope.launch {
            settings = Repositories.settings(this@RingingActivity)
                .observe().first()
        }

        setContent {
            // 鳴動画面も設定で選んだ配色に合わせる
            TermAlarmTheme(appTheme = settings.theme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RingingScreen(
                        alarmId = alarmId,
                        triggerAtMillis = triggerAtMillis,
                        silenceAfterMinutes = settings.silenceAfterMinutes,
                        onFinish = { finish() },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(ringingFinishedReceiver)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return super.onKeyDown(keyCode, event)
    }

    // ロック画面の上に鳴動画面を表示するためのウィンドウ設定。
    // setShowWhenLocked/setTurnScreenOnはAPI27(O_MR1)以降のみ存在するため、minSdk26端末向けに旧来フラグへフォールバックする
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

/**
 * 鳴動画面のメインコンポーネント。
 * design/Ringing.dc.htmlの設計に基づき、9個の要素を上から順に配置する。
 * 解除チャレンジ出題時はミニゲーム画面を表示し、「タームを終了」選択時はMainActivityへ遷移して確認画面を開く。
 */
@Composable
private fun RingingScreen(
    alarmId: Long,
    triggerAtMillis: Long,
    silenceAfterMinutes: Int?,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    var schedule by remember { mutableStateOf<AlarmSchedule?>(null) }
    var showGame by remember { mutableStateOf(false) }

    // 鳴動中のoccurrenceの実時刻。予約時に意図していた時刻を使うことで、サービス起動の遅延に影響されない
    val occurrenceAt = remember(triggerAtMillis) {
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(triggerAtMillis), ZoneId.systemDefault())
    }

    LaunchedEffect(alarmId) {
        schedule = Repositories.alarm(context).getById(alarmId)
    }

    // 設定「消音までの時間」と同じ時間で画面を自動終了する
    if (silenceAfterMinutes != null) {
        LaunchedEffect(silenceAfterMinutes) {
            delay(silenceAfterMinutes * 60_000L)
            onFinish()
        }
    }

    val currentSchedule = schedule ?: return

    val totalCount = remember(currentSchedule) { occurrenceCount(currentSchedule) }
    val remainingCount = remember(currentSchedule, occurrenceAt) {
        remainingOccurrenceCount(currentSchedule, occurrenceAt)
    }
    val currentOccurrence = (totalCount - remainingCount).coerceAtLeast(1)
    val currentOccurrenceIndex = (currentOccurrence - 1).coerceAtLeast(0)

    val hasChallenge = remember(currentSchedule, currentOccurrenceIndex) {
        challengeQuestionCount(currentSchedule, currentOccurrenceIndex) > 0
    }

    if (showGame) {
        val repository = remember { Repositories.alarm(context) }
        val hasShake = remember { hasShakeSensor(context) }
        val gameViewModel: SkipGameViewModel = viewModel(
            factory = SkipGameViewModelFactory(
                repository = repository,
                context = context,
                alarmId = alarmId,
                hasShakeSensor = hasShake,
                occurrenceIndex = currentOccurrenceIndex,
            ),
        )
        SkipGameScreen(
            viewModel = gameViewModel,
            onClose = {
                context.startService(RingingService.stopIntent(context, com.marutyan.termalarm.domain.StopMethod.CHALLENGE))
                onFinish()
            },
        )
    } else {
        RingingContent(
            schedule = currentSchedule,
            occurrenceAt = occurrenceAt,
            totalCount = totalCount,
            remainingCount = remainingCount,
            currentOccurrence = currentOccurrence,
            currentOccurrenceIndex = currentOccurrenceIndex,
            onStop = {
                if (hasChallenge) {
                    showGame = true
                } else {
                    context.startService(RingingService.stopIntent(context, com.marutyan.termalarm.domain.StopMethod.TAP))
                    onFinish()
                }
            },
            onEndTerm = {
                // 鳴動音を停止して次回を予約
                context.startService(RingingService.stopIntent(context, com.marutyan.termalarm.domain.StopMethod.TAP))
                // アプリを開いて確認画面（TermEndDialog）を出す
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_DEEPLINK_END_TERM_ID, alarmId)
                }
                context.startActivity(mainIntent)
                onFinish()
            },
        )
    }
}

/**
 * 鳴動画面の表示内容を描画するComposable。
 * 9つの要素を上から順に配置し、200%の文字拡大時にもスクロール可能なレイアウトを提供する。
 */
@Composable
internal fun RingingContent(
    schedule: AlarmSchedule,
    occurrenceAt: ZonedDateTime,
    totalCount: Int,
    remainingCount: Int,
    currentOccurrence: Int,
    currentOccurrenceIndex: Int,
    onStop: () -> Unit,
    onEndTerm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val timePattern = remember { clockTimePattern(false) }
    val timeFormatter = remember(timePattern) { DateTimeFormatter.ofPattern(timePattern, Locale.getDefault()) }

    // 1. 「4回目 / 25回」のラベルテキスト (10sp, 字間0.16em, 主役色)
    val occurrenceLabelText = stringResource(
        R.string.ringing_occurrence_label,
        currentOccurrence,
        totalCount,
    )

    // 2. 鳴っている時刻テキスト (86sp, 太さ200, 等幅数字)
    val occurrenceTimeString = remember(occurrenceAt, timeFormatter) {
        occurrenceAt.format(timeFormatter)
    }

    // 3. 範囲と間隔テキスト (13sp, 薄い文字)
    val startTimeText = remember(schedule.startMinutes, timePattern) {
        formatClockMinutes(schedule.startMinutes, timePattern)
    }
    val endTimeText = remember(schedule.endMinutes, timePattern) {
        formatClockMinutes(schedule.endMinutes, timePattern)
    }
    val intervalText = if (schedule.startIntervalMinutes == schedule.endIntervalMinutes) {
        stringResource(R.string.ringing_interval_constant, schedule.startIntervalMinutes)
    } else {
        stringResource(
            R.string.term_edit_interval_accelerate_summary,
            schedule.startIntervalMinutes,
            schedule.endIntervalMinutes,
        )
    }
    val rangeAndIntervalText = stringResource(
        R.string.ringing_range_and_interval,
        startTimeText,
        endTimeText,
        intervalText,
    )

    // 4. 音量レベル (0..5)
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val currentVolume = remember(audioManager) { audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: 3 }
    val maxVolume = remember(audioManager) { audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 5 }
    val volumeLevel = remember(currentVolume, maxVolume) {
        if (maxVolume > 0) ((currentVolume.toFloat() / maxVolume) * 5).roundToInt().coerceIn(1, 5) else 3
    }

    // 7. 「9:00まで 残り21回」テキスト (12.5sp)
    val remainingText = if (remainingCount > 0) {
        stringResource(R.string.ringing_remaining_until_end, endTimeText, remainingCount)
    } else {
        stringResource(R.string.ringing_remaining_last, endTimeText)
    }

    // 8. 「ストップ」カードの「次は 7:20」テキスト
    val nextTriggerTime = remember(schedule, occurrenceAt) { nextTrigger(schedule, occurrenceAt) }
    val nextOccurrenceText = if (nextTriggerTime != null) {
        stringResource(R.string.ringing_next_occurrence, nextTriggerTime.format(timeFormatter))
    } else {
        stringResource(R.string.ringing_next_none)
    }

    // ステータスバーの下端から余白62dpを確保
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(
                    top = statusBarTop + 62.dp,
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 30.dp,
                ),
        ) {
            // 1〜4: 上部表示（左: 回数ラベル、時刻、範囲間隔 / 右: 音量目盛）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    // 1. 「4回目 / 25回」のラベル。11sp、字間0.16em、主役の色
                    Text(
                        text = occurrenceLabelText,
                        style = TextStyle(
                            fontFamily = ibmPlexMonoFontFamily(400),
                            fontSize = 11.sp,
                            letterSpacing = 0.16.em,
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )

                    // 2. 鳴っている時刻。86sp、太さ200、等幅数字
                    Text(
                        text = occurrenceTimeString,
                        style = TextStyle(
                            fontFamily = ibmPlexMonoFontFamily(200),
                            fontWeight = FontWeight.W200,
                            fontSize = 86.sp,
                            lineHeight = 76.sp,
                            letterSpacing = (-0.055).em,
                            fontFeatureSettings = "tnum",
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    )

                    // 3. 範囲と間隔。13sp、薄い文字の色
                    Text(
                        text = rangeAndIntervalText,
                        style = TextStyle(
                            fontFamily = ibmPlexMonoFontFamily(400),
                            fontSize = 13.sp,
                            fontFeatureSettings = "tnum",
                            color = MaterialTheme.customColors.subtleText,
                        ),
                    )
                }

                // 4. 右上に音量の目盛。5本の縦棒で、いまの音量を示す
                VolumeIndicator(volumeLevel = volumeLevel)
            }

            // 5. 18dp空ける
            Spacer(modifier = Modifier.height(18.dp))

            // 6. 鳴動の目盛。高さ12dp
            OccurrenceScaleBar(
                totalCount = totalCount,
                currentIndex = currentOccurrenceIndex,
            )

            // 7. 「9:00まで 残り21回」。12.5sp
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = remainingText,
                style = TextStyle(
                    fontSize = 12.5.sp,
                    color = MaterialTheme.customColors.subtleText,
                ),
            )

            // 中央のスペーサー（文字拡大時にも最小32dpの間隔を保ち、通常時はボタン群を画面下端へ押し出す）
            Spacer(modifier = Modifier.height(32.dp))
            Spacer(modifier = Modifier.weight(1f))

            // 8 & 9: 下部カード群
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 8. 下部に「ストップ」。主役の色で塗ったカード。左にアイコン、「次は 7:20」を添える
                RingingStopCard(
                    nextOccurrenceText = nextOccurrenceText,
                    onClick = onStop,
                )

                // 9. その下に「タームを終了」。枠線だけのカード。右に「>」
                RingingEndTermCard(
                    onClick = onEndTerm,
                )
            }
        }
    }
}
