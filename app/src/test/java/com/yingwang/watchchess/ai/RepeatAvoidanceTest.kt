package com.yingwang.watchchess.ai

import com.yingwang.watchchess.model.Board
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 同一局棋里回到同一个局面时，不该照原样再走一遍。
 *
 * 殿下 2026-09-20 说的就是这件事：引擎是确定性的，同一个局面永远回同一着，两边来回推，
 * 一局棋就卡在那儿出不来。挑着法的那段逻辑要做到：已经从这个局面走过的着法往后排，
 * 但绝不为了换花样跳出「跟最优着差不多好」的范围。
 *
 * 这里直接测挑选逻辑本身，不启动引擎，所以跑得快也不挑环境。
 */
class RepeatAvoidanceTest {

    /** multipv 序号 to (分数, 着法)。分数越大越好。 */
    private fun lines(vararg pairs: Pair<Int, String>) =
        pairs.mapIndexed { i, (cp, mv) -> (i + 1) to (cp to mv) }.toMap()

    @Test
    fun `picks a move it has not played from this position`() {
        // 四着分数接近，其中三着这一局已经从这个局面走过了，只剩一着是新的
        val pool = lines(100 to "b0c2", 95 to "h0g2", 92 to "b2e2", 90 to "h2e2")
        val seen = setOf("b0c2", "h0g2", "b2e2")
        repeat(30) {
            assertEquals("h2e2", FairyProtocol.pick(pool, "b0c2", spreadCp = 30, alreadyPlayedHere = seen))
        }
    }

    @Test
    fun `falls back to repeating rather than playing a bad move`() {
        // 差不多好的只有一着，而它已经走过了。宁可重复，也不能跳到差很多的那一着上去。
        val pool = lines(100 to "b0c2", 10 to "a0a1", -50 to "i0i1")
        val chosen = FairyProtocol.pick(pool, "b0c2", spreadCp = 20, alreadyPlayedHere = setOf("b0c2"))
        assertEquals("b0c2", chosen)
    }

    @Test
    fun `never leaves the near-best band even when everything is fresh`() {
        val pool = lines(100 to "b0c2", 98 to "h0g2", 10 to "a0a1")
        val allowed = setOf("b0c2", "h0g2")
        repeat(50) {
            val chosen = FairyProtocol.pick(pool, "b0c2", spreadCp = 25, alreadyPlayedHere = emptySet())
            assertTrue("挑到了带外的 $chosen", chosen in allowed)
        }
    }

    @Test
    fun `an empty candidate list falls back to the engine's own answer`() {
        assertEquals("b0c2", FairyProtocol.pick(emptyMap(), "b0c2", spreadCp = 50, alreadyPlayedHere = emptySet()))
    }

    @Test
    fun `parsing keeps the deepest line for each multipv slot`() {
        // 同一个 multipv 序号会随着搜索加深反复出现，留下的必须是最后那一条
        assertEquals(
            1 to (57 to "b0c2"),
            FairyProtocol.parseInfo("info depth 12 seldepth 15 multipv 1 score cp 57 nodes 900 pv b0c2 h9g7"),
        )
        // 有杀着的局面不参与抽签，分数不是 cp 的行一律忽略
        assertEquals(null, FairyProtocol.parseInfo("info depth 9 multipv 1 score mate 3 pv b0c2"))
        assertEquals(null, FairyProtocol.parseInfo("info string classical evaluation enabled"))
    }

    @Test
    fun `positions are keyed so that the same layout collides and a different one does not`() {
        val a = Board.createInitialBoard()
        val b = Board.createInitialBoard()
        assertEquals("同一个局面必须给出同一个键", a.toFen(), b.toFen())

        val moved = a.makeMove(uciToMove(a, "b0c2")!!)
        assertNotEquals("走了一步之后必须换一个键", a.toFen(), moved.toFen())
    }
}
