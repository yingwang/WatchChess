package com.yingwang.watchchess.ui

import android.content.Context
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import com.yingwang.watchchess.R
import com.yingwang.watchchess.ai.ChessAI
import com.yingwang.watchchess.ai.FairyEngine
import com.yingwang.watchchess.ai.GameVerdict
import com.yingwang.watchchess.ai.toFen
import com.yingwang.watchchess.ai.toUci
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.yingwang.watchchess.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.min

// ── Colors ─────────────────────────────────────────────────────────────────

private val BoardBg = Color(0xFFD4A960)
private val GridColor = Color(0xFF3D2B1F)
private val RedPiece = Color(0xFFCC2222)
private val BlackPiece = Color(0xFF1A1A1A)
private val SelectedRing = Color(0xFF00CC44)
private val LegalDot = Color(0x8800CC44)
private val OpponentMoveColor = Color(0xFF1B6BFF)
private val OwnMoveColor = Color(0xFF17A34A)
private val CursorRing = Color(0xFF00FF66)

// 表冠：累计到这个像素量算走一格。数值越大越钝，转同样的角度走的格子越少。
// 2026-09-20 殿下试了两轮都说偏快，先从 45 调到 68，仍嫌敏感，再调到 105。
private const val ROTARY_STEP = 105f

// ── Difficulty ─────────────────────────────────────────────────────────────

/**
 * 一档难度要同时配两套参数，因为下棋的可能是两个引擎。
 *
 * Fairy-Stockfish 那边用结点数封顶。结点数与机器快慢无关，同一档在哪台机器上都是同样
 * 的棋力，时间上限只是兜底，免得一步棋想太久把表烤热。内置那个 Kotlin 引擎则仍按深度与
 * 时限走，它只在外部引擎起不来的时候顶上。
 */
private data class Difficulty(
    /** 档位名要跟着系统语言走，所以存资源 id 不存字面量。 */
    val nameRes: Int,
    val depth: Int,
    val timeMs: Long,
    val nodes: Long,
    /**
     * 挑着法时容许比最优着差多少（百分兵）。
     * 主要是给「同一局面换一着」留出候选，不是拿来削弱棋力的，所以给得很窄。
     * 真要让入门那档更好赢，往上调这个数就是，那是现成的旋钮。
     */
    val spreadCp: Int,
)

// 这四组数字是在表上量出来的，不是估的。中局局面下实测：
//   五万结点  用时 1.4 秒  搜到第 11 层
//   二十万结点 用时 5.9 秒  搜到第 16 层
//   四十二万   用时 11.9 秒 仍是第 16 层
// 可见两件事。一是原先给高级设的三百万结点从来没跑到过，十二秒只够四十二万，封顶的
// 一直是时间不是结点；二是中级与高级都停在第 16 层，多花的六秒一层都没多搜，这两档
// 其实一样强。2026-09-20 殿下也说偏慢。
// 所以改成让结点数真正生效，时间只当兜底，四档按大约三到四倍递进，每档差两层上下。
//
// 2026-09-20 殿下说高级等得不够久、还能更难，于是又量了一轮，同一个中局局面：
//   二十五万结点  五点九秒  第 13 层   （原来的高级）
//   六十万结点    十五秒    第 15 层
//   一百二十万    二十五秒  第 15 层   （多花十秒，一层没多搜）
// 所以高级定在六十万、十五秒：多等九秒换两层，再往上就不划算了。
private val DIFFICULTIES = listOf(
    Difficulty(R.string.level_beginner, depth = 3, timeMs = 800, nodes = 2_000, spreadCp = 60),
    Difficulty(R.string.level_easy, depth = 5, timeMs = 1500, nodes = 20_000, spreadCp = 30),
    Difficulty(R.string.level_medium, depth = 6, timeMs = 3000, nodes = 80_000, spreadCp = 15),
    Difficulty(R.string.level_hard, depth = 8, timeMs = 15000, nodes = 600_000, spreadCp = 8),
)

private data class Snapshot(val board: Board, val move: Move)
/** 结局文案带的是资源 id 不是成品字串，好让它跟着系统语言走。 */
private data class TurnResult(val move: Move?, val messageRes: Int?)

// ── Helpers ────────────────────────────────────────────────────────────────

/**
 * 一格多大。
 *
 * 圆屏上真正的限制不是棋盘的外框，而是四个角上的车会不会被圆边切掉。角上棋子的中心
 * 离盘心是 sqrt(4^2 + 4.5^2) 约 6.02 格，棋子本身半径 0.43 格，所以只要
 * (6.02 + 0.43) * 格宽 不超过半径，就一个子都不会缺。426 像素的屏幕半径 213，
 * 算下来格宽上限约 33 像素，对应除数 12.9。取 13.0 留一点余量。
 * 原先是 14.2，盘子偏小，四周空了一圈，殿下 2026-09-20 说「边上还有一点点空隙，
 * 再铺满一点」。现在一格从 30 像素涨到约 32.8，整盘大了一成。
 */
private fun cellForRound(w: Float, h: Float): Float {
    val d = min(w, h)
    return d / 13.0f
}

private fun vibrate(context: Context, ms: Long = 30) {
    val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
}

private fun vibrateDouble(context: Context) {
    val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40), -1))
}

// ── Sound ──────────────────────────────────────────────────────────────────

