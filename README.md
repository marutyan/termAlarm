# TermAlarm

TermAlarm は、時刻の範囲と間隔を決めて、その間くり返しアラームを鳴らすことで二度寝を防ぐAndroidアプリです。

## 主な機能

- **ターム**: 開始時刻・終了時刻・間隔を指定し、その時間範囲でくり返しアラームを鳴らします。
- **ミッション**: アラームを止めるときに小さな課題を出します。計算、順にタップ、書き写し、端末を振る、図形を数える、色と文字、鏡文字、光った順を再現、神経衰弱、歩く の10種類があります。出題のタイミングは「ターム終了時のみ」または「鳴動のたびに毎回」から選べます。
- **記録**: 何回目で起きたか、開始から何分かかったかを起床記録として残します。
- **タイマー、ストップウォッチ**: タイマー機能およびストップウォッチ機能を備えています。
- **ホーム画面のウィジェット**: 次のアラーム情報などをホーム画面で確認できるウィジェットを提供します。

## 動作環境

- Android 8.0（API レベル 26）以上

## ビルド方法

### 必要なもの

- JDK 17 以上
- Android Studio または Android SDK

### デバッグビルド

```sh
./gradlew :app:assembleDebug
```

### リリースビルド

リリースビルドを行うには、プロジェクトのルートディレクトリに `keystore.properties` が必要です。

書式:

```properties
storeFile=/path/to/your/keystore.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

雛形は `keystore.properties.example` を参照してください。
このファイルには署名鍵のパスワードなどの機密情報が含まれるため、リポジトリには含めないでください。

ビルドコマンド:

```sh
./gradlew :app:assembleRelease
```

## テストの実行

### 単体テスト

```sh
./gradlew :app:testDebugUnitTest
```

### 計測テスト

Android 端末（実機またはエミュレータ）を接続した状態で実行します。

```sh
./gradlew :app:connectedDebugAndroidTest
```

## ディレクトリ構成

`app/src/main/java/com/marutyan/termalarm` 以下の主要ディレクトリの構成です。

- `alarm`: アラームのスケジューリング、レシーバー、鳴動画面およびサービスなどのアラーム中核処理
- `data`: Room データベース、エンティティ、DAO、およびリポジトリの実装
- `domain`: アラーム計算、タイマー計算、ミッション（ミニゲーム）のロジック、ドメインモデル
- `notification`: 通知チャンネルの作成や非同期レシーバーなどの通知共通処理
- `stopwatch`: ストップウォッチのフォアグラウンドサービス、通知、レシーバー
- `timer`: タイマーのフォアグラウンドサービス、通知、スケジューラー
- `ui`: Jetpack Compose による画面表示、UI コンポーネント、テーマ、ナビゲーション
- `widget`: Glance を使用したホーム画面ウィジェット、ウィジェット設定画面および更新処理

## プライバシー

TermAlarm は、利用者の個人情報や利用状況を一切収集しません。インターネットへの接続権限（`INTERNET`）を持たず、アプリ自身が外部と通信することもありません。
なお、アラームの設定などの端末内のデータは、Android の標準機能により利用者自身の Google ドライブへバックアップされます。詳細は [docs/PRIVACY.md](docs/PRIVACY.md) を参照してください。

## ライセンス

GNU General Public License version 3 (GPL-3.0) のもとで公開されています。
詳細は [LICENSE](LICENSE) を参照してください。

## 作者

marutyan
