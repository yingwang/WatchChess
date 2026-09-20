package com.yingwang.watchchess.ai

import android.content.Context
import android.util.Log
import com.yingwang.watchchess.model.Board
import com.yingwang.watchchess.model.Move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

/**
 * Fairy-Stockfish 的静态子进程适配器。类名暂时保留，避免改动游戏界面和生命周期。
 *
 * 这只表有个特别之处：硬件是六十四位，可安卓那一层是三十二位的，abilist64 是空的，
 * 系统里既没有 linker64 也没有 /system/lib64。所以引擎必须编成完全静态的 arm64
 * 可执行文件，不依赖系统里任何六十四位的库。应用自身仍是三十二位进程，把它当子进程
 * 叫起来即可，内核对父子进程的位数并不挑剔。
 *
 * 可执行文件只能放在应用的原生库目录里。安卓从 API 29 起不准从应用可写的数据目录里
 * 执行文件，而 lib 目录不受这条限制，所以二进制以 libfairystockfish.so 的名义打包进
 * jniLibs，靠 extractNativeLibs 在安装时释放出来。
 *
 * 使用手写评估，不读取或解包任何 NNUE 网络。
 */
class FairyEngine(private val context: Context) {

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private var output: UciOutput? = null

    var lastError: String? = null
        private set

    val isRunning: Boolean
        get() = process?.isAlive == true

    // ── 启停 ────────────────────────────────────────────────────────────────

