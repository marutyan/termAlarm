package com.marutyan.termalarm.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.data.WakeRecordRepository
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.domain.SessionRecord
import com.marutyan.termalarm.domain.wakeDurationMinutes
import com.marutyan.termalarm.domain.wakeOccurrence
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * 記録画面で表示対象とする集計期間の種別。
 * 今週(月曜〜日曜)と直近30日の切り替えに用いる。
 */
enum class RecordsPeriod {
    THIS_WEEK,
    LAST_30_DAYS,
}

/**
 * 週の棒グラフで1日分の描画状態を表すUIモデル。
 * 曜日名、棒の高さ比率(0..1)、表示数値、完了・今日・未来の状態種別を保持する。
 */
data class WeeklyBarUiModel(
    val dayOfWeekText: String,
    val valueText: String,
    val heightRatio: Float,
    val isDone: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean,
    val isWarningColor: Boolean,
)

/**
 * 1日ごとの実績一覧行を表すUIモデル。
 * 日付、起床回数、所要時間および強調色フラグを保持する。
 */
data class DailyRowUiModel(
    val dateText: String,
    val occurrenceText: String,
    val durationText: String,
    val isWarningOccurrence: Boolean,
    val isOngoing: Boolean,
)

/**
 * 記録画面全体の表示状態を保持するデータクラス。
 * 選択期間、3つの集計指標、週棒グラフデータ、日別一覧行をUIへ渡すために用いる。
 */
data class RecordsUiState(
    val selectedPeriod: RecordsPeriod = RecordsPeriod.THIS_WEEK,
    val averageOccurrence: Double? = null,
    val averageDurationMinutes: Double? = null,
    /**
     * 前の期間（今週なら先週、直近30日なら前の30日）との平均起床回数の差。
     * 継続による改善度合いを把握できるようにするために保持する。
     * 指標カードの3つ目に符号付き（減っていれば主役色、増えていれば警告色）で表示する。どちらかの期間に実績がない場合は null。
     */
    val averageOccurrenceDiff: Double? = null,
    val weekDateRangeText: String = "",
    val weeklyBars: List<WeeklyBarUiModel> = emptyList(),
    val dailyRows: List<DailyRowUiModel> = emptyList(),
    val isEmpty: Boolean = true,
)

