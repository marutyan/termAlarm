package com.marutyan.termalarm.domain

/**
 * 世界時計に登録した1都市。zoneIdは`ZoneId.getAvailableZoneIds()`の中から選ばれた識別子
 * （例: "Asia/Tokyo"）で、都市データを自前で持たない方針(docs/SPEC.md「時計タブ」)に合わせて
 * これ単体が唯一の情報源になる。sortOrderは一覧での表示順で、並べ替えるたびに0始まりで振り直す。
 * idは新規追加時は0を渡し、RoomのautoGenerateで採番される(AlarmScheduleと同じ運用)。
 */
data class WorldClockCity(
    val id: Long,
    val zoneId: String,
    val sortOrder: Int,
)

/**
 * 一覧からidの都市を取り除いた新しい一覧を返す。sortOrderは詰め直す。
 * DBやUIの状態から切り離して削除ロジックを単体テストするための純粋関数。
 */
fun List<WorldClockCity>.withoutCity(id: Long): List<WorldClockCity> =
    filterNot { it.id == id }.reindexed()

/**
 * fromIndexにある都市をtoIndexへ移した新しい一覧を返す。sortOrderは0始まりで振り直す。
 * fromIndex/toIndexが範囲外、または同じ位置を指す場合は元の一覧をそのまま返す。
 */
fun List<WorldClockCity>.movedCity(fromIndex: Int, toIndex: Int): List<WorldClockCity> {
    if (fromIndex == toIndex || fromIndex !in indices || toIndex !in indices) return this
    return toMutableList().apply { add(toIndex, removeAt(fromIndex)) }.reindexed()
}

// sortOrderを一覧の並び順どおり0,1,2...に振り直す
private fun List<WorldClockCity>.reindexed(): List<WorldClockCity> =
    mapIndexed { index, city -> city.copy(sortOrder = index) }
