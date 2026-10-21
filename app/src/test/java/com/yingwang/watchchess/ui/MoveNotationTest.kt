package com.yingwang.watchchess.ui

import com.yingwang.watchchess.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class MoveNotationTest {
    /** Plays [moves] (from/to pairs) from the opening and returns the board before the last one with that move. */
    private fun play(vararg moves: Pair<Position, Position>): Pair<Board, Move> {
        var board = Board.createInitialBoard()
        var last: Pair<Board, Move>? = null
        for ((from, to) in moves) {
            val move = board.getAllLegalMoves().first { it.from == from && it.to == to }
            last = board to move
            board = board.makeMove(move).also { it.currentPlayer = board.currentPlayer.opposite() }
        }
        return last!!
    }
    private fun zh(vararg moves: Pair<Position, Position>) = play(*moves).let { (b, m) -> moveNotation(b, m, chinese = true) }
    private fun en(vararg moves: Pair<Position, Position>) = play(*moves).let { (b, m) -> moveNotation(b, m, chinese = false) }

    private val redCannonCentre = Position(7, 7) to Position(7, 4)
    private val blackHorse = Position(0, 1) to Position(2, 2)

    @Test fun redCountsFilesFromItsRightInChineseNumerals() {
        assertEquals("炮二平五", zh(redCannonCentre))
        assertEquals("马二进三", zh(Position(9, 7) to Position(7, 6)))
        assertEquals("车一进一", zh(Position(9, 8) to Position(8, 8)))
        assertEquals("相七进五", zh(Position(9, 2) to Position(7, 4)))
    }

    @Test fun blackCountsFilesFromItsRightInDigits() {
        assertEquals("马2进3", zh(redCannonCentre, blackHorse))
        assertEquals("卒3进1", zh(redCannonCentre, Position(3, 2) to Position(4, 2)))
    }

    @Test fun straightPiecesCountStepsAndSidewaysMovesNameTheFile() {
        // Red's files count from its right, so the advisor at the general's right is on file 四.
        // 仕四进五 clears that square, then 帅五平四 steps into it.
        val advisor = Position(9, 5) to Position(8, 4)
        assertEquals("仕四进五", zh(advisor))
        assertEquals("帅五平四", zh(advisor, blackHorse, Position(9, 4) to Position(9, 5)))
    }

    @Test fun twoOfAKindOnOneFileAreToldApartAsFrontAndRear() {
        // 车九进一, then along the empty rank to the right chariot's file: 前车平五.
        val up = Position(9, 0) to Position(8, 0)
        val across = Position(8, 0) to Position(8, 8)
        assertEquals("前车平五", zh(up, blackHorse, across, Position(2, 2) to Position(0, 1), Position(8, 8) to Position(8, 4)))
    }

    @Test fun englishUsesTheWxfLetters() {
        assertEquals("C2=5", en(redCannonCentre))
        assertEquals("H2+3", en(redCannonCentre, blackHorse))
        assertEquals("R1+1", en(Position(9, 8) to Position(8, 8)))
        assertEquals("E7+5", en(Position(9, 2) to Position(7, 4)))
        val up = Position(9, 0) to Position(8, 0)
        assertEquals("+R=5", en(up, blackHorse, Position(8, 0) to Position(8, 8), Position(2, 2) to Position(0, 1), Position(8, 8) to Position(8, 4)))
    }
}
