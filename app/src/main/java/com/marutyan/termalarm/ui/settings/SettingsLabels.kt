package com.marutyan.termalarm.ui.settings

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.marutyan.termalarm.R

/**
 * 設定の値を、画面へ出す言葉へ変える。
 * 設定画面が長くなりすぎたため、表示のための変換だけをこちらへ分けている。
 */

// 秒数の表示用整形。0は「なし」、小数を含む場合はそのまま(1.5秒)、整数なら小数点を出さない(5秒)
@Composable
internal fun formatSeconds(seconds: Number): String {
    val value = seconds.toFloat()
    if (value == 0f) return stringResource(R.string.settings_fade_in_off)
    val text = if (value == value.toLong().toFloat()) value.toLong().toString() else value.toString()
    return stringResource(R.string.settings_seconds_format, text)
}

// 選択中のUriからタイトルを取り出す。未設定・取得失敗時は既定の音の表記にする
internal fun soundLabel(context: Context, uriString: String?): String {
    val defaultLabel = context.getString(R.string.sound_default)
    val uri = uriString?.let(Uri::parse) ?: return defaultLabel
    return runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull() ?: defaultLabel
}
