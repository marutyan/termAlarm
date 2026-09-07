package com.marutyan.termalarm.timer

import android.app.NotificationManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.TimerRepository
import com.marutyan.termalarm.domain.TimerRunState
import com.marutyan.termalarm.domain.TimerState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * タイマーの通知が、サービスを常駐させずに出て、状態に合わせて消えることを守る。
 *
 * 動作中にフォアグラウンドサービスを持たせる作りをやめたため（純正も同じ作り）、
 * 通知が出ないまま気付かれない不具合が起きうる。ここで最低限の保証を置く。
 */
@RunWith(AndroidJUnit4::class)
class TimerNotificationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = TimerRepository(AlarmDatabase.getInstance(context).timerDao())
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        grantNotificationPermission()
        clearTimers()
    }

    /**
     * 通知を出す許可を取る。
     * テストのたびにアプリを入れ直すため許可が消えており、そのままだと通知が出ない。
     * 依存を増やさずに済むよう、シェル経由で与える。
     */
    private fun grantNotificationPermission() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            .close()
        // 反映されるまでわずかに待つ
        Thread.sleep(300)
    }

    @After
    fun tearDown() {
        clearTimers()
        manager.cancel(TIMER_FOREGROUND_NOTIFICATION_ID)
    }

    private fun clearTimers() = runBlocking {
        repository.observeAll().first().forEach { repository.delete(it.id) }
    }

    private fun postedNotification() =
        manager.activeNotifications.firstOrNull { it.id == TIMER_FOREGROUND_NOTIFICATION_ID }

    private fun runningTimer(label: String, remainingMillis: Long) = TimerState(
        id = 0L,
        label = label,
        totalMillis = if (remainingMillis > 0L) remainingMillis else 1L,
        remainingMillisAtAnchor = remainingMillis,
        anchorElapsedRealtime = SystemClock.elapsedRealtime(),
        anchorWallClockMillis = System.currentTimeMillis(),
        runState = TimerRunState.RUNNING,
    )

    @Test
    fun 動作中のタイマーがあると通知が出る() = runBlocking {
        repository.add(runningTimer("パスタ", 600_000L))
        TimerActions.refreshNotification(context)
        Thread.sleep(700)

        val posted = postedNotification()
        assertNotNull("動作中のタイマーがあれば通知が出ること", posted)
        assertTrue("消せない扱いであること", posted!!.isOngoing)
    }

    @Test
    fun タイマーが1件も無いと通知が消える() = runBlocking {
        val id = repository.add(runningTimer("パスタ", 600_000L))
        TimerActions.refreshNotification(context)
        Thread.sleep(700)
        assertNotNull(postedNotification())

        repository.delete(id)
        TimerActions.refreshNotification(context)
        Thread.sleep(700)
        assertNull("タイマーが無くなれば通知も消えること", postedNotification())
    }

    @Test
    fun 期限が来たタイマーは鳴動中へ移る() = runBlocking {
        // すでに期限が過ぎている状態を作る
        val id = repository.add(runningTimer("もう終わり", 0L))
        val changed = TimerActions.markDueTimersFinished(context)

        assertTrue("期限が来たタイマーがあったこと", changed)
        assertEquals(
            "鳴動中へ移っていること",
            TimerRunState.FINISHED,
            repository.getById(id)?.runState,
        )
    }

    @Test
    fun 通知の停止でタイマーが消える() = runBlocking {
        val id = repository.add(runningTimer("消える", 600_000L))
        TimerActions.stop(context, id)

        assertNull("停止するとタイマー自体が消えること", repository.getById(id))
    }

    @Test
    fun 通知の一時停止で止まり残り時間が保たれる() = runBlocking {
        val id = repository.add(runningTimer("止める", 600_000L))
        TimerActions.pause(context, id)

        val paused = repository.getById(id)
        assertEquals(TimerRunState.PAUSED, paused?.runState)
        assertTrue(
            "止めた時点の残り時間が保たれること",
            (paused?.remainingMillisAtAnchor ?: 0L) > 590_000L,
        )
    }
}
