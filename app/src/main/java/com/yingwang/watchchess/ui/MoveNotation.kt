package com.yingwang.watchchess.ui

import com.yingwang.watchchess.model.Board
import com.yingwang.watchchess.model.Move
import com.yingwang.watchchess.model.PieceColor
import com.yingwang.watchchess.model.PieceType
import kotlin.math.abs

/**
 * 一着棋的记谱，给棋盘底下那行字用。
 *
 * 中文用传统纵线记谱：红方用汉字数目（炮二平五），黑方用阿拉伯数字（马2进3）；纵线从各自
 * 的右手边数起，所以红方的一路在棋盘右边、黑方的 1 路在棋盘左边。车炮兵帅将直走时，进退
 * 后面跟走的步数；马相士斜走时跟落点的纵线。同一纵线上有两枚同样的子，用前后代替纵线号，
 * 三枚用前中后。
 *
 * 英文用世界象棋联合会那套（WXF）：字母记子，+ - = 记进退平，前后用 + - 作前缀。
 *
 * [before] 是走这一步之前的局面，用来数同一纵线上还有几枚同样的子。
 */
internal fun moveNotation(before: Board, move: Move, chinese: Boolean): String {
    val piece = move.piece
    val red = piece.color == PieceColor.RED
    fun file(col: Int) = if (red) 9 - col else col + 1

    val onSameFile = before.getAllPieces()
        .filter { it.type == piece.type && it.color == piece.color && it.position.col == move.from.col }
        .sortedBy { if (red) it.position.row else -it.position.row } // 前面的先
    val rank = onSameFile.indexOfFirst { it.position == move.from }

    val dRow = move.to.row - move.from.row
    val forward = if (red) dRow < 0 else dRow > 0
    val straight = piece.type in setOf(PieceType.CHARIOT, PieceType.CANNON, PieceType.SOLDIER, PieceType.GENERAL)
    val number = if (dRow == 0 || !straight) file(move.to.col) else abs(dRow)

    return if (chinese) {
        val digits = if (red) "一二三四五六七八九" else "123456789"
        val name = pieceName(piece.type, red)
        val head = when (onSameFile.size) {
            0, 1 -> name + digits[file(move.from.col) - 1]
            2 -> (if (rank == 0) "前" else "后") + name
            3 -> "前中后"[rank] + name
            else -> digits[rank] + name
        }
        val direction = when { dRow == 0 -> "平"; forward -> "进"; else -> "退" }
        head + direction + digits[number - 1]
    } else {
        val letter = when (piece.type) {
            PieceType.GENERAL -> "K"; PieceType.ADVISOR -> "A"; PieceType.ELEPHANT -> "E"; PieceType.HORSE -> "H"
            PieceType.CHARIOT -> "R"; PieceType.CANNON -> "C"; PieceType.SOLDIER -> "P"
        }
        val head = when (onSameFile.size) {
            0, 1 -> letter + file(move.from.col)
            2 -> (if (rank == 0) "+" else "-") + letter
            else -> "${rank + 1}$letter"
        }
        val direction = when { dRow == 0 -> "="; forward -> "+"; else -> "-" }
        head + direction + number
    }
}

private fun pieceName(type: PieceType, red: Boolean): String = when (type) {
    PieceType.GENERAL -> if (red) "帅" else "将"
    PieceType.ADVISOR -> if (red) "仕" else "士"
    PieceType.ELEPHANT -> if (red) "相" else "象"
    PieceType.HORSE -> "马"
    PieceType.CHARIOT -> "车"
    PieceType.CANNON -> "炮"
    PieceType.SOLDIER -> if (red) "兵" else "卒"
}
