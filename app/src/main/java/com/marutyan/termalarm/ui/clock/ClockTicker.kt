package com.marutyan.termalarm.ui.clock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

/**
 * 1秒ごとに更新される現在時刻を返す。アナログ表示は秒針が動くため秒単位の更新が必要
 * (docs/SPEC.md「更新頻度」)。次の秒の頭に合わせて起こし、無駄な再計算を避ける。
 */
@Composable
fun rememberCurrentSecond(): ZonedDateTime {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val next = now.plusSeconds(1).withNano(0)
            delay(maxOf(200L, Duration.between(ZonedDateTime.now(), next).toMillis()))
            now = ZonedDateTime.now()
        }
    }
    return now
}

/**
 * 1分ごとに更新される現在時刻を返す。デジタル表示は秒を出さないため分単位の更新で足りる
 * (docs/SPEC.md「更新頻度」、ui/alarmlist/AlarmListScreen.ktのrememberCurrentMinuteと同じ考え方)。
 */
@Composable
fun rememberCurrentMinute(): ZonedDateTime {
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
