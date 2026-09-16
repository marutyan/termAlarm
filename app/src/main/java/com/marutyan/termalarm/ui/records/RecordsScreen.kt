package com.marutyan.termalarm.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marutyan.termalarm.ui.common.SCREEN_HORIZONTAL_PADDING
import com.marutyan.termalarm.ui.common.TOP_BAR_CONTENT_GAP
import com.marutyan.termalarm.R
import com.marutyan.termalarm.ui.common.TermAlarmTopBar
import com.marutyan.termalarm.ui.theme.IbmPlexMono
import com.marutyan.termalarm.ui.theme.customColors
import java.util.Locale

/**
 * 記録画面。design/Records.dc.htmlの設計に基づき、起床実績の集計指標・週棒グラフ・日別一覧を描画する。
 * 「今週」と「直近30日」の表示期間切り替えを提供し、200%文字拡大時にもスクロール可能なレイアウトを維持する。
 */
@Composable
fun RecordsScreen(
    viewModel: RecordsViewModel,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 画面上部の帯。スクロールの外へ置き、どの画面でも同じ位置に固定する
    Column(modifier = modifier.fillMaxSize()) {
        TermAlarmTopBar(
            onOpenSettings = onOpenSettings,
            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
            onOpenAbout = onOpenAbout,
        )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = SCREEN_HORIZONTAL_PADDING,
                end = SCREEN_HORIZONTAL_PADDING,
                bottom = 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {

        Spacer(modifier = Modifier.height(TOP_BAR_CONTENT_GAP))

        // 1. 見出し「記録」
        Text(
            text = stringResource(R.string.records_title),
            style = TextStyle(
                fontFamily = com.marutyan.termalarm.ui.theme.HeadlineStyle.fontFamily,
                fontWeight = FontWeight.W300,
                fontSize = 28.sp,
                letterSpacing = (-0.01).em,
                color = MaterialTheme.colorScheme.onSurface,
            ),
        )

        // 2. 「今週」「直近30日」切り替えタブ
        PeriodSelector(
            selectedPeriod = uiState.selectedPeriod,
            onSelectPeriod = { viewModel.selectPeriod(it) },
        )

        // 3. 指標3つ（平均回、平均分、前の期間との差）
        MetricsCards(
            selectedPeriod = uiState.selectedPeriod,
            averageOccurrence = uiState.averageOccurrence,
            averageDurationMinutes = uiState.averageDurationMinutes,
            averageOccurrenceDiff = uiState.averageOccurrenceDiff,
        )

        // 4. 週の棒グラフ
        WeeklyBarChart(
            weekDateRangeText = uiState.weekDateRangeText,
            bars = uiState.weeklyBars,
        )

        // 5. 1日ごとの一覧
        DailyRecordList(
            rows = uiState.dailyRows,
            isEmpty = uiState.isEmpty,
        )
    }
    }
}

/**
 * 集計期間（今週 / 直近30日）を切り替える2択ボタン領域。
 * 44dp以上のタッチ領域を確保し、選択中タブを主役色で強調表示する。
 */
