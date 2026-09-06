package com.marutyan.termalarm.ui.theme

import android.graphics.Path
import android.view.animation.PathInterpolator
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Easing
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
import androidx.compose.ui.unit.dp

// 画面が入れ替わるときの時間(ミリ秒)。
// 薄く消えて薄く現れるだけなので短くてよい。長いと閉じたのに残っているように見える。
const val TAB_TRANSITION_DURATION_MS = 150

// 画面を開く・閉じるときの動き。端末が持つ既定のActivityアニメーション
// (framework-resのanim/activity_open_enterなど)から、そのままの値を写している。
// 純正の時計アプリは設定画面を別のActivityとして開くため、この動きがそのまま出る。
//
// 中身は「96dp分だけ横へ滑らせる」だけで、消える側・現れる側のどちらかが短くフェードする。
const val SCREEN_SLIDE_DISTANCE_DP = 96
const val SCREEN_SLIDE_DURATION_MS = 450
const val SCREEN_FADE_DURATION_MS = 83
const val SCREEN_OPEN_FADE_DELAY_MS = 50
const val SCREEN_CLOSE_FADE_DELAY_MS = 35

// タイマー新規追加画面の表示・非表示アニメーション時間(ミリ秒)。下からの出現と上への消去に合わせるために定義する。
const val TIMER_ADD_TRANSITION_DURATION_MS = 300

// 画面の高さが狭いと判定するしきい値(dp)。分割画面や小型端末で余白や文字サイズを詰める基準として用いる。
val COMPACT_SCREEN_HEIGHT_THRESHOLD = 480.dp

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
 * タブ切り替え時のフェードイン・フェードアウトを補間するAnimationSpecを生成する。
 * 横スライドと協調して画面内容の切り替わりを自然に見せる。
 */
fun tabFadeSpec(): TweenSpec<Float> = tween(
    durationMillis = TAB_TRANSITION_DURATION_MS,
    easing = LinearEasing,
)

/**
 * 端末の`fast_out_extra_slow_in`と同じ曲線。最初に速く動いて長く減速する、
 * Androidが画面の出入りに使っている動き方。値はframework-resの定義そのまま。
 */
val FastOutExtraSlowIn: Easing = PathInterpolator(
    Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    },
).let { interpolator -> Easing { fraction -> interpolator.getInterpolation(fraction) } }

/** 画面を開くとき、現れる側。右から96dp滑り込みながら、少し遅れて短くフェードインする */
fun screenOpenEnter(slidePx: Int): EnterTransition =
    slideInHorizontally(animationSpec = screenSlideSpec()) { slidePx } +
        fadeIn(
            animationSpec = tween(
                durationMillis = SCREEN_FADE_DURATION_MS,
                delayMillis = SCREEN_OPEN_FADE_DELAY_MS,
                easing = LinearEasing,
            ),
        )

/** 画面を開くとき、隠れる側。左へ96dp滑るだけで、透明度は変えない */
fun screenOpenExit(slidePx: Int): ExitTransition =
    slideOutHorizontally(animationSpec = screenSlideSpec()) { -slidePx }

/** 戻るとき、戻り先。左から96dp滑って戻るだけで、透明度は変えない */
fun screenCloseEnter(slidePx: Int): EnterTransition =
    slideInHorizontally(animationSpec = screenSlideSpec()) { -slidePx }

/** 戻るとき、閉じる側。右へ96dp滑りながら、少し遅れて短くフェードアウトする */
fun screenCloseExit(slidePx: Int): ExitTransition =
    slideOutHorizontally(animationSpec = screenSlideSpec()) { slidePx } +
        fadeOut(
            animationSpec = tween(
                durationMillis = SCREEN_FADE_DURATION_MS,
                delayMillis = SCREEN_CLOSE_FADE_DELAY_MS,
                easing = LinearEasing,
            ),
        )

private fun screenSlideSpec(): TweenSpec<IntOffset> = tween(
    durationMillis = SCREEN_SLIDE_DURATION_MS,
    easing = FastOutExtraSlowIn,
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
