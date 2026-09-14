package com.marutyan.termalarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.marutyan.termalarm.alarm.AlarmScheduler
import com.marutyan.termalarm.data.Repositories
import com.marutyan.termalarm.domain.AppSettings
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.marutyan.termalarm.timer.TimerActions
import kotlinx.coroutines.launch
import com.marutyan.termalarm.ui.navigation.TermAlarmNavHost
import com.marutyan.termalarm.ui.skipgame.hasShakeSensor
import com.marutyan.termalarm.ui.theme.TermAlarmTheme

/**
 * アプリの起点となるActivity。Repositoryを1つだけ組み立ててNavHostへ渡す(依存注入フレームワークは使わない手作り配線)。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ステータスバー/ナビゲーションバーの裏まで描画するエッジツーエッジ表示を有効化
        enableEdgeToEdge()
        // 動いているタイマーがあれば、その通知を出し直す。
        // 通知を消してしまっても、アプリを開けば戻るようにするため
        lifecycleScope.launch { TimerActions.refreshNotification(applicationContext) }
        // アラームの予約をすべて入れ直す。
        // 強制停止や電池最適化で予約が消えても、端末を再起動せずアプリを開くだけで戻るようにする。
        // 同じ宛先への登録は上書きになるので、重ねて入ることはない
        lifecycleScope.launch { AlarmScheduler.rescheduleAll(applicationContext) }
        setContent {
            val context = LocalContext.current
            // 設定で選んだ配色を反映する。読み込みが終わるまでは既定値のまま描く
            val settings by remember { Repositories.settings(context).observe() }
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            TermAlarmTheme(appTheme = settings.theme) {
                // 明るい配色のときは、状態表示の文字と記号を暗くしないと地に埋もれて読めない
                val isLightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f
                LaunchedEffect(isLightSurface) {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = isLightSurface
                        isAppearanceLightNavigationBars = isLightSurface
                    }
                }
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val repository = remember { Repositories.alarm(context) }
                    val hasShakeSensor = remember { hasShakeSensor(context) }
                    TermAlarmNavHost(repository = repository, hasShakeSensor = hasShakeSensor)
                }
            }
        }
    }
}
