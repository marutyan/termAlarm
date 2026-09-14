package com.marutyan.termalarm.alarm

import android.content.Context
import com.marutyan.termalarm.domain.AlarmSchedule
import java.time.ZonedDateTime

/**
 * タームの保存と、AlarmManagerの予約の入れ直しを対にして行う窓口。
 *
 * 画面からは保存だけを行わず、必ずここを通す。
 * 「保存する」と「予約を入れ直す」を呼び出し側それぞれに任せた結果、
 * 一覧の切り替えとタームの終了で予約の入れ直しが漏れ、
 * 切り替えても鳴らない・終了させても次の1回が鳴る、という不具合が起きた。
 *
 * 差し替えられる形にしてあるのは、予約が入ったことをテストで確かめられるようにするため。
 * 予約はAndroidのAlarmManagerが相手で、そのままでは単体テストから触れない。
 */
interface ScheduleStore {

    /** 有効・無効を切り替え、予約もそれに合わせる。 */
    suspend fun setEnabled(id: Long, enabled: Boolean)

    /** タームの内容を保存し、予約もそれに合わせる。 */
    suspend fun update(schedule: AlarmSchedule)

    /** 今日のタームを終了し、予約を片付ける。曜日を指定していないタームはオフにする。 */
    suspend fun endSession(id: Long, at: ZonedDateTime)
}

/**
 * 実際にAlarmManagerを操作する[ScheduleStore]。
 * 中身は[AlarmScheduler]への委譲だけで、判断はそちらが持つ。
 */
class AlarmSchedulerStore(private val appContext: Context) : ScheduleStore {

    override suspend fun setEnabled(id: Long, enabled: Boolean) =
        AlarmScheduler.setEnabled(appContext, id, enabled)

    override suspend fun update(schedule: AlarmSchedule) =
        AlarmScheduler.updateSchedule(appContext, schedule)

    override suspend fun endSession(id: Long, at: ZonedDateTime) =
        AlarmScheduler.onSessionEnded(appContext, id, at)
}
