package app.jisr.vpn.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.jisr.vpn.data.Server

/** حالة التشخيص المشتركة: تُحدَّث يدوياً (زر الفحص) أو تلقائياً بعد فشل الاتصال. */
object DiagState {
    var running by mutableStateOf(false)
    var report by mutableStateOf<List<Finding>?>(null)

    suspend fun run(ctx: Context, server: Server?, lastError: String?) {
        running = true
        try {
            report = Diagnostics.run(ctx, server, lastError)
        } finally {
            running = false
        }
    }
}
