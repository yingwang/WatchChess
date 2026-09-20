package com.yingwang.watchchess.ai

/** 固定象棋协议，保留现有 0 到 9 的纵坐标；普通 UCI 在此版本中使用 1 到 10。 */
internal object FairyProtocol {
    val handshake = listOf("ucicyclone", "uci")

    fun configure(advertisedOptions: Set<String>): List<String> = buildList {
        // 此版本没有 NUMA 自动绑定；若后续引擎提供该选项，必须关闭自动探测。
        if ("NumaPolicy" in advertisedOptions) add("setoption name NumaPolicy value none")
        add("setoption name Use NNUE value false")
        add("setoption name UCI_Variant value xiangqi")
        add("setoption name Threads value 1")
        add("setoption name Hash value 4")
        add("isready")
    }
}
