package com.yingwang.watchchess.ai

import com.yingwang.watchchess.model.Board
import com.yingwang.watchchess.model.PieceColor
import com.yingwang.watchchess.model.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 坐标与局面换算的对照测试。
 *
 * 所有期望值都是从皮卡鱼本身取来的，不是我推出来的：在电脑上编一份皮卡鱼，喂给它
 * `position startpos` 再打 `d`，它自己打印出局面串与坐标轴；走一步 `b0c2` 再打一次，
 * 对照前后两串就能确定行号是怎么翻的。拿引擎的输出当标准答案，换算写反了这里会立刻红。
 */
class UciCoordsTest {

    /** 皮卡鱼对起始局面打印的那一串，一字不差。 */
    private val startFen =
        "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"

    @Test
    fun `initial board serialises to the engine's own fen`() {
        assertEquals(startFen, Board.createInitialBoard().toFen())
    }

    @Test
    fun `squares map to the engine's axis labels`() {
        // 引擎的坐标轴：列 a 到 i 自左向右，行 0 到 9 自下而上，第 0 行是红方底线。
        // 本项目的 row 0 在最上面，所以行号要翻过来。
        assertEquals("a9", Position(0, 0).toUci())   // 左上角，黑车
        assertEquals("i9", Position(0, 8).toUci())   // 右上角，黑车
        assertEquals("a0", Position(9, 0).toUci())   // 左下角，红车
        assertEquals("i0", Position(9, 8).toUci())   // 右下角，红车
        assertEquals("e0", Position(9, 4).toUci())   // 红帅
        assertEquals("e9", Position(0, 4).toUci())   // 黑将
    }

    @Test
    fun `square conversion round trips over the whole board`() {
        for (row in 0..9) for (col in 0..8) {
            val p = Position(row, col)
            assertEquals(p, uciToPosition(p.toUci()))
        }
    }

    @Test
    fun `the engine's own move b0c2 lands where the engine put it`() {
        // 把 b0c2 喂给电脑上那份皮卡鱼，它回的局面串是下面这个。
        val fromEngine =
            "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1CN4C1/9/R1BAKABNR b - - 1 1"

        val board = Board.createInitialBoard()
        val move = uciToMove(board, "b0c2")
        assertNotNull("b0c2 should resolve against the initial board", move)

        val after = board.makeMove(move!!)
        after.currentPlayer = PieceColor.BLACK

        // 只比到走子方为止。末尾两个计数本项目没有记，toFen 那边写死成 0 和 1，
        // 拿它跟引擎对是对不上的，也没必要对：真正下棋喂的是完整着法序列。
        fun meaningfulPart(fen: String) = fen.split(" ").take(2).joinToString(" ")
        assertEquals(meaningfulPart(fromEngine), meaningfulPart(after.toFen()))
    }

    @Test
    fun `the placeholder counters are documented as placeholders`() {
        // 这一条不是在验正确，是在钉住那个已知的取巧：计数没记，写死了。
        // 哪天有人给 Board 加上计数，这里会红，提醒他把 toFen 一并改掉。
        val tail = Board.createInitialBoard().toFen().split(" ").takeLast(2)
        assertEquals(listOf("0", "1"), tail)
    }

    @Test
    fun `b0c2 is the red horse stepping from its own back rank`() {
        val board = Board.createInitialBoard()
        val move = uciToMove(board, "b0c2")!!
        assertEquals(Position(9, 1), move.from)   // 红方底线，左马
        assertEquals(Position(7, 2), move.to)
        assertEquals(PieceColor.RED, move.piece.color)
        assertEquals(com.yingwang.watchchess.model.PieceType.HORSE, move.piece.type)
    }

    @Test
    fun `every legal opening move survives a round trip through uci`() {
        val board = Board.createInitialBoard()
        val legal = board.getAllLegalMoves()
        assertTrue("initial position should have legal moves", legal.isNotEmpty())
        for (m in legal) {
            val back = uciToMove(board, m.toUci())
            assertNotNull("${m.toUci()} should resolve", back)
            assertEquals(m.from, back!!.from)
            assertEquals(m.to, back.to)
        }
    }

    @Test
    fun `uci move text matches the engine's perft listing`() {
        // 皮卡鱼对起始局面跑 perft 1 时列出的头几个着法，照抄过来。
        // 本项目生成的合法着法集合必须把这些都包含进去。
        val fromEngine = listOf("a3a4", "c3c4", "e3e4", "g3g4", "i3i4", "c0a2", "c0e2", "b0c2")
        val mine = Board.createInitialBoard().getAllLegalMoves().map { it.toUci() }.toSet()
        for (u in fromEngine) {
            assertTrue("engine lists $u but we do not generate it", u in mine)
        }
    }
}
