package com.yingwang.watchchess.ai

import com.yingwang.watchchess.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * 内置引擎在外部引擎起不来时要顶上，所以它得守时限、能被取消，而且时限再紧也交得出一着。
 * 原先只在结点数撞上 2048 的整倍数时看一眼时钟，表上一秒才一百多个结点，一等就是十几秒。
 */
class FallbackSearchTest {
    /** 一个中局局面。开局库只认初始局面的几个着法，这里一个也对不上，所以走的是真搜索。 */
    private fun middlegame(): Board {
        fun p(t: PieceType, c: PieceColor, row: Int, col: Int) = Piece(t, c, Position(row, col))
        val red = PieceColor.RED; val black = PieceColor.BLACK
        return Board.createFromPieces(listOf(
            p(PieceType.GENERAL, red, 9, 4), p(PieceType.ADVISOR, red, 9, 3), p(PieceType.ADVISOR, red, 9, 5),
            p(PieceType.CHARIOT, red, 9, 0), p(PieceType.CHARIOT, red, 9, 8), p(PieceType.HORSE, red, 9, 2),
            p(PieceType.CANNON, red, 7, 3), p(PieceType.CANNON, red, 7, 5),
            p(PieceType.SOLDIER, red, 6, 0), p(PieceType.SOLDIER, red, 6, 2),
            p(PieceType.SOLDIER, red, 6, 6), p(PieceType.SOLDIER, red, 6, 8),
            p(PieceType.GENERAL, black, 0, 4), p(PieceType.ADVISOR, black, 1, 4),
            p(PieceType.CHARIOT, black, 0, 0), p(PieceType.CHARIOT, black, 0, 8),
            p(PieceType.HORSE, black, 0, 1), p(PieceType.HORSE, black, 0, 7),
            p(PieceType.CANNON, black, 2, 1), p(PieceType.CANNON, black, 2, 7),
            p(PieceType.SOLDIER, black, 3, 0), p(PieceType.SOLDIER, black, 3, 2), p(PieceType.SOLDIER, black, 3, 4),
            p(PieceType.SOLDIER, black, 3, 6), p(PieceType.SOLDIER, black, 3, 8),
        ), red)
    }

    private fun millisSince(start: Long) = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)

    @Test fun anExhaustedTimeLimitStillYieldsALegalMove() = runBlocking {
        val board = middlegame()
        val move = ChessAI(maxDepth = 8, timeLimit = 0, quiescenceDepth = 2).findBestMove(board)
        assertNotNull("时限用完也得交出第一层的结果", move)
        assertTrue(board.getAllLegalMoves().contains(move!!))
    }

    @Test fun theTimeLimitHoldsInsideADepth() = runBlocking {
        val start = System.nanoTime()
        val move = ChessAI(maxDepth = 30, timeLimit = 300, quiescenceDepth = 3).findBestMove(middlegame())
        assertNotNull(move)
        // 宽松的上限：只要求时限在一层搜索当中也管用，不卡在机器快慢上。
        assertTrue("用了 ${millisSince(start)} ms", millisSince(start) < 5_000)
    }

    @Test fun cancellingStopsTheSearch() = runBlocking {
        // 放在独立的作用域里：万一停不下来，测试照样按时失败，而不是陪着它一直等下去。
        val scope = CoroutineScope(Dispatchers.Default)
        val search = scope.async {
            ChessAI(maxDepth = 30, timeLimit = 600_000, quiescenceDepth = 3).findBestMove(middlegame())
        }
        delay(300)
        val start = System.nanoTime()
        search.cancel()
        val stopped = withTimeoutOrNull(10_000) { search.join(); true } ?: false
        scope.cancel()
        assertTrue("取消后十秒仍在搜", stopped)
        assertTrue("被取消的搜索不该交出着法", search.isCancelled)
        assertTrue("取消后又搜了 ${millisSince(start)} ms", millisSince(start) < 5_000)
    }
}
