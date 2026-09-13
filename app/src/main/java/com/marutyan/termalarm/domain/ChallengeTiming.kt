package com.marutyan.termalarm.domain

/**
 * 鳴動時に解除チャレンジを出題するタイミングを表す列挙型。
 * 出題有無を難易度と分離して制御するために用い、アラームごとに問題をいつ出題するかを決定する。
 */
enum class ChallengeTiming {
    /** 問題を出さない。押すだけで停止する */
    NEVER,

    /** ターム終了時の1回だけ出題する。途中は押すだけで停止する */
    END_ONLY,

    /** 鳴動のたびに出題する */
    EVERY_TIME,
}
