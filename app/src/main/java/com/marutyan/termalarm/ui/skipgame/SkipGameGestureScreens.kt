package com.marutyan.termalarm.ui.skipgame

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.GameQuestion
import com.marutyan.termalarm.ui.theme.tabularNums
import kotlin.math.sqrt

/**
 * 端末にSHAKE_DEVICE用の加速度センサー(TYPE_ACCELEROMETER)があるかを判定する。
 * SkipGameのViewModel生成時に呼び出し側(NavHost)がこれを渡し、無い端末では出題候補から除外する
 * (docs/SPEC.md「端末を振るはセンサーの無い端末では出題しない」)。
 */
fun hasShakeSensor(context: Context): Boolean {
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
    return sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
}

// 順にタップ:1〜12をシャッフルして並べ、昇順にタップさせる
@Composable
fun SequentialTapGame(question: GameQuestion.SequentialTap, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    // 次にタップすべき数(1始まり)。ずれたタップがあれば即座に最初からやり直す(SPEC「即時やり直しは画面側の判断でよい」)
    var nextExpected by rememberSaveable(question) { mutableIntStateOf(1) }
    var tappedOrder by rememberSaveable(question) { mutableStateOf(listOf<Int>()) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.game_kind_sequential_tap), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.sequential_tap_next, nextExpected), style = MaterialTheme.typography.bodyMedium)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().height(320.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(question.shuffledNumbers) { number ->
                val done = number in tappedOrder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(enabled = !done) {
                            if (number == nextExpected) {
                                val updated = tappedOrder + number
                                tappedOrder = updated
                                if (updated.size == question.shuffledNumbers.size) {
                                    onSubmit(updated.joinToString(","))
                                } else {
                                    nextExpected += 1
                                }
                            } else {
                                // 順序を誤ったので最初からやり直す
                                tappedOrder = emptyList()
                                nextExpected = 1
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        number.toString(),
                        style = MaterialTheme.typography.headlineSmall.tabularNums(),
                        color = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        androidx.compose.material3.TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
    }
}

// 端末を振る:加速度センサーの合成加速度がしきい値を超えた回数を数える
@Composable
fun ShakeDeviceGame(question: GameQuestion.ShakeDevice, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var shakeCount by rememberSaveable(question) { mutableIntStateOf(0) }

    DisposableEffect(question) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        // 前回検知からの再検知までの猶予(ms)。1回の振りで複数回カウントしてしまうのを防ぐ
        var lastShakeAtMillis = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val gX = event.values[0] / SensorManager.GRAVITY_EARTH
                val gY = event.values[1] / SensorManager.GRAVITY_EARTH
                val gZ = event.values[2] / SensorManager.GRAVITY_EARTH
                val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
                if (gForce > SHAKE_THRESHOLD_G) {
                    val now = System.currentTimeMillis()
                    if (now - lastShakeAtMillis > SHAKE_DEBOUNCE_MILLIS) {
                        lastShakeAtMillis = now
                        shakeCount += 1
                        if (shakeCount >= question.requiredShakes) {
                            onSubmit(shakeCount.toString())
                        }
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(stringResource(R.string.game_kind_shake_device), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.shake_device_progress, shakeCount, question.requiredShakes),
            style = MaterialTheme.typography.displaySmall.tabularNums(),
        )
        LinearProgressIndicator(
            progress = { (shakeCount.toFloat() / question.requiredShakes).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        androidx.compose.material3.TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
    }
}

private const val SHAKE_THRESHOLD_G = 2.2f
private const val SHAKE_DEBOUNCE_MILLIS = 400L

// 色と文字(ストループ課題):文字の意味と異なる色で表示された語の「文字色」を選ばせる
@Composable
fun ColorWordGame(question: GameQuestion.ColorWord, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Text(stringResource(R.string.game_kind_color_word), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.color_word_prompt), style = MaterialTheme.typography.bodyMedium)
        Text(
            question.word,
            style = MaterialTheme.typography.displayLarge,
            color = colorForName(question.displayColor),
        )
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            question.choices.forEach { choice ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onSubmit(choice) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(choice, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        androidx.compose.material3.TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
    }
}

// ストループ課題で使う色名(domain.Game.ktのSTROOP_COLOR_NAMESと同じ「赤・青・緑・黄・紫・橙」)を実際の色へ変換する。
// ゲームの成立に必要な固定の色見本のため、テーマトークンではなくハードコードした色を使う
private fun colorForName(name: String): Color = when (name) {
    "赤" -> Color(0xFFE53935)
    "青" -> Color(0xFF1E88E5)
    "緑" -> Color(0xFF43A047)
    "黄" -> Color(0xFFFDD835)
    "紫" -> Color(0xFF8E24AA)
    "橙" -> Color(0xFFFB8C00)
    else -> Color.Black
}