private class GameSounds(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val moveId = pool.load(context, R.raw.move_piece, 1)
    private val captureId = pool.load(context, R.raw.capture_piece, 1)
    // 报子音。单字「将」在普通话里有两读，合成出来听着含糊，所以报的是「将军」，
    // 也是棋盘上真正会喊的那一句。
    private val sayCaptureId = pool.load(context, R.raw.say_capture, 1)
    private val sayCheckId = pool.load(context, R.raw.say_check, 1)
    private val bgm = android.media.MediaPlayer.create(context, R.raw.background_music)?.apply {
        isLooping = true; setVolume(0.15f, 0.15f)
    }

    var sfxOn = true

    // 背景音乐默认开着。2026-09-20 先按殿下的话关成默认不开，当天她改了主意说还是
    // 默认开，所以改回来。菜单里开关仍在，随时可关。
    var bgmOn = true

    fun startBgm() { if (bgmOn) bgm?.start() }
    fun stopBgm() { bgm?.pause() }
    fun toggleBgm(): Boolean { bgmOn = !bgmOn; if (bgmOn) bgm?.start() else bgm?.pause(); return bgmOn }
    fun toggleSfx(): Boolean { sfxOn = !sfxOn; return sfxOn }
    fun playMove() { if (sfxOn) pool.play(moveId, 0.6f, 0.6f, 1, 0, 1f) }
    fun playCapture() { if (sfxOn) pool.play(captureId, 0.8f, 0.8f, 1, 0, 1f) }
    fun sayCapture() { if (sfxOn) pool.play(sayCaptureId, 1f, 1f, 2, 0, 1f) }
    fun sayCheck() { if (sfxOn) pool.play(sayCheckId, 1f, 1f, 2, 0, 1f) }
    fun release() { bgm?.release(); pool.release() }
}

// ── Root ───────────────────────────────────────────────────────────────────

