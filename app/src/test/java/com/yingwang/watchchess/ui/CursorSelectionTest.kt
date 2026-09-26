package com.yingwang.watchchess.ui

import com.yingwang.watchchess.model.Position
import org.junit.Assert.assertEquals
import org.junit.Test

class CursorSelectionTest {
    @Test fun cancelReturnsToSelectedPiece() {
        val pieces = listOf(Position(9, 0), Position(9, 1), Position(9, 2))
        assertEquals(2, restoredCursorIndex(pieces, pieces[2]))
        assertEquals(0, restoredCursorIndex(pieces.reversed(), pieces[2]))
    }
    @Test fun missingOrEmptySelectionStartsAtBeginning() {
        assertEquals(0, restoredCursorIndex(emptyList(), Position(9, 0)))
        assertEquals(0, restoredCursorIndex(listOf(Position(9, 0)), null))
    }
}
