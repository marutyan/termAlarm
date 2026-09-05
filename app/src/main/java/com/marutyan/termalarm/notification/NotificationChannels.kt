package com.marutyan.termalarm.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * 通知チャンネルを用意する。アラーム・タイマー・ストップウォッチで同じ手順を書き写さないための置き場所。
 *
 * どのチャンネルも音は鳴らさない。音はMediaPlayerがアラーム用途で鳴らすため、
 * チャンネル側でも鳴らすと二重になる。
 */
object NotificationChannels {

    /**
     * 指定のチャンネルが無ければ作り、そのIDを返す。
     * 一度作ったチャンネルは重要度を上げられないため、重要度を変えたいときは新しいIDで作り直すこと。
     *
     * @param importance NotificationManager.IMPORTANCE_* のいずれか。
     *   LOWにすると通知が「サイレント」欄へ入り、HIGHにすると画面の上へ降りてくる。
     */
    fun ensure(context: Context, id: String, nameRes: Int, importance: Int): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(id) == null) {
            manager.createNotificationChannel(
                NotificationChannel(id, context.getString(nameRes), importance).apply {
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
        return id
    }

    /** 古いチャンネルを消す。重要度を変えるためにIDを変えたときに使う */
    fun delete(context: Context, id: String) {
        context.getSystemService(NotificationManager::class.java).deleteNotificationChannel(id)
    }
}
