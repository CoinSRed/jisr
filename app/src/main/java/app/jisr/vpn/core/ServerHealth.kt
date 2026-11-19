package app.jisr.vpn.core

import androidx.compose.runtime.mutableStateMapOf
import app.jisr.vpn.data.Server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** نتائج اختبار زمن الاستجابة للسيرفرات. القيمة -1 تعني أن السيرفر لا يرد. */
object ServerHealth {
    val latency = mutableStateMapOf<String, Long>()

    suspend fun testAll(servers: List<Server>) {
        val results = withContext(Dispatchers.IO) {
            coroutineScope {
                servers.map { s ->
                    async {
                        val n = LinkParser.parse(s.link)
                        val ms = if (n == null) -1L else (Diagnostics.tcpLatency(n.host, n.port) ?: -1L)
                        s.id to ms
                    }
                }.awaitAll()
            }
        }
        results.forEach { latency[it.first] = it.second }
    }

    /** أسرع سيرفر يرد، مع استبعاد ما جُرّب. */
    fun best(servers: List<Server>, excluding: Set<String> = emptySet()): Server? =
        servers
            .filter { it.id !in excluding && (latency[it.id] ?: -1L) > 0 }
            .minByOrNull { latency[it.id] ?: Long.MAX_VALUE }
}
