package com.marutyan.termalarm.ui.intent

import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.marutyan.termalarm.MainActivity
import com.marutyan.termalarm.alarm.AlarmScheduler
import com.marutyan.termalarm.alarm.RingingService
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.ui.navigation.EXTRA_DEEPLINK_ALARM_ID
import java.time.DayOfWeek
import java.util.Calendar
import kotlinx.coroutines.launch

// start==endの単発アラームに必要なintervalMinutesの値。occurrenceCount()はspan==0のとき
// intervalMinutesの値に関わらず常に1回になるため、鳴動回数には影響しない(docs/SPEC.md「鳴動回数」)
private const val DEGENERATE_INTERVAL_MINUTES = 5

/**
 * 純正時計アプリを置き換えるための外部インテント(SET_ALARM/SHOW_ALARMS/DISMISS_ALARM/SNOOZE_ALARM)の受け口。
 * 画面を持たず、必要なときだけMainActivityへ委譲するか、鳴動中のRingingServiceへ操作を送って即finishする
 * (docs/SPEC.md「純正アプリを置き換えるために必要なもの」)。
 */
class AlarmIntentActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent?.action) {
            AlarmClock.ACTION_SET_ALARM -> handleSetAlarm(intent)
            AlarmClock.ACTION_SHOW_ALARMS -> openMain(deepLinkAlarmId = null)
            // SEARCH_MODEは扱わない。鳴っているアラームを止めるだけの単純な動作にする
            // (実装が重くなるため。docs/SPEC.mdでも許容されている簡略化)
            AlarmClock.ACTION_DISMISS_ALARM -> {
                startService(RingingService.stopIntent(this))
                finish()
            }
            AlarmClock.ACTION_SNOOZE_ALARM -> {
                // 分数を指定しないことで、鳴動中のアラームに設定されたsnoozeMinutesをRingingService側で解決させる
                // (nullの場合の扱いはRingingService.snoozeOrStopに一本化: docs/SPEC.md「スヌーズ（既定オフ）」)
                startService(Intent(this, RingingService::class.java).setAction(RingingService.ACTION_SNOOZE))
                finish()
            }
            else -> finish()
        }
    }

    // SET_ALARMを処理する。時刻の指定が無ければ編集画面を開く(純正と同じ挙動)。
    // 時刻があれば開始==終了の単発アラームとして保存し、EXTRA_SKIP_UIがtrueなら画面を出さず終了、
    // falseなら保存した内容を確認・編集できるよう編集画面を開く(docs/SPEC.md「SET_ALARMの扱い」)
    private fun handleSetAlarm(intent: Intent) {
        if (!intent.hasExtra(AlarmClock.EXTRA_HOUR)) {
            openMain(deepLinkAlarmId = NEW_ALARM_ID)
            return
        }
        val hour = intent.getIntExtra(AlarmClock.EXTRA_HOUR, 0)
        val minute = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0)
        val totalMinutes = hour * 60 + minute
        val schedule = AlarmSchedule(
            id = 0L,
            startMinutes = totalMinutes,
            endMinutes = totalMinutes,
            intervalMinutes = DEGENERATE_INTERVAL_MINUTES,
            repeatDays = daysExtraToDayOfWeekSet(intent),
            label = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE) ?: "",
            soundUri = null,
            vibrate = intent.getBooleanExtra(AlarmClock.EXTRA_VIBRATE, true),
            enabled = true,
            skippedSessionStart = null,
        )
        val skipUi = intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)
        lifecycleScope.launch {
            val repository = AlarmRepository(AlarmDatabase.getInstance(applicationContext).alarmDao())
            val savedId = repository.add(schedule)
            // 作成しただけでは鳴らない。必ず次回の予約を反映する(呼び忘れると永久に鳴らない不具合になる)
            AlarmScheduler.reschedule(applicationContext, savedId)
            if (skipUi) finish() else openMain(deepLinkAlarmId = savedId)
        }
    }

    // MainActivityを起動してこの透明なActivityを終える。deepLinkAlarmIdがnullなら通常起動(一覧)、
    // NEW_ALARM_IDなら新規作成画面、0以上ならそのidの編集画面へ直接遷移する(TermAlarmNavHost側で解釈する)
    private fun openMain(deepLinkAlarmId: Long?) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                if (deepLinkAlarmId != null) putExtra(EXTRA_DEEPLINK_ALARM_ID, deepLinkAlarmId)
            },
        )
        finish()
    }

    companion object {
        // TermAlarmNavHostのROUTE_EDIT(idなし)と同じ「新規作成」を表す値
        private const val NEW_ALARM_ID = -1L
    }
}

// SET_ALARMのEXTRA_DAYSはjava.util.Calendarの曜日定数(日曜=1)の配列で渡されるため、
// domainのDayOfWeek(月曜=1〜日曜=7)へ変換する
private fun daysExtraToDayOfWeekSet(intent: Intent): Set<DayOfWeek> {
    val calendarDays = intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS) ?: return emptySet()
    return calendarDays.mapNotNull(::calendarDayToDayOfWeek).toSet()
}

private fun calendarDayToDayOfWeek(calendarDay: Int): DayOfWeek? = when (calendarDay) {
    Calendar.MONDAY -> DayOfWeek.MONDAY
    Calendar.TUESDAY -> DayOfWeek.TUESDAY
    Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
    Calendar.THURSDAY -> DayOfWeek.THURSDAY
    Calendar.FRIDAY -> DayOfWeek.FRIDAY
    Calendar.SATURDAY -> DayOfWeek.SATURDAY
    Calendar.SUNDAY -> DayOfWeek.SUNDAY
    else -> null
}
