# リリース手順

**まず次を実行してください。** 今どこまで進んでいて、次に何をすればよいかが出ます。

```sh
./scripts/release.sh
```

以降の作業もこのスクリプトで進みます。この文書は、スクリプトが何をしているかを
知りたいときと、人が手で用意するものを確かめたいときに読んでください。

---

## コマンド一覧

| コマンド | 何をするか |
|---|---|
| `./scripts/release.sh` | 進み具合を見て、次にやることを表示する |
| `./scripts/release.sh keygen` | 署名鍵を作る（対話式） |
| `./scripts/release.sh build` | Playへ出すファイルを作る |
| `./scripts/release.sh verify` | 作ったものが実機で動くか確かめる |
| `./scripts/release.sh all` | build と verify を続けて行う |

## 進める順番

```
./scripts/release.sh keygen     鍵を作る（最初の一回だけ）
./scripts/release.sh all        ファイルを作って実機で確かめる
```

これで `app/build/outputs/bundle/release/app-release.aab` ができます。
Play Console へアップロードするのはこのファイルです。

---

## 各コマンドが何をしているか

### keygen

`keytool` で署名鍵を作り、その場所と認証情報を `keystore.properties` へ書きます。

**鍵を失うとPlayでアプリを更新できなくなります。** 作った後に必ず別の場所へ
バックアップしてください。スクリプトも最後にそう促します。

認証情報は入力時に画面へ出さず、`keystore.properties` は本人だけが読める権限で
作ります。このファイルと `*.jks` は `.gitignore` に入っているのでgitへ入りません。

### build

1. 単体テストを実行する
2. Playへ出すファイル（`.aab`）と、確認用のファイル（`.apk`）を作る
3. **署名されているかを確かめる**

3を入れているのは、`keystore.properties` の書き間違いに気づかないまま
署名なしのファイルを作ってしまうと、Playへ出す段になって弾かれるためです。

### verify

リリース用のファイルは未使用のコードを削るため、**削りすぎて動かなくなる**ことが
あります。それを実機で確かめます。

1. Manifestに書いた画面や処理が、削られずに残っているかを照合する
2. 実機へ入れて起動し、落ちないことを確かめる
3. データベースが作られたことを確かめる

1が重要です。Activity・Service・BroadcastReceiver はManifestから名前で
参照されるだけで、コード上の呼び出しがありません。そのため何もしないと削除され、
実機で `Activity class ... does not exist` の形で失敗します。
`app/proguard-rules.pro` で残す指定をしていますが、新しく追加したときに
指定漏れが起きるため、毎回照合します。

**ビルドが通るだけでは確認になりません。** 必ず実機で動かします。

---

## 人が用意するもの

スクリプトでは作れないものです。

| 項目 | 内容 |
|---|---|
| Play Consoleのアカウント | 登録が必要 |
| プライバシーポリシーのURL | 本文は `docs/PRIVACY.md` にある。どこかに公開してURLを得る |
| アイコン | 512x512 PNG |
| スクリーンショット | 携帯用に2枚以上 |
| ストアの説明文 | 下記の案を使えます |
| データ安全性の申告 | 「データを収集しない」を選ぶ |

### 短い説明（80文字以内）

```
時刻の範囲と間隔でアラームを鳴らす。7時から9時を5分ごとに。
```

### 詳しい説明

```
TermAlarmは、鳴らす時刻をひとつ指定するのではなく、時刻の範囲と間隔を
指定するアラームです。

「7時から9時まで5分ごと」と決めれば、その間25回鳴ります。一度で起きられない
人のために、止めても次が来ます。

■ 主な機能
・時刻の範囲と間隔を指定して繰り返し鳴らす
・繰り返す曜日の指定
・寝ぼけて止めてしまうのを防ぐ「止めにくさ」の設定
・止める前に簡単な問題を出して、考えないと止められないようにする
・タイマー、ストップウォッチ、アナログ／デジタルの時計

■ 開始と終了を同じ時刻にすれば
ふつうのアラームとして使えます。

■ 情報を集めません
通信を行わないため、インターネットへの接続許可を持っていません。
広告も表示しません。
```

### 説明文で書かないこと

**他社の製品名を出して、そこと同じだと書かない。** 具体的には次を書かない。

- 「Google純正と同じ」「Google Clockの代替」「〇〇互換」
- 他社のアプリ名やブランド名を並べて、乗り換え先だと示すこと
- スクリーンショットに他社アプリの画面を並べて見せること

見た目や操作を標準へ寄せていること自体は書いてよい。その場合は次のように書く。

- 「Androidの標準的な見た目に合わせています」
- 「Material Design 3に沿った画面です」

他社の承認を受けた製品だと誤解させる書き方が問題になるため、
製品名を出さずに、何をするアプリなのかで説明する。

