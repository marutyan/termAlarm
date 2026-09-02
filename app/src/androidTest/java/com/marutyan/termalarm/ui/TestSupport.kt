package com.marutyan.termalarm.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.data.TimerRepository
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.ui.alarmedit.AlarmEditScreen
import com.marutyan.termalarm.ui.alarmedit.AlarmEditViewModel
import com.marutyan.termalarm.ui.alarmlist.AlarmListScreen
import com.marutyan.termalarm.ui.alarmlist.AlarmListViewModel

// UIテスト共通の下ごしらえ。各テストで3回以上使うため1箇所にまとめる(app/src/main/には触れない)。

/**
 * テスト専用のインメモリRoomDB+Repositoryを作る。テストごとに独立させ、前のテストの影響を受けないようにする。
 * 実機に旧バージョンのDBが残っていてもversion不整合でクラッシュしないよう、破壊的マイグレーションを許可しておく
 * (実機の実データには触れない。あくまでテストが自分で作るインメモリDBの初期化を安定させるためのオプション)。
 */
internal fun createTestRepository(): Pair<AlarmDatabase, AlarmRepository> {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val db = Room.inMemoryDatabaseBuilder(context, AlarmDatabase::class.java)
        .fallbackToDestructiveMigration(true)
        .build()
    return db to AlarmRepository(db.alarmDao())
}

// AlarmEditViewModel/AlarmListViewModel/SkipGameViewModelが要求するContext。ApplicationContextで足りる
internal fun testAppContext(): Context = InstrumentationRegistry.getInstrumentation().targetContext

/**
 * タイマーUIテスト専用のインメモリRoomDB+TimerRepositoryを作る。createTestRepository()と同じDB
 * クラス(AlarmDatabase)を使うが、テストごとに新しいインメモリDBを作るためアラームのテストとは独立する。
 */
internal fun createTestTimerRepository(): Pair<AlarmDatabase, TimerRepository> {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val db = Room.inMemoryDatabaseBuilder(context, AlarmDatabase::class.java)
        .fallbackToDestructiveMigration(true)
        .build()
    return db to TimerRepository(db.timerDao())
}

/**
 * テストで使う既定値のアラーム。docs/SPEC.mdの既定値(7:00〜9:00・5分間隔・skipRequiresApp=true等)と
 * AlarmEditUiStateの既定値に合わせる。個々のテストは変えたいフィールドだけ引数で上書きする。
 */
internal fun defaultTestSchedule(
    startMinutes: Int = 7 * 60,
    endMinutes: Int = 9 * 60,
    intervalMinutes: Int = 5,
    label: String = "",
    skipRequiresApp: Boolean = true,
    skipGame: Boolean = false,
    snoozeMinutes: Int? = null,
): AlarmSchedule = AlarmSchedule(
    id = 0,
    startMinutes = startMinutes,
    endMinutes = endMinutes,
    intervalMinutes = intervalMinutes,
    repeatDays = emptySet(),
    label = label,
    soundUri = null,
    vibrate = true,
    enabled = true,
    skippedSessionStart = null,
    skipRequiresApp = skipRequiresApp,
    skipGame = skipGame,
    snoozeMinutes = snoozeMinutes,
)

// テスト内で「一覧」⇔「追加・編集」を行き来するための最小限の画面切り替え。
// 本物のNavHost(TermAlarmNavHost)はComposeNavigationのルーティングを担うが、
// テストではその配線自体を検証したいわけではないため、実際の画面(AlarmListScreen/AlarmEditScreen)と
// 本物のViewModelをそのまま使いつつ、画面切り替えだけをローカルなStateで代替する。
private sealed interface ListEditScreen {
    data object List : ListEditScreen
    data class Edit(val id: Long?) : ListEditScreen
}

@Composable
internal fun ListEditHost(repository: AlarmRepository) {
    val context = testAppContext()
    var screen by remember { mutableStateOf<ListEditScreen>(ListEditScreen.List) }
    when (val current = screen) {
        ListEditScreen.List -> AlarmListScreen(
            viewModel = remember { AlarmListViewModel(repository, context) },
            onAddAlarm = { screen = ListEditScreen.Edit(null) },
            onEditAlarm = { id -> screen = ListEditScreen.Edit(id) },
            onOpenAbout = {},
            onNavigateToSkipGame = {},
            exactAlarmBanner = {},
            notificationPermissionBanner = {},
        )
        is ListEditScreen.Edit -> AlarmEditScreen(
            viewModel = remember(current.id) { AlarmEditViewModel(repository, context, current.id) },
            onClose = { screen = ListEditScreen.List },
        )
    }
}

/**
 * タイトル文言の近くにあるSwitchを探す。「止めにくさ」カードはタイトル・説明・Switchを
 * testTag無しのRowに並べているだけで、セマンティクスツリー上はカード内の全Rowの中身が
 * フラットな兄弟になってしまうため(実機のセマンティクスツリーで確認済み)、hasSiblingでは
 * 同じカード内の複数Switchを区別できない。そのため縦位置が重なるSwitchを幾何的に特定する。
 */
internal fun ComposeTestRule.switchNear(label: String): SemanticsNodeInteraction {
    val labelBounds = onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val match: SemanticsNode = onAllNodes(isToggleable(), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .first { it.boundsInRoot.top < labelBounds.bottom && it.boundsInRoot.bottom > labelBounds.top }
    return onNode(SemanticsMatcher("id=${match.id}") { it.id == match.id }, useUnmergedTree = true)
}
