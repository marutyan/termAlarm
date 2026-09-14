package com.marutyan.termalarm.ui.theme

import androidx.activity.BackEventCompat
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

// 画面が入れ替わるときの時間(ミリ秒)。
// 薄く消えて薄く現れるだけなので短くてよい。長いと閉じたのに残っているように見える。
const val TAB_TRANSITION_DURATION_MS = 150

/**
 * 下位の画面を出し入れする時間(ミリ秒)。
 * Androidの予測型「戻る」が、指を離してから完了するまでに使う長さ。
 */
const val SCREEN_TRANSITION_DURATION_MS = 300

/** 閉じていく画面が縮む先の倍率。Androidが公開している予測型「戻る」の設計値。 */
const val SCREEN_EXIT_SCALE = 0.9f

/**
 * 現れる画面が始まるときの倍率。
 * 戻るときは手前にいた画面が実寸まで縮んでくる形になるので、1.0より大きい。
 */
const val SCREEN_ENTER_START_SCALE = 1.1f

/**
 * 後ろで順番を待っている画面の濃さ。
 * 純正を録画して測ると、指を動かしている間は暗いままで、離してから濃くなっていた。
 */
const val SCREEN_ENTER_DIM_ALPHA = 0.25f

// 濃さが入れ替わり始める、変化全体のうちの位置。ここまでは濃さを変えない
private const val SCREEN_FADE_THROUGH_START = 0.65f

// 濃さの入れ替えを始めるまでの時間(ミリ秒)と、入れ替えにかける時間(ミリ秒)
private val SCREEN_FADE_DELAY_MS = (SCREEN_TRANSITION_DURATION_MS * SCREEN_FADE_THROUGH_START).toInt()
private val SCREEN_FADE_DURATION_MS = SCREEN_TRANSITION_DURATION_MS - SCREEN_FADE_DELAY_MS

// タイマー新規追加画面の表示・非表示アニメーション時間(ミリ秒)。下からの出現と上への消去に合わせるために定義する。
const val TIMER_ADD_TRANSITION_DURATION_MS = 300

// 画面の高さが狭いと判定するしきい値(dp)。分割画面や小型端末で余白や文字サイズを詰める基準として用いる。
val COMPACT_SCREEN_HEIGHT_THRESHOLD = 480.dp

// ボタンを押下した際の縮小倍率。指の接触に応じた適度な押し込み感を出すために定義する。
const val BUTTON_PRESS_SCALE = 0.92f

// ボタンを押下した際と離した際のアニメーション時間(ミリ秒)。機敏なフィードバックを返すために定義する。
const val BUTTON_PRESS_DURATION_MS = 100

// タイマーカードの状態切り替え時に色を遷移させる時間(ミリ秒)。急激な明度や色の変化を和らげるために定義する。
const val TIMER_COLOR_TRANSITION_DURATION_MS = 200

// 時計のアナログ・デジタル表示を切り替えるアニメーション時間(ミリ秒)。自然な拡大縮小フェードにするために定義する。
const val CLOCK_MODE_TRANSITION_DURATION_MS = 300

// アラーム一覧のスイッチ切り替え時に時刻等の色を遷移させる時間(ミリ秒)。急激な明度変化を和らげるために定義する。
const val ALARM_COLOR_TRANSITION_DURATION_MS = 250

// ホーム画面の「次の鳴動」セクションの開閉アニメーション時間(ミリ秒)。
// 出現・消滅に合わせて下の一覧が滑らかに追従するよう定義する。
const val HOME_NEXT_TRIGGER_TRANSITION_DURATION_MS = 300

/**
 * ホーム画面の「次の鳴動」セクションの垂直展開・縮小を補間するAnimationSpecを生成する。
 * 上からの展開と上への縮小時に滑らかな加減速を適用するために用いる。
 */
fun homeNextTriggerExpandSpec(): TweenSpec<IntSize> = tween(
    durationMillis = HOME_NEXT_TRIGGER_TRANSITION_DURATION_MS,
    easing = FastOutSlowInEasing,
)

/**
 * タブ切り替え時のフェードイン・フェードアウトを補間するAnimationSpecを生成する。
 * 横スライドと協調して画面内容の切り替わりを自然に見せる。
 */
fun tabFadeSpec(): TweenSpec<Float> = tween(
    durationMillis = TAB_TRANSITION_DURATION_MS,
    easing = LinearEasing,
)

