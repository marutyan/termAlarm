package com.marutyan.termalarm.ui.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.marutyan.termalarm.data.Repositories
import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.data.SettingsRepository
import com.marutyan.termalarm.ui.settings.SettingsScreen
import com.marutyan.termalarm.ui.settings.SettingsViewModel
import com.marutyan.termalarm.ui.settings.SettingsViewModelFactory
import com.marutyan.termalarm.ui.about.AboutScreen
import com.marutyan.termalarm.ui.privacy.PrivacyScreen
import com.marutyan.termalarm.ui.alarmedit.AlarmEditScreen
import com.marutyan.termalarm.ui.alarmedit.AlarmEditViewModel
import com.marutyan.termalarm.ui.alarmedit.AlarmEditViewModelFactory
import com.marutyan.termalarm.ui.alarmlist.AlarmListScreen
import com.marutyan.termalarm.ui.alarmlist.AlarmListViewModel
import com.marutyan.termalarm.ui.alarmlist.AlarmListViewModelFactory
import com.marutyan.termalarm.ui.alarmlist.TermAlarmBottomBar
import com.marutyan.termalarm.ui.alarmlist.TermAlarmTab
import com.marutyan.termalarm.ui.clock.ClockScreen
import com.marutyan.termalarm.ui.clock.ClockViewModel
import com.marutyan.termalarm.ui.clock.ClockViewModelFactory
import com.marutyan.termalarm.data.ClockSettingsRepository
import com.marutyan.termalarm.ui.common.PlaceholderTabScreen
import com.marutyan.termalarm.ui.permission.ExactAlarmPermissionBanner
import com.marutyan.termalarm.ui.permission.NotificationPermissionBanner
import com.marutyan.termalarm.ui.skipgame.SkipGameScreen
import com.marutyan.termalarm.ui.skipgame.SkipGameViewModel
import com.marutyan.termalarm.ui.skipgame.SkipGameViewModelFactory
import com.marutyan.termalarm.data.StopwatchRepository
import com.marutyan.termalarm.data.TimerRepository
import com.marutyan.termalarm.ui.stopwatch.StopwatchScreen
import com.marutyan.termalarm.ui.stopwatch.StopwatchViewModel
import com.marutyan.termalarm.ui.stopwatch.StopwatchViewModelFactory
import com.marutyan.termalarm.ui.timer.TimerScreen
import com.marutyan.termalarm.ui.timer.TimerViewModel
import com.marutyan.termalarm.ui.timer.TimerViewModelFactory
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.res.stringResource
import com.marutyan.termalarm.R
import com.marutyan.termalarm.ui.theme.screenPopFadeSpec
import com.marutyan.termalarm.ui.theme.tabFadeSpec

private const val ROUTE_LIST = "list"
private const val ROUTE_EDIT = "edit"
private const val ROUTE_SKIP_GAME = "skipGame"
private const val ROUTE_ABOUT = "about"
// プライバシーポリシー画面への遷移ルート。各タブ右上の「⋮」メニューから開く画面を識別するために定義する。
private const val ROUTE_PRIVACY = "privacy"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_CLOCK = "clock"
private const val ROUTE_TIMER = "timer"
private const val ROUTE_STOPWATCH = "stopwatch"
private const val ARG_ALARM_ID = "alarmId"

// SET_ALARM等の外部インテントを受けたAlarmIntentActivity(ui.intent)がMainActivity起動時に付ける拡張。
// 値が-1なら新規作成画面、0以上ならそのidの編集画面へ直接遷移する。他パッケージから参照するためpublic。
const val EXTRA_DEEPLINK_ALARM_ID = "com.marutyan.termalarm.ui.EXTRA_DEEPLINK_ALARM_ID"

// どのタブを開いた状態で始めるかを指定する拡張。TermAlarmTabの名前(ALARM/CLOCK/TIMER/STOPWATCH)を入れる。
// タイマーの通知から開いたときにタイマータブが出るようにするために使う。他パッケージから参照するためpublic。
const val EXTRA_DEEPLINK_TAB = "com.marutyan.termalarm.ui.EXTRA_DEEPLINK_TAB"

/**
 * アプリ全体の画面遷移。アラーム一覧を起点に、追加・編集、当日終了ゲーム、ライセンス表示、
 * 下部ナビの4タブ(アラーム/時計/タイマー/ストップウォッチ)を1つのNavHostへまとめる。
 * domain/data層への依存はrepositoryを通じて各ViewModelへ配る(依存注入フレームワークは使わない)。
 */
