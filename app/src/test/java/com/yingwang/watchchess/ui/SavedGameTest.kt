package com.yingwang.watchchess.ui

import com.yingwang.watchchess.ai.toFen
import com.yingwang.watchchess.model.*
import org.junit.Assert.*
import org.junit.Test

class SavedGameTest {
    private val moves = listOf("b0c2", "b9c7", "c2b0", "c7b9")

    @Test fun roundTripPreservesSideDifficultyHistoryAndResult() {
        val saved = SavedGame(3, PieceColor.BLACK, 123456, "result_red_wins", moves)
        assertEquals(saved, SavedGame.decode(saved.encode()))
        assertEquals(4, saved.replay().size)
    }

    @Test fun emptyGameCanBeRestoredForEitherSide() {
        PieceColor.values().forEach { side ->
            val saved = SavedGame(0, side, 1, "", emptyList())
            assertEquals(saved, SavedGame.decode(saved.encode()))
            assertTrue(saved.replay().isEmpty())
        }
    }

    @Test fun replayRestoresBoardTurnAndUndoSnapshots() {
        val replay = SavedGame(1, PieceColor.RED, 1, "", moves).replay()
        val (before, move) = replay.last()
        val final = before.makeMove(move).also { it.currentPlayer = before.currentPlayer.opposite() }
        assertEquals(Board.createInitialBoard().toFen(), final.toFen())
        assertEquals(PieceColor.RED, replay[2].first.currentPlayer)
    }

    @Test fun undoHumanTerminalMoveRemovesOnePlyNotTwo() {
        val history = SavedGame(1, PieceColor.RED, 1, "", moves.take(3)).replay().map { it.second }
        assertEquals(2, undoHistorySize(history, PieceColor.RED))
    }

    @Test fun undoAfterAiReplyRemovesTwoPlies() {
        val history = SavedGame(1, PieceColor.RED, 1, "", moves).replay().map { it.second }
        assertEquals(2, undoHistorySize(history, PieceColor.RED))
    }

    @Test fun blackCannotUndoBeforeMakingFirstMove() {
        val history = SavedGame(1, PieceColor.BLACK, 1, "", moves.take(1)).replay().map { it.second }
        assertNull(undoHistorySize(history, PieceColor.BLACK))
    }

    @Test fun blackUndoRestoresBlackTurn() {
        val replay = SavedGame(1, PieceColor.BLACK, 1, "", moves.take(3)).replay()
        val keep = undoHistorySize(replay.map { it.second }, PieceColor.BLACK)!!
        assertEquals(1, keep)
        assertEquals(PieceColor.BLACK, replay[keep].first.currentPlayer)
    }

    @Test fun invalidFormatIsRejected() {
        assertNull(SavedGame.decode("broken"))
        assertNull(SavedGame.decode("1\n8\nRED\n1\n\nb0c2"))
        assertNull(SavedGame.decode("1\n1\nRED\n1\n\nxxxx"))
    }

    @Test(expected = IllegalStateException::class)
    fun illegalMoveIsRejectedBeforeRestore() {
        SavedGame(1, PieceColor.RED, 1, "", listOf("b0b9")).replay()
    }
}
