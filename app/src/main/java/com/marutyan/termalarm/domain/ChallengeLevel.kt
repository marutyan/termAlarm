package com.marutyan.termalarm.domain

/**
 * 鳴動停止時に要求する解除チャレンジの難易度。
 * 二度寝を防ぐため、朝の起床状況に応じて出題なし(NONE)、固定1問(LIGHT)、
 * 進捗率に応じて増える1〜3問(HARD)を指定する。
 */
enum class ChallengeLevel {
    /** 問題を出題しない。停止操作だけでアラームを止める */
    NONE,

    /** 毎回1問出題する */
    LIGHT,

    /** 範囲の進捗率に応じて1〜3問を出題する */
    HARD,
}
