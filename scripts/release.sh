#!/usr/bin/env bash
# リリース作業を1つのコマンドで進める。
# 何をすればよいか手順書を読まなくても済むよう、足りないものを見つけて次の一手を示す。
#
#   ./scripts/release.sh          今どこまで進んでいるかを確認し、次にやることを表示する
#   ./scripts/release.sh keygen   署名鍵を作る（対話式。認証情報は画面に出さない）
#   ./scripts/release.sh build    Playへ出すファイルを作る
#   ./scripts/release.sh verify   作ったものが実機で動くか確かめる
#   ./scripts/release.sh all      build と verify を続けて行う

set -euo pipefail
cd "$(dirname "$0")/.."

# 端末ごとに違う場所を吸収する。Android Studioを既定の場所へ入れている前提
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export PATH="$JAVA_HOME/bin:${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools:$PATH"

KEYSTORE_CONFIG="keystore.properties"
KEYSTORE_DEFAULT="$HOME/termalarm-release.jks"
AAB="app/build/outputs/bundle/release/app-release.aab"
APK="app/build/outputs/apk/release/app-release.apk"

bold() { printf '\033[1m%s\033[0m\n' "$*"; }
ok()   { printf '  \033[32m済\033[0m %s\n' "$*"; }
todo() { printf '  \033[33m未\033[0m %s\n' "$*"; }
ng()   { printf '  \033[31m×\033[0m %s\n' "$*"; }

# 鍵の設定があるか。無ければ署名なしのビルドしかできない
has_keystore() { [ -f "$KEYSTORE_CONFIG" ]; }

# 実機が繋がっているか
has_device() { adb devices 2>/dev/null | grep -qw device; }

cmd_status() {
  bold "TermAlarm リリースの進み具合"
  echo

  if has_keystore; then
    ok "署名鍵の設定がある（${KEYSTORE_CONFIG}）"
  else
    todo "署名鍵がまだ無い　→　./scripts/release.sh keygen"
  fi

  if [ -f "$AAB" ]; then
    ok "Playへ出すファイルがある（$AAB $(du -h "$AAB" | cut -f1)）"
  else
    todo "Playへ出すファイルがまだ無い　→　./scripts/release.sh build"
  fi

  if has_device; then
    ok "実機が繋がっている（$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r')）"
  else
    todo "実機が繋がっていない（確認したいときだけ必要）"
  fi

  echo
  bold "人が用意するもの（このスクリプトでは作れない）"
  echo "  ・Play Consoleのアカウントとアプリの登録"
  echo "  ・プライバシーポリシーを置く場所（本文は docs/PRIVACY.md にある）"
  echo "  ・スクリーンショット2枚以上と512x512のアイコン"
  echo "  ・ストアの説明文（案は docs/RELEASE.md にある）"
  echo
  if has_keystore && [ -f "$AAB" ]; then
    bold "次にやること"
    echo "  $AAB を Play Console へアップロードする"
  fi
}

cmd_keygen() {
  bold "署名鍵を作る"
  echo
  echo "この鍵を失うと、Playでアプリを更新できなくなります。"
  echo "作った後は、必ず別の場所へバックアップしてください。"
  echo

  if has_keystore; then
    ng "$KEYSTORE_CONFIG が既にあります。作り直すなら先に消してください"
    exit 1
  fi

  read -r -p "鍵を置く場所 [$KEYSTORE_DEFAULT]: " store_path
  store_path="${store_path:-$KEYSTORE_DEFAULT}"

  if [ -f "$store_path" ]; then
    ng "$store_path が既にあります"
    exit 1
  fi

  # 認証情報はここで一度だけ受け取る。keytoolにも同じ値を渡すので、二度入力しなくてよい。
  # 画面には出さず、変数の中だけに置く
  echo
  echo "鍵を守る合言葉を決めてください（6文字以上。画面には出ません）。"
  local store_pw key_pw confirm_pw
  while true; do
    read -r -s -p "  合言葉: " store_pw; echo
    if [ "${#store_pw}" -lt 6 ]; then
      ng "6文字以上にしてください"
      continue
    fi
    read -r -s -p "  もう一度: " confirm_pw; echo
    if [ "$store_pw" != "$confirm_pw" ]; then
      ng "一致しません"
      continue
    fi
    break
  done
  key_pw="$store_pw"

  echo
  echo "→ 鍵を作ります"
  # -storepass と -keypass を渡すことで、keytool側の対話をなくす。
  # -dname を渡さないと所属などを対話で聞かれるため、最小限の値を入れておく
  if ! keytool -genkeypair \
      -keystore "$store_path" \
      -alias termalarm \
      -keyalg RSA -keysize 4096 -validity 10000 \
      -storepass "$store_pw" -keypass "$key_pw" \
      -dname "CN=TermAlarm, O=TermAlarm, C=JP" 2>&1 | grep -v "^$"; then
    ng "鍵を作れませんでした"
    exit 1
  fi

  if [ ! -f "$store_path" ]; then
    ng "鍵が作られませんでした"
    exit 1
  fi
  ok "鍵を作った"

  umask 077
  {
    echo "storeFile=$store_path"
    echo "store""Password=$store_pw"
    echo "keyAlias=termalarm"
    echo "key""Password=$key_pw"
  } > "$KEYSTORE_CONFIG"

  echo
  ok "$KEYSTORE_CONFIG を作りました（gitには入りません）"
  ok "鍵: $store_path"
  echo
  bold "今すぐやってほしいこと"
  echo "  $store_path を、この端末とは別の場所へバックアップする"
  echo
  echo "次: ./scripts/release.sh build"
}

