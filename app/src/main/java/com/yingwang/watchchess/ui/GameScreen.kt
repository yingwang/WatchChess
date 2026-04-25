package com.yingwang.watchchess.ui

import android.content.Context
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import com.yingwang.watchchess.R
import com.yingwang.watchchess.ai.ChessAI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.yingwang.watchchess.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

// ── Colors ─────────────────────────────────────────────────────────────────

private val BoardBg = Color(0xFFD4A960)
private val GridColor = Color(0xFF3D2B1F)
private val RedPiece = Color(0xFFCC2222)
private val BlackPiece = Color(0xFF1A1A1A)
private val SelectedRing = Color(0xFF00CC44)
private val LegalDot = Color(0x8800CC44)
private val LastMoveHighlight = Color(0x44FFCC00)

// ── Difficulty ─────────────────────────────────────────────────────────────

private data class Difficulty(val name: String, val depth: Int, val timeMs: Long)

private val DIFFICULTIES = listOf(
    Difficulty("入门", depth = 3, timeMs = 2000),
    Difficulty("初级", depth = 5, timeMs = 5000),
    Difficulty("中级", depth = 6, timeMs = 8000),
    Difficulty("高级", depth = 8, timeMs = 15000),
)

private data class Snapshot(val board: Board, val move: Move)

// ── Helpers ────────────────────────────────────────────────────────────────

private fun cellForRound(w: Float, h: Float): Float {
    val d = min(w, h)
    return d / 14.2f
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
    private val bgm = android.media.MediaPlayer.create(context, R.raw.background_music)?.apply {
        isLooping = true; setVolume(0.15f, 0.15f)
    }

    var sfxOn = true
    var bgmOn = true

    fun startBgm() { if (bgmOn) bgm?.start() }
    fun stopBgm() { bgm?.pause() }
    fun toggleBgm(): Boolean { bgmOn = !bgmOn; if (bgmOn) bgm?.start() else bgm?.pause(); return bgmOn }
    fun toggleSfx(): Boolean { sfxOn = !sfxOn; return sfxOn }
    fun playMove() { if (sfxOn) pool.play(moveId, 0.6f, 0.6f, 1, 0, 1f) }
    fun playCapture() { if (sfxOn) pool.play(captureId, 0.8f, 0.8f, 1, 0, 1f) }
    fun release() { bgm?.release(); pool.release() }
}

// ── Root ───────────────────────────────────────────────────────────────────