### 正確なアラームの用途説明

`USE_EXACT_ALARM` を宣言するアプリは、審査で用途の説明を求められます。

```
このアプリはアラーム時計です。利用者が指定した時刻に音を鳴らすことが
アプリの中心的な機能であり、時刻がずれると目的を果たせません。
時刻の範囲と間隔を指定して繰り返し鳴らす仕組みのため、各回の鳴動が
正確な時刻に発火する必要があります。
```

---

## 手で確かめること

### 2026年9月5日の点検結果

公開前設定の点検を行い、以下を確認した。

| 項目 | 確認内容 | 結果 |
|---|---|---|
| バージョン | `versionCode` が 1、`versionName` が "1.0" と初回リリースとして妥当か | 確認済み（変更なし） |
| 権限 | `AndroidManifest.xml` の全10件の権限がコード内で実際に使われており、不要な権限が残っていないか | 確認済み（全10件利用中、不要権限なし） |
| アプリ情報 | アプリ名（`@string/app_name`）、アイコン（`ic_launcher` / `ic_launcher_round`）、テーマ（`@style/Theme.TermAlarm`）の指定・リソースが存在するか | 確認済み（指定・リソースとも揃っている） |
| SDKバージョン | `compileSdk` (37) と `targetSdk` (37) が Google Play の要件を満たしているか | 確認済み（要件を満たしている） |
| リリースビルド | `./gradlew assembleRelease` が警告なく成功するか | 成功（警告なし） |

#### 権限の使用状況

- `SCHEDULE_EXACT_ALARM` (maxSdkVersion=32): Android 12/12L向けの正確なアラーム予約（`AlarmScheduler`）
- `USE_EXACT_ALARM`: Android 13以降向けの正確なアラーム予約（`AlarmScheduler`）
- `POST_NOTIFICATIONS`: 鳴動通知、タイマー/ストップウォッチの通知表示（`NotificationPermission`, 各通知生成処理）
- `POST_PROMOTED_NOTIFICATIONS`: タイマーの残り時間をステータスバーへ出す（`TimerNotifications`）。
  実行時に許可を求める種類ではなく、宣言するだけで効く
- `RECEIVE_BOOT_COMPLETED`: 端末再起動時のアラーム・タイマー・ストップウォッチの復元（各RescheduleReceiver）
- `FOREGROUND_SERVICE`: フォアグラウンドサービスの実行（`RingingService`, `TimerRingingService`, `StopwatchForegroundService`）
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: 音声再生を行うフォアグラウンドサービス用（`RingingService`, `TimerRingingService`）
- `FOREGROUND_SERVICE_SPECIAL_USE`: ストップウォッチ用フォアグラウンドサービス（`StopwatchForegroundService`）
- `VIBRATE`: アラームおよびタイマー鳴動時のバイブレーション（`RingingService`, `TimerRingingService`）
- `WAKE_LOCK`: アラームおよびタイマー発火時の端末スリープ解除（`AlarmTriggerReceiver`, `TimerTriggerReceiver`）
- `USE_FULL_SCREEN_INTENT`: ロック画面上での鳴動画面表示用全画面インテント（`RingingService`）

## 6. コードの圧縮についての注意
`verify` の後、時刻を待つ必要があるものだけが残ります。自動化できません。

1. アラームを2分後に仕掛けて、鳴ることを確かめる
2. 画面を消した状態で鳴らして、全画面で出ることを確かめる
3. マナーモードにして鳴らして、音が出ることを確かめる
4. タイマーを動かしたままアプリを閉じて、通知が残ることを確かめる

詳しい手順は `docs/DEVICE_TEST.md` にあります。

---

## 付録: フォントのライセンス表記の確認方法

Google Sans Flex の著作権表記は、**フォントファイル自身に埋め込まれた情報が原典**です。
配布ページの表示ではなく、こちらを見ます。

```sh
uv run --with fonttools python -c "
from fontTools.ttLib import TTFont
n = TTFont('app/src/main/res/font/google_sans_flex.ttf')['name']
print(n.getDebugName(0))   # 著作権
print(n.getDebugName(1))   # ファミリ名
print(n.getDebugName(5))   # バージョン
print(n.getDebugName(14))  # ライセンスURL
"
```

2026年9月3日の確認結果。

| 項目 | 値 |
|---|---|
| 著作権 | Copyright 2015 Google LLC. All Rights Reserved. |
| ファミリ名 | Google Sans Flex 18pt |
| バージョン | Version 4.005 |
| ライセンスURL | https://openfontlicense.org |

ライセンス本文は、Google Fonts が配布する OFL.txt と照合して完全一致（4052文字）を
確認しました。フォントを差し替えたときは、この手順で表記を作り直してください。
