package com.marutyan.termalarm.widget

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.marutyan.termalarm.data.Repositories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * タームの追加・変更・削除を監視してウィジェットを再描画するマネージャー。
 * Roomデータベースの変更Flowを購読し、常駐サービスを作らずにリアルタイム更新を行うために用いる。
 */
object WidgetUpdateManager {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isInitialized = false

    /**
     * ターム変更監視コルーチンを開始する。
     * アプリ起動時に一度だけ呼ばれ、データベースの変更通知をウィジェットへ伝搬するために用いる。
     */
    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val appContext = context.applicationContext
        scope.launch {
            val repository = Repositories.alarm(appContext)
            // 初回読み込み時はウィジェット初期配置時の描画と重複するため除外する
            repository.observeAll().drop(1).collect {
                val glanceManager = GlanceAppWidgetManager(appContext)
                val glanceIds = glanceManager.getGlanceIds(TermAlarmWidget::class.java)
                if (glanceIds.isNotEmpty()) {
                    WidgetUpdateScheduler.scheduleNextRefresh(appContext)
                    runCatching { TermAlarmWidget().updateAll(appContext) }
                }
            }
        }
    }
}

/**
 * ウィジェットのデータ監視をアプリプロセス起動時に初期化するためのContentProvider。
 * マニフェストへ宣言することで常駐サービスを使わずにアプリ起動時に自動初期化するために用いる。
 */
class WidgetInitProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val context = context ?: return true
        WidgetUpdateManager.init(context)
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
