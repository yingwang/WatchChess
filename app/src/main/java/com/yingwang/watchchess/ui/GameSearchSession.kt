package com.yingwang.watchchess.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 从界面线程调用。世代号隔离旧结果，互斥锁隔离共享引擎的输入输出。 */
internal class GameSearchSession {
    private var generation = 0L
    private var job: Job? = null
    private val engineMutex = Mutex()

    fun invalidate() {
        generation++
        job?.cancel()
        job = null
    }

    suspend fun <T> withEngine(block: suspend () -> T): T = engineMutex.withLock { block() }

    fun <T> search(
        scope: CoroutineScope,
        compute: suspend () -> T,
        applyResult: (T) -> Unit,
        finished: () -> Unit,
    ) {
        invalidate()
        val ticket = generation
        job = scope.launch {
            try {
                val result = withEngine {
                    ensureActive()
                    compute()
                }
                ensureActive()
                if (ticket == generation) applyResult(result)
            } finally {
                // 旧任务的 finally 也不能清掉新一局的“思考中”。
                if (ticket == generation) finished()
            }
        }
    }
}
