package com.yingwang.watchchess.ai

import com.yingwang.watchchess.model.Board
import com.yingwang.watchchess.model.Piece
import com.yingwang.watchchess.model.PieceColor
import com.yingwang.watchchess.model.PieceType
import com.yingwang.watchchess.model.Position
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 拿 Fairy-Stockfish 当标准答案，逐局面核对 Board.getAllLegalMoves 有没有漏着法。
 *
 * 起因：殿下 2026-09-20 说「有时候明明好几个子能动，转表冠却只停在一个子上」。
 * 表冠的候选完全来自 getAllLegalMoves，所以要么是这个函数漏了，要么是她看错了。
 * 与其猜，不如让引擎来判：同一个局面，我们生成的着法集合必须跟引擎的 perft 1 一字不差。
 *
 * 挑的都是容易写错的局面：被将、将帅照面、马腿被别、象眼被塞、炮要隔子。
 * 引擎不在时这个测试自动跳过，不拖累别人的构建。
 */
class MoveGenCrossCheckTest {

    // 这里要的是能在开发机上跑的那一份。src/stockfish 会被交叉编译覆盖成表上用的
    // arm64 版本，在 macOS 上一启动就退出，报 Stream closed，所以另存一个宿主机版本。
    // 生成：cd ~/claude/fairy-stockfish/src && make build ARCH=x86-64-modern largeboards=yes nnue=no
    //      然后 mv stockfish ../stockfish-host
    private val engine = File(System.getProperty("user.home"), "claude/fairy-stockfish/stockfish-host")

    private fun p(t: PieceType, c: PieceColor, row: Int, col: Int) = Piece(t, c, Position(row, col))

    /** 引擎列出的合法着法。ucicyclone 那句让纵坐标用 0 到 9，跟本项目一致。 */
    private fun engineMoves(fen: String): Set<String> {
        val proc = ProcessBuilder(engine.absolutePath).redirectErrorStream(true).start()
        proc.outputStream.bufferedWriter().use { w ->
            w.write(
                "ucicyclone\nuci\nsetoption name Use NNUE value false\n" +
                    "setoption name UCI_Variant value xiangqi\n" +
                    "position fen $fen\ngo perft 1\nquit\n",
            )
        }
        val out = proc.inputStream.bufferedReader().readText()
        proc.waitFor(60, TimeUnit.SECONDS)
        return out.lineSequence()
            .mapNotNull { line ->
                val head = line.substringBefore(':').trim()
                if (line.contains(':') && head.length in 4..5 &&
                    head[0] in 'a'..'i' && head[1] in '0'..'9'
                ) head else null
            }
            .toSet()
    }

    private fun check(name: String, board: Board) {
        val fen = board.toFen()
        val theirs = engineMoves(fen)
        val ours = board.getAllLegalMoves().map { it.toUci() }.toSet()
        assertEquals("$name 对不上\nFEN: $fen", theirs.sorted(), ours.sorted())
    }

    @Test
    fun `opening position matches the engine`() {
        assumeTrue("engine not built", engine.canExecute())
        check("开局", Board.createInitialBoard())
    }

    @Test
    fun `in check with several escapes offers every one of them`() {
        assumeTrue("engine not built", engine.canExecute())
        // 这一条正对殿下说的那个现象：被将的时候到底还有几个子能动。
        // 引擎在这个局面给出三着，分属两个子：马可以垫在 f0 或 h0，帅可以上到 e1。
        // 如果我们只生成一着，那光标只停在一个子上就是真漏了。
        check(
            "被将但有三条活路",
            Board.createFromPieces(
                listOf(
                    p(PieceType.GENERAL, PieceColor.BLACK, 0, 3),
                    p(PieceType.GENERAL, PieceColor.RED, 9, 4),
                    p(PieceType.CHARIOT, PieceColor.BLACK, 9, 8),
                    p(PieceType.HORSE, PieceColor.RED, 7, 6),
                ),
                PieceColor.RED,
            ),
        )
    }

