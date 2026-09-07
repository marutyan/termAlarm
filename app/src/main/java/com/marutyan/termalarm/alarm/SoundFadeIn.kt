package com.marutyan.termalarm.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
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
     * 鳴らす音を用意して再生を始め、指定の秒数かけて音量を上げる。
     *
     * アラームもタイマーも「マナーモードでも鳴らす」「繰り返す」「小さい音から上げる」が同じなので、
     * ここへまとめる。同じ手順を書き写すと、片方だけ直して食い違う。
     *
     * 音源を開けなかった場合はnullを返す。呼び出し側は鳴らせなかったものとして扱うこと。
     */
    fun startRinging(
        context: Context,
        scope: CoroutineScope,
        uri: Uri,
        fadeInSeconds: Number,
    ): MediaPlayer? {
        val player = MediaPlayer().apply {
            // マナーモードでも鳴る必要があるため、通知/メディアではなくALARM用途を明示する
            setAudioAttributes(alarmAudioAttributes())
            isLooping = true
            // 鳴り始めの音量。0にすると鳴っているか分からない
            setVolume(START_VOLUME, START_VOLUME)
        }
        val started = runCatching {
            player.setDataSource(context, uri)
            player.prepare()
            player.start()
        }.isSuccess
        if (!started) {
            runCatching { player.release() }
            return null
        }
        val duration = durationMillisOrNull(fadeInSeconds)
        if (duration == null) {
            player.setVolume(1f, 1f)
        } else {
            start(scope, player, duration)
        }
        return player
    }

    /**
     * 設定画面で選べる秒数(0は「なし」)からフェードインの所要ミリ秒を求める。
     * 0以下ならnullを返し、呼び出し側はフェードインせず即座に最大音量にする合図とする。
     */
    internal fun durationMillisOrNull(seconds: Number): Long? =
        (seconds.toDouble() * 1000).toLong().takeIf { it > 0 }

    /**
     * マナーモードでも鳴らす必要がある音(アラーム本体・タイマー完了音・音量スライダーの試聴音)に
     * 共通するAudioAttributes。USAGE_ALARMを明示することで、通知/メディアの音量設定に影響されない。
     */
    fun alarmAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

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
