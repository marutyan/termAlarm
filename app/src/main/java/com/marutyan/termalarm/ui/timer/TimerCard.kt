package com.marutyan.termalarm.ui.timer

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.snap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.TimerRunState
import com.marutyan.termalarm.domain.TimerState
import com.marutyan.termalarm.domain.isTimerActive
import com.marutyan.termalarm.domain.isTimerOverdue
import com.marutyan.termalarm.domain.timerDisplayText
import com.marutyan.termalarm.domain.timerRemainingFraction
import com.marutyan.termalarm.domain.userLabelOrNull
import com.marutyan.termalarm.domain.remainingMillis
import com.marutyan.termalarm.ui.theme.IbmPlexMono
import com.marutyan.termalarm.ui.theme.customColors
import com.marutyan.termalarm.ui.theme.pressScaleEffect
import com.marutyan.termalarm.ui.theme.timerColorAnimationSpec

/** 円形リングの直径(dp)。純正時計アプリの実測値304.7dpに基づく。 */
val TIMER_RING_SIZE = 304.7.dp

/** 円形リングの線の太さ(dp)。純正時計アプリの実測値11.2dpに基づく。 */
val TIMER_RING_STROKE_WIDTH = 11.2.dp

/** カードの角丸の半径(dp)。純正時計アプリの実測値38dpに基づく。 */
val TIMER_CARD_CORNER_RADIUS = 38.dp

/** カード下部の「＋1:00」ボタンの幅(dp)。純正時計アプリの実測値176.2dpに基づく。 */
val TIMER_EXTEND_BUTTON_WIDTH = 176.2.dp

/** カード下部のボタンの高さ(dp)。純正時計アプリの実測値91.2dpに基づく。 */
val TIMER_BUTTON_ROW_HEIGHT = 91.2.dp

/** カード下部のリセットボタンの直径(dp)。純正時計アプリの実測値85.4dpに基づく。 */
val TIMER_RESET_BUTTON_SIZE = 85.4.dp

/** カード下部のボタン間の間隔(dp)。純正時計アプリの実測値11.2dpに基づく。 */
val TIMER_BUTTON_SPACING = 11.2.dp

/** カード下部の余白(dp)。純正時計アプリの実測値26.1dpに基づく。 */
val TIMER_CARD_BOTTOM_PADDING = 26.1.dp

/** カード右上の閉じる「×」アイコンサイズ(dp)。純正時計アプリの実測値24dpに基づく。 */
val TIMER_CARD_CLOSE_ICON_SIZE = 24.dp

/** カード右上の閉じる「×」ボタンのタップ領域サイズ(dp)。アクセシビリティ基準を満たすため48dpとする。 */
val TIMER_CARD_CLOSE_BUTTON_SIZE = 48.dp

/**
 * リセットの記号の形。24の座標系で描いた、輪をひと回りして戻る矢印。
 * 設定した長さへ戻すことを表す。線ではなく塗りで描くため、小さくしても潰れない。
 */
private const val TIMER_RESET_PATH =
    "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -8,8s3.58,8 8,8c3.73,0 6.84,-2.55 7.73,-6" +
        "h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78" +
        "L13,11h7V4L17.65,6.35z"

/** カード中央の一時停止・再開の印のサイズ(dp)。純正時計アプリの実測値27dpに基づく。 */
val TIMER_ACTION_ICON_SIZE = 27.dp

/**
 * 一時停止・再開の印を、円の中心からどれだけ下へずらすか。
 * 残り時間を中心に置いたうえで、その下へ重ならずに収まる位置とする。
 */
val TIMER_ACTION_ICON_CENTER_OFFSET = 64.dp

/**
 * 2件以上のタイマーを2列グリッドで並べる際の画面左右の余白(dp)。
 * 純正時計アプリの実測値14dpに基づき、端のカードと画面縁との適正な距離を保つために定義する。
 */
val COMPACT_GRID_HORIZONTAL_PADDING = 14.dp

/**
 * 2列グリッドにおける列間の水平余白(dp)。
 * 純正時計アプリの実測値30dpに基づき、左右のカードが近すぎず一覧しやすい間隔を確保するために定義する。
 */
val COMPACT_GRID_COLUMN_SPACING = 30.dp

/**
 * 2列グリッドにおける行間の垂直余白(dp)。
 * 縦スクロール時に上下のカード間の境界を明確にするために定義する。
 */
val COMPACT_GRID_ROW_SPACING = 16.dp

