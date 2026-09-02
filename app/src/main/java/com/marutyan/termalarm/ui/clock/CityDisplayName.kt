package com.marutyan.termalarm.ui.clock

import android.icu.text.TimeZoneNames
import java.util.Locale

/**
 * "Asia/Tokyo"のようなZoneIdの識別子を、利用者向けの都市名へ変換する。
 * 都市データを自前で持たない方針(docs/SPEC.md「時計タブ」)のため、Androidに組み込まれているICUの
 * タイムゾーン名データ(CLDR)から、そのゾーンの代表都市名(exemplar location)を引く。
 * 例: ロケールがja_JPなら"Asia/Tokyo"→"東京"、既定ロケール(英語)なら"Tokyo"になる。
 * ICUにデータが無いゾーン(固定オフセットの"Etc/GMT+5"など)は、識別子の末尾から機械的に組み立てる。
 * android.icu.*はAndroid実機専用でJVM単体テストでは使えないため、この関数はテスト対象に含めない。
 */
fun cityDisplayName(zoneId: String, locale: Locale = Locale.getDefault()): String =
    TimeZoneNames.getInstance(locale).getExemplarLocationName(zoneId)
        ?: zoneId.substringAfterLast('/').replace('_', ' ')
