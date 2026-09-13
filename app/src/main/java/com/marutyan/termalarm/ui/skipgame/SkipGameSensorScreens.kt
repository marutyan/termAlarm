package com.marutyan.termalarm.ui.skipgame

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.GameQuestion
import com.marutyan.termalarm.ui.theme.customColors
import com.marutyan.termalarm.ui.theme.ibmPlexMonoFontFamily
import kotlin.math.sqrt

// 振る動作を検出するための合成加速度しきい値（G単位）
private const val SHAKE_THRESHOLD_G = 2.2f

// 1回の振りを重複カウントしないための最小時間間隔（ミリ秒）
private const val SHAKE_DEBOUNCE_MILLIS = 400L

// 歩行の1歩を検出するための合成加速度しきい値（m/s^2単位）
private const val WALK_STEP_THRESHOLD_MS2 = 11.5f

// 歩行の1歩を重複カウントしないための最小時間間隔（ミリ秒）
private const val WALK_DEBOUNCE_MILLIS = 300L

/**
 * 端末に加速度センサー（Sensor.TYPE_ACCELEROMETER）が存在するかを判定する。
 * センサーが存在しない端末においてSHAKE_DEVICEやWALKを出題候補から除外するために用いる。
 */
fun hasShakeSensor(context: Context): Boolean {
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
    return sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
}

/**
 * 端末を振るゲーム（GameQuestion.ShakeDevice）の画面Composable。
 * 加速度センサーで規定の振り回数を検知し、達成時にその回数を回答として提出するために用いる。
 */
@Composable
internal fun ShakeDeviceGameContent(
    question: GameQuestion.ShakeDevice,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var shakeCount by rememberSaveable(question) { mutableIntStateOf(0) }

    DisposableEffect(question) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
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
        onDispose { sensorManager?.unregisterListener(listener) }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        GameKindBadge(title = stringResource(R.string.game_kind_shake_device))

        Text(
            text = stringResource(R.string.shake_device_prompt),
            style = TextStyle(
                fontSize = 13.sp,
                color = MaterialTheme.customColors.subtleText,
            ),
        )

        // 振った回数と目標回数の進捗表示
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.shake_device_progress, shakeCount, question.requiredShakes),
                style = TextStyle(
                    fontFamily = ibmPlexMonoFontFamily(300),
                    fontSize = 44.sp,
                    fontFeatureSettings = "tnum",
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }

        // 達成度合いを示すプログレスバー
        val progress = (shakeCount.toFloat() / question.requiredShakes).coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainer,
        )
    }
}

/**
 * 歩くゲーム（GameQuestion.Walk）の画面Composable。
 * 権限追加を伴わずに加速度センサーから歩数を検出し、規定歩数に達した時点で回答を提出するために用いる。
 */
@Composable
internal fun WalkGameContent(
    question: GameQuestion.Walk,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var stepCount by rememberSaveable(question) { mutableIntStateOf(0) }

    DisposableEffect(question) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var lastStepAtMillis = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt(x * x + y * y + z * z)

                if (magnitude > WALK_STEP_THRESHOLD_MS2) {
                    val now = System.currentTimeMillis()
                    if (now - lastStepAtMillis > WALK_DEBOUNCE_MILLIS) {
                        lastStepAtMillis = now
                        stepCount += 1
                        if (stepCount >= question.requiredSteps) {
                            onSubmit(stepCount.toString())
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sensorManager?.unregisterListener(listener) }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        GameKindBadge(title = stringResource(R.string.game_kind_walk))

        Text(
            text = stringResource(R.string.walk_prompt),
            style = TextStyle(
                fontSize = 13.sp,
                color = MaterialTheme.customColors.subtleText,
            ),
        )

        // 歩数進捗表示
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.walk_progress, stepCount, question.requiredSteps),
                style = TextStyle(
                    fontFamily = ibmPlexMonoFontFamily(300),
                    fontSize = 44.sp,
                    fontFeatureSettings = "tnum",
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }

        // 達成度合いを示すプログレスバー
        val progress = (stepCount.toFloat() / question.requiredSteps).coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainer,
        )
    }
}
