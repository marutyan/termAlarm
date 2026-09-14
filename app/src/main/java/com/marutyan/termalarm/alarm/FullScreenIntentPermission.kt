package com.marutyan.termalarm.alarm

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 全画面通知の許可（Android 14以降のcanUseFullScreenIntent）の確認と、設定画面への誘導を行う。
 *
 * この許可が無いと、画面が消えているときに鳴動画面が前面へ出ず、通知が降りてくるだけになる。
 * 音は鳴るが、寝ている人が止めるための画面が出ないため、アラームとしては用を成さない。
 *
 * Android 14以降はアラームや通話の用途でも端末の設定から個別に切れるため、
 * 宣言しているだけでは足りず、都度確認する必要がある。
 * ExactAlarmPermissionと同じく、ここでは判定と遷移用Intentの生成だけを提供する。
 */
object FullScreenIntentPermission {

    // Android 13以前はこの区分自体が無いため常にtrue。14(UPSIDE_DOWN_CAKE)以降は通知の仕組みに確認する
    fun isGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    // 端末の「フルスクリーン通知」設定画面を、このアプリの項目で開くIntent
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
}
