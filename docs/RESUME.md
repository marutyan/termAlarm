# 作業の再開手順

2026年9月2日に中断した時点の状態と、再開に必要な情報をまとめる。

## 中断の理由

利用回数の上限が近づいたため、作業を止めて状態を保存した。実機も外した。

---

## 1. 最優先: リモートへのpushが未完了

**ローカルの全コミットから `Co-Authored-By` の行を削除し、履歴を書き換え済み。**
ただし force push が権限で拒否されたため、**リモートには反映されていない**。

再開時に次を実行する。

```sh
cd /Users/marutyan/PrivateDev/termAlarm
git push --force-with-lease origin main
git push --force-with-lease origin feature/fade-in-and-remaining-time
git push --force-with-lease origin feature/external-alarm-intents
```

これでGitHub上のコミットとcontributorから該当の名前が消える。
著者は元から `marutyan` のみで、書き換えていない。

未pushのブランチ（`chore/release-preparation` / `feature/clock-tab` / `feature/timer-tab`）は
中身が空なので、pushの必要はない。

---

## 2. リポジトリの現状

| ブランチ | 内容 | 状態 |
|---|---|---|
| `main` | アラーム機能の一式（26コミット） | ローカルはpush待ち |
| `feature/fade-in-and-remaining-time` | 音量の漸増、残り時間表示 | PR #1。push待ち |
| `feature/external-alarm-intents` | SET_ALARM など5種のインテント | PR #2。push待ち |
| `chore/release-preparation` | 空（下記の理由で成果を失った） | 作り直しが必要 |
| `feature/clock-tab` | 空（未着手） | |
| `feature/timer-tab` | 空（作業途中のファイルは退避済み） | |

PRは2件出ている。どちらもマージ可能な状態。mergeは人が行う。

---

## 3. 失った成果と作り直しが必要なもの

### 経緯

4人（PMと3担当）が**同じ作業ツリーを共有している**のに、それぞれにブランチを切らせた。
各自の `git checkout` が互いのブランチを奪い合い、コミット前のファイルが消えた。

**再開時は、複数の担当を並列で動かすなら `git worktree` で作業ツリーを分ける。**
同じツリーで並列作業をさせてはいけない。

### 失ったもの（内容は分かっているので作り直せる）

**プライバシーポリシー** (`docs/PRIVACY.md`)
- このアプリは通信を行わず、`INTERNET` 権限を持たないため集める情報が無いことを明記
- 端末内に保存するもの（アラーム設定、タイマーの状態、世界時計の都市）を表で示す
- 各権限（`USE_EXACT_ALARM`、`POST_NOTIFICATIONS`、`USE_FULL_SCREEN_INTENT`、
  `RECEIVE_BOOT_COMPLETED`、`VIBRATE`、`FOREGROUND_SERVICE`、`WAKE_LOCK`）の理由
- 加速度センサーはゲームの「端末を振る」でのみ読み、保存も送信もしないこと
- 位置情報・連絡先・カメラ・マイク・ストレージは要求しないこと

**リリース手順** (`docs/RELEASE.md`)
- 署名鍵の作成手順（`keytool -genkeypair -keystore ~/termalarm-release.jks -alias termalarm -keyalg RSA -keysize 4096 -validity 10000`）。**鍵は人が作り、人が保管する**
- リリースビルド（`./gradlew bundleRelease` / `assembleRelease`）
- Play Consoleに必要なもの一覧、ストアの説明文の案
- 正確なアラームの用途説明の案（審査で求められる）

**署名の設定** (`app/build.gradle.kts`)
- `keystore.properties` から鍵の情報を読む。**このファイルが無い環境でもビルドが通る**ようにする
- `keystore.properties.example` を雛形として置く
- `.gitignore` に `keystore.properties` / `*.jks` / `*.keystore` を追加（**これは適用済み**）

**コードの圧縮** (`app/build.gradle.kts` と `app/proguard-rules.pro`)
- `isMinifyEnabled = true` と `isShrinkResources = true`
- 効果を実測済み: **debug 15MB → release 1.6MB（89%削減）**