@Composable
private fun PeriodSelector(
    selectedPeriod: RecordsPeriod,
    onSelectPeriod: (RecordsPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PeriodTabButton(
            text = stringResource(R.string.records_tab_this_week),
            isSelected = selectedPeriod == RecordsPeriod.THIS_WEEK,
            onClick = { onSelectPeriod(RecordsPeriod.THIS_WEEK) },
            modifier = Modifier.weight(1f),
        )
        PeriodTabButton(
            text = stringResource(R.string.records_tab_last_30_days),
            isSelected = selectedPeriod == RecordsPeriod.LAST_30_DAYS,
            onClick = { onSelectPeriod(RecordsPeriod.LAST_30_DAYS) },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 期間切り替えの個別タブボタン。
 * 選択時はprimaryContainer背景とscalePast枠線、非選択時は透明背景とoutlineVariant枠線を描画する。
 */
@Composable
private fun PeriodTabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val borderColor = if (isSelected) {
        MaterialTheme.customColors.scalePast
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.customColors.subtleText
    }

    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .height(42.dp)
            .background(backgroundColor, RoundedCornerShape(3.dp))
            .border(1.dp, borderColor, RoundedCornerShape(3.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = textColor,
            ),
        )
    }
}

/**
 * 起床実績の3大指標（平均起床回、平均所要分数、前の期間との差）を表示する3分割カード。
 * 等幅数字(tnum)フォントを用いて可読性を確保する。
 */
@Composable
private fun MetricsCards(
    selectedPeriod: RecordsPeriod,
    averageOccurrence: Double?,
    averageDurationMinutes: Double?,
    averageOccurrenceDiff: Double?,
    modifier: Modifier = Modifier,
) {
    // 説明の行が無いカードがあっても3枚の高さがそろうよう、いちばん高いカードに合わせる
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        // カード1: 平均回数
        val occText = averageOccurrence?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
        MetricCard(
            topLabel = stringResource(R.string.records_metric_average),
            valueText = occText,
            unitText = null,
            bottomLabel = stringResource(R.string.records_metric_wake_occurrence_suffix),
            valueColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )

        // カード2: 平均分数
        val durText = averageDurationMinutes?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
        MetricCard(
            topLabel = stringResource(R.string.records_metric_average),
            valueText = durText,
            unitText = if (averageDurationMinutes != null) stringResource(R.string.records_metric_duration_suffix) else null,
            bottomLabel = stringResource(R.string.records_metric_duration_caption),
            valueColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )

        // カード3: 前の期間との差
        val diffTopLabel = when (selectedPeriod) {
            RecordsPeriod.THIS_WEEK -> stringResource(R.string.records_metric_diff_this_week)
            RecordsPeriod.LAST_30_DAYS -> stringResource(R.string.records_metric_diff_last_30_days)
        }
        val (diffText, diffColor) = if (averageOccurrenceDiff != null) {
            val rounded = String.format(Locale.US, "%.1f", kotlin.math.abs(averageOccurrenceDiff))
            val text = when {
                rounded == "0.0" -> "0.0"
                averageOccurrenceDiff < 0.0 -> "−$rounded"
                else -> "+$rounded"
            }
            val color = when {
                rounded == "0.0" -> MaterialTheme.colorScheme.onSurface
                averageOccurrenceDiff < 0.0 -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.tertiary
            }
            text to color
        } else {
            "—" to MaterialTheme.colorScheme.onSurface
        }

        MetricCard(
            topLabel = diffTopLabel,
            valueText = diffText,
            unitText = if (averageOccurrenceDiff != null) stringResource(R.string.records_metric_diff_unit) else null,
            // 差の意味は見出しで足りるため、説明の行は置かない
            bottomLabel = null,
            valueColor = diffColor,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

/**
 * 指標を表示する個別カード。
 * 上部ラベル、数値と単位、下部補足ラベルを垂直方向に配置する。
 */
@Composable
private fun MetricCard(
    topLabel: String,
    valueText: String,
    unitText: String?,
    bottomLabel: String?,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(3.dp),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(3.dp),
            )
            .padding(vertical = 12.dp, horizontal = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = topLabel,
                style = TextStyle(
                    fontFamily = IbmPlexMono,
                    fontSize = 13.sp,
                    letterSpacing = 0.1.em,
                    color = MaterialTheme.customColors.subtleText,
                ),
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = valueText,
                    style = TextStyle(
                        fontFamily = IbmPlexMono,
                        fontWeight = FontWeight.W300,
                        fontSize = 25.sp,
                        fontFeatureSettings = "tnum",
                        color = valueColor,
                        lineHeight = 25.sp,
                    ),
                )
                if (unitText != null) {
                    Text(
                        text = unitText,
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = MaterialTheme.customColors.subtleText,
                        ),
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
            if (bottomLabel != null) {
                Text(
                    text = bottomLabel,
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = MaterialTheme.customColors.subtleText,
                    ),
                )
            }
        }
    }
}

/**
 * 週棒グラフで各曜日の縦列（WeeklyBarColumn）を並べる行の高さ。
 * 上部の数値テキスト（13sp・約18dp）、下部の曜日ラベル（13sp・約18dp）、
 * それらと棒の間の上下の空き（各6dp、計12dp）を確保した上で、
 * 棒本体に十分な高さ（約64dp目安）を割り当てるために112dpとしている。
 * グラフ領域全体の縦幅を決定し、端末の文字サイズ設定を大きくしても
 * 棒が曜日のラベルに重ならずに収まる土台としての役割を持つ。
 */
private val WEEKLY_BAR_ROW_HEIGHT = 112.dp

/**
 * 1週間の起床回数推移を表示する棒グラフカード。
 * 月曜から日曜の7本のバーを描画し、今日は破線枠、未到来日は細線で表現する。
 */
