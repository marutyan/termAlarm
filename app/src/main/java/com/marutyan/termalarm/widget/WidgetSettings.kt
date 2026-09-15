package com.marutyan.termalarm.widget

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceTheme
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.unit.ColorProvider
import com.marutyan.termalarm.ui.theme.BlackSurface

/**
 * ウィジェットの時刻表示に適用する色の選択肢を定義する列挙型。
 * 月日や次の鳴動時刻を白で固定しつつ、メインとなる時刻の視認性や好みに応じた色を選べるようにするために必要となる。
 * ウィジェット設定画面での色選択項目および描画時の文字色決定処理において、選ばれた時刻色を識別・伝達する役割を持つ。
 */
enum class WidgetTimeColor {
    /**
     * 白色の時刻表示。
     * 暗い背景や壁紙上で最も安定した視認性を確保するために必要となる。
     * 時刻色設定の既定値および標準的な明色表示として画面描画に用いられる役割を持つ。
     */
    WHITE,

    /**
     * 黒色の時刻表示。
     * 明るい壁紙上で時刻をくっきりと読めるようにするために必要となる。
     * コントラストを反転させた暗色表示の選択肢として画面描画に用いられる役割を持つ。
     */
    BLACK,

    /**
     * 端末の動的カラー（Material You）に連動する時刻表示。
     * システムの壁紙やOS全体のカラーテーマとウィジェットの外観を調和させるために必要となる。
     * Android 12以降の端末色を活かしたパーソナライズ表示を提供する役割を持つ。
     */
    SYSTEM,
}

/**
 * ウィジェットの背景表示スタイルを定義する列挙型。
 * ホーム画面の壁紙をそのまま見せるか、黒背景で文字の可読性を優先するかをユーザーが選択できるようにするために必要となる。
 * ウィジェット設定画面での背景選択項目およびウィジェット描画時の背景色適用において、背景の表示形式を決定する役割を持つ。
 */
enum class WidgetBackgroundStyle {
    /**
     * 完全に透明な背景スタイル。
     * ホーム画面の壁紙デザインを邪魔せずウィジェットの情報を溶け込ませるために必要となる。
     * 透過表示を選択した際の背景描画を透明色で行う役割を持つ。
     */
    TRANSPARENT,

    /**
     * 背景付きのスタイル。
     * 多様な壁紙の上でも文字のコントラストを常に担保して視認性を高めるために必要となる。
     * 既定の表示形式として暗色背景（BlackSurface）を敷いて文字を際立たせる役割を持つ。
     */
    FILLED,
}

/**
 * ウィジェットの数字と文字に適用する書体の選択肢を定義する列挙型。
 * Glanceウィジェット上で確実に描画可能なフォントから好みの書体を選択できるようにするために必要となる。
 * 設定画面でのフォント選択およびウィジェット描画時のテキストスタイル構築において、FontFamilyを提供する役割を持つ。
 *
 * @property family Glanceのテキスト描画に用いるフォントファミリー。
 */
enum class WidgetFontStyle(val family: FontFamily) {
    /**
     * 標準のゴシック体フォント（Sans-Serif）。
     * どの端末環境でも安定した視認性と自然な可読性を確保するために必要となる。
     * 書体設定の既定値として標準的なテキスト表示を提供する役割を持つ。
     */
    STANDARD(FontFamily.SansSerif),

    /**
     * 等幅フォント（Monospace）。
     * 各文字の幅を均一にして、時刻の更新時や数字の変化による文字の横揺れを防ぐために必要となる。
     * カウンターやデジタル時計らしい正確な佇まいを提供する役割を持つ。
     */
    MONOSPACE(FontFamily.Monospace),

    /**
     * 明朝体フォント（Serif）。
     * 落ち着いた書籍風のクラシックな佇まいを好むユーザーの要望に応えるために必要となる。
     * 個性的なテキストデザインでウィジェットを演出する役割を持つ。
     */
    SERIF(FontFamily.Serif),
}