@Composable
fun TermAlarmNavHost(repository: AlarmRepository, hasShakeSensor: Boolean) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // AlarmIntentActivity経由でMainActivityが起動された場合、起動intentのEXTRA_DEEPLINK_ALARM_IDを見て
    // アラーム一覧の代わりに編集画面(または新規作成画面)へ直接遷移する。通常起動時はこの拡張が付かないため
    // 一覧のままになる(docs/SPEC.md「SET_ALARMの扱い」: 時刻指定が無い/確認UIを出す場合に編集画面を開く)。
    LaunchedEffect(Unit) {
        val launchIntent = (context as? Activity)?.intent
        if (launchIntent?.hasExtra(EXTRA_DEEPLINK_ALARM_ID) == true) {
            val id = launchIntent.getLongExtra(EXTRA_DEEPLINK_ALARM_ID, -1L)
            navController.navigate(if (id >= 0) "$ROUTE_EDIT?$ARG_ALARM_ID=$id" else ROUTE_EDIT)
        }
        // 通知から開いたときは、そのタブを出す。知らない名前が入っていた場合は一覧のままにする
        launchIntent?.getStringExtra(EXTRA_DEEPLINK_TAB)?.let { name ->
            runCatching { TermAlarmTab.valueOf(name) }.getOrNull()?.let { tab ->
                navController.navigate(routeOf(tab))
            }
        }
    }

    fun goToTab(tab: TermAlarmTab) {
        navController.navigate(routeOf(tab)) {
            popUpTo(ROUTE_LIST) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_LIST,
        // タブを移るとき、画面を横へ滑らせない。純正も滑らせず、その場で入れ替わる。
        // 横に動くと、押した場所と違うところが動いて見えて落ち着かない
        enterTransition = { fadeIn(animationSpec = tabFadeSpec()) },
        exitTransition = { fadeOut(animationSpec = tabFadeSpec()) },
        // 戻るときは待たせない。前の画面はそのまま出し、閉じる画面だけがすっと消える
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { fadeOut(animationSpec = screenPopFadeSpec()) },
    ) {
        composable(ROUTE_LIST) {
            val viewModel: AlarmListViewModel = viewModel(factory = AlarmListViewModelFactory(repository, context))
            AlarmListScreen(
                viewModel = viewModel,
                onAddAlarm = { navController.navigate(ROUTE_EDIT) },
                onEditAlarm = { id -> navController.navigate("$ROUTE_EDIT?$ARG_ALARM_ID=$id") },
                onOpenAbout = { navController.navigate(ROUTE_ABOUT) },
                onOpenPrivacyPolicy = { navController.navigate(ROUTE_PRIVACY) },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onNavigateToSkipGame = { id -> navController.navigate("$ROUTE_SKIP_GAME/$id") },
                exactAlarmBanner = { ExactAlarmPermissionBanner() },
                notificationPermissionBanner = { NotificationPermissionBanner() },
                bottomBar = { TermAlarmBottomBar(selected = TermAlarmTab.ALARM, onSelect = ::goToTab) },
            )
        }
        composable(ROUTE_CLOCK) {
            // AlarmDatabaseは共有シングルトンのため、ここでdaoを取り出して組み立てる
            // (MainActivityの配線は変えず、時計タブの行だけで完結させる)
            val clockRepository = remember {
                Repositories.clockSettings(context)
            }
            val clockSettingsRepository = remember {
                Repositories.settings(context)
            }
            val viewModel: ClockViewModel =
                viewModel(factory = ClockViewModelFactory(clockRepository, clockSettingsRepository))
            ClockScreen(
                viewModel = viewModel,
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onOpenPrivacyPolicy = { navController.navigate(ROUTE_PRIVACY) },
                onOpenAbout = { navController.navigate(ROUTE_ABOUT) },
                bottomBar = { TermAlarmBottomBar(TermAlarmTab.CLOCK, ::goToTab) },
            )
        }
        composable(ROUTE_TIMER) {
            val timerRepository = remember { Repositories.timer(context) }
            val viewModel: TimerViewModel = viewModel(factory = TimerViewModelFactory(timerRepository, context))
            TimerScreen(
                viewModel = viewModel,
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onOpenPrivacyPolicy = { navController.navigate(ROUTE_PRIVACY) },
                onOpenAbout = { navController.navigate(ROUTE_ABOUT) },
                bottomBar = { TermAlarmBottomBar(TermAlarmTab.TIMER, ::goToTab) },
            )
        }
        composable(ROUTE_STOPWATCH) {
            val stopwatchRepository = remember { Repositories.stopwatch(context) }
            val viewModel: StopwatchViewModel = viewModel(factory = StopwatchViewModelFactory(stopwatchRepository, context))
            StopwatchScreen(
                viewModel = viewModel,
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onOpenPrivacyPolicy = { navController.navigate(ROUTE_PRIVACY) },
                onOpenAbout = { navController.navigate(ROUTE_ABOUT) },
                bottomBar = { TermAlarmBottomBar(TermAlarmTab.STOPWATCH, ::goToTab) },
            )
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
        composable(ROUTE_SETTINGS) {
            // 設定は全体で1つなので、ここでRepositoryを組み立てて渡す
            val settingsRepository = remember {
                Repositories.settings(context)
            }
            val clockRepository = remember {
                Repositories.clockSettings(context)
            }
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(settingsRepository, clockRepository),
            )
            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(ROUTE_ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_PRIVACY) {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }
    }
}

// タブと画面の対応。下部ナビからの移動と、通知から開いたときの移動の両方で使う
private fun routeOf(tab: TermAlarmTab): String = when (tab) {
    TermAlarmTab.ALARM -> ROUTE_LIST
    TermAlarmTab.CLOCK -> ROUTE_CLOCK
    TermAlarmTab.TIMER -> ROUTE_TIMER
    TermAlarmTab.STOPWATCH -> ROUTE_STOPWATCH
}

