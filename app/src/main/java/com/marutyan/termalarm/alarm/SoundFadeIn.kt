package com.marutyan.termalarm.alarm

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 鳴り始めの音量を徐々に上げる処理。アラームとタイマーの両方から使う。
 *
 * 端末のアラーム音量(STREAM_ALARM)には触れず、MediaPlayer側の音量だけを変える。
 * 端末の設定を書き換えるとアプリを消しても戻らず、利用者に迷惑がかかるため。
 *
 * 上げきる時間は呼び出し側が決める。アラームは寝ている人を起こすため長くかけ、
 * タイマーは起きている人へ知らせるだけなので短くする。
 */
object SoundFadeIn {
    /** 開始時の音量。0から始めると鳴っているか分からないため、聞こえる最小限にする */
    const val START_VOLUME = 0.05f

    /** 音量を変える間隔。これより細かくしても聞き分けられない */
    private const val STEP_MILLIS = 100L

    /**
     * playerの音量をSTART_VOLUMEから最大までdurationMillisかけて上げる。
     * 呼び出し側は再生開始の直前にsetVolume(START_VOLUME, START_VOLUME)を済ませておく。
     * 戻り値のJobは、鳴動を止めるときにキャンセルする。
     */
    fun start(scope: CoroutineScope, player: MediaPlayer, durationMillis: Long): Job =
        scope.launch {
            val steps = (durationMillis / STEP_MILLIS).toInt().coerceAtLeast(1)
            for (step in 1..steps) {
                delay(STEP_MILLIS)
                val volume = START_VOLUME + (1f - START_VOLUME) * (step.toFloat() / steps)
                // 音量を上げる途中でreleaseされることがあるため、例外は無視して打ち切る
                runCatching { player.setVolume(volume, volume) }.onFailure { return@launch }
            }
        }
}