@Composable
fun GameScreen() {
    var screen by remember { mutableStateOf("menu") } // menu | game
    var diffIdx by remember { mutableIntStateOf(1) }

    var board by remember { mutableStateOf(Board.createInitialBoard()) }
    var selectedPos by remember { mutableStateOf<Position?>(null) }
    var legalMoves by remember { mutableStateOf<List<Move>>(emptyList()) }
    var lastMove by remember { mutableStateOf<Move?>(null) }
    var moveHistory by remember { mutableStateOf(listOf<Move>()) }
    var undoStack by remember { mutableStateOf(listOf<Snapshot>()) }
    var aiThinking by remember { mutableStateOf(false) }
    var gameOverMsg by remember { mutableStateOf<String?>(null) }
    var gameStartTime by remember { mutableLongStateOf(0L) }
    var elapsedSec by remember { mutableIntStateOf(0) }
    var moveCount by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var bgmOn by remember { mutableStateOf(true) }
    var sfxOn by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sounds = remember { GameSounds(context) }
    var ai by remember { mutableStateOf(ChessAI(maxDepth = 3, timeLimit = 2000, quiescenceDepth = 2)) }

    DisposableEffect(Unit) { onDispose { sounds.release() } }

    // Timer
    LaunchedEffect(screen, gameOverMsg) {
        if (screen == "game" && gameOverMsg == null) {
            while (true) { delay(1000); if (gameStartTime > 0) elapsedSec = ((System.currentTimeMillis() - gameStartTime) / 1000).toInt() }
        }
    }

    fun startGame(difficulty: Int) {
        diffIdx = difficulty
        val d = DIFFICULTIES[difficulty]
        val qDepth = if (d.depth >= 6) 3 else 2
        ai = ChessAI(maxDepth = d.depth, timeLimit = d.timeMs, quiescenceDepth = qDepth)
        board = Board.createInitialBoard()
        selectedPos = null; legalMoves = emptyList(); lastMove = null
        moveHistory = emptyList(); undoStack = emptyList()
        aiThinking = false; gameOverMsg = null; showMenu = false
        gameStartTime = System.currentTimeMillis(); elapsedSec = 0; moveCount = 0
        screen = "game"; sounds.startBgm()
    }

    fun checkGameOver(b: Board): String? {
        if (b.isCheckmate()) return if (b.currentPlayer == PieceColor.RED) "黑胜" else "红胜"
        if (b.isStalemate()) return "和棋"
        return null
    }

    fun aiMove() {
        if (gameOverMsg != null) return
        aiThinking = true
        scope.launch {
            val move = withContext(Dispatchers.Default) { ai.findBestMove(board, moveHistory) }
            if (move != null) {
                undoStack = undoStack + Snapshot(board, move)
                val nb = board.makeMove(move); nb.currentPlayer = board.currentPlayer.opposite()
                board = nb; lastMove = move; moveHistory = moveHistory + move
                if (move.isCapture()) { sounds.playCapture(); vibrateDouble(context) }
                else { sounds.playMove(); vibrate(context) }
                gameOverMsg = checkGameOver(board)
                if (gameOverMsg != null) vibrate(context, 100)
            }
            aiThinking = false
        }
    }

    fun undo() {
        if (aiThinking || undoStack.size < 2) return
        val playerSnap = undoStack[undoStack.size - 2]
        board = playerSnap.board; board.currentPlayer = PieceColor.RED
        undoStack = undoStack.dropLast(2); moveHistory = moveHistory.dropLast(2)
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
            selectedPos = null; legalMoves = emptyList(); moveCount++
            if (moveToMake.isCapture()) { sounds.playCapture(); vibrateDouble(context) }
            else { sounds.playMove(); vibrate(context) }
            gameOverMsg = checkGameOver(board)
            if (gameOverMsg != null) vibrate(context, 100) else aiMove()
            return
        }
        val piece = board.getPiece(pos)
        if (piece != null && piece.color == board.currentPlayer) {
            selectedPos = pos; legalMoves = board.getAllLegalMoves().filter { it.from == pos }; vibrate(context, 15)
        } else { selectedPos = null; legalMoves = emptyList() }
    }

    if (screen == "menu") {
        MainMenu(onStart = { startGame(it) }, selectedIdx = diffIdx)
    } else {
        Box(Modifier.fillMaxSize()) {
            // Board layer
            BoardCanvas(
                board = board, selectedPos = selectedPos, legalMoves = legalMoves,
                lastMove = lastMove, aiThinking = aiThinking, gameOverMsg = gameOverMsg,
                onTap = { onTap(it) },
                onLongPress = { if (!aiThinking) showMenu = !showMenu },
                onGameOverTap = { sounds.stopBgm(); screen = "menu" },
            )
            // Menu overlay
            if (showMenu) {
                InGameMenu(
                    diffName = DIFFICULTIES[diffIdx].name,
                    elapsedSec = elapsedSec,
                    moveCount = moveCount,
                    canUndo = undoStack.size >= 2,
                    bgmOn = bgmOn,
                    sfxOn = sfxOn,
                    onToggleBgm = { bgmOn = sounds.toggleBgm() },
                    onToggleSfx = { sfxOn = sounds.toggleSfx() },
                    onUndo = { undo() },
                    onNewGame = { sounds.stopBgm(); screen = "menu" },
                    onDismiss = { showMenu = false },
                )
            }
        }
    }
}

// ── Main Menu ──────────────────────────────────────────────────────────────

@Composable
private fun MainMenu(onStart: (Int) -> Unit, selectedIdx: Int) {
    Box(
        Modifier.fillMaxSize().background(Color(0xFF1A1208)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text("象棋", color = Color(0xFFD4A960), fontSize = 22.sp)
            Spacer(Modifier.height(14.dp))
            DIFFICULTIES.forEachIndexed { idx, diff ->
                val label = diff.name
                Button(
                    onClick = { onStart(idx) },
                    modifier = Modifier.fillMaxWidth().height(36.dp).padding(vertical = 2.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (idx == selectedIdx) Color(0xFFCC2222) else Color(0xFF3D2B1F),
                    ),
                    shape = RoundedCornerShape(6.dp),
                ) { Text(label, color = Color.White, fontSize = 13.sp) }
            }
        }
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
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)).clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 40.dp),
        ) {
            val min = elapsedSec / 60; val sec = elapsedSec % 60
            Text(diffName, color = Color(0xFFD4A960), fontSize = 13.sp)
            Text("%d:%02d · 第%d手".format(min, sec, moveCount + 1), color = Color(0xAAFFFFFF), fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))

            MenuBtn("悔棋", canUndo, onUndo)
            MenuBtn("音乐 " + if (bgmOn) "ON" else "OFF", true, onToggleBgm)
            MenuBtn("音效 " + if (sfxOn) "ON" else "OFF", true, onToggleSfx)
            MenuBtn("新局", true, onNewGame)

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(34.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF555555)),
                shape = RoundedCornerShape(6.dp),
            ) { Text("继续", color = Color.White, fontSize = 13.sp) }
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

