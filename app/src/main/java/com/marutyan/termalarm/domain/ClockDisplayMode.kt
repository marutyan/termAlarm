package com.marutyan.termalarm.domain

/**
 * 時計タブの時計をアナログ/デジタルのどちらで表示するかの設定(docs/SPEC.md「時計タブ」)。
 * 画面下のトグルで切り替え、選んだ側を設定として残す。
 */
enum class ClockDisplayMode {
    ANALOG,
    DIGITAL,
}
