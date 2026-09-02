package com.marutyan.termalarm.alarm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 通知権限（POST_NOTIFICATIONS）の確認を行う。Android 13(33)未満は実行時権限自体が存在しないため常に許可扱いにする。
 * 要求ダイアログの表示自体は担当Dのui層が ActivityResultContracts.RequestPermission() で行う契約とし、
 * ここでは判定と、要求すべき権限文字列の提供だけを行う。
 */
object NotificationPermission {

    const val PERMISSION: String = Manifest.permission.POST_NOTIFICATIONS

    // POST_NOTIFICATIONSの実行時権限要求がそもそも必要な端末かどうか
    fun isRuntimeRequestRequired(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun isGranted(context: Context): Boolean =
        !isRuntimeRequestRequired() ||
            ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
}
