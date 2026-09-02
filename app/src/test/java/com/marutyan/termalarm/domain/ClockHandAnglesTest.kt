package com.marutyan.termalarm.domain

import org.junit.Assert.assertEquals
import org.junit.Test

// 角度計算の浮動小数比較に許す誤差
private const val DELTA = 0.001f

class ClockHandAnglesTest {

    @Test
    fun `ちょうど3時は時針が90度`() {
        val angles = clockHandAngles(hour = 3, minute = 0, second = 0)
        assertEquals(90f, angles.hourDegrees, DELTA)
        assertEquals(0f, angles.minuteDegrees, DELTA)
        assertEquals(0f, angles.secondDegrees, DELTA)
    }

    @Test
    fun `時針は分の進みも反映する`() {
        // 3時30分は時針が3時と4時のちょうど中間(90度+15度)まで進む
        val angles = clockHandAngles(hour = 3, minute = 30, second = 0)
        assertEquals(105f, angles.hourDegrees, DELTA)
    }

    @Test
    fun `12時は0度に折り返す`() {
        val angles = clockHandAngles(hour = 12, minute = 0, second = 0)
        assertEquals(0f, angles.hourDegrees, DELTA)
    }

    @Test
    fun `分針は秒の進みも反映する`() {
        // 0分30秒は分針が0分と1分のちょうど中間(0度+3度)まで進む
        val angles = clockHandAngles(hour = 0, minute = 0, second = 30)
        assertEquals(3f, angles.minuteDegrees, DELTA)
    }

    @Test
    fun `10時9分35秒の3針の角度`() {
        val angles = clockHandAngles(hour = 10, minute = 9, second = 35)
        // 時針: (10 + 9/60) * 30
        assertEquals(304.5f, angles.hourDegrees, DELTA)
        // 分針: (9 + 35/60) * 6
        assertEquals(57.5f, angles.minuteDegrees, DELTA)
        // 秒針: 35 * 6
        assertEquals(210f, angles.secondDegrees, DELTA)
    }
}
