package com.marutyan.termalarm.data

import com.marutyan.termalarm.domain.AlarmDismissMethod
import com.marutyan.termalarm.domain.AppSettings
import com.marutyan.termalarm.domain.VolumeButtonAction
import com.marutyan.termalarm.domain.WeekStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryTest {

    @Test
    fun `未保存の状態ではAppSettingsの既定値を返す`() = runTest {
        val repository = SettingsRepository(FakeAppSettingsDao())

        val loaded = repository.observe().first()

        assertEquals(AppSettings(), loaded)
    }

    @Test
    fun `保存した値がそのまま読み出せる`() = runTest {
        val repository = SettingsRepository(FakeAppSettingsDao())
        val settings = AppSettings(
            dismissMethod = AlarmDismissMethod.SWIPE,
            autoStopMinutes = 15,
            defaultSnoozeMinutes = 10,
            alarmFadeInSeconds = 20,
            volumeButtonAction = VolumeButtonAction.SNOOZE,
            weekStart = WeekStart.MONDAY,
            showClockSeconds = true,
            timerSoundUri = "content://media/timer",
            timerFadeInSeconds = 3f,
            timerVibration = false,
        )

        repository.update(settings)

        assertEquals(settings, repository.observe().first())
    }

    @Test
    fun `一部だけ変更した保存は他の項目を保ったまま上書きする`() = runTest {
        val repository = SettingsRepository(FakeAppSettingsDao())
        repository.update(AppSettings(autoStopMinutes = 5))

        val current = repository.observe().first()
        repository.update(current.copy(defaultSnoozeMinutes = 20))

        val loaded = repository.observe().first()
        assertEquals(5, loaded.autoStopMinutes)
        assertEquals(20, loaded.defaultSnoozeMinutes)
    }

    @Test
    fun `未知のenum文字列は既定値へ倒す`() {
        val entity = AppSettingsEntity(
            dismissMethod = "UNKNOWN",
            autoStopMinutes = 10,
            defaultSnoozeMinutes = 5,
            alarmFadeInSeconds = 5,
            volumeButtonAction = "UNKNOWN",
            weekStart = "UNKNOWN",
            showClockSeconds = false,
            timerSoundUri = null,
            timerFadeInSeconds = 1.5f,
            timerVibration = true,
        )

        val domain = entity.toDomain()

        assertEquals(AppSettings().dismissMethod, domain.dismissMethod)
        assertEquals(AppSettings().volumeButtonAction, domain.volumeButtonAction)
        assertEquals(AppSettings().weekStart, domain.weekStart)
    }
}
