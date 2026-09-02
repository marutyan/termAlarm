// ルートプロジェクト。各モジュールで使うプラグインをここで宣言だけしておく（apply falseでバージョン統一）。
// AGP 9系はKotlinコンパイルを内蔵しており org.jetbrains.kotlin.android は不要（適用するとエラーになる）。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