/**
 * 大きさと位置の変化の時間の取り方。
 *
 * 端の引っ張りでは指の進みがそのまま時間になるため、重み付けをすると
 * 指の動きと画面の動きがずれる。Androidの実装例も進みへ直接掛けている。
 */
private fun screenShapeSpec(): TweenSpec<Float> = tween(
    durationMillis = SCREEN_TRANSITION_DURATION_MS,
    easing = LinearEasing,
)

/** 位置の変化用。[screenShapeSpec]と同じ時間の取り方をIntOffsetへ当てる */
private fun screenShiftSpec(): TweenSpec<IntOffset> = tween(
    durationMillis = SCREEN_TRANSITION_DURATION_MS,
    easing = LinearEasing,
)

// 濃さの入れ替えに使う曲線。Androidが画面の終了に使っているものと同じ
private val ScreenFadeEasing: Easing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

/**
 * 濃さの入れ替えの時間の取り方。
 * 変化の終盤だけで入れ替えるため、[SCREEN_FADE_DELAY_MS]だけ待ってから動かす。
 */
private fun screenFadeSpec(): TweenSpec<Float> = tween(
    durationMillis = SCREEN_FADE_DURATION_MS,
    delayMillis = SCREEN_FADE_DELAY_MS,
    easing = ScreenFadeEasing,
)

/**
 * 端の引っ張りで戻すとき、閉じる画面を指と反対側へずらす量(px)を求める。
 *
 * Androidの設計資料にある式で、画面幅の1/20から端に残す余白8dpを引く。
 * 同じ式を複数箇所へ書かないよう、ここだけで計算する。
 */
@Composable
fun rememberScreenBackShiftPx(): Int {
    val density = LocalDensity.current
    val containerWidth = LocalWindowInfo.current.containerSize.width
    return remember(density, containerWidth) {
        with(density) {
            val widthDp = containerWidth.toDp().value
            ((widthDp / 20f) - 8f).dp.roundToPx()
        }
    }
}

/** 下位の画面を開くとき、新しく現れる側。奥から手前へ来るように、少し小さいところから実寸へ広がる */
fun screenOpenEnter(): EnterTransition =
    scaleIn(animationSpec = screenShapeSpec(), initialScale = SCREEN_EXIT_SCALE) +
        fadeIn(animationSpec = screenFadeSpec(), initialAlpha = SCREEN_ENTER_DIM_ALPHA)

/** 下位の画面を開くとき、下に隠れる側。手前へ抜けるように少し広がりながら消える */
fun screenOpenExit(): ExitTransition =
    scaleOut(animationSpec = screenShapeSpec(), targetScale = SCREEN_ENTER_START_SCALE) +
        fadeOut(animationSpec = screenFadeSpec())

/** 戻るとき、現れる側。手前にいた画面が実寸まで縮んでくるので、実寸より大きいところから始める */
fun screenCloseEnter(): EnterTransition =
    scaleIn(animationSpec = screenShapeSpec(), initialScale = SCREEN_ENTER_START_SCALE) +
        fadeIn(animationSpec = screenFadeSpec(), initialAlpha = SCREEN_ENTER_DIM_ALPHA)

/** 戻るとき、閉じる側。奥へ下がるように縮みながら消える */
fun screenCloseExit(): ExitTransition =
    scaleOut(animationSpec = screenShapeSpec(), targetScale = SCREEN_EXIT_SCALE) +
        fadeOut(animationSpec = screenFadeSpec())

/**
 * 端の引っ張りで戻している間の、閉じる側。
 *
 * [screenCloseExit]に、指と反対側への横ずらしを足したもの。
 * どちらの端から引いたかで向きが変わるため、[swipeEdge]を見て符号を決める。
 */
fun screenPredictivePopExit(shiftPx: Int, swipeEdge: Int): ExitTransition {
    val signedShift = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -shiftPx else shiftPx
    return screenCloseExit() +
        slideOutHorizontally(animationSpec = screenShiftSpec()) { signedShift }
}

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
 * タイマーカードの色切り替えを補間するAnimationSpecを生成する。
 * 200msかけて状態に応じた色へ自然に遷移させる。
 */
fun timerColorAnimationSpec(): TweenSpec<Color> = tween(
    durationMillis = TIMER_COLOR_TRANSITION_DURATION_MS,
    easing = FastOutSlowInEasing,
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
