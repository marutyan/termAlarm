package com.marutyan.termalarm.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * テストの実行中、画面を点けたままにする。
 *
 * Composeのテストは画面が消えていると「No compose hierarchies found」で失敗する。
 * 端末がスリープに入っているだけで全件落ちるため、実行のたびに人が画面を起こす必要があった。
 * このRuleを付けると端末の状態に左右されなくなる。
 *
 * 画面ロック（PINやパターン）が設定されている場合、プログラムからは解除できない。
 * その場合もActivityをロック画面の上へ出すことで、テストは動く。
 */
class ScreenWakeRule : TestWatcher() {
    override fun starting(description: Description) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // 画面を消えないようにするフラグは、UIスレッドからしか設定できない
        instrumentation.runOnMainSync {
            val activity = currentActivity() ?: return@runOnMainSync
            activity.window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
                val keyguard = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                keyguard.requestDismissKeyguard(activity, null)
            } else {
                @Suppress("DEPRECATION")
                activity.window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
                )
            }
        }
    }

    // ComposeのテストRuleが立ち上げたActivityを、実行中のものから探す
    private fun currentActivity(): Activity? {
        val stage = androidx.test.runner.lifecycle.Stage.RESUMED
        return androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(stage)
            .firstOrNull()
    }
}
