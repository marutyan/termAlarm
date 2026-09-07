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
 * 1秒ごとに更新される現在時刻を返す。アナログ表示は秒針が、デジタル表示は秒の数字が動くため、
 * どちらの表示モードでも秒単位の更新が必要(要件「1秒ごとの更新」)。次の秒の頭に合わせて起こし、
 * 無駄な再計算を避ける。
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