/**
 * 小さいカードの設計基準幅比率。
 * 純正時計アプリの実測値213dpに基づき、カードのアスペクト比を計算するために定義する。
 */
const val COMPACT_TIMER_CARD_WIDTH_RATIO = 213f

/**
 * 小さいカードの設計基準高さ比率。
 * 純正時計アプリの実測値287dpに基づき、カードのアスペクト比を計算するために定義する。
 */
const val COMPACT_TIMER_CARD_HEIGHT_RATIO = 287f

/**
 * 小さいカードの縦横比(高さ ÷ 幅)。
 * 実測値「幅213dp、高さ287dp」に基づき、利用可能幅から求めた幅に応じて高さを比例計算するために用いる。
 */
const val COMPACT_TIMER_CARD_ASPECT_RATIO = COMPACT_TIMER_CARD_HEIGHT_RATIO / COMPACT_TIMER_CARD_WIDTH_RATIO

/**
 * 小さいカードの角丸の半径(dp)。
 * 純正時計アプリの実測値24dpに基づき、2列表示時に適した丸みを付与するために定義する。
 */
val COMPACT_TIMER_CARD_CORNER_RADIUS = 24.dp

/**
 * 小さいカードの中央の円の直径比率(カード幅に対する比率)。
 * 純正時計アプリの実測値「直径160dp / 幅213dp」に基づき、カード幅に比例した円の大きさを決めるために用いる。
 */
const val COMPACT_TIMER_RING_SIZE_RATIO = 160f / 213f

/**
 * 小さいカードの上端から円の上端までの位置比率(カード高さに対する比率)。
 * 純正時計アプリの実測値「高さ43.5dp / 287dp」に基づき、上部見出し行と円の垂直配置を決めるために用いる。
 */
const val COMPACT_TIMER_RING_TOP_OFFSET_RATIO = 43.5f / 287f

/**
 * 止まっている小さいカードの円の線の太さ(dp)。
 * 純正時計アプリの実測値7dpに基づき、停止時の輪郭線を適正な太さで描くために定義する。
 */
val COMPACT_TIMER_RING_STROKE_WIDTH = 7.dp

/**
 * 小さいカード下部の「+1:00」延長ボタンの幅比率(カード幅に対する比率)。
 * 純正時計アプリの実測値「幅86dp / 幅213.5dp (約0.4029)」に基づき、画面幅に応じて横幅を伸縮するために定義する。
 */
const val COMPACT_TIMER_EXTEND_BUTTON_WIDTH_RATIO = 0.4029f

/**
 * 小さいカード下部の「+1:00」延長ボタンの高さ比率(カード幅に対する比率)。
 * 純正時計アプリの実測値「高さ48dp / 幅213.5dp (約0.2249)」に基づき、画面幅に応じて高さを伸縮するために定義する。
 */
const val COMPACT_TIMER_EXTEND_BUTTON_HEIGHT_RATIO = 0.2249f

/**
 * 小さいカードの円下端からボタン上端までの垂直間隔比率(カード幅に対する比率)。
 * 純正時計アプリの実測値「余白20dp / 幅213.5dp (約0.0937)」に基づき、画面幅に応じて余白を伸縮するために定義する。
 */
const val COMPACT_TIMER_RING_TO_BUTTON_GAP_RATIO = 0.0937f

/**
 * 小さいカードのボタン下端からカード下端までの余白比率(カード幅に対する比率)。
 * 純正時計アプリの実測値「余白14dp / 幅213.5dp (約0.0656)」に基づき、画面幅に応じて余白を伸縮するために定義する。
 */
const val COMPACT_TIMER_BUTTON_TO_BOTTOM_GAP_RATIO = 0.0656f

/**
 * 操作ボタンやアイコンの最小タップ領域サイズ(dp)。
 * 見た目の部品サイズが縮小された場合でもアクセシビリティ基準の操作性を維持するため、44dpを下回らないタッチ範囲を確保するために定義する。
 */
val COMPACT_TIMER_MIN_TOUCH_TARGET_SIZE = 44.dp

/**
 * 小さいカード中央の一時停止・再開の印のサイズ(dp)。
 * 直径約160dpの小型円内部で残り時間とバランス良く視認できる大きさを保つために定義する。
 */
val COMPACT_TIMER_ACTION_ICON_SIZE = 18.dp

/**
 * 小さいカード中央の一時停止・再開の印を中心から下へずらすオフセット(dp)。
 * 小型円内で残り時間の数字と印が重ならない適切な位置へ配置するために定義する。
 */
