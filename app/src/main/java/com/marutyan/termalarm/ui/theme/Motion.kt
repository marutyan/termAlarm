package com.marutyan.termalarm.ui.theme

import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset

// タブ間を切り替える画面遷移のアニメーション時間(ミリ秒)。滑らかな横スライドを実現するために定義する。
const val TAB_TRANSITION_DURATION_MS = 300

// タイマー新規追加画面の表示・非表示アニメーション時間(ミリ秒)。下からの出現と上への消去に合わせるために定義する。
const val TIMER_ADD_TRANSITION_DURATION_MS = 300

// ボタンを押下した際の縮小倍率。指の接触に応じた適度な押し込み感を出すために定義する。
const val BUTTON_PRESS_SCALE = 0.92f

// ボタンを押下した際と離した際のアニメーション時間(ミリ秒)。機敏なフィードバックを返すために定義する。
const val BUTTON_PRESS_DURATION_MS = 100

// タイマーの輪の進捗角度を補間する時間(ミリ秒)。1秒間隔の更新を滑らかな連続移動にするために定義する。
const val TIMER_PROGRESS_DURATION_MS = 1000

// 時計のアナログ・デジタル表示を切り替えるアニメーション時間(ミリ秒)。自然な拡大縮小フェードにするために定義する。
const val CLOCK_MODE_TRANSITION_DURATION_MS = 300

// アラーム一覧のスイッチ切り替え時に時刻等の色を遷移させる時間(ミリ秒)。急激な明度変化を和らげるために定義する。
const val ALARM_COLOR_TRANSITION_DURATION_MS = 250

/**
 * タブ切り替え時の横スライド移動量を補間するAnimationSpecを生成する。
 * 減速から始まる曲線(FastOutSlowInEasing)で300msかけて滑らかに遷移させる。
 */
fun tabSlideSpec(): TweenSpec<IntOffset> = tween(
    durationMillis = TAB_TRANSITION_DURATION_MS,
    easing = FastOutSlowInEasing,
)

/**
 * タブ切り替え時のフェードイン・フェードアウトを補間するAnimationSpecを生成する。
 * 横スライドと協調して画面内容の切り替わりを自然に見せる。
 */
fun tabFadeSpec(): TweenSpec<Float> = tween(
    durationMillis = TAB_TRANSITION_DURATION_MS,
    easing = FastOutSlowInEasing,
)

/**
 * タイマー追加画面の上下スライドを補間するAnimationSpecを生成する。
 * 下から滑らかに現れ、上へ抜ける動きを作る。
 */
fun timerAddSlideSpec(): TweenSpec<IntOffset> = tween(
    durationMillis = TIMER_ADD_TRANSITION_DURATION_MS,
    easing = FastOutSlowInEasing,
)

/**
 * タイマー追加画面のフェードを補間するAnimationSpecを生成する。
 * スライドと連動して画面の透明度を自然に変化させる。
 */
fun timerAddFadeSpec(): TweenSpec<Float> = tween(
    durationMillis = TIMER_ADD_TRANSITION_DURATION_MS,
    easing = FastOutSlowInEasing,
)

/**
 * ボタン押下時の縮小・復元アニメーションを補間するAnimationSpecを生成する。
 * 100msの素早い応答でタッチ操作の手応えを伝える。
 */
fun buttonPressAnimationSpec(): TweenSpec<Float> = tween(
    durationMillis = BUTTON_PRESS_DURATION_MS,
    easing = FastOutSlowInEasing,
)

/**
 * タイマーの円形プログレス角度を補間するAnimationSpecを生成する。
 * 1秒かけて等速(LinearEasing)で次の角度へ直進させ、針が飛ぶ現象を解消する。
 */
fun timerProgressAnimationSpec(): TweenSpec<Float> = tween(
    durationMillis = TIMER_PROGRESS_DURATION_MS,
    easing = LinearEasing,
)

/**
 * 時計のアナログ・デジタル切り替え時の拡縮と透明度を補間するAnimationSpecを生成する。
 * 300msの減速曲線で前後の画面を自然にブレンドする。
 */
fun clockModeAnimationSpec(): TweenSpec<Float> = tween(
    durationMillis = CLOCK_MODE_TRANSITION_DURATION_MS,
    easing = FastOutSlowInEasing,
)

/**
 * アラーム一覧のテキスト色切り替えを補間するAnimationSpecを生成する。
 * 250msかけて有効・無効時の色へじんわりと遷移させる。
 */
fun alarmColorAnimationSpec(): TweenSpec<Color> = tween(
    durationMillis = ALARM_COLOR_TRANSITION_DURATION_MS,
    easing = FastOutSlowInEasing,
)

/**
 * ボタンを押下している間だけ0.92倍に縮小し、離すと元に戻る手応え効果を付与するModifier。
 * 物理的なクリック感を画面上で表現するために用いる。
 */
@Composable
fun Modifier.pressScaleEffect(interactionSource: MutableInteractionSource): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) BUTTON_PRESS_SCALE else 1.0f,
        animationSpec = buttonPressAnimationSpec(),
        label = "ButtonPressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
