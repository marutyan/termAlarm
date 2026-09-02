package com.marutyan.termalarm.ui.alarmlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import java.time.Duration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.domain.RemainingTime
import com.marutyan.termalarm.domain.occurrenceCount
import com.marutyan.termalarm.domain.canEndTodaySession
import java.time.ZonedDateTime
import com.marutyan.termalarm.domain.remainingTimeUntilNextTrigger
import com.marutyan.termalarm.domain.scheduleSummary
import com.marutyan.termalarm.ui.common.formatClockMinutes
import java.time.DayOfWeek

/**
 * 1分ごとに更新される現在時刻を返す。
 * 残り時間や当日終了の可否は時刻とともに変わるため、画面を触らなくても表示が追従するようにする。
 * 秒までは表示しないので、次の分の頭に合わせて起こすことで無駄な再計算を避ける。
 */
@Composable
private fun rememberCurrentMinute(): ZonedDateTime {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val next = now.plusMinutes(1).withSecond(0).withNano(0)
            delay(maxOf(1_000L, Duration.between(ZonedDateTime.now(), next).toMillis()))
            now = ZonedDateTime.now()
        }
    }
    return now
}

/** 下部ナビの4タブ。「アラーム」以外は空画面(docs/SPEC.md「画面範囲」)。 */
enum class TermAlarmTab { ALARM, CLOCK, TIMER, STOPWATCH }

/**
 * アラーム一覧画面。design/Main.dc.htmlを再現する。
 * 上部の権限バナー、アラームカードの一覧、追加用FAB、下部ナビをまとめて持つ。
 * bottomBarとfabは呼び出し側(NavHost)がタブ間で共通のScaffoldを持てるよう引数で受け取る。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlarmListScreen(
    viewModel: AlarmListViewModel,
    onAddAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    onOpenAbout: () -> Unit,
    onNavigateToSkipGame: (Long) -> Unit,
    exactAlarmBanner: @Composable () -> Unit,
    notificationPermissionBanner: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    // 残り時間と当日終了の可否は時刻で変わるため、1分ごとに更新される現在時刻を使う
    val now = rememberCurrentMinute()
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    // 「今日はもう止める」の確認ダイアログ対象。skipGame=trueのアラームはダイアログを出さずSkipGame画面へ遷移させる
    var pendingSkipTarget by remember { mutableStateOf<AlarmSchedule?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.alarm_list_title), style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu_more))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_about_license)) },
                                onClick = { menuExpanded = false; onOpenAbout() },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddAlarm) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_alarm))
            }
        },
        bottomBar = bottomBar,
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            notificationPermissionBanner()
            exactAlarmBanner()
            if (alarms.isEmpty()) {
                EmptyAlarmList(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(alarms, key = { it.id }) { schedule ->
                        AlarmCard(
                            schedule = schedule,
                            now = now,
                            onToggleEnabled = { enabled -> viewModel.setEnabled(schedule.id, enabled) },
                            onClick = { onEditAlarm(schedule.id) },
                            onRequestEndTodaySession = {
                                // skipGameがtrueならその場でゲーム画面へ遷移し、falseなら確認ダイアログを出す
                                if (schedule.skipGame) onNavigateToSkipGame(schedule.id) else pendingSkipTarget = schedule
                            },
                        )
                    }
                }
            }
        }
    }

    // skipGame=falseのアラームだけがここに来る(skipGame=trueはクリック時点で直接SkipGame画面へ遷移済み)
    pendingSkipTarget?.let { target ->
        EndTodaySessionDialog(
            schedule = target,
            onConfirm = {
                viewModel.endTodaySession(target.id)
                pendingSkipTarget = null
            },
            onDismiss = { pendingSkipTarget = null },
        )
    }
}

// 一覧が空のときの案内表示
@Composable
private fun EmptyAlarmList(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.alarm_list_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * アラーム1件のカード。design/Main.dc.html / AlarmListDark.dc.htmlのカードを再現する。
 * 開始と終了が同じ場合は単発として時刻を1つだけ表示する(SPEC「開始と終了が同じ場合」)。
 */
