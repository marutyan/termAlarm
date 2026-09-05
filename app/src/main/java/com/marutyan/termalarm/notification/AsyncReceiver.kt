package com.marutyan.termalarm.notification

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiverの中で、保存などの時間のかかる処理を安全に走らせる。
 *
 * onReceiveを抜けるとシステムはプロセスを止めてよいと判断するため、
 * そのまま非同期で始めると途中で消える。goAsync()で終わりを待たせる必要がある。
 * この手順をアラーム・タイマー・ストップウォッチの各受け口で書き写さないための置き場所。
 *
 * 失敗しても必ず終わりを知らせる。知らせないとシステムがプロセスを掴んだままになる。
 */
fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}
