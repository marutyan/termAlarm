package com.marutyan.termalarm.data

import com.marutyan.termalarm.domain.AppSettings
import com.marutyan.termalarm.domain.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            alarmSoundUri = "content://media/alarm/1",
            vibration = false,
            fadeInSeconds = 15,
            silenceAfterMinutes = 20,
            wakeCheckMinutes = 10,
            theme = AppTheme.BLACK,
        )

        repository.update(settings)

        assertEquals(settings, repository.observe().first())
    }

    @Test
    fun `一部だけ変更した保存は他の項目を保ったまま上書きする`() = runTest {
        val repository = SettingsRepository(FakeAppSettingsDao())
        repository.update(AppSettings(silenceAfterMinutes = 5))

        val current = repository.observe().first()
        repository.update(current.copy(fadeInSeconds = 20))

        val loaded = repository.observe().first()
        assertEquals(5, loaded.silenceAfterMinutes)
        assertEquals(20, loaded.fadeInSeconds)
    }

    @Test
    fun `silenceAfterMinutesがnullの場合nullのまま保存復元される`() = runTest {
        val repository = SettingsRepository(FakeAppSettingsDao())
        val settings = AppSettings(
            alarmSoundUri = "content://media/alarm/2",
            vibration = true,
            fadeInSeconds = 5,
            silenceAfterMinutes = null,
            wakeCheckMinutes = 5,
            theme = AppTheme.NAVY,
        )

        repository.update(settings)

        val loaded = repository.observe().first()
        assertNull(loaded.silenceAfterMinutes)
        assertEquals(settings, loaded)
    }

    /**
     * 利用者が選んだ配色テーマが正しく保存され復元されることを検証する。
     * 設定画面で選択したテーマがアプリ起動時や画面再描画時にも保たれるために必要。
     */
    @Test
    fun `配色の設定が保存して読み直しても保たれる`() = runTest {
        val repository = SettingsRepository(FakeAppSettingsDao())

        repository.update(AppSettings(theme = AppTheme.BLACK))
        assertEquals(AppTheme.BLACK, repository.observe().first().theme)

        repository.update(AppSettings(theme = AppTheme.LIGHT))
        assertEquals(AppTheme.LIGHT, repository.observe().first().theme)

        repository.update(AppSettings(theme = AppTheme.DYNAMIC))
        assertEquals(AppTheme.DYNAMIC, repository.observe().first().theme)
    }

    @Test
    fun `未知のenum文字列は既定値へ倒す`() {
        val entity = AppSettingsEntity(
            alarmSoundUri = null,
            vibration = true,
            fadeInSeconds = 5,
            silenceAfterMinutes = null,
            wakeCheckMinutes = 5,
            theme = "UNKNOWN",
        )

        val domain = entity.toDomain()

        assertEquals(AppSettings().theme, domain.theme)
    }
}