🔴 **重要な発見（失うと同じ失敗を繰り返す）**

Activity・Service・BroadcastReceiver は **Manifestから名前で参照されるだけで
コード上の呼び出しが無いため、圧縮で削除される。** ProGuardで明示的に残す必要がある。

```proguard
-keep class * extends android.app.Activity
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class com.marutyan.termalarm.data.** { *; }
```

これを入れずにリリースすると、実機で `Activity class ... does not exist` で失敗する。
**ビルドが通るだけでは確認にならない。必ずリリースビルドを実機で動かすこと。**

確認には `app/build/outputs/mapping/release/seeds.txt`（残ったもの）と
`usage.txt`（削除されたもの）を見る。

---

## 4. 3タブの実装状況

`docs/SPEC.md` の「時計・タイマー・ストップウォッチ」に仕様がある。この節はコミット済み。

| タブ | 状態 |
|---|---|
| タイマー | 作業途中。5ファイル249行が退避先にある（下記） |
| ストップウォッチ | 未着手 |
| 時計（世界時計） | 未着手 |

### タイマーの作業途中のファイル

退避先: `/private/tmp/claude-501/-Users-marutyan-PrivateDev-termAlarm/cc9df5c9-fda6-4fd4-849b-499eda00848c/scratchpad/wip-timer/`

| ファイル | 行数 |
|---|---|
| `domain/TimerState.kt` | 26 |
| `domain/TimerCalculator.kt` | 97 |
| `data/TimerEntity.kt` | 44 |
| `data/TimerDao.kt` | 36 |
| `data/TimerRepository.kt` | 46 |

⚠️ **このコードはビルドが通らない状態**。`TimerRepository.kt` の44行目で
`observeAllOnce` / `runState` が未解決。作業途中で止めたため。

一時ディレクトリなので消える可能性がある。再開時に残っていなければ、
作り直した方が早い（249行、うち計算部分が97行）。

### 3タブ実装時の注意

`docs/SPEC.md` に書いた通り、**経過時間は `SystemClock.elapsedRealtime()` を基準にする**。
壁時計の時刻で計算すると、時刻合わせやタイムゾーン変更で計測が飛ぶ。
ただし再起動をまたぐ復元には壁時計が必要（`elapsedRealtime` は再起動でゼロに戻る）。
この両立が実装の要点になる。

---

## 5. 検証の状況

| 種類 | 結果 |
|---|---|
| JVM単体テスト | 28件合格（計算15・ゲーム9・DB4） |
| 実機UIテスト | 13件合格 |
| 実機での鳴動 | 合格。予約時刻に発火し、`USAGE_ALARM` で音が鳴ることを利用者が確認 |
| 音量の漸増 | 合格。利用者が確認 |
| 予約 | 合格。`setAlarmClock()`、鳴動中に次回を確保 |
| アプリ更新後の復元 | 合格。11件が維持される |
| リリースビルドの動作 | **未検証**。圧縮でクラスが消えていないか実機で確かめる必要がある |
| Doze中の鳴動 | 未検証。手順は `docs/DEVICE_TEST.md` |
| マナーモードでの音 | 未検証 |
| ロック画面上の全画面表示 | 未検証 |

---

## 6. 残っている作業

**リリースに必要**
1. 上記1のforce push
2. 失ったリリース準備の作り直し（内容は本文に記載）
3. 署名鍵の作成（**人が行う**）
4. プライバシーポリシーの公開先を決める
5. リリースビルドを実機で動かして、圧縮で壊れていないことを確認
6. Google Sans Flex の著作権表記を、Google Fontsの配布物の原文と照合（🟡 OFL全文は入っているが Copyright 行が一般的な表記のまま）

**機能**
7. 3タブの実装（タイマー → ストップウォッチ → 時計の順を推奨。タイマーが最も使われる）

**実機**
8. 検証で作った一時アラームが11件残っている。アプリのデータを消せば片付く
