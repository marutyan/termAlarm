package com.marutyan.termalarm.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.marutyan.termalarm.R
import com.marutyan.termalarm.ui.privacy.PrivacyScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * プライバシーポリシー画面(PrivacyScreen)のUI表示と操作を保証する。
 * 画面が開いたときにタイトルとポリシー本文が正しく表示され、戻るボタンで前の画面へ戻れることを検証する。
 */
@OptIn(ExperimentalTestApi::class)
class PrivacyScreenTest {
    // 端末がスリープしていてもテストが動くようにする
    @get:Rule
    val screenWakeRule = ScreenWakeRule()

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * リソースIDから文字列を取得するヘルパー。
     * テストコード内での文字列リソース参照を簡潔にする。
     */
    private fun string(resId: Int) = composeTestRule.activity.getString(resId)

    // プライバシーポリシー画面が開いたとき、タイトルとポリシー本文が表示されることを保証する
    @Test
    fun 画面が開いてタイトルと本文が表示される() {
        composeTestRule.setContent {
            PrivacyScreen(onBack = {})
        }

        composeTestRule.onNodeWithText(string(R.string.privacy_policy_title)).assertExists()
        composeTestRule.onNodeWithText("TermAlarm プライバシーポリシー", substring = true).assertExists()
        composeTestRule.onNodeWithText("このアプリは、利用者に関する情報を一切収集しません。", substring = true).assertExists()
    }

    // TopAppBarの戻るボタンを押したときに、onBackコールバックが呼ばれることを保証する
    @Test
    fun 戻るボタンを押すとコールバックが呼ばれる() {
        var backCalled = false
        composeTestRule.setContent {
            PrivacyScreen(onBack = { backCalled = true })
        }

        val title = string(R.string.privacy_policy_title)
        composeTestRule.onNode(hasClickAction() and hasAnySibling(hasText(title))).performClick()

        assertTrue("戻るボタンタップでonBackが呼ばれること", backCalled)
    }
}
