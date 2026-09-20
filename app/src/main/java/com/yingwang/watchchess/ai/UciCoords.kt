package com.yingwang.watchchess.ai

import com.yingwang.watchchess.model.Board
import com.yingwang.watchchess.model.Move
import com.yingwang.watchchess.model.Piece
import com.yingwang.watchchess.model.PieceColor
import com.yingwang.watchchess.model.PieceType
import com.yingwang.watchchess.model.Position

// ── 坐标与局面的换算 ────────────────────────────────────────────────────────
//
// 皮卡鱼那一侧：列是 a 到 i 从左到右，行是 0 到 9 自下而上，第 0 行是红方底线。
// 这个项目里：row 0 在最上面（黑方底线），row 9 在最下面（红方底线），col 0 到 8 从左到右。
// 于是 col 直接对应 a 到 i，而行号要上下翻过来。
// 记谱串里十组棋子自上而下排列，正好等于 row 0 到 row 9，不需要再倒序。

internal fun Position.toUci(): String = "${'a' + col}${9 - row}"

internal fun uciToPosition(s: String): Position = Position(9 - (s[1] - '0'), s[0] - 'a')

internal fun Move.toUci(): String = from.toUci() + to.toUci()

internal fun uciToMove(board: Board, uci: String): Move? {
    val from = uciToPosition(uci.substring(0, 2))
    val to = uciToPosition(uci.substring(2, 4))
    if (!from.isValid() || !to.isValid()) return null
    val piece = board.getPiece(from) ?: return null
    return Move(from, to, piece, board.getPiece(to))
}

internal fun fenChar(piece: Piece): Char {
    val c = when (piece.type) {
        PieceType.GENERAL -> 'K'
        PieceType.ADVISOR -> 'A'
        PieceType.ELEPHANT -> 'B'
        PieceType.HORSE -> 'N'
        PieceType.CHARIOT -> 'R'
        PieceType.CANNON -> 'C'
        PieceType.SOLDIER -> 'P'
    }
    return if (piece.color == PieceColor.RED) c else c.lowercaseChar()
}

/**
 * 局面的记谱串。
 *
 * 末尾那两个计数（未吃子步数与回合数）本项目的 Board 没有记，这里一律写成 0 和 1，
 * 所以它们是占位而非实情。这也正是求着时不走这条路的原因：真要下棋是把「起始局面加
 * 一整串着法」喂给引擎，历史完整，引擎自己数得清，长将长捉也判得了。这个函数留着
 * 是为了排查问题时能一眼看见盘面。
 */
internal fun Board.toFen(): String {
    val sb = StringBuilder()
    for (row in 0..9) {
        var empty = 0
        for (col in 0..8) {
            val p = getPiece(Position(row, col))
            if (p == null) {
                empty++
            } else {
                if (empty > 0) { sb.append(empty); empty = 0 }
                sb.append(fenChar(p))
            }
        }
        if (empty > 0) sb.append(empty)
        if (row < 9) sb.append('/')
    }
    sb.append(if (currentPlayer == PieceColor.RED) " w" else " b")
    sb.append(" - - 0 1")
    return sb.toString()
}