val COMPACT_TIMER_ACTION_ICON_CENTER_OFFSET = 30.dp

/**
 * タイマーカードのサイズ変化や出入り、並べ替えアニメーションの時間(ミリ秒)。
 * 200msの機敏かつ滑らかな遷移で画面の切り替えを見せるために定義する。
 */
const val TIMER_ITEM_ANIMATION_DURATION_MS = 200

/**
 * 画面の利用可能な横幅から、小さいカード1枚の幅(dp)を計算する。
 * 「(使える幅 - 列の間) ÷ 2」の式に従い、決め打ちにせず動的な画面幅に対応したカード幅を算出する。
 *
 * @param availableWidth 左右の余白を差し引いた利用可能な全幅
 * @return 2列グリッドに配置するカード1枚の幅
 */
fun calculateCompactCardWidth(availableWidth: Dp): Dp {
    return ((availableWidth - COMPACT_GRID_COLUMN_SPACING) / 2).coerceAtLeast(0.dp)
}

/**
 * 小さいカードの幅から高さ(dp)を計算する。
 * 「カードの幅 × 287 ÷ 213」の設計比率を維持し、幅に応じた正確なカードの高さを算出する。
 *
 * @param cardWidth 計算されたカードの幅
 * @return 設計比率に基づくカードの高さ
 */
fun calculateCompactCardHeight(cardWidth: Dp): Dp {
    return cardWidth * COMPACT_TIMER_CARD_ASPECT_RATIO
}

/**
 * 端末の「アニメーションを減らす」または「アニメーションの無効化」が有効になっているかを判定する。
 * 動きに弱い利用者に配慮し、アニメーションの抑制設定を検知するために用いる。
 */
fun isReduceMotionEnabled(context: Context): Boolean {
    return try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1.0f,
        ) == 0f
    } catch (_: Exception) {
        false
    }
}

/**
 * タイマーの円形プログレスの進捗割合(0f..1f)を計算・提供する。
 * 通常時は毎フレームなめらかに減らし、端末の「アニメーションを減らす」設定時は1秒ごとの更新にとどめる。
 * 呼び出し元全体の再構成を防ぐためStateを返し、Canvasの描画処理内でのみ値を読み出す。
 */
@Composable
fun rememberTimerProgress(
    timer: TimerState,
    nowElapsed: Long,
    nowWall: Long,
    reduceMotion: Boolean,
): State<Float> {
    if (timer.totalMillis <= 0L) {
        return rememberUpdatedState(0f)
    }
    if (timer.runState != TimerRunState.RUNNING || reduceMotion) {
        return rememberUpdatedState(timerRemainingFraction(timer, nowElapsed, nowWall))
    }

    val progressState = remember(timer.id, timer.anchorElapsedRealtime, timer.runState) {
        mutableFloatStateOf(timerRemainingFraction(timer, nowElapsed, nowWall))
    }

    LaunchedEffect(timer.id, timer.anchorElapsedRealtime, timer.runState, timer.totalMillis) {
        while (true) {
            var reachedZero = false
            withFrameMillis {
                val currentElapsed = SystemClock.elapsedRealtime()
                val currentWall = System.currentTimeMillis()
                val remaining = remainingMillis(timer, currentElapsed, currentWall)
                progressState.floatValue = timerRemainingFraction(timer, currentElapsed, currentWall)
                if (remaining <= 0L) {
                    reachedZero = true
                }
            }
            if (reachedZero) {
                break
            }
        }
    }

    return progressState
}

/**
 * 動作中タイマー1件を表示するカードComposable。
 * design/Timer.dc.html の設計に基づき、角丸20dpのカードにラベル、円形リング、中央の残り時間・印、
 * 「＋1:00」ボタンおよびリセットボタンを配置する。
 */