@Composable
fun GameScreen() {
    var screen by remember { mutableStateOf("menu") } // menu | game
    var diffIdx by remember { mutableIntStateOf(1) }
    // 人执哪一方。象棋红先，所以执黑就是后手，开局要先让引擎走一步。
    var playerColor by remember { mutableStateOf(PieceColor.RED) }
    // startGame 里没法直接叫 aiMove（它定义在后面），用一个计数触发。
    var aiMoveTrigger by remember { mutableIntStateOf(0) }

    var board by remember { mutableStateOf(Board.createInitialBoard()) }
    var selectedPos by remember { mutableStateOf<Position?>(null) }
    var legalMoves by remember { mutableStateOf<List<Move>>(emptyList()) }
    var lastMove by remember { mutableStateOf<Move?>(null) }
    var moveHistory by remember { mutableStateOf(listOf<Move>()) }
    var undoStack by remember { mutableStateOf(listOf<Snapshot>()) }
    var aiThinking by remember { mutableStateOf(false) }
    // 存资源 id，画的时候才解析成当前语言的字。
    var gameOverMsg by remember { mutableStateOf<Int?>(null) }
    var gameStartTime by remember { mutableLongStateOf(0L) }
    var elapsedSec by remember { mutableIntStateOf(0) }
    var moveCount by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var bgmOn by remember { mutableStateOf(true) }
    var sfxOn by remember { mutableStateOf(true) }
    var cursorIdx by remember { mutableIntStateOf(0) }
    // 这一局里每个局面走过哪些着法。键是局面本身，值是从这个局面走出去过的着法。
    // 引擎是死的，同一个局面会照原样再走一遍，两边来回推就卡住了，所以记下来让它换一着。
    // 只在本局有效，开新局清空。
    val playedFrom = remember { mutableMapOf<String, MutableSet<String>>() }
    // 每走一步之后的局面，跟着法历史一一对应，所以悔棋时一起回退，不会数错。
    // 仅记录盘面；裁决用引擎收到的完整 moveHistory，不以重复次数直接判和。
    var positionHistory by remember { mutableStateOf(listOf<String>()) }
    var rotaryAcc by remember { mutableFloatStateOf(0f) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val searches = remember { GameSearchSession() }
    val sounds = remember { GameSounds(context) }
    var ai by remember { mutableStateOf(ChessAI(maxDepth = 3, timeLimit = 2000, quiescenceDepth = 2)) }

    // 引擎跑在一个外部进程里。它起不来的情形是有的（包里没带、系统不让执行），
    // 所以内置那个 Kotlin 引擎一直留着顶班，绝不让棋下不下去。
    //
    // 菜单中保留预热进程；取消正在进行的搜索时回收进程，下次求着自动重启。
    val engine = remember { FairyEngine(context) }
    var engineReady by remember { mutableStateOf(false) }
    // 应用一打开就先把引擎热起来，别等到开局才启动。它启动加握手要一秒七，摆在开局
    // 那一刻就正好压在第一步棋上，殿下 2026-09-20 说的「感觉有点偏慢」多半是这一下。
    // 挑难度那几秒足够它准备好。现在它常驻也就八十多兆，表上还有五百多兆余量，扛得住。
    LaunchedEffect(Unit) {
        searches.withEngine { engineReady = engine.start() }
        if (!engineReady) Log.w("WatchChess", "engine unavailable: ${engine.lastError}")
    }

    DisposableEffect(Unit) { onDispose { searches.invalidate(); sounds.release(); engine.stop() } }

    // Timer
    LaunchedEffect(screen, gameOverMsg) {
        if (screen == "game" && gameOverMsg == null) {
            while (true) { delay(1000); if (gameStartTime > 0) elapsedSec = ((System.currentTimeMillis() - gameStartTime) / 1000).toInt() }
        }
    }

    fun startGame(difficulty: Int, side: PieceColor) {
        searches.invalidate()
        diffIdx = difficulty
        playerColor = side
        val d = DIFFICULTIES[difficulty]
        val qDepth = if (d.depth >= 6) 3 else 2
        ai = ChessAI(maxDepth = d.depth, timeLimit = d.timeMs, quiescenceDepth = qDepth)
        board = Board.createInitialBoard()
        selectedPos = null; legalMoves = emptyList(); lastMove = null
        moveHistory = emptyList(); undoStack = emptyList()
        aiThinking = false; gameOverMsg = null; showMenu = false
        playedFrom.clear(); positionHistory = listOf(board.toFen())
        gameStartTime = System.currentTimeMillis(); elapsedSec = 0; moveCount = 0
        screen = "game"; sounds.startBgm()
        // 象棋红先。人执黑就是后手，开局得先让引擎替红走一步。
        if (side == PieceColor.BLACK) aiMoveTrigger++
    }

    /** 将死和困毙可立即判断；重复须交给引擎检查完整棋谱及长将、长捉责任。 */
    fun checkGameOver(b: Board): Int? {
        b.noLegalMoveWinner()?.let {
            return if (it == PieceColor.RED) R.string.result_red_wins else R.string.result_black_wins
        }
        return null
    }

    fun aiMove() {
        if (gameOverMsg != null || aiThinking || screen != "game") return
        aiThinking = true
        val searchBoard = board
        val searchHistory = moveHistory.toList()
        val fallbackAi = ai
        val d = DIFFICULTIES[diffIdx]
        val positionKey = searchBoard.toFen()
        val alreadyPlayed = playedFrom[positionKey].orEmpty().toSet()
        searches.search(scope, compute = {
            // 取消旧搜索会关闭旧进程；下一次调用在同一把锁内重新启动，避免协议串线。
            if (!engine.isRunning) engineReady = engine.start()
            val before = if (engineReady) engine.adjudicate(searchHistory) else GameVerdict.UNAVAILABLE
            if (before != GameVerdict.ONGOING) return@search TurnResult(null, before.messageRes)
            val move = if (engineReady) {
                engine.findBestMove(
                    searchBoard, searchHistory, d.nodes, d.timeMs,
                    d.spreadCp,
                    alreadyPlayed,
                )
                    // 引擎中途死了就当场退回内置的，这一步棋照样走得出来
                    ?: withContext(Dispatchers.Default) { fallbackAi.findBestMove(searchBoard, searchHistory) }
            } else {
                withContext(Dispatchers.Default) { fallbackAi.findBestMove(searchBoard, searchHistory) }
            }
            if (move == null) return@search TurnResult(null, R.string.engine_no_move)
            if (!engine.isRunning) engineReady = engine.start()
            val after = if (engineReady) engine.adjudicate(searchHistory + move) else GameVerdict.UNAVAILABLE
            TurnResult(move, after.messageRes)
        }, applyResult = { result ->
            val move = result.move
            if (move == null && screen == "game" && board === searchBoard) gameOverMsg = result.messageRes
            if (move != null && screen == "game" && board === searchBoard) {
                playedFrom.getOrPut(positionKey) { mutableSetOf() }.add(move.toUci())
                undoStack = undoStack + Snapshot(board, move)
                val nb = board.makeMove(move); nb.currentPlayer = board.currentPlayer.opposite()
                board = nb; lastMove = move; moveHistory = moveHistory + move
                positionHistory = positionHistory + nb.toFen()
                if (move.isCapture()) { sounds.playCapture(); sounds.sayCapture(); vibrateDouble(context) }
                else { sounds.playMove(); vibrate(context) }
                gameOverMsg = checkGameOver(board) ?: result.messageRes
                if (gameOverMsg != null) vibrate(context, 100)
                else if (board.isInCheck(board.currentPlayer)) { sounds.sayCheck(); vibrateDouble(context) }
            }
        }, finished = { aiThinking = false })
    }

    fun returnToMenu() {
        searches.invalidate()
        aiThinking = false
        showMenu = false
        sounds.stopBgm()
        screen = "menu"
    }

    fun undo() {
        if (aiThinking || undoStack.size < 2) return
        val playerSnap = undoStack[undoStack.size - 2]
        board = playerSnap.board; board.currentPlayer = playerColor
        undoStack = undoStack.dropLast(2); moveHistory = moveHistory.dropLast(2)
        positionHistory = positionHistory.dropLast(2)
        lastMove = moveHistory.lastOrNull(); selectedPos = null; legalMoves = emptyList()
        moveCount--; vibrate(context, 20); showMenu = false
    }

    fun onTap(pos: Position) {
        if (aiThinking || gameOverMsg != null) return
        val moveToMake = legalMoves.find { it.to == pos }
        if (moveToMake != null) {
            undoStack = undoStack + Snapshot(board, moveToMake)
            val nb = board.makeMove(moveToMake); nb.currentPlayer = board.currentPlayer.opposite()
            board = nb; lastMove = moveToMake; moveHistory = moveHistory + moveToMake
            positionHistory = positionHistory + nb.toFen()
            selectedPos = null; legalMoves = emptyList(); moveCount++
            if (moveToMake.isCapture()) { sounds.playCapture(); sounds.sayCapture(); vibrateDouble(context) }
            else { sounds.playMove(); vibrate(context) }
            gameOverMsg = checkGameOver(board)
            if (gameOverMsg != null) { vibrate(context, 100) } else {
                if (board.isInCheck(board.currentPlayer)) sounds.sayCheck()
                aiMove()
            }
            return
        }
        val piece = board.getPiece(pos)
        if (piece != null && piece.color == board.currentPlayer) {
            selectedPos = pos; legalMoves = board.getAllLegalMoves().filter { it.from == pos }; vibrate(context, 15)
        } else { selectedPos = null; legalMoves = emptyList() }
    }

    // 执黑开局时让引擎先走一步。startGame 里叫不到 aiMove（它定义在后面），
    // 所以那边只把计数加一，由这个效应接住。
    LaunchedEffect(aiMoveTrigger) {
        if (aiMoveTrigger > 0 && screen == "game" && board.currentPlayer != playerColor) aiMove()
    }

    // ── 表冠光标 ───────────────────────────────────────────────────────────
    // 走一步棋分两段，两段都靠转表冠挑、点屏幕定。
    // 第一段在「有合法着法的己方棋子」之间跳，用棋盘从上到下、从左到右的固定顺序，
    // 固定顺序手才记得住要转多远。第二段在选中那只子的合法落点之间跳，按绕着它
    // 转一圈的角度排，转起来像围着它看一遍。
    // 因为两段的候选都只含合法项，这套操作里走不出一步违规的棋，也就不需要报错。
    val movablePieces = remember(board, gameOverMsg, aiThinking, playerColor) {
        if (gameOverMsg != null || aiThinking || board.currentPlayer != playerColor) emptyList()
        else board.getAllLegalMoves().map { it.from }.distinct()
            // 按屏幕上看到的顺序排，不是按棋盘内部坐标排。执黑时整盘是翻过来画的，
            // 若仍按内部坐标排，转表冠会觉得光标在乱跳。
            .sortedWith(
                compareBy(
                    { flipRow(it.row, playerColor == PieceColor.BLACK) },
                    { flipCol(it.col, playerColor == PieceColor.BLACK) },
                ),
            )
    }
    val destinations = remember(selectedPos, legalMoves) {
        val from = selectedPos
        if (from == null) emptyList()
        else legalMoves.map { it.to }
            .sortedBy { atan2((it.row - from.row).toFloat(), (it.col - from.col).toFloat()) }
    }
    // 被吃的子直接从着法历史里推，不另存一份状态，这样悔棋的时候它自然跟着回退，
    // 不会出现棋子回到盘上、侧边却还挂着的情形。
    val capturedPieces = remember(moveHistory) { moveHistory.mapNotNull { it.capturedPiece } }

    val candidates = if (selectedPos == null) movablePieces else destinations
    val cursorPos = candidates.getOrNull(cursorIdx.coerceIn(0, maxOf(0, candidates.size - 1)))

    // 换了一段（选中、取消、落子、悔棋）就把光标拨回头一个
    LaunchedEffect(selectedPos, board) { cursorIdx = 0; rotaryAcc = 0f }

    fun onRotary(px: Float): Boolean {
        if (aiThinking || gameOverMsg != null || showMenu) return false
        val n = candidates.size
        if (n == 0) return false
        rotaryAcc += px
        var stepped = false
        while (rotaryAcc >= ROTARY_STEP) { rotaryAcc -= ROTARY_STEP; cursorIdx = (cursorIdx + 1) % n; stepped = true }
        while (rotaryAcc <= -ROTARY_STEP) { rotaryAcc += ROTARY_STEP; cursorIdx = (cursorIdx - 1 + n) % n; stepped = true }
        if (stepped) vibrate(context, 10)
        return true
    }

    // 点屏幕任意一处等于确认当前亮着的那个。两段共用这一条规矩，不必记两套。
    fun onConfirm() {
        if (aiThinking || gameOverMsg != null) return
        onTap(cursorPos ?: return)
    }

    // 右滑返回等于取消选中，退回挑子那一段
    BackHandler(enabled = screen == "game" && (showMenu || selectedPos != null)) {
        if (showMenu) showMenu = false
        else { selectedPos = null; legalMoves = emptyList(); cursorIdx = 0; vibrate(context, 15) }
    }

    if (screen == "menu") {
        MainMenu(
            onStart = { idx, side -> startGame(idx, side) },
            selectedIdx = diffIdx,
            selectedSide = playerColor,
        )
    } else {
        Box(Modifier.fillMaxSize()) {
            // Board layer
            BoardCanvas(
                board = board, selectedPos = selectedPos, legalMoves = legalMoves,
                lastMove = lastMove, aiThinking = aiThinking,
                gameOverMsg = gameOverMsg?.let { stringResource(it) },
                cursorPos = cursorPos, pickingDestination = selectedPos != null,
                menuShowing = showMenu,
                captured = capturedPieces,
                flipped = playerColor == PieceColor.BLACK,
                playerColor = playerColor,
                diffName = stringResource(DIFFICULTIES[diffIdx].nameRes),
                thinkingLabel = stringResource(R.string.thinking, stringResource(DIFFICULTIES[diffIdx].nameRes)),
                tapToReturn = stringResource(R.string.tap_to_return),
                elapsedSec = elapsedSec,
                onConfirm = { onConfirm() },
                onRotary = { onRotary(it) },
                onLongPress = { showMenu = !showMenu },
                onGameOverTap = { returnToMenu() },
            )
            // Menu overlay
            if (showMenu) {
                InGameMenu(
                    diffName = stringResource(DIFFICULTIES[diffIdx].nameRes),
                    elapsedSec = elapsedSec,
                    moveCount = moveCount,
                    canUndo = undoStack.size >= 2,
                    bgmOn = bgmOn,
                    sfxOn = sfxOn,
                    onToggleBgm = { bgmOn = sounds.toggleBgm() },
                    onToggleSfx = { sfxOn = sounds.toggleSfx() },
                    onUndo = { undo() },
                    onNewGame = { returnToMenu() },
                    onDismiss = { showMenu = false },
                )
            }
        }
    }
}

// ── Main Menu ──────────────────────────────────────────────────────────────

/**
 * 开局这一屏：先挑难度，执哪一方摆在下面。
 *
 * 殿下 2026-09-20 问过先选难度还是先选红黑，她自己也倾向先选难度，我同意。
 * 难度是每一局都可能改的东西，执哪一方多半定下来就不动了，所以常改的放前面，
 * 点难度那一下直接开局，不必为了一个很少改的选项多走一屏。
 *
 * 红黑那一行做成两个并排的按钮，选中的那个亮起来，不占一整屏。
 * 这一屏跟局内菜单一样能滚、能用表冠滚，因为四个难度加一行红黑已经超过圆屏的高度。
 */
@Composable
private fun MainMenu(
    onStart: (Int, PieceColor) -> Unit,
    selectedIdx: Int,
    selectedSide: PieceColor,
) {
    var side by remember(selectedSide) { mutableStateOf(selectedSide) }
    val scrollState = rememberScrollState()
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        Modifier.fillMaxSize().background(Color(0xFF1A1208)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .onRotaryScrollEvent {
                    scope.launch { scrollState.scrollBy(it.verticalScrollPixels) }
                    true
                }
                .focusRequester(focus)
                .focusable()
                .verticalScroll(scrollState)
                .padding(horizontal = 30.dp, vertical = 12.dp),
        ) {
            // 不放标题。四个难度加一行红黑已经顶满这块圆屏，标题一摆，红黑那行就被挤到
            // 屏幕外面，得滚一下才看得见，而那是开局前就该一眼看到的东西。应用叫什么，
            // 表盘上点进来的时候已经看过了。
            DIFFICULTIES.forEachIndexed { idx, diff ->
                val label = stringResource(diff.nameRes)
                Button(
                    onClick = { onStart(idx, side) },
                    modifier = Modifier.fillMaxWidth().height(34.dp).padding(vertical = 2.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (idx == selectedIdx) Color(0xFFCC2222) else Color(0xFF3D2B1F),
                    ),
                    shape = RoundedCornerShape(6.dp),
                ) { Text(label, color = Color.White, fontSize = 13.sp) }
            }

            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                SideBtn(
                    label = stringResource(R.string.side_red),
                    selected = side == PieceColor.RED,
                    accent = RedPiece,
                    modifier = Modifier.weight(1f),
                ) { side = PieceColor.RED }
                Spacer(Modifier.width(6.dp))
                SideBtn(
                    label = stringResource(R.string.side_black),
                    selected = side == PieceColor.BLACK,
                    accent = Color(0xFF111111),
                    modifier = Modifier.weight(1f),
                ) { side = PieceColor.BLACK }
            }
        }
    }
}

