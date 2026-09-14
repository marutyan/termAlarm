package com.marutyan.termalarm.alarm

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 鳴っている間の振動。アラームとタイマーで同じ鳴らし方をするため、1か所へまとめる。
 *
 * 同じ意味の値（振動の間隔）を複数の場所へ書き写さないための置き場所でもある。
 */
object AlarmVibration {

    // 1秒振って1秒休むのを、止められるまで繰り返す
    private val PATTERN = longArrayOf(0, 1000, 1000)

    /**
     * 振動の用途。音と同じくアラームとして扱わせる。
     * 用途を渡さないと通知の振動として扱われ、マナーモードや通知を切る設定で振動しなくなる。
     * 音はUSAGE_ALARMで鳴らしているので、振動だけ別の扱いになっていると食い違う。
     */
    private val ALARM_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /**
     * 振動を始めて、止めるためのVibratorを返す。
     * 端末の振動装置は版によって取り方が違うため、その差もここで吸収する。
     */
    fun start(context: Context): Vibrator {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API31以降はVibratorManager経由での取得が推奨される
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        // 第2引数の1は「1番目の要素から繰り返す」の意味。0番目は待ち時間なので飛ばす
        vibrator.vibrate(VibrationEffect.createWaveform(PATTERN, 1), ALARM_ATTRIBUTES)
        return vibrator
    }
}
