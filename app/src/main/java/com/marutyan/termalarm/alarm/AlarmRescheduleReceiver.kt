package com.marutyan.termalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.marutyan.termalarm.notification.runAsync

/**
 * 端末再起動・タイムゾーン変更・時刻変更・ロケール変更・アプリ更新のたびに、
 * 全アラームの予約を再計算して登録し直す（docs/SPEC.md「予約の方式」）。
 * Room読み込みを伴う非同期処理のためgoAsync()でBroadcastReceiverの生存期間を延長し、
 * 完了を待ってfinish()する。
 *
 * ロック解除前に届くLOCKED_BOOT_COMPLETEDも受ける。データベースは端末保護ストレージにあり、
 * 解除前でも読めるため、再起動した直後から予約を戻せる。
 * 解除後にはBOOT_COMPLETEDも届くが、同じ内容を計算し直すだけなので重ねて受けて構わない。
 */
class AlarmRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 外部アプリから偽のIntentが送られた場合に意図しない再予約処理が走るのを防ぐため、
        // AndroidManifest.xmlのintent-filterで定義された想定通りのactionであるか検証する。
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }

        // Receiverが受け取るContextは短命なので、アプリ全体のものへ持ち替える
        val appContext = context.applicationContext
        runAsync { AlarmScheduler.rescheduleAll(appContext) }
    }
}
