package com.marutyan.termalarm.ui.clock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.ClockDisplayMode
import com.marutyan.termalarm.domain.TimeDifference
import com.marutyan.termalarm.domain.WorldClockCity
import com.marutyan.termalarm.domain.timeDifference
import com.marutyan.termalarm.ui.theme.tabularNums
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// 世界時計の各都市の時刻表示に使うフォーマット。分までにとどめ、秒は出さない(docs/SPEC.md「更新頻度」)
private val CITY_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// 端末の時刻をデジタル表示するときのフォーマット。CITY_TIME_FORMATTERと同じ粒度に揃える
private val DIGITAL_CLOCK_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 時計タブの画面。端末の現在時刻を設定どおりアナログ/デジタルで表示し、その下に世界時計の
 * 都市一覧(追加・削除・並べ替え可能)を出す(docs/SPEC.md「時計タブ」)。
 */
@Composable
fun ClockScreen(viewModel: ClockViewModel, bottomBar: @Composable () -> Unit) {
    val cities by viewModel.cities.collectAsStateWithLifecycle()
    val displayMode by viewModel.displayMode.collectAsStateWithLifecycle()
    var showAddCity by rememberSaveable { mutableStateOf(false) }

    // アナログは秒針が動くため1秒ごと、デジタルは分までの表示なので1分ごとに更新する
    val now = if (displayMode == ClockDisplayMode.ANALOG) rememberCurrentSecond() else rememberCurrentMinute()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_clock), style = MaterialTheme.typography.headlineMedium) }) },
        bottomBar = bottomBar,
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddCity = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.clock_add_city))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { DisplayModeToggle(mode = displayMode, onModeChange = viewModel::setDisplayMode) }
            item {
                MainClock(
                    mode = displayMode,
                    time = now,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = stringResource(R.string.clock_world_clock_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (cities.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.clock_city_list_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(cities, key = { _, city -> city.id }) { index, city ->
                CityRow(
                    city = city,
                    nowInstant = now.toInstant(),
                    deviceZone = now.zone,
                    canMoveUp = index > 0,
                    canMoveDown = index < cities.lastIndex,
                    onMoveUp = { viewModel.moveCity(index, index - 1) },
                    onMoveDown = { viewModel.moveCity(index, index + 1) },
                    onDelete = { viewModel.removeCity(city.id) },
                )
            }
        }
    }

    if (showAddCity) {
        AddCityDialog(
            existingZoneIds = remember(cities) { cities.map { it.zoneId }.toSet() },
            onDismiss = { showAddCity = false },
            onSelect = { zoneId ->
                viewModel.addCity(zoneId)
                showAddCity = false
            },
        )
    }
}

// 表示モード(アナログ/デジタル)を選ぶ2択のセグメントボタン
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DisplayModeToggle(mode: ClockDisplayMode, onModeChange: (ClockDisplayMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = mode == ClockDisplayMode.ANALOG,
            onClick = { onModeChange(ClockDisplayMode.ANALOG) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
            Text(stringResource(R.string.clock_display_mode_analog))
        }
        SegmentedButton(
            selected = mode == ClockDisplayMode.DIGITAL,
            onClick = { onModeChange(ClockDisplayMode.DIGITAL) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
            Text(stringResource(R.string.clock_display_mode_digital))
        }
    }
}

// 端末の現在時刻を、設定どおりアナログ(Canvas描画)またはデジタル(等幅数字)で表示する
@Composable
private fun MainClock(mode: ClockDisplayMode, time: ZonedDateTime, modifier: Modifier = Modifier) {
    when (mode) {
        ClockDisplayMode.ANALOG -> AnalogClockFace(time = time, modifier = modifier.size(240.dp))
        ClockDisplayMode.DIGITAL -> Text(
            text = time.format(DIGITAL_CLOCK_FORMATTER),
            style = MaterialTheme.typography.displayLarge.tabularNums(),
            modifier = modifier,
        )
    }
}

// 世界時計の1都市分の行。都市名・生のzoneId・時差を左に、時刻を右に、並べ替えと削除のボタンを続ける
@Composable
private fun CityRow(
    city: WorldClockCity,
    nowInstant: Instant,
    deviceZone: ZoneId,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    val targetZone = remember(city.zoneId) { ZoneId.of(city.zoneId) }
    val name = remember(city.zoneId) { cityDisplayName(city.zoneId) }
    val timeText = remember(nowInstant, targetZone) {
        ZonedDateTime.ofInstant(nowInstant, targetZone).format(CITY_TIME_FORMATTER)
    }
    val diff = remember(nowInstant, targetZone, deviceZone) { timeDifference(targetZone, deviceZone, nowInstant) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = formatTimeDifference(diff),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = timeText,
                style = MaterialTheme.typography.headlineSmall.tabularNums(),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.clock_move_up))
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.clock_move_down))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.clock_delete_city))
            }
        }
    }
}

// domainのTimeDifference(数値のみ)をstrings.xmlの文言へ変換する(remainingTimeTextと同じ考え方)。
// 符号("+"/"-")は言語ではなく記号なので文字列リソース化せず、プレースホルダへそのまま渡す
@Composable
private fun formatTimeDifference(diff: TimeDifference): String {
    val sign = if (diff.isAhead) "+" else "-"
    val timeText = when {
        diff.hourPart == 0 && diff.minutePart == 0 -> stringResource(R.string.clock_diff_same_time)
        diff.minutePart == 0 -> stringResource(R.string.clock_diff_hours, sign, diff.hourPart)
        diff.hourPart == 0 -> stringResource(R.string.clock_diff_minutes, sign, diff.minutePart)
        else -> stringResource(R.string.clock_diff_hours_minutes, sign, diff.hourPart, diff.minutePart)
    }
    val dayText = when {
        diff.dayOffset == -1 -> stringResource(R.string.clock_diff_day_before)
        diff.dayOffset == 1 -> stringResource(R.string.clock_diff_day_after)
        diff.dayOffset <= -2 -> stringResource(R.string.clock_diff_days_before, -diff.dayOffset)
        diff.dayOffset >= 2 -> stringResource(R.string.clock_diff_days_after, diff.dayOffset)
        else -> null
    }
    return if (dayText != null) "$timeText $dayText" else timeText
}

// 都市追加ダイアログ。ZoneId.getAvailableZoneIds()全件を検索して選ぶ(都市データを自前で持たない方針)
@Composable
private fun AddCityDialog(existingZoneIds: Set<String>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val locale = remember { Locale.getDefault() }
    // 全ゾーンIDの一覧と表示名の組を1回だけ作り、検索のたびに作り直さない(約600件)
    val allCities = remember(locale) {
        ZoneId.getAvailableZoneIds()
            .map { zoneId -> zoneId to cityDisplayName(zoneId, locale) }
            .sortedBy { it.second }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, allCities, existingZoneIds) {
        allCities.filter { (zoneId, name) ->
            zoneId !in existingZoneIds &&
                (query.isBlank() || name.contains(query, ignoreCase = true) || zoneId.contains(query, ignoreCase = true))
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                    Text(
                        text = stringResource(R.string.clock_add_city_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.clock_search_city)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered, key = { it.first }) { (zoneId, name) ->
                        ListItem(
                            headlineContent = { Text(name) },
                            supportingContent = { Text(zoneId) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { onSelect(zoneId) },
                        )
                    }
                }
            }
        }
    }
}