@Composable
fun TimerCard(
    timer: TimerState,
    nowElapsed: Long,
    nowWall: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onExtend: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reduceMotion = remember(context) { isReduceMotionEnabled(context) }
    // 残りが尽きたら、状態が鳴動中へ変わるのを待たずに鳴り終わった見せ方へ移る。
    // 状態が変わるのはサービスが気づいた後で数秒遅れることがあり、その間画面が固まって見えるため
    val isFinished = isTimerOverdue(timer, nowElapsed, nowWall)
    val isRunning = timer.runState == TimerRunState.RUNNING && !isFinished

    val remaining = remainingMillis(timer, nowElapsed, nowWall)
    val progressState = rememberTimerProgress(
        timer = timer,
        nowElapsed = nowElapsed,
        nowWall = nowWall,
        reduceMotion = reduceMotion,
    )

    // カード背景色。完了時は主役の色に近い primaryContainer へ移り変わる
    val targetContainerColor = if (isFinished) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = timerColorAnimationSpec(),
        label = "TimerContainerColor",
    )

    // 円形リングの弧の色。一時停止時は沈んだ色 subtleText へ移り変わる
    val targetArcColor = when (timer.runState) {
        TimerRunState.RUNNING -> MaterialTheme.colorScheme.primary
        TimerRunState.PAUSED -> MaterialTheme.customColors.subtleText
        TimerRunState.FINISHED -> MaterialTheme.colorScheme.primary
    }
    val arcColor by animateColorAsState(
        targetValue = targetArcColor,
        animationSpec = timerColorAnimationSpec(),
        label = "TimerArcColor",
    )

    // 残り時間および印の色。完了時は onPrimary、一時停止時は subtleText へ移り変わる
    val targetTextColor = when (timer.runState) {
        TimerRunState.FINISHED -> MaterialTheme.colorScheme.onPrimary
        TimerRunState.RUNNING -> MaterialTheme.colorScheme.onSurface
        TimerRunState.PAUSED -> MaterialTheme.customColors.subtleText
    }
    val textColor by animateColorAsState(
        targetValue = targetTextColor,
        animationSpec = timerColorAnimationSpec(),
        label = "TimerTextColor",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(TIMER_CARD_CORNER_RADIUS),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 35.5.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 1. 上の行: 左にラベル(16sp、薄い色)、右に閉じる「×」(24dp、タップ領域48dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
            ) {
                val formattedDuration = formatTimerDuration(timer.totalMillis)
                val userLabel = timer.userLabelOrNull()
                val labelText = if (userLabel != null) "$userLabel · $formattedDuration" else formattedDuration
                Text(
                    text = labelText,
                    style = TextStyle(
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.customColors.subtleText,
                    ),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(end = TIMER_CARD_CLOSE_BUTTON_SIZE),
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(TIMER_CARD_CLOSE_BUTTON_SIZE),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(if (isFinished) R.string.timer_stop else R.string.timer_delete),
                        tint = MaterialTheme.customColors.subtleText,
                        modifier = Modifier.size(TIMER_CARD_CLOSE_ICON_SIZE),
                    )
                }
            }

            // ラベル行からリング上端(77.8dp)までの間隔
            Spacer(modifier = Modifier.height(18.3.dp))

            // 2. 円形のリング(直径304.7dp、線幅11.2dp) と 3. 中央の残り時間・印
            Box(
                modifier = Modifier.size(TIMER_RING_SIZE),
                contentAlignment = Alignment.Center,
            ) {
                val outlineColor = MaterialTheme.colorScheme.outline
                val primaryColor = MaterialTheme.colorScheme.primary

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = TIMER_RING_STROKE_WIDTH.toPx()
                    val radius = (size.minDimension - strokePx) / 2f
                    val arcTopLeft = Offset(strokePx / 2f, strokePx / 2f)
                    val arcSize = Size(radius * 2f, radius * 2f)

                    if (isFinished) {
                        // 完了時はリングの内側を主役の色で塗りつぶす
                        drawCircle(
                            color = primaryColor,
                            radius = radius + strokePx / 2f,
                            center = center,
                        )
                    } else {
                        // 下地の円: 地とはっきり見分けがつく輪郭の色(outline)
                        drawCircle(
                            color = outlineColor,
                            radius = radius,
                            center = center,
                            style = Stroke(width = strokePx),
                        )

                        // 残りぶんの弧: 主役の色。12時の位置(-90度)から時計回りに描き、減っていく
                        val progress = progressState.value
                        if (progress > 0.001f) {
                            val sweepAngle = 360f * progress
                            drawArc(
                                color = arcColor,
                                startAngle = -90f,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = arcTopLeft,
                                size = arcSize,
                                style = Stroke(width = strokePx, cap = StrokeCap.Round),
                            )
                        }
                    }
                }

                // リング中央: 残り時間 (54sp, 太さ200, 等幅数字, 28spまで自動縮小) と 一時停止/再開/停止の印 (27dp)
                val actionDesc = stringResource(
                    when {
                        isFinished -> R.string.timer_stop
                        isRunning -> R.string.timer_pause
                        else -> R.string.timer_resume
                    }
                )
                val toggleAction = {
                    when {
                        isFinished -> onReset()
                        isRunning -> onPause()
                        else -> onResume()
                    }
                }
                val centerInteraction = remember { MutableInteractionSource() }

                // 残り時間を円のちょうど中心へ置く。印はその下へ重ねて配置する
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(TIMER_RING_SIZE)
                        .clickable(
                            interactionSource = centerInteraction,
                            indication = ripple(bounded = false, radius = 75.dp),
                            onClick = toggleAction,
                        )
                        .semantics { contentDescription = actionDesc },
                ) {
                    Text(
                        // 通知とステータスバーのチップも同じ文字を出す
                        text = timerDisplayText(timer, nowElapsed, nowWall),
                        style = TextStyle(
                            fontFamily = IbmPlexMono,
                            fontWeight = FontWeight.W200,
                            fontSize = 68.sp,
                            lineHeight = 68.sp,
                            letterSpacing = (-0.03).em,
                            fontFeatureSettings = "tnum",
                        ),
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 28.sp,
                            maxFontSize = 68.sp,
                            stepSize = 1.sp,
                        ),
                        maxLines = 1,
                        softWrap = false,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = TIMER_RING_STROKE_WIDTH * 2),
                    )

                    TimerActionIcon(
                        runState = timer.runState,
                        color = textColor,
                        modifier = Modifier.offset(y = TIMER_ACTION_ICON_CENTER_OFFSET),
                    )
                }
            }

            // リングからボタン行(上端417.1dp)までの間隔
            // 設定した長さのまま止まっているときは、延長もリセットも意味が無いので出さない。
            // 純正の時計アプリも、停止して元へ戻った状態ではボタンの行ごと消える
            val isAtFullDuration = !isTimerActive(timer, nowElapsed, nowWall)
            if (!isAtFullDuration) {
            Spacer(modifier = Modifier.height(34.6.dp))

            // 4. 下に2つのボタン。「＋1:00」（幅176.2dp、高さ91.2dp、角丸45.6dp）と、リセット（直径85.4dpの円）。間隔11.2dp、中央揃え
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TIMER_BUTTON_ROW_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(TIMER_BUTTON_SPACING, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ボタン1: ＋1:00 (幅176.2dp、高さ91.2dp、角丸45.6dp)
                val extendInteraction = remember { MutableInteractionSource() }
                val extendTextColor = if (isRunning || isFinished) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                val animatedExtendTextColor by animateColorAsState(
                    targetValue = extendTextColor,
                    animationSpec = timerColorAnimationSpec(),
                    label = "TimerExtendTextColor",
                )

                Surface(
                    onClick = onExtend,
                    interactionSource = extendInteraction,
                    shape = RoundedCornerShape(45.6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .width(TIMER_EXTEND_BUTTON_WIDTH)
                        .height(TIMER_BUTTON_ROW_HEIGHT)
                        .pressScaleEffect(extendInteraction),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Text(
                            text = stringResource(R.string.timer_extend_one_minute_button),
                            style = TextStyle(
                                fontFamily = IbmPlexMono,
                                fontSize = 19.5.sp,
                            ),
                            color = animatedExtendTextColor,
                        )
                    }
                }

                // ボタン2: リセット (直径85.4dpの円)。完了時は出さない
                if (!isFinished) {
                    val resetInteraction = remember { MutableInteractionSource() }
                    val resetDesc = stringResource(R.string.timer_reset)
                    val resetIconColor = if (isRunning) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val animatedResetIconColor by animateColorAsState(
                        targetValue = resetIconColor,
                        animationSpec = timerColorAnimationSpec(),
                        label = "TimerResetIconColor",
                    )

                    Surface(
                        onClick = onReset,
                        interactionSource = resetInteraction,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .size(TIMER_RESET_BUTTON_SIZE)
                            .semantics { contentDescription = resetDesc }
                            .pressScaleEffect(resetInteraction),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            TimerResetIcon(color = animatedResetIconColor, modifier = Modifier.size(32.4.dp))
                        }
                    }
                }
            }
            }

            // カードの下の余白（実測値26.1dp）
            Spacer(modifier = Modifier.height(TIMER_CARD_BOTTOM_PADDING))
        }
    }
}

