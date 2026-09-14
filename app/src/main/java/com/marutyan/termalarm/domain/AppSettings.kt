package com.marutyan.termalarm.domain

/**
 * アプリの配色テーマを表す列挙型。
 * 画面全体の配色を切り替えるために用い、アプリアイコン由来の紺(NAVY)、明るい配色(LIGHT)、黒基調(BLACK)、壁紙に基づく動的配色(DYNAMIC)を指定する。
 */
enum class AppTheme {
    NAVY,
    LIGHT,
    BLACK,
    DYNAMIC,
}

/**
 * アプリ全体の設定値（docs/SPEC.md「アプリ全体の設定」）。
 * タームごとに変える意味が薄いものをここへ集める。
 */
data class AppSettings(
    val alarmSoundUri: String? = null, // null ならシステム既定のアラーム音
    val vibration: Boolean = true, // 鳴動時に振動するか。既定 true
    val fadeInSeconds: Int = 5, // 1回の鳴動の中で音量を上げきるまでの秒数。既定5
    val silenceAfterMinutes: Int? = null, // 放置したとき自動で止まるまでの分数。null なら止めない。既定 null
    val wakeCheckMinutes: Int = 5, // ターム終了後、二度寝チェックまでの分数。既定5
    val theme: AppTheme = AppTheme.NAVY, // 既定 NAVY
    val enabledGames: Set<GameType> = GameType.entries.toSet() - GameType.WALK, // 出題を有効にするミニゲームの集合。WALKは起床負荷が高いため既定では除外する
)

