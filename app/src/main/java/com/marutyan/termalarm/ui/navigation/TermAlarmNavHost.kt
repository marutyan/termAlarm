package com.marutyan.termalarm.ui.navigation

import android.app.Activity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import com.marutyan.termalarm.alarm.AlarmSchedulerStore
import com.marutyan.termalarm.ui.termend.TermEndDialog
import java.time.ZonedDateTime
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.data.Repositories
import com.marutyan.termalarm.ui.about.AboutScreen
import com.marutyan.termalarm.ui.alarmedit.AlarmEditScreen
import com.marutyan.termalarm.ui.alarmedit.AlarmEditViewModel
import com.marutyan.termalarm.ui.alarmedit.AlarmEditViewModelFactory
import com.marutyan.termalarm.ui.alarms.AlarmsScreen
import com.marutyan.termalarm.ui.alarms.AlarmsViewModel
import com.marutyan.termalarm.ui.alarms.AlarmsViewModelFactory
import com.marutyan.termalarm.ui.common.PlaceholderScreen
import com.marutyan.termalarm.ui.home.HomeScreen
import com.marutyan.termalarm.ui.home.HomeViewModel
import com.marutyan.termalarm.ui.home.HomeViewModelFactory
import com.marutyan.termalarm.ui.privacy.PrivacyScreen
import com.marutyan.termalarm.ui.records.RecordsScreen
import com.marutyan.termalarm.ui.records.RecordsViewModel
import com.marutyan.termalarm.ui.records.RecordsViewModelFactory
import com.marutyan.termalarm.ui.settings.GameListScreen
import com.marutyan.termalarm.ui.settings.SettingsScreen
import com.marutyan.termalarm.ui.settings.SettingsViewModel
import com.marutyan.termalarm.ui.settings.SettingsViewModelFactory
import com.marutyan.termalarm.ui.skipgame.SkipGameScreen
import com.marutyan.termalarm.ui.skipgame.SkipGameViewModel
import com.marutyan.termalarm.ui.skipgame.SkipGameViewModelFactory
import com.marutyan.termalarm.ui.stopwatch.StopwatchScreen
import com.marutyan.termalarm.ui.stopwatch.StopwatchViewModel
import com.marutyan.termalarm.ui.stopwatch.StopwatchViewModelFactory
import com.marutyan.termalarm.ui.theme.SCREEN_SLIDE_DISTANCE_DP
import com.marutyan.termalarm.ui.theme.screenCloseEnter
import com.marutyan.termalarm.ui.theme.screenCloseExit
import com.marutyan.termalarm.ui.theme.screenOpenEnter
import com.marutyan.termalarm.ui.theme.screenOpenExit
import com.marutyan.termalarm.ui.theme.screenPredictivePopExit
import com.marutyan.termalarm.ui.theme.tabFadeSpec
import com.marutyan.termalarm.ui.timer.TimerScreen
import com.marutyan.termalarm.ui.timer.TimerViewModel
import com.marutyan.termalarm.ui.timer.TimerViewModelFactory

private const val ROUTE_END_TODAY_GAME = "endTodayGame"
private const val ROUTE_GAME_LIST = "gameList"
private const val ROUTE_ABOUT = "about"
private const val ROUTE_PRIVACY = "privacy"

/**
 * タブの上へ重ねて開く下位の画面。
 * これらから戻るときは、下にいた画面も滑り込ませて動きを対にするために用いる。
 */
private val SUB_SCREEN_ROUTES = setOf(ROUTE_GAME_LIST, ROUTE_ABOUT, ROUTE_PRIVACY)
private const val ARG_ALARM_ID = "alarmId"

// タームまたは通常アラームの編集シート対象。alarmIdがnullなら新規作成、値があれば該当IDの編集
/**
 * 開いているターム編集シートの対象。
 *
 * [openId] は開くたびに変わる番号。ViewModelを覚えておく鍵に混ぜるために持つ。
 * 新規追加はalarmIdがいつもnullなので、これが無いと2回目以降に
 * 前回の「保存済み」の状態を引き継いだViewModelが使い回され、開いた直後に閉じてしまう。
 */
private data class EditTarget(val alarmId: Long?, val openId: Long = nextEditOpenId())

/** 編集シートを開くたびに1つ増える番号。ViewModelの鍵を毎回変えるために用いる。 */
private var editOpenCounter: Long = 0L

private fun nextEditOpenId(): Long = ++editOpenCounter

