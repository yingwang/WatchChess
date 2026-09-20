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

    /**
     * 从一行 info 里取出 multipv 序号、分数和首着。取不到就返回 null。
     *
     * 只认 score cp，不认 score mate。有杀着的时候本来就不该为了花样去挑别的走法。
     */
    fun parseInfo(line: String): Pair<Int, Pair<Int, String>>? {
        if (!line.startsWith("info ") || " pv " !in line) return null
        val tok = line.split(" ")
        fun after(key: String): String? = tok.indexOf(key).takeIf { it >= 0 && it + 1 < tok.size }?.let { tok[it + 1] }
        val idx = after("multipv")?.toIntOrNull() ?: 1
        if (after("score") != "cp") return null
        val cp = after("cp")?.toIntOrNull() ?: return null
        val move = tok.getOrNull(tok.indexOf("pv") + 1) ?: return null
        if (move.length < 4) return null
        return idx to (cp to move)
    }

    /**
     * 挑这一步走哪着。
     *
     * 主要是为了解决殿下 2026-09-20 说的那件事：同一局棋里又回到一模一样的局面时，引擎是
     * 死的，会照原样再走一遍，于是两边来回推，一局棋卡在那儿出不来。alreadyPlayedHere 是
     * 这一局里从当前局面走出去过的着法，这里优先挑没走过的。
     *
     * spreadCp 管的是「差不多好的几着里随便挑一个」。它给得很窄，是为了给换着法留出候选，
     * 不是拿来削弱棋力的；真想让低档更好赢，把它调宽就是，那是现成的旋钮。
     *
     * 两道都取不到候选就照用引擎给的 bestmove。避免重复也好、换花样也好，都不值得拿一步
     * 坏棋去换，所以候选池永远只在「跟最优着差不超过 spreadCp」这个范围里取。
     */
    fun pick(
        lines: Map<Int, Pair<Int, String>>,
        fallback: String,
        spreadCp: Int,
        alreadyPlayedHere: Set<String>,
    ): String {
        if (lines.isEmpty()) return fallback
        val topScore = lines.values.maxOf { it.first }
        val pool = lines.values
            .filter { it.first >= topScore - maxOf(spreadCp, 0) }
            .map { it.second }
            .distinct()
        if (pool.isEmpty()) return fallback
        val fresh = pool.filterNot { it in alreadyPlayedHere }
        return if (fresh.isNotEmpty()) fresh.random() else pool.random()
    }
}