    @Test
    fun `checkmate offers nothing`() {
        assumeTrue("engine not built", engine.canExecute())
        // 真的被将死时两边都该是空的。空集合也要对上，否则界面会卡在一个走不了的子上。
        check(
            "将死",
            Board.createFromPieces(
                listOf(
                    p(PieceType.ADVISOR, PieceColor.BLACK, 0, 3),
                    p(PieceType.GENERAL, PieceColor.BLACK, 1, 4),
                    p(PieceType.GENERAL, PieceColor.RED, 9, 4),
                    p(PieceType.ADVISOR, PieceColor.RED, 8, 3),
                    p(PieceType.ADVISOR, PieceColor.RED, 8, 4),
                    p(PieceType.CHARIOT, PieceColor.BLACK, 9, 8),
                ),
                PieceColor.RED,
            ),
        )
    }

    @Test
    fun `flying general rule constrains the king`() {
        assumeTrue("engine not built", engine.canExecute())
        // 两将同在一条竖线上、中间无子，红帅不能走到让两将照面的位置
        check(
            "将帅照面",
            Board.createFromPieces(
                listOf(
                    p(PieceType.GENERAL, PieceColor.BLACK, 0, 4),
                    p(PieceType.GENERAL, PieceColor.RED, 9, 4),
                ),
                PieceColor.RED,
            ),
        )
    }

    @Test
    fun `horse leg is blocked`() {
        assumeTrue("engine not built", engine.canExecute())
        check(
            "马被别腿",
            Board.createFromPieces(
                listOf(
                    p(PieceType.GENERAL, PieceColor.BLACK, 0, 4),
                    p(PieceType.GENERAL, PieceColor.RED, 9, 4),
                    p(PieceType.HORSE, PieceColor.RED, 8, 4),
                    p(PieceType.SOLDIER, PieceColor.RED, 7, 4),
                ),
                PieceColor.RED,
            ),
        )
    }

    @Test
    fun `cannon needs a screen to capture`() {
        assumeTrue("engine not built", engine.canExecute())
        check(
            "炮需隔子",
            Board.createFromPieces(
                listOf(
                    p(PieceType.GENERAL, PieceColor.BLACK, 0, 4),
                    p(PieceType.GENERAL, PieceColor.RED, 9, 3),
                    p(PieceType.CANNON, PieceColor.RED, 7, 4),
                    p(PieceType.SOLDIER, PieceColor.BLACK, 4, 4),
                ),
                PieceColor.RED,
            ),
        )
    }

    @Test
    fun `a crowded middlegame matches the engine`() {
        assumeTrue("engine not built", engine.canExecute())
        check(
            "中局",
            Board.createFromPieces(
                listOf(
                    p(PieceType.GENERAL, PieceColor.BLACK, 0, 4),
                    p(PieceType.ADVISOR, PieceColor.BLACK, 0, 3),
                    p(PieceType.ELEPHANT, PieceColor.BLACK, 0, 2),
                    p(PieceType.HORSE, PieceColor.BLACK, 2, 2),
                    p(PieceType.CANNON, PieceColor.BLACK, 2, 4),
                    p(PieceType.SOLDIER, PieceColor.BLACK, 3, 0),
                    p(PieceType.SOLDIER, PieceColor.BLACK, 3, 4),
                    p(PieceType.GENERAL, PieceColor.RED, 9, 4),
                    p(PieceType.ADVISOR, PieceColor.RED, 9, 3),
                    p(PieceType.CHARIOT, PieceColor.RED, 9, 0),
                    p(PieceType.HORSE, PieceColor.RED, 7, 6),
                    p(PieceType.CANNON, PieceColor.RED, 7, 1),
                    p(PieceType.CANNON, PieceColor.RED, 7, 4),
                    p(PieceType.SOLDIER, PieceColor.RED, 6, 0),
                    p(PieceType.SOLDIER, PieceColor.RED, 6, 4),
                ),
                PieceColor.RED,
            ),
        )
    }
}