@Composable
private fun SideBtn(
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(34.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (selected) accent else Color(0xFF2B1B0E),
        ),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            label,
            color = if (selected) Color.White else Color(0xFF8A7A5A),
            fontSize = 12.sp,
        )
    }
}

// ── In-Game Menu Overlay ───────────────────────────────────────────────────

@Composable
private fun InGameMenu(
    diffName: String,
    elapsedSec: Int,
    moveCount: Int,
    canUndo: Boolean,
    bgmOn: Boolean,
    sfxOn: Boolean,
    onToggleBgm: () -> Unit,
    onToggleSfx: () -> Unit,
    onUndo: () -> Unit,
    onNewGame: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 两行字加五个按钮竖着排，比这块圆屏高，最底下那个「继续」会被切掉，殿下
    // 2026-09-20 说「最下面那个选项没有显示全」。圆屏上下还要各让出一块，可用的高度
    // 比看上去更少。所以这里让它能滚，并且把表冠接上去滚，跟棋盘那边同一套手势。
    // 上下各留一段空白，好让首尾两项都能滚到屏幕中间，不至于卡在圆边上。
    val scrollState = rememberScrollState()
    val menuFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { menuFocus.requestFocus() }

    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)).clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .onRotaryScrollEvent {
                    scope.launch { scrollState.scrollBy(it.verticalScrollPixels) }
                    true
                }
                .focusRequester(menuFocus)
                .focusable()
                .verticalScroll(scrollState)
                .padding(horizontal = 40.dp, vertical = 16.dp),
        ) {
            val min = elapsedSec / 60; val sec = elapsedSec % 60
            Text(diffName, color = Color(0xFFD4A960), fontSize = 13.sp)
            Text(stringResource(R.string.clock_moves, min, sec, moveCount + 1), color = Color(0xAAFFFFFF), fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))

            MenuBtn(stringResource(R.string.menu_undo), canUndo, onUndo)
            MenuBtn(stringResource(R.string.menu_music, stringResource(if (bgmOn) R.string.on else R.string.off)), true, onToggleBgm)
            MenuBtn(stringResource(R.string.menu_sound, stringResource(if (sfxOn) R.string.on else R.string.off)), true, onToggleSfx)
            MenuBtn(stringResource(R.string.menu_new_game), true, onNewGame)

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(34.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF555555)),
                shape = RoundedCornerShape(6.dp),
            ) { Text(stringResource(R.string.menu_resume), color = Color.White, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun MenuBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(34.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3D2B1F)),
        shape = RoundedCornerShape(6.dp),
    ) { Text(label, color = if (enabled) Color.White else Color(0xFF666666), fontSize = 13.sp) }
}

