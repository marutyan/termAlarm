# Manifestやフレームワークから名前で参照される要素は、コード上の呼び出しが無いため
# 圧縮で削除される。Activity/Service/Receiverは名前解決で起動されるので明示的に残す。
# これを入れずにリリースすると、実機で「Activity class ... does not exist」で失敗する。
-keep class * extends android.app.Activity
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver

# Roomが生成するコードは実行時に名前で参照される
-keep class com.marutyan.termalarm.data.** { *; }