/**
 * 一時停止・再開・停止の状態に応じた27dpの印を描画するComposable。
 * 動作中は一時停止(縦2本線)、一時停止中は再開(三角)、完了時は停止(四角)を表示する。
 * 純正時計アプリの実測値27dpに基づく。
 */
@Composable
fun TimerActionIcon(
    runState: TimerRunState,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(TIMER_ACTION_ICON_SIZE)) {
        when (runState) {
            TimerRunState.RUNNING -> {
                // 一時停止の印: 縦2本線
                val strokeWidth = 3.24.dp.toPx()
                val x1 = size.width * (9f / 24f)
                val x2 = size.width * (15f / 24f)
                val y1 = size.height * (5f / 24f)
                val y2 = size.height * (19f / 24f)
                drawLine(
                    color = color,
                    start = Offset(x1, y1),
                    end = Offset(x1, y2),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(x2, y1),
                    end = Offset(x2, y2),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
            TimerRunState.PAUSED -> {
                // 再開の印: 右向き三角
                val path = Path().apply {
                    moveTo(size.width * (7f / 24f), size.height * (4f / 24f))
                    lineTo(size.width * (19f / 24f), size.height * (12f / 24f))
                    lineTo(size.width * (7f / 24f), size.height * (20f / 24f))
                    close()
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = 2.7.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
            TimerRunState.FINISHED -> {
                // 停止の印: 四角 (■)
                val squareSize = size.width * (12f / 24f)
                val left = (size.width - squareSize) / 2f
                val top = (size.height - squareSize) / 2f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(squareSize, squareSize),
                    cornerRadius = CornerRadius(2.7.dp.toPx(), 2.7.dp.toPx()),
                )
            }
        }
    }
}

/**
 * リセット操作を表す円形矢印アイコンを描画するComposable。
 * 経過時間を最初の設定時間へ巻き戻す手応えを伝えるために用いる。
 * 純正時計アプリの実測値に基づくリセットボタン内に適した大きさで描画する。
 */
@Composable
fun TimerResetIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val path = remember { PathParser().parsePathString(TIMER_RESET_PATH).toPath() }
    Canvas(modifier = modifier.size(26.dp)) {
        // 24の座標系で描かれた形なので、実際の大きさへ合わせて全体を拡大する
        scale(scale = size.width / 24f, pivot = Offset.Zero) {
            drawPath(path = path, color = color)
        }
    }
}

/**
 * タイマーの設定時間(ミリ秒)を「3分」「8分」「1時間」のような日本語表記に整形する。
 * カード上部の時間ラベル表示に用いる。
 */
@Composable
private fun formatTimerDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return buildString {
        if (hours > 0) {
            append(stringResource(R.string.timer_duration_hours, hours))
        }
        if (minutes > 0) {
            append(stringResource(R.string.timer_duration_minutes, minutes))
        }
        if (seconds > 0 || (hours == 0L && minutes == 0L)) {
            append(stringResource(R.string.timer_duration_seconds, seconds))
        }
    }
}

