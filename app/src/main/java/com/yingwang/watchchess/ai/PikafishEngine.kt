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

/**
 * 把皮卡鱼当作一个外部进程使唤，走 UCI 协议。
 *
 * 这只表有个特别之处：硬件是六十四位，可安卓那一层是三十二位的，abilist64 是空的，
 * 系统里既没有 linker64 也没有 /system/lib64。所以引擎必须编成完全静态的 arm64
 * 可执行文件，不依赖系统里任何六十四位的库。应用自身仍是三十二位进程，把它当子进程
 * 叫起来即可，内核对父子进程的位数并不挑剔。
 *
 * 可执行文件只能放在应用的原生库目录里。安卓从 API 29 起不准从应用可写的数据目录里
 * 执行文件，而 lib 目录不受这条限制，所以二进制以 libpikafish.so 的名义打包进
 * jniLibs，靠 extractNativeLibs 在安装时释放出来。
 *
 * 网络文件只需要读，不需要执行权限，因此放在 assets 里，首次运行时拷到 filesDir。
 */
class PikafishEngine(private val context: Context) {

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null

    var lastError: String? = null
        private set

    val isRunning: Boolean
        get() = process?.isAlive == true

    // ── 启停 ────────────────────────────────────────────────────────────────

    /** 启动并初始化引擎。失败时返回 false 并把原因记在 lastError 里，调用方应回落到内置引擎。 */
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext true
        try {
            val bin = File(context.applicationInfo.nativeLibraryDir, BIN_NAME)
            if (!bin.exists()) {
                lastError = "engine binary not found at ${bin.absolutePath}"
                return@withContext false
            }
            val net = ensureNetwork() ?: run {
                lastError = "network file could not be unpacked"
                return@withContext false
            }

            val p = ProcessBuilder(bin.absolutePath)
                .directory(context.filesDir)
                .redirectErrorStream(true)
                .start()
            process = p
            writer = BufferedWriter(OutputStreamWriter(p.outputStream))
            reader = BufferedReader(InputStreamReader(p.inputStream))

            send("uci")
            if (!awaitToken("uciok", HANDSHAKE_TIMEOUT_MS)) {
                lastError = "no uciok from engine"
                stop()
                return@withContext false
            }

            // 这只表的内核没有 NUMA 那套 sysfs，/sys/devices/system/node 根本不存在，
            // 引擎自动探测处理器时会得到一个空集合，于是线程建起来却不干活，搜索返回零个
            // 结点。把策略关掉它就正常了。这一条是必须的，不是调优。
            send("setoption name NumaPolicy value none")
            send("setoption name EvalFile value ${net.absolutePath}")
            send("setoption name Threads value 1")
            send("setoption name Hash value $HASH_MB")
            send("isready")
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
        try { writer?.apply { write("quit\n"); flush() } } catch (_: Exception) {}
        try { process?.destroy() } catch (_: Exception) {}
        process = null; writer = null; reader = null
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
    ): Move? = withContext(Dispatchers.IO) {
        if (!isRunning) return@withContext null
        try {
            val moves = moveHistory.joinToString(" ") { it.toUci() }
            send(if (moves.isEmpty()) "position startpos" else "position startpos moves $moves")
            send("go nodes $nodeLimit movetime $timeLimitMs")

            val deadline = System.currentTimeMillis() + timeLimitMs + SEARCH_GRACE_MS
            var best: String? = null
            while (System.currentTimeMillis() < deadline) {
                val line = reader?.readLine() ?: break
                if (line.startsWith("bestmove")) {
                    best = line.split(" ").getOrNull(1)
                    break
                }
            }
            if (best == null || best == "(none)" || best.length < 4) return@withContext null
            uciToMove(board, best)
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            Log.w(TAG, "findBestMove failed", e)
            null
        }
    }

    // ── 私有 ────────────────────────────────────────────────────────────────

    private fun send(cmd: String) {
        writer?.apply { write(cmd); write("\n"); flush() }
    }

    private fun awaitToken(token: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val line = reader?.readLine() ?: return false
            if (line.trim() == token) return true
        }
        return false
    }

    /** 把网络文件从 assets 拷到 filesDir，已经拷过且大小一致就不重复拷。 */
    private fun ensureNetwork(): File? {
        val target = File(context.filesDir, NET_NAME)
        return try {
            val expected = context.assets.openFd(NET_NAME).use { it.length }
            if (target.exists() && target.length() == expected) return target
            context.assets.open(NET_NAME).use { input ->
                target.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
            }
            target
        } catch (e: Exception) {
            Log.w(TAG, "ensureNetwork failed", e)
            // openFd 对压缩过的 assets 会抛异常，退回到直接拷贝
            try {
                context.assets.open(NET_NAME).use { input ->
                    target.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
                }
                target
            } catch (e2: Exception) {
                Log.w(TAG, "ensureNetwork fallback failed", e2)
                null
            }
        }
    }

    companion object {
        private const val TAG = "PikafishEngine"
        private const val BIN_NAME = "libpikafish.so"
        private const val NET_NAME = "pikafish.nnue"
        private const val HASH_MB = 32
        private const val HANDSHAKE_TIMEOUT_MS = 20_000L
        private const val SEARCH_GRACE_MS = 8_000L
    }
}
