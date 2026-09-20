package com.yingwang.watchchess.ui

import com.yingwang.watchchess.ai.toUci
import com.yingwang.watchchess.model.*

/** 保存棋谱而不是 Android 资源 ID 或引擎状态，恢复时重新验证每一步。 */
internal data class SavedGame(
    val difficulty: Int,
    val side: PieceColor,
    val startedAt: Long,
    val resultName: String,
    val moves: List<String>,
) {
    fun encode(): String = listOf("1", difficulty.toString(), side.name,
        startedAt.toString(), resultName, moves.joinToString(" ")).joinToString("\n")

    fun replay(): List<Pair<Board, Move>> {
        var board = Board.createInitialBoard()
        return moves.map { notation ->
            val move = board.getAllLegalMoves().firstOrNull { it.toUci() == notation }
                ?: error("Invalid saved move")
            val snapshot = board to move
            board = board.makeMove(move).also { it.currentPlayer = board.currentPlayer.opposite() }
            snapshot
        }
    }

    companion object {
        fun decode(text: String): SavedGame? = runCatching {
            val fields = text.split('\n')
            require(fields.size == 6 && fields[0] == "1")
            val level = fields[1].toInt()
            require(level in 0..3)
            val moves = fields[5].split(' ').filter { it.isNotBlank() }
            require(moves.size <= 10000 && moves.all { it.matches(Regex("[a-i][0-9][a-i][0-9]")) })
            SavedGame(level, PieceColor.valueOf(fields[2]), fields[3].toLong(), fields[4], moves)
        }.getOrNull()
    }
}

/** 回到玩家最近一次落子之前；终局可能只走了半回合，不能固定撤两步。 */
internal fun undoHistorySize(moves: List<Move>, player: PieceColor): Int? =
    moves.indexOfLast { it.piece.color == player }.takeIf { it >= 0 }