// ── Board Canvas ───────────────────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BoardCanvas(
    board: Board,
    selectedPos: Position?,
    legalMoves: List<Move>,
    lastMove: Move?,
    aiThinking: Boolean,
    gameOverMsg: String?,
    cursorPos: Position?,
    pickingDestination: Boolean,
    menuShowing: Boolean,
    captured: List<Piece>,
    flipped: Boolean,
    playerColor: PieceColor,
    diffName: String,
    thinkingLabel: String,
    tapToReturn: String,
    elapsedSec: Int,
    onConfirm: () -> Unit,
    onRotary: (Float) -> Boolean,
    onLongPress: () -> Unit,
    onGameOverTap: () -> Unit,
) {
    // 表冠事件要有焦点才收得到。菜单打开时焦点会被菜单抢走，关掉之后必须抢回来，
    // 否则棋盘上的表冠就此失灵，而且只有开过一次菜单的人才会碰到，最难查。
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(menuShowing) { if (!menuShowing) focusRequester.requestFocus() }

    // pointerInput 的手势块只在 key 变化时重建，闭包会一直抓着旧的回调不放。
    // 光标每转一下都在变，若把它当 key，手势检测器就得跟着反复重建。这里改用
    // rememberUpdatedState 让手势块始终读到最新的回调，key 保持为 Unit。
    val confirm by rememberUpdatedState(onConfirm)
    val longPress by rememberUpdatedState(onLongPress)
    val gameOverTap by rememberUpdatedState(onGameOverTap)
    val overMsg by rememberUpdatedState(gameOverMsg)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2B1B0E))
            .onRotaryScrollEvent { onRotary(it.verticalScrollPixels) }
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(
                    // 点屏幕任意一处就是确认当前亮着的那个，位置不参与判断，
                    // 所以不存在「点到了别的子」这种事，也不需要瞄准。
                    onTap = { if (overMsg != null) gameOverTap() else confirm() },
                    onLongPress = { longPress() },
                )
            },
    ) {
        val cell = cellForRound(size.width, size.height)
        val bw = cell * 8f; val bh = cell * 9f
        val ox = (size.width - bw) / 2f; val oy = (size.height - bh) / 2f

        drawBoard(ox, oy, cell)
        drawSelection(ox, oy, cell, selectedPos, flipped)
        drawLegalMoves(ox, oy, cell, legalMoves, flipped)
        drawPieces(ox, oy, cell, board, flipped)
        drawCaptured(ox, cell, captured)
        // 标出刚走的那一步，画在棋子上面才看得见。对方是蓝的，自己是绿的。
        lastMove?.let {
            drawMoveMarker(ox, oy, cell, it,
                if (it.piece.color == playerColor) OwnMoveColor else OpponentMoveColor, flipped)
        }
        drawCursor(ox, oy, cell, cursorPos, pickingDestination, flipped)

        if (board.isInCheck(board.currentPlayer) && gameOverMsg == null)
            drawCheckGlow(ox, oy, cell, board, flipped)

        drawStatusLines(cell, diffName, thinkingLabel, elapsedSec, aiThinking)

        if (gameOverMsg != null) drawGameOver(gameOverMsg, tapToReturn, cell)
    }
}

