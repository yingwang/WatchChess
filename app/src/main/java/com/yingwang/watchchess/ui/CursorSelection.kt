package com.yingwang.watchchess.ui

import com.yingwang.watchchess.model.Position

internal fun restoredCursorIndex(candidates: List<Position>, cancelled: Position?): Int =
    candidates.indexOf(cancelled).coerceAtLeast(0)