// SET_ALARM等の外部インテントを受けたAlarmIntentActivity(ui.intent)がMainActivity起動時に付ける拡張。
// 値が-1なら新規作成、0以上ならそのidの編集シートをホーム画面上で開く。他パッケージから参照するためpublic。
const val EXTRA_DEEPLINK_ALARM_ID = "com.marutyan.termalarm.ui.EXTRA_DEEPLINK_ALARM_ID"

// どの画面を開いた状態で始めるかを指定する拡張。タイマーの通知から開いたときにタイマー画面が出るようにするために使う。
const val EXTRA_DEEPLINK_TAB = "com.marutyan.termalarm.ui.EXTRA_DEEPLINK_TAB"

// 鳴動画面の「タームを終了」から遷移した際に、当日終了確認ダイアログを開く対象のアラームidを指定する拡張。他パッケージから参照するためpublic。
const val EXTRA_DEEPLINK_END_TERM_ID = "com.marutyan.termalarm.ui.EXTRA_DEEPLINK_END_TERM_ID"

/**
 * アプリ全体の画面遷移を管理するNavHost。
 * 上部に主要画面または個別機能画面を表示し、主要5画面では下部に横並びの帯(TermAlarmBottomBar)を配して画面間を切り替える。
 */
@Composable
fun TermAlarmNavHost(
    repository: AlarmRepository,
    hasShakeSensor: Boolean,
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // 起動時のディープリンクIntentを処理し、指定されたタブ画面へ遷移する
    LaunchedEffect(Unit) {
        val launchIntent = (context as? Activity)?.intent
        launchIntent?.getStringExtra(EXTRA_DEEPLINK_TAB)?.let { name ->
            // EXTRA_DEEPLINK_ALARM_IDが指定されている場合はホーム画面にシートを重ねるためタブ移動を行わない
            if (launchIntent.hasExtra(EXTRA_DEEPLINK_ALARM_ID)) return@let
            val targetItem = when (name) {
                "TIMER" -> NavItem.TIMER
                "STOPWATCH" -> NavItem.STOPWATCH
                "ALARM", "TERMS" -> NavItem.TERMS
                "SETTINGS" -> NavItem.SETTINGS
                else -> null
            }
            targetItem?.let { item ->
                navController.navigate(item.route) {
                    popUpTo(NavItem.TERMS.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    val slidePx = with(LocalDensity.current) { SCREEN_SLIDE_DISTANCE_DP.dp.roundToPx() }

    // 現在のバックスタックエントリからベースルートを判定し、下の帯を表示すべき主要画面かを特定する
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val baseRoute = currentRoute?.substringBefore("?")?.substringBefore("/")
    val currentNavItem = NavItem.entries.find { it.route == baseRoute }

    val mainNavItems = remember {
        listOf(
            NavItem.TERMS,
            NavItem.STANDARD_ALARM,
            NavItem.RECORD,
            NavItem.TIMER,
            NavItem.STOPWATCH,
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            NavHost(
                navController = navController,
                startDestination = NavItem.TERMS.route,
                enterTransition = { fadeIn(animationSpec = tabFadeSpec()) },
                exitTransition = { fadeOut(animationSpec = tabFadeSpec()) },
                // 下位の画面から戻るときだけ、下にいた画面も滑り込ませる。
                // 出ていく側だけが動くと、戻る動きが途中で切れて見える。
                // タブどうしの行き来はこれまでどおり動かさない
                popEnterTransition = {
                    if (initialState.destination.route in SUB_SCREEN_ROUTES) {
                        screenCloseEnter(slidePx)
                    } else {
                        EnterTransition.None
                    }
                },
                popExitTransition = { ExitTransition.None },
                predictivePopEnterTransition = { _ -> EnterTransition.None },
                predictivePopExitTransition = { _ -> screenPredictivePopExit() },
            ) {
                // 1. ターム（ホーム画面）
                composable(NavItem.TERMS.route) {
                    val scheduleStore = remember(context) { AlarmSchedulerStore(context.applicationContext) }
                    val viewModel: HomeViewModel =
                        viewModel(factory = HomeViewModelFactory(repository, scheduleStore))
                    val terms by viewModel.terms.collectAsStateWithLifecycle()
                    val activity = context as? Activity
                    val initialTermEndId = remember {
                        val id = activity?.intent?.getLongExtra(EXTRA_DEEPLINK_END_TERM_ID, -1L) ?: -1L
                        activity?.intent?.removeExtra(EXTRA_DEEPLINK_END_TERM_ID)
                        if (id >= 0) id else null
                    }
                    var termEndAlarmId by remember { mutableStateOf<Long?>(initialTermEndId) }

                    // ディープリンクまたは画面操作によるターム編集シートの表示対象
                    val initialEditTarget = remember {
                        if (activity?.intent?.hasExtra(EXTRA_DEEPLINK_ALARM_ID) == true) {
                            val id = activity.intent.getLongExtra(EXTRA_DEEPLINK_ALARM_ID, -1L)
                            activity.intent.removeExtra(EXTRA_DEEPLINK_ALARM_ID)
                            val alarmId = if (id >= 0L) id else null
                            EditTarget(alarmId = alarmId)
                        } else {
                            null
                        }
                    }
                    var editTarget by remember { mutableStateOf(initialEditTarget) }

                    HomeScreen(
                        viewModel = viewModel,
                        onAddTerm = { editTarget = EditTarget(alarmId = null) },
                        onEditTerm = { id -> editTarget = EditTarget(alarmId = id) },
                        onEndTodayTerm = { id -> termEndAlarmId = id },
                        onOpenSettings = {
                            navController.navigate(NavItem.SETTINGS.route) {
                                popUpTo(NavItem.TERMS.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenPrivacyPolicy = { navController.navigate(ROUTE_PRIVACY) },
                        onOpenAbout = { navController.navigate(ROUTE_ABOUT) },
                    )

                    editTarget?.let { target ->
                        val editViewModel: AlarmEditViewModel = viewModel(
                            key = "term_edit_${target.alarmId ?: "new"}_${target.openId}",
                            factory = AlarmEditViewModelFactory(repository, context, target.alarmId, isSingleAlarm = false),
                        )
                        AlarmEditScreen(
                            viewModel = editViewModel,
                            onClose = { editTarget = null },
                        )
                    }

                    termEndAlarmId?.let { alarmId ->
                        val targetSchedule = terms.find { it.id == alarmId }
                        if (targetSchedule != null) {
                            TermEndDialog(
                                schedule = targetSchedule,
                                repository = repository,
                                now = ZonedDateTime.now(),
                                onDismiss = { termEndAlarmId = null },
                                onStartChallenge = { challengeAlarmId ->
                                    termEndAlarmId = null
                                    navController.navigate("$ROUTE_END_TODAY_GAME/$challengeAlarmId")
                                },
                            )
                        }
                    }
                }

                // 2. 通常アラーム画面
                composable(NavItem.STANDARD_ALARM.route) {
                    val viewModel: AlarmsViewModel = viewModel(factory = AlarmsViewModelFactory(repository, remember(context) { AlarmSchedulerStore(context.applicationContext) }))
                    var editTarget by remember { mutableStateOf<EditTarget?>(null) }

                    AlarmsScreen(
                        viewModel = viewModel,
                        onAddAlarm = { editTarget = EditTarget(alarmId = null) },
                        onEditAlarm = { id -> editTarget = EditTarget(alarmId = id) },
                        onOpenSettings = {
                            navController.navigate(NavItem.SETTINGS.route) {
                                popUpTo(NavItem.TERMS.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenPrivacyPolicy = { navController.navigate(ROUTE_PRIVACY) },
                        onOpenAbout = { navController.navigate(ROUTE_ABOUT) },
                    )

                    editTarget?.let { target ->
                        val editViewModel: AlarmEditViewModel = viewModel(
                            key = "alarm_edit_${target.alarmId ?: "new"}_${target.openId}",
                            factory = AlarmEditViewModelFactory(repository, context, target.alarmId, isSingleAlarm = true),
                        )
                        AlarmEditScreen(
                            viewModel = editViewModel,
                            onClose = { editTarget = null },
                        )
                    }
                }

                // 3. 記録
                composable(NavItem.RECORD.route) {
                    val wakeRecordRepository = remember { Repositories.wakeRecord(context) }
                    val viewModel: RecordsViewModel = viewModel(
                        factory = RecordsViewModelFactory(wakeRecordRepository, repository),
                    )
                    RecordsScreen(
                        viewModel = viewModel,
                        onOpenSettings = {
                            navController.navigate(NavItem.SETTINGS.route) {
                                popUpTo(NavItem.TERMS.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenPrivacyPolicy = { navController.navigate(ROUTE_PRIVACY) },
                        onOpenAbout = { navController.navigate(ROUTE_ABOUT) },
                    )
                }

                // 4. タイマー（既存画面を流用）
                composable(NavItem.TIMER.route) {
                    val timerRepository = remember { Repositories.timer(context) }
                    val viewModel: TimerViewModel = viewModel(factory = TimerViewModelFactory(timerRepository, context))
                    TimerScreen(
                        viewModel = viewModel,
                        onOpenSettings = {
                            navController.navigate(NavItem.SETTINGS.route) {
                                popUpTo(NavItem.TERMS.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenPrivacyPolicy = { navController.navigate(ROUTE_PRIVACY) },
                        onOpenAbout = { navController.navigate(ROUTE_ABOUT) },
                    )
                }

                // 5. ストップウォッチ（既存画面を流用）
                composable(NavItem.STOPWATCH.route) {
                    val stopwatchRepository = remember { Repositories.stopwatch(context) }
                    val viewModel: StopwatchViewModel = viewModel(factory = StopwatchViewModelFactory(stopwatchRepository, context))
                    StopwatchScreen(
                        viewModel = viewModel,
                        onOpenSettings = {
                            navController.navigate(NavItem.SETTINGS.route) {
                                popUpTo(NavItem.TERMS.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenPrivacyPolicy = { navController.navigate(ROUTE_PRIVACY) },
                        onOpenAbout = { navController.navigate(ROUTE_ABOUT) },
                    )
                }

                // 6. 設定
                composable(NavItem.SETTINGS.route) {
                    val settingsRepository = remember { Repositories.settings(context) }
                    val viewModel: SettingsViewModel = viewModel(
                        factory = SettingsViewModelFactory(settingsRepository),
                    )
                    SettingsScreen(
                        viewModel = viewModel,
                        onOpenGameList = { navController.navigate(ROUTE_GAME_LIST) },
                        onBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(NavItem.TERMS.route) {
                                    popUpTo(NavItem.TERMS.route) { inclusive = true }
                                }
                            }
                        },
                    )
                }

                // ミニゲーム選択画面
                composable(
                    ROUTE_GAME_LIST,
                    enterTransition = { screenOpenEnter(slidePx) },
                    exitTransition = { screenOpenExit(slidePx) },
                    popEnterTransition = { screenCloseEnter(slidePx) },
                    popExitTransition = { screenCloseExit(slidePx) },
                ) {
                    val settingsRepository = remember { Repositories.settings(context) }
                    val viewModel: SettingsViewModel = viewModel(
                        factory = SettingsViewModelFactory(settingsRepository),
                    )
                    GameListScreen(
                        viewModel = viewModel,
                        hasShakeSensor = hasShakeSensor,
                        onBack = { navController.popBackStack() },
                    )
                }

                // 当日終了のゲーム画面
                composable(
                    route = "$ROUTE_END_TODAY_GAME/{$ARG_ALARM_ID}",
                    arguments = listOf(navArgument(ARG_ALARM_ID) { type = NavType.LongType }),
                    enterTransition = { screenOpenEnter(slidePx) },
                    exitTransition = { screenOpenExit(slidePx) },
                    popEnterTransition = { screenCloseEnter(slidePx) },
                    popExitTransition = { screenCloseExit(slidePx) },
                ) { backStackEntry ->
                    val alarmId = backStackEntry.arguments?.getLong(ARG_ALARM_ID) ?: return@composable
                    val viewModel: SkipGameViewModel = viewModel(factory = SkipGameViewModelFactory(repository, context, alarmId, hasShakeSensor))
                    SkipGameScreen(viewModel = viewModel, onClose = { navController.popBackStack() })
                }

                // ライセンス情報画面
                composable(
                    ROUTE_ABOUT,
                    enterTransition = { screenOpenEnter(slidePx) },
                    exitTransition = { screenOpenExit(slidePx) },
                    popEnterTransition = { screenCloseEnter(slidePx) },
                    popExitTransition = { screenCloseExit(slidePx) },
                ) {
                    AboutScreen(onBack = { navController.popBackStack() })
                }

                // プライバシーポリシー画面
                composable(
                    ROUTE_PRIVACY,
                    enterTransition = { screenOpenEnter(slidePx) },
                    exitTransition = { screenOpenExit(slidePx) },
                    popEnterTransition = { screenCloseEnter(slidePx) },
                    popExitTransition = { screenCloseExit(slidePx) },
                ) {
                    PrivacyScreen(onBack = { navController.popBackStack() })
                }
            }
        }

        // 5つの主要画面を表示している場合に下の帯を表示する
        if (currentNavItem != null && currentNavItem in mainNavItems) {
            TermAlarmBottomBar(
                selectedItem = currentNavItem,
                onSelectItem = { item ->
                    navController.navigate(item.route) {
                        popUpTo(NavItem.TERMS.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}