/**
 * ウィジェットの文字の太さの選択肢を定義する列挙型。
 * ウィジェットの文字にメリハリを付け、視認性や好みに応じた太さを選べるようにするために必要となる。
 * 設定画面での太さ選択およびウィジェット描画時のフォントウェイト決定において、GlanceのFontWeightを提供する役割を持つ。
 *
 * @property glanceWeight Glanceのテキスト描画に用いる文字の太さ。
 */
enum class WidgetFontWeight(val glanceWeight: FontWeight) {
    /**
     * 標準の太さ。
     * 最もバランスの取れた標準的な太さで日常的な読みやすさを確保するために必要となる。
     * 文字太さ設定の既定値として標準描画を提供する役割を持つ。
     */
    NORMAL(FontWeight.Normal),

    /**
     * 中間の太さ。
     * 標準では少し細く感じるが太字ほど主張させたくない場合に適切な強調を行うために必要となる。
     * 適度な視認性向上を図る中間ウェイトを提供する役割を持つ。
     */
    MEDIUM(FontWeight.Medium),

    /**
     * 太字。
     * 離れた場所からでも一目で時刻やアラーム状況を確認できるようにするために必要となる。
     * 最も強いコントラストと強調でテキストを表示する役割を持つ。
     */
    BOLD(FontWeight.Bold),
}

/**
 * ウィジェットごとに選んだ時刻の色設定をDataStoreに保存・取得するためのキー。
 * 以前の版の設定値を引き継がず新たな体系で時刻色を安全に永続化するために必要となる。
 * 設定画面での保存処理およびウィジェット更新時の設定読み出しにおいて、時刻色の保存項目を一意に識別する役割を持つ。
 */
val WIDGET_TIME_COLOR_KEY: Preferences.Key<String> = stringPreferencesKey("widget_time_color_v2")

/**
 * ウィジェットごとに選んだ背景スタイル設定をDataStoreに保存・取得するためのキー。
 * 透過または背景付きのどちらが選択されたかを永続化するために必要となる。
 * 設定画面での保存処理およびウィジェット更新時の設定読み出しにおいて、背景スタイルの保存項目を一意に識別する役割を持つ。
 */
val WIDGET_BACKGROUND_STYLE_KEY: Preferences.Key<String> = stringPreferencesKey("widget_background_style")

/**
 * ウィジェットごとに選んだ書体設定をDataStoreに保存・取得するためのキー。
 * ユーザーが指定したフォントスタイルを端末再起動後も一貫して適用できるようにするために必要となる。
 * 設定画面での保存処理およびウィジェット更新時の設定読み出しにおいて、書体設定を一意に識別する役割を持つ。
 */
val WIDGET_FONT_STYLE_KEY: Preferences.Key<String> = stringPreferencesKey("widget_font_style")

/**
 * ウィジェットごとに選んだ文字の太さ設定をDataStoreに保存・取得するためのキー。
 * ユーザーが指定したフォントウェイトを永続化してウィジェットの描画に反映するために必要となる。
 * 設定画面での保存処理およびウィジェット更新時の設定読み出しにおいて、太さ設定を一意に識別する役割を持つ。
 */
val WIDGET_FONT_WEIGHT_KEY: Preferences.Key<String> = stringPreferencesKey("widget_font_weight")

/**
 * DataStoreのPreferencesから保存された時刻の色設定を読み出す関数。
 * 未保存時や想定外の文字列が保存されていた場合でも安全に既定値へフォールバックさせるために必要となる。
 * ウィジェット描画処理や設定画面の初期表示において、有効なWidgetTimeColorを常に提供する役割を持つ。
 */
fun widgetTimeColor(preferences: Preferences): WidgetTimeColor {
    val name = preferences[WIDGET_TIME_COLOR_KEY] ?: return WidgetTimeColor.WHITE
    return runCatching { WidgetTimeColor.valueOf(name) }.getOrDefault(WidgetTimeColor.WHITE)
}

