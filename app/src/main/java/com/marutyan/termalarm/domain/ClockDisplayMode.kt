package com.marutyan.termalarm.domain

/**
 * 時計タブのメインの時計をアナログ/デジタルのどちらで表示するかの設定(docs/SPEC.md「時計タブ」)。
 * 世界時計の各都市の行は一覧の読みやすさを優先し、この設定に関わらず常にデジタル表示にする。
 */
enum class ClockDisplayMode {
    ANALOG,
    DIGITAL,
}