@Composable
private fun WeeklyBarChart(
    weekDateRangeText: String,
    bars: List<WeeklyBarUiModel>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(3.dp),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(3.dp),
            )
            .padding(start = 15.dp, end = 15.dp, top = 14.dp, bottom = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // グラフ上部ヘッダー
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = stringResource(R.string.records_chart_title),
                    style = TextStyle(
                        fontFamily = IbmPlexMono,
                        fontSize = 13.sp,
                        letterSpacing = 0.12.em,
                        color = MaterialTheme.customColors.subtleText,
                    ),
                )
                Text(
                    text = weekDateRangeText,
                    style = TextStyle(
                        fontFamily = IbmPlexMono,
                        fontSize = 13.sp,
                        color = MaterialTheme.customColors.subtleText,
                    ),
                )
            }

            // 7本のバー
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WEEKLY_BAR_ROW_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                bars.forEach { bar ->
                    WeeklyBarColumn(
                        bar = bar,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * 棒グラフの1曜日分の縦列コンポーネント。
 * 上部数値テキスト、棒本体の描画領域、下部曜日ラベルを配置する。
 */
@Composable
private fun WeeklyBarColumn(
    bar: WeeklyBarUiModel,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val scalePastColor = MaterialTheme.customColors.scalePast
    val subtleTextColor = MaterialTheme.customColors.subtleText

    val dayLabelColor = if (bar.isToday) primaryColor else subtleTextColor

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        // 上部数値
        Text(
            text = bar.valueText,
            style = TextStyle(
                fontFamily = IbmPlexMono,
                fontSize = 13.sp,
                color = subtleTextColor,
            ),
        )
        Spacer(modifier = Modifier.height(6.dp))

        // 棒本体
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .drawBehind {
                    when {
                        bar.isDone -> {
                            val color = if (bar.isWarningColor) tertiaryColor else primaryColor
                            val barHeightPx = (size.height * bar.heightRatio)
                                .coerceAtLeast(4.dp.toPx())
                                .coerceAtMost(size.height)
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(0f, size.height - barHeightPx),
                                size = Size(size.width, barHeightPx),
                                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
                            )
                        }
                        bar.isToday && !bar.isDone -> {
                            // 今日の破線枠（残りの高さの半分を目安に描画）
                            val barHeightPx = (size.height * 0.5f).coerceAtMost(size.height)
                            val stroke = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
                            )
                            drawRoundRect(
                                color = scalePastColor,
                                topLeft = Offset(0f, size.height - barHeightPx),
                                size = Size(size.width, barHeightPx),
                                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
                                style = stroke,
                            )
                        }
                        else -> {
                            // 未到来日の細線
                            val barHeightPx = 2.dp.toPx().coerceAtMost(size.height)
                            drawRoundRect(
                                color = outlineVariantColor,
                                topLeft = Offset(0f, size.height - barHeightPx),
                                size = Size(size.width, barHeightPx),
                                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
                            )
                        }
                    }
                },
        )
        Spacer(modifier = Modifier.height(6.dp))

        // 曜日ラベル
        Text(
            text = bar.dayOfWeekText,
            style = TextStyle(
                fontFamily = IbmPlexMono,
                fontSize = 13.sp,
                color = dayLabelColor,
            ),
        )
    }
}

/**
 * 1日ごとの起床実績一覧。
 * 記録が無いときは案内を1行で表示し、記録がある場合は日付順の行リストを描画する。
 */
@Composable
private fun DailyRecordList(
    rows: List<DailyRowUiModel>,
    isEmpty: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = stringResource(R.string.records_list_title),
            style = TextStyle(
                fontFamily = IbmPlexMono,
                fontSize = 13.sp,
                letterSpacing = 0.12.em,
                color = MaterialTheme.customColors.subtleText,
            ),
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = stringResource(R.string.records_empty),
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = MaterialTheme.customColors.subtleText,
                    ),
                )
            }
        } else {
            rows.forEach { row ->
                DailyRecordRow(row = row)
            }
        }
    }
}

/**
 * 1日分の実績行コンポーネント。
 * 日付、回数、所要時間を横並びで配置し、底面に区切り線を描画する。
 */
@Composable
private fun DailyRecordRow(
    row: DailyRowUiModel,
    modifier: Modifier = Modifier,
) {
    val dividerColor = MaterialTheme.customColors.divider
    val subtleTextColor = MaterialTheme.customColors.subtleText
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val occurrenceColor = when {
        row.isOngoing -> primaryColor
        row.isWarningOccurrence -> tertiaryColor
        else -> onSurfaceColor
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                drawLine(
                    color = dividerColor,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height - strokeWidth / 2f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height - strokeWidth / 2f),
                    strokeWidth = strokeWidth,
                )
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 日付
        Text(
            text = row.dateText,
            style = TextStyle(
                fontFamily = IbmPlexMono,
                fontSize = 13.sp,
                fontFeatureSettings = "tnum",
                color = subtleTextColor,
            ),
            modifier = Modifier.width(72.dp),
        )

        // 開始から起きるまでにかかった時間
        Text(
            text = row.durationText,
            style = TextStyle(
                fontFamily = IbmPlexMono,
                fontSize = 13.sp,
                fontFeatureSettings = "tnum",
                color = subtleTextColor,
            ),
            modifier = Modifier.weight(1f),
        )

        // 何回目で起きたか。この画面でいちばん見たい値なので右端へ置く
        Text(
            text = row.occurrenceText,
            style = TextStyle(
                fontFamily = IbmPlexMono,
                fontSize = 14.sp,
                fontFeatureSettings = "tnum",
                color = occurrenceColor,
            ),
        )
    }
}
