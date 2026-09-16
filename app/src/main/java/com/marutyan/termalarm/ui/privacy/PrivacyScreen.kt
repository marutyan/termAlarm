package com.marutyan.termalarm.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.marutyan.termalarm.R
import com.marutyan.termalarm.ui.theme.IbmPlexMono
import com.marutyan.termalarm.ui.theme.TEXT_MIN_SIZE
import com.marutyan.termalarm.ui.theme.customColors

// 表の左列（項目名・識別子）の固定幅。スマートフォンの画面幅において右列の説明領域を十分に確保しつつ、権限の英字識別子を無理なく収めるために130dpとする。
private val TABLE_LEFT_COLUMN_WIDTH = 130.dp

/**
 * ポリシーのセクション中見出しを表示するComposable。
 * Markdownの「##」記号を排除し、アプリのタイポグラフィ（titleMedium、太字）に合わせた大きさと太さで見出しを際立たせるために必要となる。
 * ポリシー画面内の情報の区切りを視覚的に明確にする役割を持つ。
 */
@Composable
private fun PolicySectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

/**
 * ポリシーの通常段落テキストを表示するComposable。
 * 本文の可読性を確保するため、標準のbodyMediumスタイルとonSurface色を適用して表示するために必要となる。
 * ポリシーの主たる説明文をユーザーに伝える役割を持つ。
 */