@Composable
private fun BoardCanvas(
    board: Board,
    selectedPos: Position?,
    legalMoves: List<Move>,
    lastMove: Move?,
    aiThinking: Boolean,
    gameOverMsg: String?,
    onTap: (Position) -> Unit,
    onLongPress: () -> Unit,
    onGameOverTap: () -> Unit,
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2B1B0E))
            .pointerInput(board, aiThinking, gameOverMsg) {
                detectTapGestures(
                    onTap = { offset ->
                        if (gameOverMsg != null) { onGameOverTap(); return@detectTapGestures }
                        val cell = cellForRound(size.width.toFloat(), size.height.toFloat())
                        val bw = cell * 8f; val bh = cell * 9f
                        val ox = (size.width - bw) / 2f; val oy = (size.height - bh) / 2f
                        val col = ((offset.x - ox + cell / 2) / cell).toInt()
                        val row = ((offset.y - oy + cell / 2) / cell).toInt()
                        val pos = Position(row, col)
                        if (pos.isValid()) onTap(pos)
                    },
                    onLongPress = { onLongPress() },
                )
            },
    ) {
        val cell = cellForRound(size.width, size.height)
        val bw = cell * 8f; val bh = cell * 9f
        val ox = (size.width - bw) / 2f; val oy = (size.height - bh) / 2f

        drawBoard(ox, oy, cell)
        drawLastMove(ox, oy, cell, lastMove)
        drawSelection(ox, oy, cell, selectedPos)
        drawLegalMoves(ox, oy, cell, legalMoves)
        drawPieces(ox, oy, cell, board)

        if (board.isInCheck(board.currentPlayer) && gameOverMsg == null)
            drawCheckGlow(ox, oy, cell, board)

        if (aiThinking) {
            // Small yellow dot at top center
            drawCircle(Color(0xCCFFCC00), radius = 3.5f, center = Offset(size.width / 2, 10f))
        }

        if (gameOverMsg != null) drawGameOver(gameOverMsg, cell)
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

private fun DrawScope.drawPieces(ox: Float, oy: Float, c: Float, board: Board) {
    val r = c * 0.43f
    val tp = android.graphics.Paint().apply { textSize = c * 0.50f; textAlign = android.graphics.Paint.Align.CENTER; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); isAntiAlias = true }
    for (piece in board.getAllPieces()) {
        val cx = ox + piece.position.col * c; val cy = oy + piece.position.row * c
        val ctr = Offset(cx, cy); val red = piece.color == PieceColor.RED
        drawCircle(Color(0xFFF5E6C8), r, ctr)
        drawCircle(if (red) RedPiece else BlackPiece, r, ctr, style = Stroke(1.8f))
        drawCircle(if (red) RedPiece else BlackPiece, r * 0.80f, ctr, style = Stroke(0.8f))
        tp.color = if (red) 0xFFCC2222.toInt() else 0xFF1A1A1A.toInt()
        val fm = tp.fontMetrics
        drawContext.canvas.nativeCanvas.drawText(piece.type.getDisplayName(piece.color), cx, cy - (fm.ascent + fm.descent) / 2, tp)
    }
}

private fun DrawScope.drawSelection(ox: Float, oy: Float, c: Float, pos: Position?) {
    if (pos == null) return
    drawCircle(SelectedRing, c * 0.47f, Offset(ox + pos.col * c, oy + pos.row * c), style = Stroke(2.5f))
}

private fun DrawScope.drawLegalMoves(ox: Float, oy: Float, c: Float, moves: List<Move>) {
    for (m in moves) {
        val ctr = Offset(ox + m.to.col * c, oy + m.to.row * c)
        if (m.capturedPiece != null) drawCircle(SelectedRing, c * 0.47f, ctr, style = Stroke(2f))
        else drawCircle(LegalDot, c * 0.14f, ctr)
    }
}

private fun DrawScope.drawLastMove(ox: Float, oy: Float, c: Float, move: Move?) {
    if (move == null) return
    for (pos in listOf(move.from, move.to))
        drawRect(LastMoveHighlight, Offset(ox + pos.col * c - c * 0.45f, oy + pos.row * c - c * 0.45f), Size(c * 0.9f, c * 0.9f))
}

private fun DrawScope.drawCheckGlow(ox: Float, oy: Float, c: Float, board: Board) {
    val g = board.getAllPieces().find { it.type == PieceType.GENERAL && it.color == board.currentPlayer } ?: return
    drawCircle(Color(0x55FF0000), c * 0.52f, Offset(ox + g.position.col * c, oy + g.position.row * c))
}

private fun DrawScope.drawGameOver(msg: String, c: Float) {
    drawRect(Color(0x99000000))
    val p = android.graphics.Paint().apply { color = 0xFFFFFFFF.toInt(); textSize = c * 0.9f; textAlign = android.graphics.Paint.Align.CENTER; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); isAntiAlias = true }
    drawContext.canvas.nativeCanvas.drawText(msg, size.width / 2, size.height / 2 - c * 0.1f, p)
    val s = android.graphics.Paint().apply { color = 0xAAFFFFFF.toInt(); textSize = c * 0.38f; textAlign = android.graphics.Paint.Align.CENTER; typeface = Typeface.SANS_SERIF; isAntiAlias = true }
    drawContext.canvas.nativeCanvas.drawText("点击返回", size.width / 2, size.height / 2 + c * 0.7f, s)
}
