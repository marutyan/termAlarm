package com.marutyan.termalarm.ui.navigation

import android.app.Activity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
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
import com.marutyan.termalarm.ui.common.PlaceholderScreen
import com.marutyan.termalarm.ui.home.HomeScreen
import com.marutyan.termalarm.ui.home.HomeViewModel
import com.marutyan.termalarm.ui.home.HomeViewModelFactory
import com.marutyan.termalarm.ui.privacy.PrivacyScreen
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

private const val ROUTE_EDIT = "edit"
private const val ROUTE_END_TODAY_GAME = "endTodayGame"
private const val ROUTE_ABOUT = "about"
private const val ROUTE_PRIVACY = "privacy"
private const val ARG_ALARM_ID = "alarmId"

// SET_ALARM等の外部インテントを受けたAlarmIntentActivity(ui.intent)がMainActivity起動時に付ける拡張。
// 値が-1なら新規作成画面、0以上ならそのidの編集画面へ直接遷移する。他パッケージから参照するためpublic。
const val EXTRA_DEEPLINK_ALARM_ID = "com.marutyan.termalarm.ui.EXTRA_DEEPLINK_ALARM_ID"

// どの画面を開いた状態で始めるかを指定する拡張。タイマーの通知から開いたときにタイマー画面が出るようにするために使う。
const val EXTRA_DEEPLINK_TAB = "com.marutyan.termalarm.ui.EXTRA_DEEPLINK_TAB"

/**
 * アプリ全体の画面遷移を管理するNavHost。
 * 左側に幅54dpの縦ナビ(TermAlarmNavRail)を配し、右側に主要画面または個別機能画面を横並びで表示する。
 */
@Composable
fun TermAlarmNavHost(
    repository: AlarmRepository,
    hasShakeSensor: Boolean,
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // 起動時のディープリンクIntentを処理し、指定された画面や編集画面へ直接遷移する
    LaunchedEffect(Unit) {
        val launchIntent = (context as? Activity)?.intent
        if (launchIntent?.hasExtra(EXTRA_DEEPLINK_ALARM_ID) == true) {
            val id = launchIntent.getLongExtra(EXTRA_DEEPLINK_ALARM_ID, -1L)
            navController.navigate(if (id >= 0) "$ROUTE_EDIT?$ARG_ALARM_ID=$id" else ROUTE_EDIT)
        }
        launchIntent?.getStringExtra(EXTRA_DEEPLINK_TAB)?.let { name ->
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

    // 現在のバックスタックエントリからベースルートを判定し、縦ナビを表示すべき主要画面かを特定する
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val baseRoute = currentRoute?.substringBefore("?")?.substringBefore("/")
    val currentNavItem = NavItem.entries.find { it.route == baseRoute }

    // 6つの主要画面のいずれかを表示している場合に縦ナビを表示する
    val showNavRail = currentNavItem != null

    Row(modifier = Modifier.fillMaxSize()) {
        if (currentNavItem != null) {
            TermAlarmNavRail(
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

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            NavHost(
                navController = navController,
                startDestination = NavItem.TERMS.route,
                enterTransition = { fadeIn(animationSpec = tabFadeSpec()) },
                exitTransition = { fadeOut(animationSpec = tabFadeSpec()) },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
                predictivePopEnterTransition = { _ -> EnterTransition.None },
                predictivePopExitTransition = { _ -> screenPredictivePopExit() },
            ) {
                // 1. ターム（ホーム画面）
                composable(NavItem.TERMS.route) {
                    val viewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(repository))
                    HomeScreen(
                        viewModel = viewModel,
                        onAddTerm = { navController.navigate(ROUTE_EDIT) },
                        onEditTerm = { id -> navController.navigate("$ROUTE_EDIT?$ARG_ALARM_ID=$id") },
                        onEndTodayTerm = { id -> navController.navigate("$ROUTE_END_TODAY_GAME/$id") },
                    )
                }

                // 2. 通常アラーム（準備中プレースホルダ）
                composable(NavItem.STANDARD_ALARM.route) {
                    PlaceholderScreen()
                }

                // 3. 記録（準備中プレースホルダ）
                composable(NavItem.RECORD.route) {
                    PlaceholderScreen()
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
                        bottomBar = {},
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
                        bottomBar = {},
                    )
                }

                // 6. 設定（既存画面を流用）
                composable(NavItem.SETTINGS.route) {
                    val settingsRepository = remember { Repositories.settings(context) }
                    val clockRepository = remember { Repositories.clockSettings(context) }
                    val viewModel: SettingsViewModel = viewModel(
                        factory = SettingsViewModelFactory(settingsRepository, clockRepository),
                    )
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(NavItem.TERMS.route) {
                                    popUpTo(NavItem.TERMS.route) { inclusive = true }
                                }
                            }
                        },
                    )
                }

                // ターム編集画面
                composable(
                    route = "$ROUTE_EDIT?$ARG_ALARM_ID={$ARG_ALARM_ID}",
                    arguments = listOf(navArgument(ARG_ALARM_ID) { type = NavType.LongType; defaultValue = -1L }),
                    enterTransition = { screenOpenEnter(slidePx) },
                    exitTransition = { screenOpenExit(slidePx) },
                    popEnterTransition = { screenCloseEnter(slidePx) },
                    popExitTransition = { screenCloseExit(slidePx) },
                ) { backStackEntry ->
                    val rawId = backStackEntry.arguments?.getLong(ARG_ALARM_ID) ?: -1L
                    val alarmId = rawId.takeIf { it >= 0 }
                    val viewModel: AlarmEditViewModel = viewModel(factory = AlarmEditViewModelFactory(repository, context, alarmId))
                    AlarmEditScreen(viewModel = viewModel, onClose = { navController.popBackStack() })
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
    }
}
