package com.yingwang.watchchess.model

/** 中国象棋无合法着法即负，将死与困毙采用同一胜负判定。 */
internal fun Board.noLegalMoveWinner(): PieceColor? =
    if (getAllLegalMoves().isEmpty()) currentPlayer.opposite() else null
