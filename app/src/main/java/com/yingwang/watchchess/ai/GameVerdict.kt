package com.yingwang.watchchess.ai

import com.yingwang.watchchess.R
import com.yingwang.watchchess.model.Board
import com.yingwang.watchchess.model.PieceColor
import com.yingwang.watchchess.model.noLegalMoveWinner

internal enum class GameVerdict(val messageRes: Int?) {
    ONGOING(null),
    RED_WIN(R.string.result_red_wins),
    BLACK_WIN(R.string.result_black_wins),
    DRAW(R.string.result_draw),
    // 无法裁决时暂停，不把协议故障伪装成和棋或继续无限循环。
    UNAVAILABLE(R.string.result_unavailable);

    companion object {
        fun fromProtocol(line: String): GameVerdict? = when (line.trim()) {
            "watchresult ongoing" -> ONGOING
            "watchresult red" -> RED_WIN
            "watchresult black" -> BLACK_WIN
            "watchresult draw" -> DRAW
            else -> null
        }
    }
}

/** 不问引擎、只在本地能判的那部分：无子可走即负（将死与困毙），重复局面这里判不了。 */
internal fun Board.localVerdict(): GameVerdict = when (noLegalMoveWinner()) {
    PieceColor.RED -> GameVerdict.RED_WIN
    PieceColor.BLACK -> GameVerdict.BLACK_WIN
    null -> GameVerdict.ONGOING
}

/**
 * 引擎给不出裁决时怎么办。
 *
 * [engine] 为 null 表示引擎没在跑（起不来），这时直接改由本地判。引擎在跑却答不上来，第一次
 * 如实暂停，让人点一下重试；重试时仍答不上，也改由本地判，免得一局棋卡在一个永远不会成功
 * 的重试上。本地判只管将死与困毙，长将、长捉与重复在这条路上不判。
 */
internal fun settleVerdict(engine: GameVerdict?, retrying: Boolean, local: () -> GameVerdict): GameVerdict = when {
    engine == null -> local()
    engine == GameVerdict.UNAVAILABLE && retrying -> local()
    else -> engine
}
