package com.marutyan.termalarm.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.ui.about.AboutScreen
import com.marutyan.termalarm.ui.alarmedit.AlarmEditScreen
import com.marutyan.termalarm.ui.alarmedit.AlarmEditViewModel
import com.marutyan.termalarm.ui.alarmedit.AlarmEditViewModelFactory
import com.marutyan.termalarm.ui.alarmlist.AlarmListScreen
import com.marutyan.termalarm.ui.alarmlist.AlarmListViewModel
import com.marutyan.termalarm.ui.alarmlist.AlarmListViewModelFactory
import com.marutyan.termalarm.ui.alarmlist.TermAlarmBottomBar
import com.marutyan.termalarm.ui.alarmlist.TermAlarmTab
import com.marutyan.termalarm.ui.common.PlaceholderTabScreen
import com.marutyan.termalarm.ui.permission.ExactAlarmPermissionBanner
import com.marutyan.termalarm.ui.permission.NotificationPermissionBanner
import com.marutyan.termalarm.ui.skipgame.SkipGameScreen
import com.marutyan.termalarm.ui.skipgame.SkipGameViewModel
import com.marutyan.termalarm.ui.skipgame.SkipGameViewModelFactory
import androidx.compose.ui.res.stringResource
import com.marutyan.termalarm.R

private const val ROUTE_LIST = "list"
private const val ROUTE_EDIT = "edit"
private const val ROUTE_SKIP_GAME = "skipGame"
private const val ROUTE_ABOUT = "about"
private const val ROUTE_CLOCK = "clock"
private const val ROUTE_TIMER = "timer"
private const val ROUTE_STOPWATCH = "stopwatch"
private const val ARG_ALARM_ID = "alarmId"

/**
 * アプリ全体の画面遷移。アラーム一覧を起点に、追加・編集、当日終了ゲーム、ライセンス表示、
 * 下部ナビの4タブ(アラーム/時計/タイマー/ストップウォッチ)を1つのNavHostへまとめる。
 * domain/data層への依存はrepositoryを通じて各ViewModelへ配る(依存注入フレームワークは使わない)。
 */
@Composable
fun TermAlarmNavHost(repository: AlarmRepository, hasShakeSensor: Boolean) {
    val navController = rememberNavController()
    val context = LocalContext.current

    fun goToTab(tab: TermAlarmTab) {
        val route = when (tab) {
            TermAlarmTab.ALARM -> ROUTE_LIST
            TermAlarmTab.CLOCK -> ROUTE_CLOCK
            TermAlarmTab.TIMER -> ROUTE_TIMER
            TermAlarmTab.STOPWATCH -> ROUTE_STOPWATCH
        }
        navController.navigate(route) {
            popUpTo(ROUTE_LIST) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavHost(navController = navController, startDestination = ROUTE_LIST) {
        composable(ROUTE_LIST) {
            val viewModel: AlarmListViewModel = viewModel(factory = AlarmListViewModelFactory(repository, context))
            AlarmListScreen(
                viewModel = viewModel,
                onAddAlarm = { navController.navigate(ROUTE_EDIT) },
                onEditAlarm = { id -> navController.navigate("$ROUTE_EDIT?$ARG_ALARM_ID=$id") },
                onOpenAbout = { navController.navigate(ROUTE_ABOUT) },
                onNavigateToSkipGame = { id -> navController.navigate("$ROUTE_SKIP_GAME/$id") },
                exactAlarmBanner = { ExactAlarmPermissionBanner() },
                notificationPermissionBanner = { NotificationPermissionBanner() },
                bottomBar = { TermAlarmBottomBar(selected = TermAlarmTab.ALARM, onSelect = ::goToTab) },
            )
        }
        composable(ROUTE_CLOCK) {
            PlaceholderTabScreen(stringResource(R.string.tab_clock)) { TermAlarmBottomBar(TermAlarmTab.CLOCK, ::goToTab) }
        }
        composable(ROUTE_TIMER) {
            PlaceholderTabScreen(stringResource(R.string.tab_timer)) { TermAlarmBottomBar(TermAlarmTab.TIMER, ::goToTab) }
        }
        composable(ROUTE_STOPWATCH) {
            PlaceholderTabScreen(stringResource(R.string.tab_stopwatch)) { TermAlarmBottomBar(TermAlarmTab.STOPWATCH, ::goToTab) }
        }
        composable(
            route = "$ROUTE_EDIT?$ARG_ALARM_ID={$ARG_ALARM_ID}",
            arguments = listOf(navArgument(ARG_ALARM_ID) { type = NavType.LongType; defaultValue = -1L }),
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getLong(ARG_ALARM_ID) ?: -1L
            val alarmId = rawId.takeIf { it >= 0 }
            val viewModel: AlarmEditViewModel = viewModel(factory = AlarmEditViewModelFactory(repository, context, alarmId))
            AlarmEditScreen(viewModel = viewModel, onClose = { navController.popBackStack() })
        }
        composable(
            route = "$ROUTE_SKIP_GAME/{$ARG_ALARM_ID}",
            arguments = listOf(navArgument(ARG_ALARM_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val alarmId = backStackEntry.arguments?.getLong(ARG_ALARM_ID) ?: return@composable
            val viewModel: SkipGameViewModel = viewModel(factory = SkipGameViewModelFactory(repository, context, alarmId, hasShakeSensor))
            SkipGameScreen(viewModel = viewModel, onClose = { navController.popBackStack() })
        }
        composable(ROUTE_ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
