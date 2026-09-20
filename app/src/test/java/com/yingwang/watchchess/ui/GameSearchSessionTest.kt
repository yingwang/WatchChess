package com.yingwang.watchchess.ui

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class GameSearchSessionTest {
    @Test fun lateOldResultCannotChangeNewGameOrClearItsThinkingState() = runBlocking {
        withTimeout(5000) {
            val session = GameSearchSession()
            val oldStarted = CompletableDeferred<Unit>()
            val releaseOld = CompletableDeferred<Unit>()
            val newStarted = CompletableDeferred<Unit>()
            val releaseNew = CompletableDeferred<Unit>()
            val done = CompletableDeferred<Unit>()
            val applied = mutableListOf<String>()
            val finished = mutableListOf<String>()
            session.search(this, compute = {
                oldStarted.complete(Unit)
                // 模拟未及时响应取消的备用搜索。
                withContext(NonCancellable) { releaseOld.await() }
                "old"
            }, applyResult = { applied.add(it) }, finished = { finished.add("old") })
            oldStarted.await()
            session.invalidate()
            session.search(this, compute = {
                newStarted.complete(Unit)
                releaseNew.await()
                "new"
            }, applyResult = { applied.add(it) }, finished = {
                finished.add("new")
                done.complete(Unit)
            })
            yield()
            assertFalse("共享引擎不能同时搜索", newStarted.isCompleted)
            releaseOld.complete(Unit)
            newStarted.await()
            assertTrue(applied.isEmpty())
            assertTrue("旧任务不能清掉新任务的思考状态", finished.isEmpty())
            releaseNew.complete(Unit)
            done.await()
            assertEquals(listOf("new"), applied)
            assertEquals(listOf("new"), finished)
        }
    }

    @Test fun returningToMenuCancelsSearchWithoutApplyingResult() = runBlocking {
        withTimeout(5000) {
            val session = GameSearchSession()
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            session.search(this, compute = {
                started.complete(Unit)
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            }, applyResult = { fail("退出后不应落子") }, finished = { fail("退出后的状态不应被旧任务修改") })
            started.await()
            session.invalidate()
            cancelled.await()
        }
    }
}
