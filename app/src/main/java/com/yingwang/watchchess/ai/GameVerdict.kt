package com.yingwang.watchchess.ai

import com.yingwang.watchchess.R

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