// ── Drawing helpers ────────────────────────────────────────────────────────

private fun DrawScope.drawBoard(ox: Float, oy: Float, c: Float) {
    drawRect(BoardBg, Offset(ox - c * 0.48f, oy - c * 0.48f), Size(c * 8 + c * 0.96f, c * 9 + c * 0.96f))
    for (r in 0..9) drawLine(GridColor, Offset(ox, oy + r * c), Offset(ox + 8 * c, oy + r * c), 1.2f)
    for (col in 0..8) {
        val x = ox + col * c
        if (col == 0 || col == 8) drawLine(GridColor, Offset(x, oy), Offset(x, oy + 9 * c), 1.2f)
        else { drawLine(GridColor, Offset(x, oy), Offset(x, oy + 4 * c), 1.2f); drawLine(GridColor, Offset(x, oy + 5 * c), Offset(x, oy + 9 * c), 1.2f) }
    }
    drawLine(GridColor, Offset(ox + 3 * c, oy), Offset(ox + 5 * c, oy + 2 * c), 1f)
    drawLine(GridColor, Offset(ox + 5 * c, oy), Offset(ox + 3 * c, oy + 2 * c), 1f)
    drawLine(GridColor, Offset(ox + 3 * c, oy + 7 * c), Offset(ox + 5 * c, oy + 9 * c), 1f)
    drawLine(GridColor, Offset(ox + 5 * c, oy + 7 * c), Offset(ox + 3 * c, oy + 9 * c), 1f)
    val p = android.graphics.Paint().apply { color = 0xFF3D2B1F.toInt(); textSize = c * 0.34f; textAlign = android.graphics.Paint.Align.CENTER; typeface = Typeface.SERIF; isAntiAlias = true }
    val ry = oy + 4.5f * c + c * 0.12f
    drawContext.canvas.nativeCanvas.drawText("楚河", ox + 2 * c, ry, p)
    drawContext.canvas.nativeCanvas.drawText("漢界", ox + 6 * c, ry, p)
}

