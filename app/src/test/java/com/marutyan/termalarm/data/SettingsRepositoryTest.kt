package com.marutyan.termalarm.data

import com.marutyan.termalarm.domain.AppSettings
import com.marutyan.termalarm.domain.AppTheme
import com.marutyan.termalarm.domain.GameType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            enabledGames = setOf(GameType.MIRROR_TEXT, GameType.WALK),
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

    /**
     * 利用者が選んだ出題ミニゲームの集合が正しく保存され復元されることを検証する。
     * 設定画面で選択したゲーム種別がアプリ再起動時や出題時にも保たれるために必要。
     */
    @Test
    fun `enabledGamesが保存して読み直しても保たれる`() = runTest {
        val repository = SettingsRepository(FakeAppSettingsDao())
        val customGames = setOf(GameType.MIRROR_TEXT, GameType.WALK, GameType.SEQUENCE_RECALL)

        repository.update(AppSettings(enabledGames = customGames))

        val loaded = repository.observe().first()
        assertEquals(customGames, loaded.enabledGames)
    }

    /**
     * AppSettingsのenabledGamesの既定値がWALKを除く8種類であることを検証する。
     * 起床負荷の高いWALKを除外した標準セットが設定されていることを確認するために必要。
     */
    @Test
    fun `enabledGamesの既定がWALK以外のすべてである`() {
        val settings = AppSettings()
        val expected = setOf(
            GameType.ARITHMETIC,
            GameType.SEQUENTIAL_TAP,
            GameType.TRANSCRIBE,
            GameType.SHAKE_DEVICE,
            GameType.COUNT_SHAPES,
            GameType.COLOR_WORD,
            GameType.MIRROR_TEXT,
            GameType.SEQUENCE_RECALL,
            GameType.MEMORY_PAIRS,
        )
        assertEquals(expected, settings.enabledGames)
        assertFalse(settings.enabledGames.contains(GameType.WALK))
        assertEquals(9, settings.enabledGames.size)
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
            enabledGames = setOf(GameType.ARITHMETIC),
        )

        val domain = entity.toDomain()

        assertEquals(AppSettings().theme, domain.theme)
        assertEquals(setOf(GameType.ARITHMETIC), domain.enabledGames)
    }
}