@Composable
private fun PolicyParagraph(
    text: String,
    modifier: Modifier = Modifier,
    isBold: Boolean = false,
) {
    Text(
        text = text,
        style = if (isBold) {
            MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
        } else {
            MaterialTheme.typography.bodyMedium
        },
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

/**
 * 装飾付き文字列（AnnotatedString）によるポリシー段落テキストを表示するComposable。
 * 太字強調や等幅コード（IbmPlexMono）が混在する文章をMarkdown記号なしで綺麗にレンダリングするために必要となる。
 * インターネット権限名や特定フレーズを強調した説明文を表示する役割を持つ。
 */
@Composable
private fun PolicyParagraph(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

/**
 * ポリシーの箇条書きの1項目を表示するComposable。
 * Markdownのハイフン（-）記号を出さず、行頭に中黒（・）を配置した2列の横並びレイアウトにより折り返し時にも美しく揃えるために必要となる。
 * 個人情報や通信の非収集項目などを並列して分かりやすく提示する役割を持つ。
 */
@Composable
private fun PolicyBulletItem(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "・",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.customColors.subtleText,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 通常文字列によるポリシー箇条書き項目を表示するComposable。
 * 内部でAnnotatedString版を呼び出して実装の重複を防ぎ、シンプルな箇条書き行を簡潔に定義するために必要となる。
 * 装飾のない箇条書き項目を画面に描画する役割を持つ。
 */
@Composable
private fun PolicyBulletItem(
    text: String,
    modifier: Modifier = Modifier,
) {
    PolicyBulletItem(text = AnnotatedString(text), modifier = modifier)
}

/**
 * ポリシーの2列の表のヘッダー行を表示するComposable。
 * 左列の項目タイトルと右列の説明タイトルを等幅フォントと補助色で表示し、下部に薄い区切り線を配置するために必要となる。
 * 表全体の列構成（項目と説明、権限と理由）をユーザーに明瞭に示す役割を持つ。
 */
@Composable
private fun PolicyTableHeader(
    leftTitle: String,
    rightTitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = leftTitle,
                style = TextStyle(
                    fontFamily = IbmPlexMono,
                    fontSize = TEXT_MIN_SIZE,
                    letterSpacing = 0.05.em,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.customColors.subtleText,
                ),
                modifier = Modifier.width(TABLE_LEFT_COLUMN_WIDTH),
            )
            Text(
                text = rightTitle,
                style = TextStyle(
                    fontFamily = IbmPlexMono,
                    fontSize = TEXT_MIN_SIZE,
                    letterSpacing = 0.05.em,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.customColors.subtleText,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.customColors.divider,
        )
    }
}

/**
 * ポリシーの2列の表におけるデータ行を表示するComposable。
 * 左列に項目名および権限識別子を固定幅で配置し、右列に残りの幅を用いて詳細な用途や理由を表示して、行間に薄い区切り線を引くために必要となる。
 * 保存情報一覧や利用権限一覧の各行を整然と閲覧できるようにする役割を持つ。
 */
@Composable
private fun PolicyTableRowItem(
    title: String,
    description: String,
    identifier: String? = null,
    isLast: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // 左列: 項目（幅固定）
            Column(modifier = Modifier.width(TABLE_LEFT_COLUMN_WIDTH)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (identifier != null) {
                    Text(
                        text = identifier,
                        style = TextStyle(
                            fontFamily = IbmPlexMono,
                            fontSize = TEXT_MIN_SIZE,
                            lineHeight = 16.sp,
                            color = MaterialTheme.customColors.subtleText,
                        ),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            // 右列: 説明（残りを使う）
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }

        if (!isLast) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.customColors.divider,
            )
        }
    }
}

/**
 * プライバシーポリシーを表示する画面。各タブ右上の「⋮」メニューから遷移する。
 * 外部通信を行わずにスクロール可能なレイアウトとして、見出し・段落・箇条書き・表からなるポリシー本文を閲覧できるようにする。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_policy_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // 大見出しと更新日
            Text(
                text = "TermAlarm プライバシーポリシー",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "最終更新日: 2026年9月17日",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.customColors.subtleText,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )

            // 第1条 本ポリシーについて
            PolicySectionTitle(text = "第1条 本ポリシーについて")
            PolicyParagraph(
                text = "本プライバシーポリシー（以下「本ポリシー」といいます。）は、marutyan（以下「提供者」といいます。）が提供するAndroidアプリケーション「TermAlarm」（以下「本アプリケーション」といいます。）における利用者の情報の取り扱いについて定めるものです。本ポリシーは、本アプリケーションをご利用になるすべての利用者に適用されます。",
            )

            // 第2条 取得する情報
            PolicySectionTitle(text = "第2条 取得する情報")
            PolicyParagraph(
                text = "このアプリは、利用者に関する情報を一切収集しません。",
                isBold = true,
            )
            PolicyBulletItem(text = "個人情報の取得: 行いません")
            PolicyBulletItem(text = "利用状況の記録および分析: 行いません")
            PolicyBulletItem(text = "広告の表示: 行いません")
            val internetBullet = remember {
                buildAnnotatedString {
                    append("外部との通信: 行いません。インターネットへの接続権限（")
                    withStyle(SpanStyle(fontFamily = IbmPlexMono)) {
                        append("INTERNET")
                    }
                    append("）自体を保持していません")
                }
            }
            PolicyBulletItem(text = internetBullet)
            PolicyParagraph(
                text = "なお、Androidの標準機能による自動バックアップはOS（オペレーティングシステム）が実行するものであり、本アプリケーション自身が通信を行うものではありません。詳細については「第5条 バックアップ」をご確認ください。",
            )

            // 第3条 端末内に保存する情報
            PolicySectionTitle(text = "第3条 端末内に保存する情報")
            val storageIntro = remember {
                buildAnnotatedString {
                    append("本アプリケーションが正常に動作するために、次の情報を")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("端末内のみ")
                    }
                    append("に保存します。")
                }
            }
            PolicyParagraph(text = storageIntro)

            Spacer(modifier = Modifier.height(4.dp))
            PolicyTableHeader(leftTitle = "項目", rightTitle = "説明")
            PolicyTableRowItem(
                title = "アラームの設定",
                description = "アラームを鳴らすため（時刻の範囲、間隔、繰り返す曜日、ラベル、ミニゲームの設定など）",
            )
            PolicyTableRowItem(
                title = "タイマーとストップウォッチの状態",
                description = "画面を閉じても計測を続けるため（残り時間やラップ記録など）",
            )
            PolicyTableRowItem(
                title = "アプリの設定",
                description = "選択した動作や外観を保持するため（アラームやタイマーの音、バイブレーションの有無、徐々に音量を上げる時間、消音までの時間、二度寝チェックまでの時間、配色テーマなど）",
            )
            PolicyTableRowItem(
                title = "時計ウィジェットの設定",
                description = "ウィジェットの外観を保持するため（時刻の色、背景スタイル、書体など）",
            )
            PolicyTableRowItem(
                title = "起床の記録",
                description = "起床の傾向を振り返るため（鳴動ごとの予定時刻や停止時刻、無効化の方法、何回目で起きたかの集計など）",
            )
            PolicyTableRowItem(
                title = "権限の要求状況",
                description = "通知権限の案内を適切に行うため（通知権限を過去に要求したことがあるかの記録）",
                isLast = true,
            )
            Spacer(modifier = Modifier.height(8.dp))

            PolicyParagraph(
                text = "これらの情報は端末内の本アプリケーション専用の領域に保存され、他のアプリケーションから読み取ることはできません。本アプリケーション自身が外部のサーバーへ送信することはありません（Androidの標準機能による自動バックアップについては「第5条 バックアップ」をご確認ください）。本アプリケーションを端末から削除（アンインストール）した場合は、端末内に保存された情報は消去されます（クラウドバックアップの扱いについては「第5条 バックアップ」をご確認ください）。",
            )

            // 第4条 情報の利用目的
            PolicySectionTitle(text = "第4条 情報の利用目的")
            PolicyParagraph(
                text = "本アプリケーションが端末内に保存する情報は、以下の目的のためにのみ利用します。",
            )
            PolicyBulletItem(text = "アラームの設定: 指定された設定に従ってアラームを鳴動させるため")
            PolicyBulletItem(text = "タイマーとストップウォッチの状態: アプリケーションを終了している間も計測を継続するため")
            PolicyBulletItem(text = "アプリの設定: 利用者が選択した動作設定や外観設定を維持するため")
            PolicyBulletItem(text = "時計ウィジェットの設定: ホーム画面上のウィジェットの外観を維持するため")
            PolicyBulletItem(text = "起床の記録: 利用者が起床傾向を振り返るための履歴表示および集計を行うため")
            PolicyBulletItem(text = "権限の要求状況: 通知権限の案内を適切に行うため")
            PolicyParagraph(
                text = "本アプリケーションは、これらの情報を上記の目的以外に利用することはありません。",
            )

            // 第5条 バックアップ
            PolicySectionTitle(text = "第5条 バックアップ")
            PolicyParagraph(
                text = "本アプリケーションは、端末の機種変更時や本アプリケーションの再インストール時に設定等を復元できるよう、Androidの標準機能による自動バックアップに対応しています。なお、このバックアップはAndroid（OS）が提供する仕組みによって実行されます。",
            )
            PolicyParagraph(
                text = "バックアップされる情報は、アラームの設定、タイマーとストップウォッチの状態、アプリの設定、および起床の記録です。なお、時計ウィジェットの設定は、機種変更後は対応づかないため、バックアップの対象外です。",
            )
            PolicyParagraph(
                text = "データの保存先は、利用者自身のGoogle ドライブの非公開領域です。この領域のデータは利用者本人や他のアプリケーションから読み取ることはできず、Google ドライブのストレージ容量も消費しません。本アプリケーションの開発者もこのデータを閲覧することはできません。また、Android 9以降で画面ロックを設定している端末では、端末の画面ロックに基づく鍵で暗号化されます。暗号化できない端末（画面ロック未設定など）では、クラウドへのバックアップは行われません。",
            )
            PolicyParagraph(
                text = "端末の設定から自動バックアップ機能を無効化することで、データのバックアップを停止できます。",
            )

            // 第6条 第三者への提供
            PolicySectionTitle(text = "第6条 第三者への提供")
            PolicyParagraph(
                text = "本アプリケーションは、外部へ送信する情報や保持する個人情報を保有していないため、第三者へ情報を提供することはありません。また、本アプリケーションには外部の分析サービスや広告配信のためのライブラリ等は一切含まれていません。",
            )

            // 第7条 アプリケーションの権限
            PolicySectionTitle(text = "第7条 アプリケーションの権限")
            PolicyParagraph(
                text = "本アプリケーションが利用する権限およびその理由は、次のとおりです。",
            )
            Spacer(modifier = Modifier.height(4.dp))
            PolicyTableHeader(leftTitle = "権限", rightTitle = "理由")
            PolicyTableRowItem(
                title = "正確なアラーム",
                identifier = "(USE_EXACT_ALARM / SCHEDULE_EXACT_ALARM)",
                description = "指定した時刻にアラームを鳴らすため。本アプリケーションの中核機能です",
            )
            PolicyTableRowItem(
                title = "通知",
                identifier = "(POST_NOTIFICATIONS)",
                description = "アラームの鳴動や、タイマーおよびストップウォッチの経過を通知するため",
            )
            PolicyTableRowItem(
                title = "進行中の通知",
                identifier = "(POST_PROMOTED_NOTIFICATIONS)",
                description = "鳴動中のアラームおよびタイマーの残り時間を、通知欄の上部に継続して表示するため",
            )
            PolicyTableRowItem(
                title = "全画面通知",
                identifier = "(USE_FULL_SCREEN_INTENT)",
                description = "画面消灯時やロック時に、アラーム画面を表示するため",
            )
            PolicyTableRowItem(
                title = "起動完了の受信",
                identifier = "(RECEIVE_BOOT_COMPLETED)",
                description = "端末の再起動後もアラーム設定を復元して鳴らすため",
            )
            PolicyTableRowItem(
                title = "バイブレーション",
                identifier = "(VIBRATE)",
                description = "アラーム鳴動時に端末を振動させるため",
            )
            PolicyTableRowItem(
                title = "フォアグラウンドサービス",
                identifier = "(FOREGROUND_SERVICE、FOREGROUND_SERVICE_MEDIA_PLAYBACK、FOREGROUND_SERVICE_SPECIAL_USE)",
                description = "アプリケーションを閉じた状態でもアラームを鳴らし続け、タイマーおよびストップウォッチの計測を継続するため",
            )
            PolicyTableRowItem(
                title = "スリープ解除",
                identifier = "(WAKE_LOCK)",
                description = "アラームの鳴動時刻に端末のスリープ状態を解除するため",
                isLast = true,
            )
            Spacer(modifier = Modifier.height(8.dp))

            PolicyParagraph(
                text = "位置情報、連絡先、カメラ、マイク、ストレージへのアクセス権限は要求しません。",
                isBold = true,
            )

            // 第8条 センサーの利用
            PolicySectionTitle(text = "第8条 センサーの利用")
            PolicyParagraph(
                text = "本アプリケーションは、アラーム停止時または「今日はもう止める」の実行前に提示されるミニゲームのうち、「端末を振る」または「歩く」が出題された場合のみ、加速度センサーの値を読み取ります。読み取った値は、端末を振った回数や歩数を計測するためにその場でのみ使用し、端末内に保存したり外部へ送信したりすることはありません。なお、加速度センサーの利用に特別な権限は不要です。",
            )

            // 第9条 児童のプライバシー
            PolicySectionTitle(text = "第9条 児童のプライバシー")
            PolicyParagraph(
                text = "本アプリケーションは、13歳未満の児童から意図的に個人情報を取得することはありません。本アプリケーションは、利用者の年齢を問わず、すべての利用者から情報を一切取得しません。",
            )

            // 第10条 本ポリシーの変更
            PolicySectionTitle(text = "第10条 本ポリシーの変更")
            PolicyParagraph(
                text = "本ポリシーの内容を変更した場合は、本ページを更新し、最終更新日を改定します。収集または取得する情報が増加するような変更を行う場合は、アプリケーションの更新時等に利用者が確認できるよう通知します。",
            )

            // 第11条 お問い合わせ
            PolicySectionTitle(text = "第11条 お問い合わせ")
            PolicyParagraph(
                text = "本ポリシーに関するご質問やお問合せがある場合は、以下の窓口よりご連絡ください。",
            )
            PolicyBulletItem(text = "提供者: marutyan")
            PolicyBulletItem(text = "連絡先: https://github.com/marutyan/termAlarm の Issue")

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