/**
 * 执黑时整盘上下左右翻过来，好让自己的子落在靠近手腕的那一侧，跟真在桌边坐着一样。
 * 只翻画面，不翻棋盘本身：操作是转表冠挑、点任意处确认，跟坐标无关，所以不必跟着翻。
 */
private fun flipRow(row: Int, flipped: Boolean) = if (flipped) 9 - row else row
private fun flipCol(col: Int, flipped: Boolean) = if (flipped) 8 - col else col

private fun DrawScope.drawPieces(ox: Float, oy: Float, c: Float, board: Board, flipped: Boolean) {
    val r = c * 0.43f
    val tp = android.graphics.Paint().apply { textSize = c * 0.50f; textAlign = android.graphics.Paint.Align.CENTER; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); isAntiAlias = true }
    for (piece in board.getAllPieces()) {
        val cx = ox + flipCol(piece.position.col, flipped) * c
        val cy = oy + flipRow(piece.position.row, flipped) * c
        val ctr = Offset(cx, cy); val red = piece.color == PieceColor.RED
        drawCircle(Color(0xFFF5E6C8), r, ctr)
        drawCircle(if (red) RedPiece else BlackPiece, r, ctr, style = Stroke(1.8f))
        drawCircle(if (red) RedPiece else BlackPiece, r * 0.80f, ctr, style = Stroke(0.8f))
        tp.color = if (red) 0xFFCC2222.toInt() else 0xFF1A1A1A.toInt()
        val fm = tp.fontMetrics
        drawContext.canvas.nativeCanvas.drawText(piece.type.getDisplayName(piece.color), cx, cy - (fm.ascent + fm.descent) / 2, tp)
    }
}

private fun DrawScope.drawSelection(ox: Float, oy: Float, c: Float, pos: Position?, flipped: Boolean) {
    if (pos == null) return
    drawCircle(SelectedRing, c * 0.47f, Offset(ox + flipCol(pos.col, flipped) * c, oy + flipRow(pos.row, flipped) * c), style = Stroke(2.5f))
}

private fun DrawScope.drawLegalMoves(ox: Float, oy: Float, c: Float, moves: List<Move>, flipped: Boolean) {
    for (m in moves) {
        val ctr = Offset(ox + flipCol(m.to.col, flipped) * c, oy + flipRow(m.to.row, flipped) * c)
        if (m.capturedPiece != null) drawCircle(SelectedRing, c * 0.47f, ctr, style = Stroke(2f))
        else drawCircle(LegalDot, c * 0.14f, ctr)
    }
}

/**
 * 表冠光标。画在棋子之上，好让被亮的那个子一眼看得出来。
 * 挑子那一段画一圈粗环；挑落点那一段在绿点上再叠一个实心点，与旁边没被选中的
 * 落点区分开。
 */
private fun DrawScope.drawCursor(ox: Float, oy: Float, c: Float, pos: Position?, pickingDestination: Boolean, flipped: Boolean) {
    if (pos == null) return
    val ctr = Offset(ox + flipCol(pos.col, flipped) * c, oy + flipRow(pos.row, flipped) * c)
    if (pickingDestination) {
        drawCircle(CursorRing, c * 0.20f, ctr)
        drawCircle(CursorRing, c * 0.40f, ctr, style = Stroke(2.5f))
    } else {
        drawCircle(CursorRing, c * 0.52f, ctr, style = Stroke(3.5f))
    }
}

/**
 * 把已经被吃掉的子排在棋盘两侧。
 *
 * 殿下 2026-09-20 要的：按被吃的先后排，红黑分列左右，好一眼看出双方损失了什么。
 * 左边一列是黑方被吃掉的（都是黑子），右边一列是红方被吃掉的。
 *
 * 位置上得迁就圆屏。棋盘已经铺到贴边，两侧各只剩六十几像素，而且越往上下越窄：在
 * 列心那个横坐标上，圆屏只在中间那一段有高度，所以这一列从屏幕竖直中点往两头长，
 * 长到装不下就不再画，宁可少画几个也不要把子甩到圆外面去。
 */
