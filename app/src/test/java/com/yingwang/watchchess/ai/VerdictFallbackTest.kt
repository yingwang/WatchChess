package com.yingwang.watchchess.ai

import com.yingwang.watchchess.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * 引擎起不来或裁决失败时，棋要能接着下，不能停在一个永远不会成功的重试上。
 * 本地只判将死与困毙，重复局面在这条路上不判。
 */
class VerdictFallbackTest {
    /** 被两车困住的一方，[checked] 为真时是将死，否则是困毙。与 GameOutcomeTest 同一个局面。 */
    private fun trapped(color: PieceColor, checked: Boolean = false): Board {
        fun piece(type: PieceType, side: PieceColor, row: Int, col: Int): Piece =
            Piece(type, side, Position(if (color == PieceColor.RED) row else 9 - row, col))
        return Board.createFromPieces(listOf(
            piece(PieceType.GENERAL, color, 9, 4),
            piece(PieceType.GENERAL, color.opposite(), 0, 3),
            piece(PieceType.CHARIOT, color.opposite(), 8, if (checked) 4 else 3),
            piece(PieceType.CHARIOT, color.opposite(), 8, 5),
        ), color)
    }

    @Test fun localVerdictJudgesMateAndStalemateForBothSides() {
        assertEquals(GameVerdict.BLACK_WIN, trapped(PieceColor.RED).localVerdict())
        assertEquals(GameVerdict.BLACK_WIN, trapped(PieceColor.RED, checked = true).localVerdict())
        assertEquals(GameVerdict.RED_WIN, trapped(PieceColor.BLACK).localVerdict())
        assertEquals(GameVerdict.RED_WIN, trapped(PieceColor.BLACK, checked = true).localVerdict())
        assertEquals(GameVerdict.ONGOING, Board.createInitialBoard().localVerdict())
    }

    @Test fun engineThatIsNotRunningIsJudgedLocallyAtOnce() {
        // 不先暂停：引擎起不来时重试也不会成功，直接接着下。
        assertEquals(GameVerdict.ONGOING, settleVerdict(null, retrying = false) { GameVerdict.ONGOING })
        assertEquals(GameVerdict.RED_WIN, settleVerdict(null, retrying = false) { GameVerdict.RED_WIN })
    }

    @Test fun firstAdjudicationFailurePausesForARetry() {
        assertEquals(GameVerdict.UNAVAILABLE,
            settleVerdict(GameVerdict.UNAVAILABLE, retrying = false) { fail("首次失败应先暂停"); GameVerdict.ONGOING })
    }

    @Test fun aRetryNeverPausesAgain() {
        GameVerdict.values().forEach { answer ->
            assertNotEquals("重试时引擎答 $answer 不能再要求重试", GameVerdict.UNAVAILABLE,
                settleVerdict(answer, retrying = true) { GameVerdict.ONGOING })
        }
    }

    @Test fun engineAnswersAreKeptWhenItHasOne() {
        listOf(GameVerdict.ONGOING, GameVerdict.RED_WIN, GameVerdict.BLACK_WIN, GameVerdict.DRAW).forEach { answer ->
            listOf(false, true).forEach { retrying ->
                assertEquals(answer, settleVerdict(answer, retrying) { fail("引擎答得上来就不该本地判"); GameVerdict.ONGOING })
            }
        }
    }
}
