package com.yingwang.watchchess.ai

internal enum class GameVerdict(val message: String?) {
    ONGOING(null), RED_WIN("红胜"), BLACK_WIN("黑胜"), DRAW("和棋"),
    // 无法裁决时暂停，不把协议故障伪装成和棋或继续无限循环。
    UNAVAILABLE("裁决失败，请重开");

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