/**
 * DataStoreのPreferencesから保存された背景スタイル設定を読み出す関数。
 * 未設定時や不正な値が保存されていた場合でも安全に既定値へフォールバックさせるために必要となる。
 * ウィジェットの背景描画処理や設定画面の初期状態復元において、有効なWidgetBackgroundStyleを常に提供する役割を持つ。
 */
fun widgetBackgroundStyle(preferences: Preferences): WidgetBackgroundStyle {
    val name = preferences[WIDGET_BACKGROUND_STYLE_KEY] ?: return WidgetBackgroundStyle.FILLED
    return runCatching { WidgetBackgroundStyle.valueOf(name) }.getOrDefault(WidgetBackgroundStyle.FILLED)
}

/**
 * DataStoreのPreferencesから保存された書体設定を読み出す関数。
 * 未設定時や未知の値が格納されていた場合に安全に既定の標準書体へフォールバックさせるために必要となる。
 * ウィジェット描画や設定画面の初期化において、有効なWidgetFontStyleを常に提供する役割を持つ。
 */
fun widgetFontStyle(preferences: Preferences): WidgetFontStyle {
    val name = preferences[WIDGET_FONT_STYLE_KEY] ?: return WidgetFontStyle.STANDARD
    return runCatching { WidgetFontStyle.valueOf(name) }.getOrDefault(WidgetFontStyle.STANDARD)
}

/**
 * DataStoreのPreferencesから保存された文字の太さ設定を読み出す関数。
 * 未設定時や未知の値が格納されていた場合に安全に既定の標準太さへフォールバックさせるために必要となる。
 * ウィジェット描画や設定画面の初期化において、有効なWidgetFontWeightを常に提供する役割を持つ。
 */
fun widgetFontWeight(preferences: Preferences): WidgetFontWeight {
    val name = preferences[WIDGET_FONT_WEIGHT_KEY] ?: return WidgetFontWeight.NORMAL
    return runCatching { WidgetFontWeight.valueOf(name) }.getOrDefault(WidgetFontWeight.NORMAL)
}

/**
 * ウィジェットの月日および次の鳴動時刻の描画に用いる色を生成する関数。
 * 契約仕様に基づき、月日と次回アラーム時刻の表示色を常に白色（Color.White）で固定して提供するために必要となる。
 * ウィジェット描画コンポーザブルにおいて、副次情報のテキスト色を一貫したColorProviderとして供給する役割を持つ。
 */
@Composable
fun widgetSecondaryColorProvider(): ColorProvider {
    return ColorProvider(Color.White)
}

/**
 * 選択された時刻色設定に対応するColorProviderを生成する関数。
 * 白・黒の固定色描画に加え、システム動的カラー選択時にAndroid 12以降のプライマリ色を適用（未満は白）するために必要となる。
 * ウィジェットのメイン時刻表示部分に対して、OSバージョンと設定値に応じた最適なColorProviderを供給する役割を持つ。
 */
@Composable
fun widgetTimeColorProvider(timeColor: WidgetTimeColor): ColorProvider {
    return when (timeColor) {
        WidgetTimeColor.WHITE -> ColorProvider(Color.White)
        WidgetTimeColor.BLACK -> ColorProvider(Color.Black)
        WidgetTimeColor.SYSTEM -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                GlanceTheme.colors.primary
            } else {
                ColorProvider(Color.White)
            }
        }
    }
}

/**
 * 選択された背景スタイル設定に対応するColorProviderを生成する関数。
 * 透過スタイル時の完全透明色と、背景付きスタイル時のBlackSurface色を切り替えて提供するために必要となる。
 * ウィジェットのルートコンテナ背景に対して、指定されたスタイルに応じたColorProviderを供給する役割を持つ。
 */
@Composable
fun widgetBackgroundProvider(style: WidgetBackgroundStyle): ColorProvider {
    return when (style) {
        WidgetBackgroundStyle.TRANSPARENT -> ColorProvider(Color(0x00000000))
        WidgetBackgroundStyle.FILLED -> ColorProvider(BlackSurface)
    }
}
