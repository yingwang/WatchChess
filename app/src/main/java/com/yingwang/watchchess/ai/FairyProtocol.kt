package com.yingwang.watchchess.ai

/** 固定象棋协议，保留现有 0 到 9 的纵坐标；普通 UCI 在此版本中使用 1 到 10。 */
internal object FairyProtocol {
    const val MULTI_PV = 4

    val handshake = listOf("ucicyclone", "uci")

    fun configure(advertisedOptions: Set<String>): List<String> = buildList {
        // 此版本没有 NUMA 自动绑定；若后续引擎提供该选项，必须关闭自动探测。
        if ("NumaPolicy" in advertisedOptions) add("setoption name NumaPolicy value none")
        add("setoption name Use NNUE value false")
        add("setoption name UCI_Variant value xiangqi")
        add("setoption name Threads value 1")
        // 让它一并报出前几条线路，好在「差不多好」的几着里随机挑一个，免得同一个局面
        // 每次都走同一着、每局开头一模一样。挑选的逻辑在 FairyEngine.pickWithSpread。
        add("setoption name MultiPV value $MULTI_PV")
        add("setoption name Hash value 4")
        add("isready")
    }
}
