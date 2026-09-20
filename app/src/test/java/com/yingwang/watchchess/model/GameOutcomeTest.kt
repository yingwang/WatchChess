package com.yingwang.watchchess.model

import com.yingwang.watchchess.ai.Evaluator
import org.junit.Assert.*
import org.junit.Test

class GameOutcomeTest {
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

    @Test fun redStalemateIsBlackWin() {
        val board = trapped(PieceColor.RED)
        assertTrue(board.isStalemate())
        assertEquals(PieceColor.BLACK, board.noLegalMoveWinner())
        assertEquals(-90000, Evaluator.evaluate(board))
    }

    @Test fun blackStalemateIsRedWin() {
        val board = trapped(PieceColor.BLACK)
        assertTrue(board.isStalemate())
        assertEquals(PieceColor.RED, board.noLegalMoveWinner())
        assertEquals(90000, Evaluator.evaluate(board))
    }

    @Test fun checkmateStillLoses() {
        val board = trapped(PieceColor.RED, checked = true)
        assertTrue(board.isCheckmate())
        assertEquals(PieceColor.BLACK, board.noLegalMoveWinner())
    }

    @Test fun initialBoardHasNoWinner() {
        assertNull(Board.createInitialBoard().noLegalMoveWinner())
    }
}
