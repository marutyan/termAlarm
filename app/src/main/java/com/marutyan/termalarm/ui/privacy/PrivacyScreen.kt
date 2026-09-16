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

            // 1. 収集する情報
            PolicySectionTitle(text = "収集する情報")
            PolicyParagraph(
                text = "このアプリは、利用者に関する情報を一切収集しません。",
                isBold = true,
            )
            PolicyBulletItem(text = "個人情報の収集: ありません")
            PolicyBulletItem(text = "利用状況の記録や分析: ありません")
            PolicyBulletItem(text = "広告: 表示しません")
            val internetBullet = remember {
                buildAnnotatedString {
                    append("通信: 行いません。インターネットへの接続許可（")
                    withStyle(SpanStyle(fontFamily = IbmPlexMono)) {
                        append("INTERNET")
                    }
                    append("）自体を持っていません")
                }
            }
            PolicyBulletItem(text = internetBullet)
            PolicyParagraph(
                text = "なお、Androidの標準機能による自動バックアップはOSが行うものであり、アプリ自身が通信するものではありません。詳しくは「バックアップについて」をご覧ください。",
            )

            // 2. 端末内に保存される情報
            PolicySectionTitle(text = "端末内に保存される情報")
            val storageIntro = remember {
                buildAnnotatedString {
                    append("アプリが動作するために、次の情報を")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("端末の中だけ")
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
                description = "選んだ動作や見た目を保つため（アラームやタイマーの音、バイブの有無、徐々に音量を上げる時間、消音までの時間、二度寝チェックまでの時間、配色テーマなど）",
            )
            PolicyTableRowItem(
                title = "時計ウィジェットの設定",
                description = "ウィジェットの見た目を保つため（時刻の色、背景スタイル、書体など）",
            )
            PolicyTableRowItem(
                title = "起床の記録",
                description = "起床の傾向を振り返るため（鳴動ごとの予定時刻や停止時刻、止め方、何回目で起きたかの集計など）",
                isLast = true,
            )
            Spacer(modifier = Modifier.height(8.dp))

            PolicyParagraph(
                text = "これらは端末のアプリ専用の領域に保存され、他のアプリからは読めません。アプリ自身が外部のサーバーへ送信することはありません（Androidの標準機能によるバックアップについては「バックアップについて」をご覧ください）。アプリを削除すると、すべて削除されます。",
            )

            // 3. バックアップについて
            PolicySectionTitle(text = "バックアップについて")
            PolicyParagraph(
                text = "このアプリは、機種変更やアプリの入れ直しのときに元へ戻すため、Androidの標準機能による自動バックアップに対応しています。",
            )
            PolicyParagraph(
                text = "預けられる情報は、アラームの設定、タイマーとストップウォッチの状態、アプリの設定、時計ウィジェットの設定、起床の記録です。",
            )
            PolicyParagraph(
                text = "預け先は利用者自身のGoogleドライブの非公開領域です。本人からも他のアプリからも読めず、ドライブの容量は消費しません。アプリの作者もこのデータを見ることはできません。また、Android 9以降では端末のPINなどで暗号化されます。",
            )
            PolicyParagraph(
                text = "端末の設定からバックアップを切れば、預けられることはありません。",
            )

            // 4. 利用する権限とその理由
            PolicySectionTitle(text = "利用する権限とその理由")
            PolicyTableHeader(leftTitle = "権限", rightTitle = "理由")
            PolicyTableRowItem(
                title = "正確なアラーム",
                identifier = "(USE_EXACT_ALARM / SCHEDULE_EXACT_ALARM)",
                description = "指定した時刻にアラームを鳴らすため。アラームアプリの中心的な機能です",
            )
            PolicyTableRowItem(
                title = "通知",
                identifier = "(POST_NOTIFICATIONS)",
                description = "アラームの鳴動と、タイマーやストップウォッチの経過を知らせるため",
            )
            PolicyTableRowItem(
                title = "進行中の通知",
                identifier = "(POST_PROMOTED_NOTIFICATIONS)",
                description = "鳴っているアラームとタイマーの残り時間を、通知欄の上に出し続けるため",
            )
            PolicyTableRowItem(
                title = "全画面通知",
                identifier = "(USE_FULL_SCREEN_INTENT)",
                description = "画面が消えているときに、アラームの画面を表示するため",
            )
            PolicyTableRowItem(
                title = "起動完了の受信",
                identifier = "(RECEIVE_BOOT_COMPLETED)",
                description = "端末を再起動した後もアラームを鳴らすため",
            )
            PolicyTableRowItem(
                title = "バイブレーション",
                identifier = "(VIBRATE)",
                description = "アラームで端末を振動させるため",
            )
            PolicyTableRowItem(
                title = "フォアグラウンドサービス",
                identifier = "(FOREGROUND_SERVICE、FOREGROUND_SERVICE_MEDIA_PLAYBACK、FOREGROUND_SERVICE_SPECIAL_USE)",
                description = "アプリを閉じてもアラームを鳴らし続け、タイマーとストップウォッチの計測を続けるため",
            )
            PolicyTableRowItem(
                title = "スリープ解除",
                identifier = "(WAKE_LOCK)",
                description = "アラームの時刻に端末を起こすため",
                isLast = true,
            )
            Spacer(modifier = Modifier.height(8.dp))

            PolicyParagraph(
                text = "位置情報、連絡先、カメラ、マイク、ストレージへのアクセスは要求しません。",
                isBold = true,
            )

            // 5. 加速度センサーについて
            PolicySectionTitle(text = "加速度センサーについて")
            PolicyParagraph(
                text = "アラーム停止時や「今日はもう止める」の前に出すゲームのうち「端末を振る」または「歩く」が出題された場合だけ、加速度センサーの値を読みます。読んだ値は振った回数や歩数を数えるためにその場で使い、保存も送信もしません。センサーの利用に権限は不要です。",
            )

            // 6. 子どもの利用について
            PolicySectionTitle(text = "子どもの利用について")
            PolicyParagraph(
                text = "このアプリは、13歳未満の子どもから意図的に情報を集めることはありません。そもそも誰からも情報を集めていません。",
            )

            // 7. 第三者への提供
            PolicySectionTitle(text = "第三者への提供")
            PolicyParagraph(
                text = "提供する情報がないため、第三者へ提供することはありません。アプリには外部の分析サービスや広告のライブラリを含んでいません。",
            )

            // 8. ポリシーの変更
            PolicySectionTitle(text = "ポリシーの変更")
            PolicyParagraph(
                text = "変更した場合は、このページを更新し、最終更新日を書き換えます。収集する情報が増えるような変更を行う場合は、アプリの更新時に分かるようにします。",
            )

            // 9. 連絡先
            PolicySectionTitle(text = "連絡先")
            PolicyParagraph(
                text = "このポリシーについて質問がある場合は、\nhttps://github.com/marutyan/termAlarm の Issue からご連絡ください。",
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
