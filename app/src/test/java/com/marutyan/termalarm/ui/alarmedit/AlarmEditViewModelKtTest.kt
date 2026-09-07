package com.marutyan.termalarm.ui.alarmedit

import org.junit.Assert.assertEquals
import org.junit.Test

// resolveSnoozeMinutesは設定「スヌーズの長さ(既定値)」を新規/既存アラームの初期値へ反映する判定。
// AlarmEditViewModel自体はContextに依存しJVM単体テストできないため、判定ロジックだけを切り出して検証する。
class AlarmEditViewModelKtTest {

    @Test
    fun `新規アラーム(スケジュール値なし)は設定の既定値を使う`() {
        assertEquals(15, resolveSnoozeMinutes(scheduleSnoozeMinutes = null, defaultSnoozeMinutes = 15))
    }

    @Test
    fun `設定の既定値を変えると新規アラームの初期値も変わる`() {
        assertEquals(20, resolveSnoozeMinutes(scheduleSnoozeMinutes = null, defaultSnoozeMinutes = 20))
    }

    @Test
    fun `既存アラームに保存済みの値があればそちらを優先する`() {
        assertEquals(7, resolveSnoozeMinutes(scheduleSnoozeMinutes = 7, defaultSnoozeMinutes = 20))
    }
}
