package com.marutyan.termalarm.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.marutyan.termalarm.alarm.RingingContent
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.ui.theme.TermAlarmTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 鳴動画面(RingingContent)のレイアウト構造と操作を保証するUIテスト。
 * design/Ringing.dc.htmlで定義された9個の要素が上から順に配置され、ストップおよびターム終了操作が機能することを検証する。
 */
@OptIn(ExperimentalTestApi::class)
class RingingScreenTest {

    @get:Rule
    val screenWakeRule = ScreenWakeRule()

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // 鳴動画面が要件の9要素を上から順に持つことを検証する
    @Test
    fun 鳴動画面が9つの要素を上から順に持つ() {
        val schedule = AlarmSchedule(
            id = 1L,
            startMinutes = 7 * 60, // 7:00
            endMinutes = 9 * 60, // 9:00
            startIntervalMinutes = 5,
            endIntervalMinutes = 5,
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            label = "朝の起床ターム",
            enabled = true,
        )

        // 7:15(4回目)の鳴動時刻を設定
        val today = LocalDate.now()
        val occurrenceAt = ZonedDateTime.of(today, LocalTime.of(7, 15), ZoneId.systemDefault())

        composeTestRule.setContent {
            TermAlarmTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    RingingContent(
                        schedule = schedule,
                        occurrenceAt = occurrenceAt,
                        totalCount = 25,
                        remainingCount = 21,
                        currentOccurrence = 4,
                        currentOccurrenceIndex = 3,
                        onStop = {},
                        onEndTerm = {},
                    )
                }
            }
        }

        // 1. 「4回目 / 25回」のラベル
        val labelNode = composeTestRule.onNodeWithText("4回目 / 25回")
        labelNode.assertExists()

        // 2. 鳴っている時刻「7:15」
        val timeNode = composeTestRule.onNodeWithText("7:15")
        timeNode.assertExists()

        // 3. 範囲と間隔「7:00 – 9:00 · 5分ごと」
        val rangeNode = composeTestRule.onNodeWithText("7:00 – 9:00 · 5分ごと")
        rangeNode.assertExists()

        // 4. 右上に音量の目盛「音量」
        val volumeNode = composeTestRule.onNodeWithText("音量")
        volumeNode.assertExists()

        // 7. 「9:00まで 残り21回」
        val remainingNode = composeTestRule.onNodeWithText("9:00まで 残り21回")
        remainingNode.assertExists()

        // 8. 下部に「ストップ」(「次は 7:20」を添える)
        val stopNode = composeTestRule.onNodeWithText("ストップ")
        stopNode.assertExists()
        val nextNode = composeTestRule.onNodeWithText("次は 7:20")
        nextNode.assertExists()

        // 9. その下に「タームを終了」
        val endTermNode = composeTestRule.onNodeWithText("タームを終了")
        endTermNode.assertExists()

        // 要素の縦方向(Y座標)の順序を検証:
        // labelNode (最上部) < timeNode < rangeNode
        // remainingNode < stopNode < endTermNode (最下部)
        val labelTop = labelNode.getBoundsInRoot().top.value
        val timeTop = timeNode.getBoundsInRoot().top.value
        val rangeTop = rangeNode.getBoundsInRoot().top.value
        val remainingTop = remainingNode.getBoundsInRoot().top.value
        val stopTop = stopNode.getBoundsInRoot().top.value
        val endTermTop = endTermNode.getBoundsInRoot().top.value

        assertTrue("1.ラベル($labelTop)は2.時刻($timeTop)より上", labelTop < timeTop)
        assertTrue("2.時刻($timeTop)は3.範囲($rangeTop)より上", timeTop < rangeTop)
        assertTrue("3.範囲($rangeTop)は7.残り回数($remainingTop)より上", rangeTop < remainingTop)
        assertTrue("7.残り回数($remainingTop)は8.ストップ($stopTop)より上", remainingTop < stopTop)
        assertTrue("8.ストップ($stopTop)は9.ターム終了($endTermTop)より上", stopTop < endTermTop)
    }

    // ストップボタンおよびターム終了ボタンのタップ操作コールバックを検証する
    @Test
    fun ストップとターム終了のクリックコールバックが呼び出される() {
        var stopClicked = false
        var endTermClicked = false

        val schedule = AlarmSchedule(
            id = 1L,
            startMinutes = 7 * 60,
            endMinutes = 9 * 60,
            startIntervalMinutes = 5,
            endIntervalMinutes = 5,
            repeatDays = setOf(DayOfWeek.MONDAY),
            label = "",
            enabled = true,
        )
        val occurrenceAt = ZonedDateTime.of(LocalDate.now(), LocalTime.of(7, 0), ZoneId.systemDefault())

        composeTestRule.setContent {
            TermAlarmTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    RingingContent(
                        schedule = schedule,
                        occurrenceAt = occurrenceAt,
                        totalCount = 25,
                        remainingCount = 24,
                        currentOccurrence = 1,
                        currentOccurrenceIndex = 0,
                        onStop = { stopClicked = true },
                        onEndTerm = { endTermClicked = true },
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("ストップ").performClick()
        assertTrue("ストップがクリックされたこと", stopClicked)

        composeTestRule.onNodeWithText("タームを終了").performClick()
        assertTrue("ターム終了がクリックされたこと", endTermClicked)
    }
}
