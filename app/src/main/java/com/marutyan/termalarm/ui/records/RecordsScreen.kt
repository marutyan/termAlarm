package com.marutyan.termalarm.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
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
import com.marutyan.termalarm.R
import com.marutyan.termalarm.ui.theme.IbmPlexMono
import com.marutyan.termalarm.ui.theme.customColors
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 記録画面。design/Records.dc.htmlの設計に基づき、起床実績の集計指標・週棒グラフ・日別一覧を描画する。
 * 「今週」と「直近30日」の表示期間切り替えを提供し、200%文字拡大時にもスクロール可能なレイアウトを維持する。
 */
@Composable
fun RecordsScreen(
    viewModel: RecordsViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = statusBarTop + 74.dp,
                start = 18.dp,
                end = 18.dp,
                bottom = 32.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
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

        // 3. 指標3つ（平均回、平均分、放置割合）
        MetricsCards(
            averageOccurrence = uiState.averageOccurrence,
            averageDurationMinutes = uiState.averageDurationMinutes,
            autoSilencedRatio = uiState.autoSilencedRatio,
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
 * 起床実績の3大指標（平均起床回、平均所要分数、放置割合）を表示する3分割カード。
 * 等幅数字(tnum)フォントを用いて可読性を確保する。
 */
@Composable
private fun MetricsCards(
    averageOccurrence: Double?,
    averageDurationMinutes: Double?,
    autoSilencedRatio: Double,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
            modifier = Modifier.weight(1f),
        )

        // カード2: 平均分数
        val durText = averageDurationMinutes?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
        MetricCard(
            topLabel = stringResource(R.string.records_metric_average),
            valueText = durText,
            unitText = if (averageDurationMinutes != null) stringResource(R.string.records_metric_duration_suffix) else null,
            bottomLabel = stringResource(R.string.records_metric_duration_caption),
            valueColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        // カード3: 放置割合
        val percent = (autoSilencedRatio * 100).roundToInt()
        MetricCard(
            topLabel = stringResource(R.string.records_metric_auto_silenced),
            valueText = percent.toString(),
            unitText = stringResource(R.string.records_metric_percent_suffix),
            bottomLabel = stringResource(R.string.records_metric_auto_silenced_caption),
            valueColor = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f),
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
    bottomLabel: String,
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
                    fontSize = 11.sp,
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
                            fontSize = 12.sp,
                            color = MaterialTheme.customColors.subtleText,
                        ),
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
            Text(
                text = bottomLabel,
                style = TextStyle(
                    fontSize = 11.sp,
                    color = MaterialTheme.customColors.subtleText,
                ),
            )
        }
    }
}

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
                        fontSize = 11.sp,
                        letterSpacing = 0.12.em,
                        color = MaterialTheme.customColors.subtleText,
                    ),
                )
                Text(
                    text = weekDateRangeText,
                    style = TextStyle(
                        fontFamily = IbmPlexMono,
                        fontSize = 11.sp,
                        color = MaterialTheme.customColors.subtleText,
                    ),
                )
            }

            // 7本のバー
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
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
                fontSize = 11.sp,
                color = subtleTextColor,
            ),
        )
        Spacer(modifier = Modifier.height(6.dp))

        // 棒本体
        val barHeight = when {
            bar.isDone -> (bar.heightRatio * 60f).coerceIn(4f, 60f).dp
            bar.isToday && !bar.isDone -> 26.dp
            else -> 2.dp
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .drawBehind {
                    when {
                        bar.isDone -> {
                            val color = if (bar.isWarningColor) tertiaryColor else primaryColor
                            drawRoundRect(
                                color = color,
                                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
                            )
                        }
                        bar.isToday && !bar.isDone -> {
                            // 今日の破線枠
                            val stroke = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
                            )
                            drawRoundRect(
                                color = scalePastColor,
                                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
                                style = stroke,
                            )
                        }
                        else -> {
                            // 未到来日の細線
                            drawRoundRect(
                                color = outlineVariantColor,
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
                fontSize = 11.sp,
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
                fontSize = 11.sp,
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
 * 日付、回数、所要時間、停止方法を横並びで配置し、底面に区切り線を描画する。
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

    val methodColor = if (row.isWarningMethod) tertiaryColor else subtleTextColor

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
                fontSize = 12.sp,
                fontFeatureSettings = "tnum",
                color = subtleTextColor,
            ),
            modifier = Modifier.width(64.dp),
        )

        // 回数
        Text(
            text = row.occurrenceText,
            style = TextStyle(
                fontFamily = IbmPlexMono,
                fontSize = 14.sp,
                fontFeatureSettings = "tnum",
                color = occurrenceColor,
            ),
            modifier = Modifier.width(52.dp),
        )

        // 所要時間
        Text(
            text = row.durationText,
            style = TextStyle(
                fontFamily = IbmPlexMono,
                fontSize = 12.5.sp,
                fontFeatureSettings = "tnum",
                color = subtleTextColor,
            ),
            modifier = Modifier.weight(1f),
        )

        // 止め方
        Text(
            text = row.methodText,
            style = TextStyle(
                fontSize = 11.5.sp,
                color = methodColor,
            ),
        )
    }
}