    /** 启动并初始化引擎。失败时返回 false 并把原因记在 lastError 里，调用方应回落到内置引擎。 */
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext true
        try {
            stop()

            val bin = File(context.applicationInfo.nativeLibraryDir, BIN_NAME)
            if (!bin.exists()) {
                lastError = "engine binary not found at ${bin.absolutePath}"
                return@withContext false
            }
            val pb = ProcessBuilder(bin.absolutePath)
                .directory(context.filesDir)
                .redirectErrorStream(true)
            // 静态链接进来的那个分配器（Scudo）默认把释放掉的内存攥在手里不还给系统，
            // 于是同一份引擎、同一个网络、同样的参数，在电脑上常驻约三百三十兆，在表上
            // 却涨到八百多兆，整块都记在一个匿名的 malloc 区里。表上一共才一点七八个 G，
            // 撑不住。这两个开关让它一释放就还，别攒着。
            pb.environment()["SCUDO_OPTIONS"] =
                "release_to_os_interval_ms=0:may_return_null=true"
            val p = pb.start()
            process = p
            writer = BufferedWriter(OutputStreamWriter(p.outputStream))
            reader = BufferedReader(InputStreamReader(p.inputStream))
            output = UciOutput(reader!!)

            FairyProtocol.handshake.forEach(::send)
            val options = mutableSetOf<String>()
            if (!awaitToken("uciok", HANDSHAKE_TIMEOUT_MS) { line ->
                    if (line.startsWith("option name "))
                        options.add(line.substringAfter("option name ").substringBefore(" type "))
                }) {
                lastError = "no uciok from engine"
                stop()
                return@withContext false
            }

            check("UCI_Variant" in options && "Use NNUE" in options) {
                "Unexpected engine: missing Fairy-Stockfish options"
            }
            FairyProtocol.configure(options).forEach(::send)
            if (!awaitToken("readyok", HANDSHAKE_TIMEOUT_MS)) {
                lastError = "no readyok from engine"
                stop()
                return@withContext false
            }
            lastError = null
            true
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            Log.w(TAG, "start failed", e)
            stop()
            false
        }
    }

    fun stop() {
        val p = process
        try { writer?.apply { write("quit\n"); flush() } } catch (_: Exception) {}
        // 不只依赖 quit；只回收本实例拥有的进程，不按名字杀其他正在运行的进程。
        try { p?.destroy() } catch (_: Exception) {}
        try {
            if (p != null && !p.waitFor(1500, TimeUnit.MILLISECONDS)) p.destroyForcibly()
        } catch (_: Exception) {
            try { p?.destroyForcibly() } catch (_: Exception) {}
        }
        output?.close()
        try { reader?.close() } catch (_: Exception) {}
        try { writer?.close() } catch (_: Exception) {}
        process = null; writer = null; reader = null; output = null
    }

    // ── 求着 ────────────────────────────────────────────────────────────────

    /**
     * 让引擎给出一步棋。
     *
     * 局面用「起始局面加着法序列」的形式喂过去，而不是直接喂当前局面的记谱串，这样引擎
     * 才看得见重复局面，能按规则处理长将与长捉。
     *
     * 强弱用结点数封顶，不用深度。结点数与机器快慢无关，同一档在哪台机器上都是同样的棋力；
     * 时间上限只是兜底，免得慢机器上一步棋想太久。
     */
    suspend fun findBestMove(
        board: Board,
        moveHistory: List<Move>,
        nodeLimit: Long,
        timeLimitMs: Long,
        spreadCp: Int,
    ): Move? = withContext(Dispatchers.IO) {
        if (!isRunning) return@withContext null
        try {
            val moves = moveHistory.joinToString(" ") { it.toUci() }
            send(if (moves.isEmpty()) "position startpos" else "position startpos moves $moves")
            send("go nodes $nodeLimit movetime $timeLimitMs")

            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeLimitMs + SEARCH_GRACE_MS)
            var best: String? = null
            // multipv 序号到（分数，着法）。同一序号后来的行覆盖先前的，留下的是最深一层的结果。
            val lines = HashMap<Int, Pair<Int, String>>()
            while (System.nanoTime() < deadline) {
                val line = output?.readUntil(deadline) ?: break
                if (line.startsWith("bestmove")) {
                    best = line.split(" ").getOrNull(1)
                    break
                }
                parseInfo(line)?.let { (idx, scored) -> lines[idx] = scored }
            }
            if (best == null) {
                lastError = "engine search timed out or ended before bestmove"
                stop() // 防止迟到的 bestmove 被下一次搜索误读。
                return@withContext null
            }
            val chosen = pickWithSpread(lines, best, spreadCp)
            // 不把坐标合法但走法违规的输出交给界面。
            board.getAllLegalMoves().find { it.toUci() == chosen }
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            Log.w(TAG, "findBestMove failed", e)
            stop()
            null
        }
    }

    // ── 私有 ────────────────────────────────────────────────────────────────

    /**
     * 从一行 info 里取出 multipv 序号、分数和首着。取不到就返回 null。
     *
     * 只认 score cp，不认 score mate。有杀着的时候本来就不该为了花样去挑别的走法。
     */
    private fun parseInfo(line: String): Pair<Int, Pair<Int, String>>? {
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
     * 在跟最优着相差不超过 spreadCp 的那些着法里随机挑一个。
     *
     * 殿下 2026-09-20 说「同一个局面每次走子不一样」。引擎是确定性的，同一局面永远回同一
     * 着，于是每局开头都一模一样。开了 MultiPV 之后它会一并报出前几条线路，这里就在
     * 「跟最优着差不多好」的那几着里抽一个。差的上限按难度给：低档放得宽，既有花样也确实
     * 弱一些；高档收得紧，基本还是走最优着。
     *
     * 取不到候选就照用引擎给的 bestmove，绝不为了花样而走坏棋。
     */
    private fun pickWithSpread(lines: Map<Int, Pair<Int, String>>, fallback: String, spreadCp: Int): String {
        if (spreadCp <= 0 || lines.isEmpty()) return fallback
        val topScore = lines.values.maxOf { it.first }
        val pool = lines.values.filter { it.first >= topScore - spreadCp }.map { it.second }.distinct()
        return if (pool.isEmpty()) fallback else pool.random()
    }

    private fun send(cmd: String) {
        writer?.apply { write(cmd); write("\n"); flush() }
    }

    private fun awaitToken(token: String, timeoutMs: Long, inspect: (String) -> Unit = {}): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            val line = output?.readUntil(deadline) ?: return false
            inspect(line)
            if (line.trim() == token) return true
        }
        return false
    }

    companion object {
        private const val TAG = "FairyStockfishEngine"
        private const val BIN_NAME = "libfairystockfish.so"
        private const val HANDSHAKE_TIMEOUT_MS = 20_000L
        private const val SEARCH_GRACE_MS = 8_000L
    }
}
