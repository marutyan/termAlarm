package com.marutyan.termalarm.ui.home

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
 * 1秒ごとに更新される現在時刻を購読して提供するComposable関数。
 * ホーム画面の現在時刻および秒表示を毎秒正確に追従させるために用いる。
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
