package com.yingwang.watchchess.ai

import org.junit.Assert.*
import org.junit.Test

class MateSelectionTest {
    private fun choose(vararg info: String): String {
        val lines = info.mapNotNull(FairyProtocol::parseInfo).toMap()
        return FairyProtocol.pick(lines, "b0c2", 100, setOf("b0c2"))
    }

    @Test fun winningMateIsNotDiscardedForFreshCpMove() {
        assertEquals("b0c2", choose("info depth 12 multipv 1 score mate 3 pv b0c2",
            "info depth 12 multipv 2 score cp 500 pv h0g2"))
    }
    @Test fun losingMateAlsoKeepsEngineAnswer() {
        assertEquals("b0c2", choose("info depth 12 multipv 1 score mate -8 pv b0c2",
            "info depth 10 multipv 2 score cp 500 pv h0g2"))
    }
    @Test fun differentDepthCannotEnterLottery() {
        assertEquals("b0c2", choose("info depth 12 multipv 1 score cp 100 pv b0c2",
            "info depth 11 multipv 2 score cp 100 pv h0g2"))
    }
    @Test fun boundedScoreCannotEnterLottery() {
        assertEquals("b0c2", choose("info depth 12 multipv 1 score cp 100 pv b0c2",
            "info depth 12 multipv 2 score cp 100 lowerbound pv h0g2"))
    }
    @Test fun mismatchedPrimaryKeepsBestmove() {
        assertEquals("b0c2", choose("info depth 12 multipv 1 score cp 100 pv h0g2"))
    }
    @Test fun verdictProtocolDoesNotInterpretSearchScoresAsResults() {
        assertEquals(GameVerdict.DRAW, GameVerdict.fromProtocol("watchresult draw"))
        assertEquals(GameVerdict.RED_WIN, GameVerdict.fromProtocol("watchresult red"))
        assertEquals(GameVerdict.BLACK_WIN, GameVerdict.fromProtocol("watchresult black"))
        assertEquals(GameVerdict.ONGOING, GameVerdict.fromProtocol("watchresult ongoing"))
        assertNull(GameVerdict.fromProtocol("info depth 4 score mate 1"))
        assertNull(GameVerdict.fromProtocol("watchresult unknown"))
    }
}