/**
 * 記録画面のビジネスロジックと状態管理を担当するViewModel。
 * WakeRecordRepositoryからセッション記録を取得し、domainの集計関数を通じて指標やグラフデータを算出する。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModel(
    private val wakeRecordRepository: WakeRecordRepository,
    private val alarmRepository: AlarmRepository,
) : ViewModel() {

    private val selectedPeriod = MutableStateFlow(RecordsPeriod.THIS_WEEK)

    val uiState: StateFlow<RecordsUiState> = selectedPeriod
        .flatMapLatest { period ->
            val today = LocalDate.now()
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val sunday = monday.plusDays(6)

            val (from, to) = when (period) {
                RecordsPeriod.THIS_WEEK -> monday to sunday
                RecordsPeriod.LAST_30_DAYS -> today.minusDays(29) to today
            }

            // 比べる直前の同じ長さの期間（今週なら先週の月〜日、直近30日ならその前の30日間）
            val (previousFrom, previousTo) = when (period) {
                RecordsPeriod.THIS_WEEK -> monday.minusWeeks(1) to sunday.minusWeeks(1)
                RecordsPeriod.LAST_30_DAYS -> today.minusDays(59) to today.minusDays(30)
            }

            // 週のグラフ用セッション（今週分）と、現在期間のセッションを両方監視する
            val currentSessionsFlow = wakeRecordRepository.observeSessionsBetween(from, to)
            val weekSessionsFlow = if (period == RecordsPeriod.THIS_WEEK) {
                currentSessionsFlow
            } else {
                wakeRecordRepository.observeSessionsBetween(monday, sunday)
            }
            // 前の期間との平均起床回数の差を計算するために前の期間のセッションを読み出すFlow
            val previousSessionsFlow = wakeRecordRepository.observeSessionsBetween(previousFrom, previousTo)

            combine(
                currentSessionsFlow,
                weekSessionsFlow,
                previousSessionsFlow,
                alarmRepository.observeAll(),
            ) { sessions, weekSessions, previousSessions, schedules ->
                buildUiState(period, today, monday, sunday, sessions, weekSessions, previousSessions, schedules)
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            RecordsUiState(),
        )

    /**
     * 表示対象の集計期間（今週 / 直近30日）を切り替える。
     * 画面上部のタブ操作時に呼び出される。
     */
    fun selectPeriod(period: RecordsPeriod) {
        selectedPeriod.value = period
    }

    /**
     * 取得したセッションデータからUIStateを組み立てる。
     * domain集計関数を呼び出し、現在と過去の期間の指標差分、週棒グラフ、1日ごとの一覧行を整形する。
     */
    private fun buildUiState(
        period: RecordsPeriod,
        today: LocalDate,
        monday: LocalDate,
        sunday: LocalDate,
        sessions: List<SessionRecord>,
        weekSessions: List<SessionRecord>,
        previousSessions: List<SessionRecord>,
        schedules: List<AlarmSchedule>,
    ): RecordsUiState {
        // 指標の算出はdomain関数を使用
        val averages = wakeRecordRepository.calculateAverages(sessions)
        val previousAverages = wakeRecordRepository.calculateAverages(previousSessions)
        val currentAvgOccurrence = averages?.averageOccurrence
        val previousAvgOccurrence = previousAverages?.averageOccurrence

        /**
         * 現在の期間の平均起床回数と直前の同じ長さの期間の平均起床回数の差。
         * 継続による起床傾向の変化（改善または悪化）を把握するために計算する。
         * 3大指標カードの3つ目の値としてUIへ渡す。どちらかの期間に実績がない場合は null。
         */
        val averageOccurrenceDiff = if (currentAvgOccurrence != null && previousAvgOccurrence != null) {
            currentAvgOccurrence - previousAvgOccurrence
        } else {
            null
        }

        // 週の日付範囲テキスト ("09.07 – 09.13")
        val monthDayFormatter = DateTimeFormatter.ofPattern("MM.dd", Locale.getDefault())
        val weekDateRangeText = "${monday.format(monthDayFormatter)} – ${sunday.format(monthDayFormatter)}"

        // 週の棒グラフデータ作成（月曜から日曜の7本）
        val dayLabels = listOf("月", "火", "水", "木", "金", "土", "日")
        val weekSessionsByDate = weekSessions.associateBy { it.sessionStart }

        val weeklyBars = (0L..6L).map { dayOffset ->
            val date = monday.plusDays(dayOffset)
            val dayLabel = dayLabels[dayOffset.toInt()]
            val session = weekSessionsByDate[date]
            val occurrence = session?.let { wakeOccurrence(it) }

            when {
                date.isBefore(today) -> {
                    if (occurrence != null) {
                        WeeklyBarUiModel(
                            dayOfWeekText = dayLabel,
                            valueText = occurrence.toString(),
                            heightRatio = (occurrence / 12f).coerceIn(0.05f, 1f),
                            isDone = true,
                            isToday = false,
                            isFuture = false,
                            isWarningColor = occurrence >= 9,
                        )
                    } else {
                        // 記録なしの過去日
                        WeeklyBarUiModel(
                            dayOfWeekText = dayLabel,
                            valueText = "",
                            heightRatio = 0.02f,
                            isDone = false,
                            isToday = false,
                            isFuture = true,
                            isWarningColor = false,
                        )
                    }
                }
                date == today -> {
                    if (occurrence != null) {
                        WeeklyBarUiModel(
                            dayOfWeekText = dayLabel,
                            valueText = occurrence.toString(),
                            heightRatio = (occurrence / 12f).coerceIn(0.05f, 1f),
                            isDone = true,
                            isToday = true,
                            isFuture = false,
                            isWarningColor = occurrence >= 9,
                        )
                    } else {
                        // 今日でまだ実績がない（点線表示）
                        WeeklyBarUiModel(
                            dayOfWeekText = dayLabel,
                            valueText = "…",
                            heightRatio = 0.3f, // 26dp相当
                            isDone = false,
                            isToday = true,
                            isFuture = false,
                            isWarningColor = false,
                        )
                    }
                }
                else -> {
                    // 未来の日
                    WeeklyBarUiModel(
                        dayOfWeekText = dayLabel,
                        valueText = "",
                        heightRatio = 0.02f, // 2dp細線
                        isDone = false,
                        isToday = false,
                        isFuture = true,
                        isWarningColor = false,
                    )
                }
            }
        }

        // 1日ごとの一覧データ（日付降順）
        val dateFormatter = DateTimeFormatter.ofPattern("MM.dd E", Locale.JAPANESE)
        val dailyRows = sessions
            .sortedByDescending { it.sessionStart }
            .map { session ->
                val occ = wakeOccurrence(session)
                val dur = wakeDurationMinutes(session)

                val occurrenceText = if (occ != null) {
                    "${occ}回目"
                } else if (session.rings.isNotEmpty()) {
                    "放置"
                } else {
                    "—"
                }

                val durationText = if (dur != null) {
                    "${dur}分"
                } else {
                    "—"
                }

                DailyRowUiModel(
                    dateText = session.sessionStart.format(dateFormatter),
                    occurrenceText = occurrenceText,
                    durationText = durationText,
                    isWarningOccurrence = (occ ?: 0) >= 9,
                    isOngoing = false,
                )
            }

        return RecordsUiState(
            selectedPeriod = period,
            averageOccurrence = averages?.averageOccurrence,
            averageDurationMinutes = averages?.averageDurationMinutes,
            averageOccurrenceDiff = averageOccurrenceDiff,
            weekDateRangeText = weekDateRangeText,
            weeklyBars = weeklyBars,
            dailyRows = dailyRows,
            isEmpty = sessions.isEmpty(),
        )
    }
}

/**
 * RecordsViewModelを生成するファクトリクラス。
 * 必要なリポジトリの依存関係を渡してViewModelをインスタンス化する。
 */
class RecordsViewModelFactory(
    private val wakeRecordRepository: WakeRecordRepository,
    private val alarmRepository: AlarmRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        RecordsViewModel(wakeRecordRepository, alarmRepository) as T
}
