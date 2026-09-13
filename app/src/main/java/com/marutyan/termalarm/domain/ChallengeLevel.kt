package com.marutyan.termalarm.domain

/**
 * 鳴動停止時に要求する解除チャレンジの難易度を表す列挙型。
 * 出題される際の問題数の計算に用い、固定1問(EASY)または進捗率に応じて増える1〜3問(HARD)を指定する。
 */
enum class ChallengeLevel {
    /** 毎回1問出題する */
    EASY,

    /** 範囲の進捗率に応じて1〜3問を出題する */
    HARD,
}
