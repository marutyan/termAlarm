package com.marutyan.termalarm.alarm

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 正確なアラーム権限（Android 12以降のcanScheduleExactAlarms）の確認と、設定画面への誘導を行う。
 * setAlarmClock()はこの権限が無いとSecurityExceptionになるため、予約前に必ず確認する。
 * 権限を要求するUI自体は担当Dのui層が持つため、ここでは判定と遷移用Intentの生成だけを提供する。
 */
object ExactAlarmPermission {

    // Android 11以前はこの権限区分自体が存在しないため常にtrue。S(31)以降はAlarmManagerに確認する
    fun isGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    // 端末の「正確なアラームとリマインダー」設定画面を、このアプリの項目で開くIntent
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
}