@Composable
private fun AlarmCard(
    schedule: AlarmSchedule,
    now: ZonedDateTime,
    onToggleEnabled: (Boolean) -> Unit,
    onClick: () -> Unit,
    onRequestEndTodaySession: () -> Unit,
) {
    val isSingle = schedule.startMinutes == schedule.endMinutes

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isSingle) {
                    Text(
                        text = formatClockMinutes(schedule.startMinutes),
                        style = MaterialTheme.typography.displaySmall,
                    )
                } else {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = formatClockMinutes(schedule.startMinutes), style = MaterialTheme.typography.displaySmall)
                        Text(text = "–", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = formatClockMinutes(schedule.endMinutes), style = MaterialTheme.typography.displaySmall)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_clock),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = scheduleSummary(schedule),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 無効なアラームには次回予定が無いため出さない(remainingTimeUntilNextTriggerがnullを返す)
                if (schedule.enabled) {
                    remainingTimeUntilNextTrigger(schedule, now)?.let { remaining ->
                        Text(
                            text = remainingTimeText(remaining),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Switch(checked = schedule.enabled, onCheckedChange = onToggleEnabled)
        }

        if (schedule.repeatDays.isNotEmpty()) {
            // 固定間隔で並べると7つが左へ寄って右に余白ができるため、幅いっぱいに均等配置する
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DayOfWeek.entries.forEach { day ->
                    val on = day in schedule.repeatDays
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = dayLabel(day),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 押しても何も起きない状態で導線を出すと、アラーム自体を無効にするトグルとの
        // 違いが伝わらない。今日これから鳴る回が残っているときだけ出す
        if (canEndTodaySession(schedule, now)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRequestEndTodaySession) {
                    Text(stringResource(R.string.ringing_skip_today))
                }
            }
        }
    }
}

// domainのRemainingTime(数値のみ)をstrings.xmlの文言へ変換する。粒度ごとに文字列リソースを切り替える
@Composable
private fun remainingTimeText(remaining: RemainingTime): String = when (remaining) {
    is RemainingTime.LessThanOneMinute -> stringResource(R.string.remaining_time_less_than_minute)
    is RemainingTime.Minutes -> stringResource(R.string.remaining_time_minutes, remaining.minutes)
    is RemainingTime.HoursAndMinutes ->
        stringResource(R.string.remaining_time_hours_minutes, remaining.hours, remaining.minutes)
    is RemainingTime.Days -> stringResource(R.string.remaining_time_days, remaining.days)
}

// 曜日1文字ラベル(月火水木金土日)。DayOfWeekの並び(MONDAY始まり)がそのままモックの表示順と一致する
private fun dayLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "月"
    DayOfWeek.TUESDAY -> "火"
    DayOfWeek.WEDNESDAY -> "水"
    DayOfWeek.THURSDAY -> "木"
    DayOfWeek.FRIDAY -> "金"
    DayOfWeek.SATURDAY -> "土"
    DayOfWeek.SUNDAY -> "日"
}

/**
 * 「今日はもう止める」の確認ダイアログ。skipGame=falseのアラーム専用で、
 * skipGame=trueの場合は呼び出し元(AlarmListScreen)がこのダイアログを出さずSkipGame画面へ直接遷移させる。
 */
@Composable
private fun EndTodaySessionDialog(
    schedule: AlarmSchedule,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultLabel = stringResource(R.string.default_alarm_label)
    val label = schedule.label.ifBlank { defaultLabel }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ringing_skip_today)) },
        text = { Text(stringResource(R.string.end_today_session_confirm_message, label, occurrenceCount(schedule))) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.end_today_session_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** 下部ナビゲーションバー(4タブ)。Material3 ExpressiveのShortNavigationBarを使う。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TermAlarmBottomBar(selected: TermAlarmTab, onSelect: (TermAlarmTab) -> Unit) {
    ShortNavigationBar {
        TabItem(TermAlarmTab.ALARM, selected, onSelect, R.drawable.ic_alarm_tab, R.string.tab_alarm)
        TabItem(TermAlarmTab.CLOCK, selected, onSelect, R.drawable.ic_clock, R.string.tab_clock)
        TabItem(TermAlarmTab.TIMER, selected, onSelect, R.drawable.ic_timer, R.string.tab_timer)
        TabItem(TermAlarmTab.STOPWATCH, selected, onSelect, R.drawable.ic_stopwatch, R.string.tab_stopwatch)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TabItem(
    tab: TermAlarmTab,
    selected: TermAlarmTab,
    onSelect: (TermAlarmTab) -> Unit,
    iconRes: Int,
    labelRes: Int,
) {
    ShortNavigationBarItem(
        selected = selected == tab,
        onClick = { onSelect(tab) },
        icon = { Icon(painterResource(iconRes), contentDescription = null) },
        label = { Text(stringResource(labelRes)) },
    )
}