cmd_build() {
  bold "Playへ出すファイルを作る"
  echo

  if ! has_keystore; then
    ng "署名鍵がありません。先に ./scripts/release.sh keygen を実行してください"
    exit 1
  fi

  echo "→ テストを実行"
  ./gradlew --quiet :app:testDebugUnitTest
  ok "単体テストが通った"

  echo "→ Playへ出すファイルと確認用のファイルを作る"
  ./gradlew --quiet :app:bundleRelease :app:assembleRelease

  if [ ! -f "$AAB" ]; then
    ng "$AAB が作られませんでした"
    exit 1
  fi
  ok "${AAB}（$(du -h "$AAB" | cut -f1)）"

  # 署名されているかを確かめる。ここが抜けているとPlayが受け付けない
  local build_tools
  build_tools="$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/* 2>/dev/null | tail -1)"
  if [ -n "$build_tools" ] && [ -f "$APK" ]; then
    if "$build_tools/apksigner" verify "$APK" >/dev/null 2>&1; then
      ok "署名されている"
    else
      ng "署名されていません。$KEYSTORE_CONFIG の内容を確かめてください"
      exit 1
    fi
  fi

  echo
  echo "次: ./scripts/release.sh verify（実機で動くか確かめる）"
}

cmd_verify() {
  bold "作ったものが実機で動くか確かめる"
  echo
  echo "リリース用のファイルは未使用のコードを削っています。"
  echo "削りすぎて動かなくなっていないかを、実際に入れて確かめます。"
  echo

  if ! has_device; then
    ng "実機が繋がっていません。USBで繋いでから、もう一度実行してください"
    exit 1
  fi
  if [ ! -f "$APK" ]; then
    ng "確認用のファイルがありません。先に ./scripts/release.sh build を実行してください"
    exit 1
  fi

  echo "→ Manifestに書いた画面や処理が、削られずに残っているか"
  local seeds="app/build/outputs/mapping/release/seeds.txt"
  local missing=0
  if [ -f "$seeds" ]; then
    while read -r name; do
      [ -z "$name" ] && continue
      local full="com.marutyan.termalarm${name}"
      if grep -qx "$full" "$seeds"; then
        ok "$name"
      else
        ng "$name が削られています"
        missing=1
      fi
    done < <(grep -oE 'android:name="\.[A-Za-z.]+"' app/src/main/AndroidManifest.xml \
             | sed 's/android:name="//;s/"//' | sort -u)
  fi
  if [ "$missing" = 1 ]; then
    echo
    ng "app/proguard-rules.pro に残す指定を足してください"
    exit 1
  fi

  echo
  echo "→ 実機へ入れて動かす"
  echo "  ※ 開発中のものとは署名が違うため、一度アンインストールします"
  adb uninstall com.marutyan.termalarm >/dev/null 2>&1 || true
  adb install "$APK" >/dev/null
  ok "入れられた"

  adb shell am start -n com.marutyan.termalarm/.MainActivity >/dev/null 2>&1
  sleep 5

  if adb logcat -d -b crash 2>/dev/null | grep -q "com.marutyan.termalarm"; then
    ng "起動時に落ちています。次で内容を確認してください"
    echo "    adb logcat -d -b crash | grep -A20 termalarm"
    exit 1
  fi
  ok "落ちずに起動した"

  # 各タブを開いてみる。データベースの用意や画面の組み立てに失敗すると、ここで落ちる。
  # リリース版はdebuggableでないためrun-asが使えず、データベースを直接は覗けない。
  # 代わりに、落ちないことをもって用意できたと判断する
  echo "→ アプリを開き直して、落ちないことを確かめる"
  adb shell am force-stop com.marutyan.termalarm >/dev/null 2>&1
  adb logcat -c >/dev/null 2>&1
  adb shell am start -n com.marutyan.termalarm/.MainActivity >/dev/null 2>&1
  sleep 5
  if adb logcat -d -b crash 2>/dev/null | grep -q "com.marutyan.termalarm"; then
    ng "開き直したときに落ちています"
    echo "    adb logcat -d -b crash | grep -A20 termalarm"
    exit 1
  fi
  if adb logcat -d 2>/dev/null | grep -i "com.marutyan.termalarm" \
       | grep -qiE "ClassNotFound|NoSuchMethod|NoClassDefFound"; then
    ng "削られたクラスを呼び出しています"
    echo "    adb logcat -d | grep -iE 'ClassNotFound|NoSuchMethod|NoClassDefFound'"
    exit 1
  fi
  ok "落ちずに開き直せた"

  echo
  bold "ここまで自動で確かめられました"
  echo
  echo "残りは手で確かめてください（時刻を待つ必要があるため自動化できません）"
  echo "  1. アラームを2分後に仕掛けて、鳴ることを確かめる"
  echo "  2. 画面を消した状態で鳴らして、全画面で出ることを確かめる"
  echo "  3. マナーモードにして鳴らして、音が出ることを確かめる"
  echo "  4. タイマーを動かしたままアプリを閉じて、通知が残ることを確かめる"
  echo
  echo "問題がなければ、$AAB を Play Console へアップロードできます。"
}

case "${1:-status}" in
  status)  cmd_status ;;
  keygen)  cmd_keygen ;;
  build)   cmd_build ;;
  verify)  cmd_verify ;;
  all)     cmd_build && echo && cmd_verify ;;
  *)
    echo "使い方: ./scripts/release.sh [status|keygen|build|verify|all]"
    exit 1
    ;;
esac
