package com.marutyan.termalarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.AlarmRepository
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
        setContent {
            TermAlarmTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val context = LocalContext.current
                    val repository = remember { AlarmRepository(AlarmDatabase.getInstance(context).alarmDao()) }
                    val hasShakeSensor = remember { hasShakeSensor(context) }
                    TermAlarmNavHost(repository = repository, hasShakeSensor = hasShakeSensor)
                }
            }
        }
    }
}