/**
 * 2件以上のタイマーがある場合に2列で表示する小型カードComposable。
 * 純正時計アプリの実測値(幅213dp、高さ287dp、角丸24dp、直径160dpの円、高さ48dpのボタン等)に基づき、
 * 画面幅から計算された動的な幅と高さで描画する。
 * 動作中は主役の色(primaryContainer)で塗りつぶした円、停止中は線だけの円とリセットボタンを表示し、
 * 複数タイマーの一覧性と操作性を両立する役割を持つ。
 *
 * @param timer 対象のタイマー状態
 * @param cardWidth 計算されたカードの幅
 * @param cardHeight 計算されたカードの高さ
 * @param nowElapsed 基準経過時刻(elapsedRealtime)
 * @param nowWall 基準壁時計時刻(wallClockMillis)
 * @param onPause 一時停止要求時のコールバック
 * @param onResume 再開要求時のコールバック
 * @param onReset リセット(やり直し)要求時のコールバック
 * @param onExtend 1分延長要求時のコールバック
 * @param onDelete 削除要求時のコールバック
 * @param modifier 外部から適用するModifier
 */
@Composable
fun CompactTimerCard(
    timer: TimerState,
    cardWidth: Dp,
    cardHeight: Dp,
    nowElapsed: Long,
    nowWall: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onExtend: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reduceMotion = remember(context) { isReduceMotionEnabled(context) }
    val isFinished = isTimerOverdue(timer, nowElapsed, nowWall)
    val isRunning = timer.runState == TimerRunState.RUNNING && !isFinished
    val isPaused = timer.runState == TimerRunState.PAUSED && !isFinished

    // 動作中(RUNNING/FINISHED)は主役の色の暗めの面(primaryContainer)、停止中は控えめな面(surfaceContainer)
    val targetContainerColor = if (isRunning || isFinished) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = if (reduceMotion) snap() else timerColorAnimationSpec(),
        label = "CompactTimerContainerColor",
    )

    // 円の描画色。動作中はprimaryで塗りつぶし、停止中はprimaryの線で描く
    val targetCircleColor = MaterialTheme.colorScheme.primary
    val circleColor by animateColorAsState(
        targetValue = targetCircleColor,
        animationSpec = if (reduceMotion) snap() else timerColorAnimationSpec(),
        label = "CompactTimerCircleColor",
    )

    // 円の中の文字・印の色。動作中はonPrimary、停止中はonSurface
    val targetTextColor = if (isRunning || isFinished) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val textColor by animateColorAsState(
        targetValue = targetTextColor,
        animationSpec = if (reduceMotion) snap() else timerColorAnimationSpec(),
        label = "CompactTimerTextColor",
    )

    // 見出しやアイコンの補助色。動作中はonPrimaryの控えめな透過、停止中はsubtleText
    val targetSubtleColor = if (isRunning || isFinished) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
    } else {
        MaterialTheme.customColors.subtleText
    }
    val subtleColor by animateColorAsState(
        targetValue = targetSubtleColor,
        animationSpec = if (reduceMotion) snap() else timerColorAnimationSpec(),
        label = "CompactTimerSubtleColor",
    )

    val circleSize = cardWidth * COMPACT_TIMER_RING_SIZE_RATIO
    val circleTopOffset = cardHeight * COMPACT_TIMER_RING_TOP_OFFSET_RATIO
    val buttonWidth = cardWidth * COMPACT_TIMER_EXTEND_BUTTON_WIDTH_RATIO
    val buttonHeight = cardWidth * COMPACT_TIMER_EXTEND_BUTTON_HEIGHT_RATIO
    val ringToButtonGap = cardWidth * COMPACT_TIMER_RING_TO_BUTTON_GAP_RATIO
    val buttonToBottomGap = cardWidth * COMPACT_TIMER_BUTTON_TO_BOTTOM_GAP_RATIO

    // 操作ボタンの押せる範囲を44dp以上確保するため、見た目の高さを保ちつつ上下の余白を案分して調整する
    val touchTargetHeight = maxOf(buttonHeight, COMPACT_TIMER_MIN_TOUCH_TARGET_SIZE)
    val extraTouchHeight = (touchTargetHeight - buttonHeight).coerceAtLeast(0.dp)
    val adjustedTopGap = (ringToButtonGap - extraTouchHeight / 2).coerceAtLeast(0.dp)
    val adjustedBottomGap = (buttonToBottomGap - extraTouchHeight / 2).coerceAtLeast(0.dp)

    Card(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight),
        shape = RoundedCornerShape(COMPACT_TIMER_CARD_CORNER_RADIUS),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 1. 上の見出し行: 左にタイマーの名前 (右端は44dpの閉じるボタン領域を確保)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(circleTopOffset)
                        .padding(start = 16.dp, end = COMPACT_TIMER_MIN_TOUCH_TARGET_SIZE),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val formattedDuration = formatTimerDuration(timer.totalMillis)
                    val userLabel = timer.userLabelOrNull()
                    val labelText = userLabel ?: formattedDuration
                    Text(
                        text = labelText,
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = subtleColor,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 2. まん中の円 (直径: カード幅の0.75倍) と 残り時間・印
                val actionDesc = stringResource(
                    when {
                        isFinished -> R.string.timer_stop
                        isRunning -> R.string.timer_pause
                        else -> R.string.timer_resume
                    }
                )
                val toggleAction = {
                    when {
                        isFinished -> onReset()
                        isRunning -> onPause()
                        else -> onResume()
                    }
                }
                val centerInteraction = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .size(circleSize)
                        .clickable(
                            interactionSource = centerInteraction,
                            indication = ripple(bounded = false, radius = 45.dp),
                            onClick = toggleAction,
                        )
                        .semantics { contentDescription = actionDesc },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokePx = COMPACT_TIMER_RING_STROKE_WIDTH.toPx()
                        val radius = (size.minDimension - strokePx) / 2f

                        if (isRunning || isFinished) {
                            // 動作中: 円を主役の色で塗りつぶす
                            drawCircle(
                                color = circleColor,
                                radius = size.minDimension / 2f,
                                center = center,
                            )
                        } else {
                            // 止まっているとき: 円は線だけ（太さ 7dp）で描く
                            drawCircle(
                                color = circleColor,
                                radius = radius,
                                center = center,
                                style = Stroke(width = strokePx),
                            )
                        }
                    }

                    // 円の中の残り時間テキスト
                    Text(
                        text = timerDisplayText(timer, nowElapsed, nowWall),
                        style = TextStyle(
                            fontFamily = IbmPlexMono,
                            fontWeight = FontWeight.W200,
                            fontSize = 32.sp,
                            lineHeight = 36.sp,
                            letterSpacing = (-0.02).em,
                            fontFeatureSettings = "tnum",
                        ),
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 16.sp,
                            maxFontSize = 32.sp,
                            stepSize = 1.sp,
                        ),
                        maxLines = 1,
                        softWrap = false,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )

                    // 一時停止・再開・停止の印
                    TimerActionIcon(
                        runState = timer.runState,
                        color = textColor,
                        modifier = Modifier
                            .size(COMPACT_TIMER_ACTION_ICON_SIZE)
                            .offset(y = COMPACT_TIMER_ACTION_ICON_CENTER_OFFSET),
                    )
                }

                // 円の下端からボタンまでの余白 (実測20dp相当の比率)
                Spacer(modifier = Modifier.height(adjustedTopGap))

                // 3. 下のボタン行: 「+1:00」と、止まっているときだけ右へ小さく出す「リセット」
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(touchTargetHeight),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // +1:00 ボタン: 見た目の高さをbuttonHeightとしつつ、タップ領域はtouchTargetHeight(44dp以上)を確保
                    val extendInteraction = remember { MutableInteractionSource() }
                    val extendTextColor = if (isRunning || isFinished) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val animatedExtendTextColor by animateColorAsState(
                        targetValue = extendTextColor,
                        animationSpec = if (reduceMotion) snap() else timerColorAnimationSpec(),
                        label = "CompactTimerExtendTextColor",
                    )
                    val extendDesc = stringResource(R.string.timer_extend_one_minute_button)

                    Box(
                        modifier = Modifier
                            .width(buttonWidth)
                            .height(touchTargetHeight)
                            .clickable(
                                interactionSource = extendInteraction,
                                indication = ripple(bounded = false, radius = buttonWidth / 2),
                                onClick = onExtend,
                            )
                            .semantics { contentDescription = extendDesc },
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(buttonHeight / 2),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .width(buttonWidth)
                                .height(buttonHeight)
                                .pressScaleEffect(extendInteraction),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Text(
                                    text = extendDesc,
                                    style = TextStyle(
                                        fontFamily = IbmPlexMono,
                                        fontSize = 14.sp,
                                    ),
                                    color = animatedExtendTextColor,
                                )
                            }
                        }
                    }

                    // リセットボタン: 止まっているとき(PAUSED)だけ +1:00 の右へ小さく出す
                    if (isPaused) {
                        val resetInteraction = remember { MutableInteractionSource() }
                        val resetDesc = stringResource(R.string.timer_reset)
                        val resetIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        val animatedResetIconColor by animateColorAsState(
                            targetValue = resetIconColor,
                            animationSpec = if (reduceMotion) snap() else timerColorAnimationSpec(),
                            label = "CompactTimerResetIconColor",
                        )

                        Box(
                            modifier = Modifier
                                .size(touchTargetHeight)
                                .clickable(
                                    interactionSource = resetInteraction,
                                    indication = ripple(bounded = false, radius = touchTargetHeight / 2),
                                    onClick = onReset,
                                )
                                .semantics { contentDescription = resetDesc },
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .size(buttonHeight)
                                    .pressScaleEffect(resetInteraction),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    TimerResetIcon(
                                        color = animatedResetIconColor,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // カード下端までの余白 (実測14dp相当の比率)
                Spacer(modifier = Modifier.height(adjustedBottomGap))
            }

            // 右上の閉じる「×」ボタン:
            // 見出し行の狭い高さ(約31dp〜36dp)に制限されず44dpのタップ領域を確保するため、カード右上に配置する
            val deleteInteraction = remember { MutableInteractionSource() }
            val deleteDesc = stringResource(if (isFinished) R.string.timer_stop else R.string.timer_delete)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(COMPACT_TIMER_MIN_TOUCH_TARGET_SIZE)
                    .clickable(
                        interactionSource = deleteInteraction,
                        indication = ripple(bounded = false, radius = 20.dp),
                        onClick = onDelete,
                    )
                    .semantics { contentDescription = deleteDesc },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = subtleColor,
                    modifier = Modifier
                        .size(20.dp)
                        .offset(y = (circleTopOffset - COMPACT_TIMER_MIN_TOUCH_TARGET_SIZE) / 2),
                )
            }
        }
    }
}
