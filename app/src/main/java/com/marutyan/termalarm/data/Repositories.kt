package com.marutyan.termalarm.data

import android.content.Context

/**
 * Repositoryの組み立てを1か所へまとめる。
 *
 * これまで `SomeRepository(AlarmDatabase.getInstance(context).someDao())` を20か所以上で
 * 書き写していた。どのDaoを渡すかを間違えても気付きにくく、
 * データベースの取り方を変えるときに直し漏れが出る。
 *
 * 依存を注入する仕組みは使わない方針のため、代わりにこの入口を通す。
 */
object Repositories {

    fun alarm(context: Context): AlarmRepository =
        AlarmRepository(db(context).alarmDao())

    fun timer(context: Context): TimerRepository =
        TimerRepository(db(context).timerDao())

    fun stopwatch(context: Context): StopwatchRepository =
        StopwatchRepository(db(context).stopwatchDao())

    fun settings(context: Context): SettingsRepository =
        SettingsRepository(db(context).appSettingsDao())

    fun clockSettings(context: Context): ClockSettingsRepository =
        ClockSettingsRepository(db(context).clockSettingsDao())

    private fun db(context: Context): AlarmDatabase = AlarmDatabase.getInstance(context)
}