private fun DrawScope.drawCaptured(ox: Float, c: Float, captured: List<Piece>) {
    if (captured.isEmpty()) return
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = size.width / 2f
    val r = c * 0.30f                       // 小一号，够看清字就行
    val step = r * 2.1f

    for ((side, colour) in listOf(PieceColor.BLACK to BlackPiece, PieceColor.RED to RedPiece)) {
        val list = captured.filter { it.color == side }
        if (list.isEmpty()) continue
        // 黑子摆左边，红子摆右边
        val x = if (side == PieceColor.BLACK) (ox - c * 0.48f) / 2f else size.width - (size.width - (ox + 8 * c + c * 0.48f)) / 2f
        // 这个横坐标上圆屏还剩多少高度
        val halfSpan = kotlin.math.sqrt((radius * radius - (x - cx) * (x - cx)).coerceAtLeast(0f)) - r
        if (halfSpan <= 0f) continue
        val fits = ((halfSpan * 2f) / step).toInt().coerceAtLeast(1)
        val shown = list.takeLast(fits)     // 装不下就留最近被吃的那些
        val top = cy - (shown.size - 1) * step / 2f

        val tp = android.graphics.Paint().apply {
            textSize = r * 1.15f
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            isAntiAlias = true
            color = if (side == PieceColor.RED) 0xFFCC2222.toInt() else 0xFF1A1A1A.toInt()
        }
        val fm = tp.fontMetrics
        for ((i, piece) in shown.withIndex()) {
            val y = top + i * step
            drawCircle(Color(0xFFF5E6C8), r, Offset(x, y))
            drawCircle(colour, r, Offset(x, y), style = Stroke(1.2f))
            drawContext.canvas.nativeCanvas.drawText(
                piece.type.getDisplayName(piece.color), x, y - (fm.ascent + fm.descent) / 2, tp,
            )
        }
    }
}

/**
 * 棋盘上下那两道月牙里，上面写档位，下面写用时。
 *
 * 殿下 2026-09-20 问要不要把时间、步数、档位摆到盘外，问我挑哪两样。挑的是档位和时间。
 * 档位是因为她那天下到一半问过「不知道是选的什么水平」，那说明这个信息本来就缺；
 * 时间是她自己说最想看的。步数留在长按的菜单里，它不像前两样那样随时需要瞄一眼。
 *
 * 圆屏上下各只剩四十来像素，而且越往两边越窄，所以只写一行、居中、字压得小。
 * 引擎思考时顺手把状态并进档位那一行，原先那个右上角的小黄点就不必了。
 */
private fun DrawScope.drawStatusLines(
    c: Float,
    diffName: String,
    thinkingLabel: String,
    elapsedSec: Int,
    thinking: Boolean,
) {
    val paint = android.graphics.Paint().apply {
        textSize = c * 0.46f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = Typeface.SANS_SERIF
        isAntiAlias = true
    }
    val top = if (thinking) thinkingLabel else diffName
    paint.color = if (thinking) 0xFFFFCC00.toInt() else 0xFF9A8A66.toInt()
    drawContext.canvas.nativeCanvas.drawText(top, size.width / 2f, c * 0.92f, paint)

    paint.color = 0xFF9A8A66.toInt()
    drawContext.canvas.nativeCanvas.drawText(
        "%d:%02d".format(elapsedSec / 60, elapsedSec % 60),
        size.width / 2f, size.height - c * 0.44f, paint,
    )
}

/**
 * 刚走的那一步，起讫各画一个圈。
 *
 * 原先只在起讫两格底下铺一层淡黄，太轻了，殿下 2026-09-20 说「对方下什么子现在不是
 * 特别清楚显示」。表上格子只有三毫米，底色的深浅根本分辨不出来，得画出来。
 * 起初连起讫两点画了一根杆子，同日她说杆子多余，两个圈就够，遂去掉。
 * 同日她又说自己走的那一步也该标出来，用绿圈，跟对方的蓝圈对称。原先自己那一步是在
 * 两格底下铺一层半透明的黄色方块，她说「不太明显，而且不对称」，那层方块已经去掉。
 *
 * 绿色跟光标是同一族，但两者不会同时出现：轮到自己时盘上标的是对方刚走的那一步，
 * 是蓝的；自己这一步的绿圈只在对方思考的那几秒里看得见，那时候光标是隐着的。
 */
private fun DrawScope.drawMoveMarker(ox: Float, oy: Float, c: Float, move: Move?, color: Color, flipped: Boolean) {
    if (move == null) return
    val a = Offset(ox + flipCol(move.from.col, flipped) * c, oy + flipRow(move.from.row, flipped) * c)
    val b = Offset(ox + flipCol(move.to.col, flipped) * c, oy + flipRow(move.to.row, flipped) * c)

    // 起点一个细圈，标明它原先在哪儿
    drawCircle(color, c * 0.30f, a, style = Stroke(2.5f))
    // 终点一个粗圈，这是它现在所在的位置
    drawCircle(color, c * 0.50f, b, style = Stroke(3.5f))
}

private fun DrawScope.drawCheckGlow(ox: Float, oy: Float, c: Float, board: Board, flipped: Boolean) {
    val g = board.getAllPieces().find { it.type == PieceType.GENERAL && it.color == board.currentPlayer } ?: return
    drawCircle(Color(0x55FF0000), c * 0.52f, Offset(ox + flipCol(g.position.col, flipped) * c, oy + flipRow(g.position.row, flipped) * c))
}

private fun DrawScope.drawGameOver(msg: String, tapToReturn: String, c: Float) {
    drawRect(Color(0x99000000))
    val p = android.graphics.Paint().apply { color = 0xFFFFFFFF.toInt(); textSize = c * 0.9f; textAlign = android.graphics.Paint.Align.CENTER; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); isAntiAlias = true }
    drawContext.canvas.nativeCanvas.drawText(msg, size.width / 2, size.height / 2 - c * 0.1f, p)
    val s = android.graphics.Paint().apply { color = 0xAAFFFFFF.toInt(); textSize = c * 0.38f; textAlign = android.graphics.Paint.Align.CENTER; typeface = Typeface.SANS_SERIF; isAntiAlias = true }
    drawContext.canvas.nativeCanvas.drawText(tapToReturn, size.width / 2, size.height / 2 + c * 0.7f, s)
}
