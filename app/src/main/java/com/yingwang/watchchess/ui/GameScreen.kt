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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.yingwang.watchchess.R
import com.yingwang.watchchess.ai.ChessAI
import com.yingwang.watchchess.ai.FairyEngine
import com.yingwang.watchchess.ai.GameVerdict
import com.yingwang.watchchess.ai.toFen
import com.yingwang.watchchess.ai.toUci
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.yingwang.watchchess.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

// ── Colors ─────────────────────────────────────────────────────────────────

private val BoardBg = Color(0xFFF0EDE3)
private val GridColor = Color(0xFF52675F)
// 两方棋子照象棋的老规矩：同一种浅色子面，红方朱砂字，黑方墨字，字外各勾一道同色圈。
// 1.0.6 曾把两方改成墨青浅子与深蓝深子，殿下 2026-09-26 看了说不像象棋，要「白底黑字、
// 白底红字」。子面比棋盘略暖一些，再垫一圈很淡的影子，免得浅子融进象牙白的盘里。
private val PieceFace = Color(0xFFFFF8E8)
private val PieceShadow = Color(0x2A000000)
private val RedPiece = Color(0xFFB8322A)
private val BlackPiece = Color(0xFF1E2326)
private val SelectedRing = Color(0xFF257B65)
private val LegalDot = Color(0xAA257B65)
private val OpponentMoveColor = Color(0xFF1B6BFF)
private val OwnMoveColor = Color(0xFF257B65)
private val CursorRing = Color(0xFF257B65)

// 菜单一套青瓷色。殿下 2026-09-20 选的方向：底色是很深的墨青，按钮同色系里稍亮一档，
// 描一道极细的浅线，当前选中的那一个填汝窑那种偏灰的蓝绿。
// 红方黑方不再各配红黑两色，她说了「不用特意用红色黑色，都按钮就是同一」，所以整套
// 界面里按钮只有两副长相：寻常的和选中的。棋盘本身不动，木色照旧。
private val MenuBg = Color(0xFF0E1614)
private val BtnFill = Color(0xFF1C2926)
private val BtnEdge = Color(0x33A9CFC8)
private val BtnText = Color(0xFFE8EFED)
private val BtnTextOff = Color(0xFF5B6B68)
private val Celadon = Color(0xFF7FA8A0)
private val CeladonText = Color(0xFF0B1211)
private val MenuHeading = Color(0xFF9CC3BB)

// 按钮占屏宽的比例。表是圆的，越往上下两端可用的宽度越窄，通栏的按钮排到最下面
// 左右两角就被圆边切掉，殿下 2026-09-20 看见的正是这个。收到这个比例再配上药丸形的
// 圆角，最下面那一个也进得来。
private const val BTN_WIDTH = 0.76f
private val BTN_HEIGHT = 34.dp
// 文字行的宽度上限。铺满屏宽的那几行在圆屏上端会被切掉首尾字符，见 CenteredLine。
private const val CONTENT_WIDTH = 0.86f

// 表冠：累计到这个像素量算走一格。数值越大越钝，转同样的角度走的格子越少。
// 2026-09-20 殿下试了两轮都说偏快，先从 45 调到 68，仍嫌敏感，再调到 105。
private const val ROTARY_STEP = 105f

/**
 * 可滚动那几屏的内边距。
 *
 * 2026-09-22 Play 以「Watch shapes」与「Wear font size」两条退回 vc10，说的是内容会被
 * 屏幕边缘切掉。圆屏上越靠近上下两端可用的宽度越窄，首尾两项若紧贴着边就会被切；系统
 * 字号调大之后更明显。这里按屏幕自身的尺寸留边，不写死数值，因为表的直径从 320 到 454
 * 都有。右侧那条位置指示条也占地方，横向这一份同时把它让开。
 */
/**
 * 列表边缘的缩放与淡出。
 *
 * 默认参数在贴到上下两端时仍留着一半的不透明度，条目那时已经探到圆外，看上去就是被切了
 * 一刀。这里把边缘处的不透明度压到零、比例压到四成，条目在走到会被切的位置之前就已经
 * 完全隐去。测下来滚动过程中落在圆外的内容像素从一千多降到个位数。
 */
@Composable
private fun edgeFadeParams() = ScalingLazyColumnDefaults.scalingParams(
    edgeScale = 0.35f,
    edgeAlpha = 0f,
    minTransitionArea = 0.4f,
    maxTransitionArea = 0.8f,
)

@Composable
private fun scrollPadding(): PaddingValues {
    val cfg = LocalConfiguration.current
    val w = cfg.screenWidthDp.dp
    val h = cfg.screenHeightDp.dp
    return if (cfg.isScreenRound) PaddingValues(horizontal = w * 0.09f, vertical = h * 0.12f)
    else PaddingValues(horizontal = w * 0.06f, vertical = h * 0.06f)
}

// 上手提示只在装上之后的第一局出现一次，看过就记下来，之后不再打扰。
private const val PREFS_NAME = "watchchess"
private const val PREF_HELP_SEEN = "help_seen"
private const val PREF_BGM_ON = "bgm_on"
private const val PREF_SFX_ON = "sfx_on"

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
// 2026-09-20 一度把高级提到六十万结点、十五秒，实测能到第 15 层（二十五万是第 13 层，
// 一百二十万仍是第 15 层，多花十秒白花）。但当天殿下试过之后说十五秒等得太久，遂调回
// 二十五万、七秒。这一档的上限不是算力，是人愿意在表上等多久。
private val DIFFICULTIES = listOf(
    Difficulty(R.string.level_beginner, depth = 3, timeMs = 800, nodes = 2_000, spreadCp = 60),
    Difficulty(R.string.level_easy, depth = 5, timeMs = 1500, nodes = 20_000, spreadCp = 30),
    Difficulty(R.string.level_medium, depth = 6, timeMs = 3000, nodes = 80_000, spreadCp = 15),
    Difficulty(R.string.level_hard, depth = 8, timeMs = 7000, nodes = 250_000, spreadCp = 8),
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
/**
 * 一格多大。
 *
 * 原先一律取短边的十三分之一。2026-09-22 Play 以「Watch shapes」退回 vc10，附的截图里
 * 木底的四个角明显探到圆外去了，角上那两个車也压在边缘上。原因是圆屏能显示的是内切圆
 * 而不是整块方形：画出来的木底连边是 8.96 格宽、9.96 格高，它的半对角线约 6.70 格，
 * 按短边十三分之一算出来是 219 像素，而半径只有 213，正好差这一点。
 *
 * 所以圆屏上改由半对角线定尺寸，再留 3% 余量。方屏维持原样，那里整块方形都能用。
 */
private fun cellForRound(w: Float, h: Float, round: Boolean): Float {
    val plain = min(w, h) / 13.0f
    if (!round) return plain
    val r = min(w, h) / 2f * 0.97f
    val halfDiag = sqrt(8.96f * 8.96f + 9.96f * 9.96f) / 2f
    return min(plain, r / halfDiag)
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

private class GameSounds(private val context: Context) {
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
    // 背景音乐播放器。MediaPlayer 有个脾气：还没 start 过就 pause，它会报错进入 Error
    // 状态，之后再 start 也不出声。1.0.5 加了「退到后台就停音乐」，停在菜单页（音乐
    // 本来就没开始）时抬腕放腕一下就踩中这条，此后整局都没音乐，殿下 2026-09-26 发现
    // 「音乐打开之后好像没有音乐了」。所以只在真在放的时候才 pause；万一还是进了 Error，
    // 就把它丢掉，下次要放时重建一个。
    private var bgm: android.media.MediaPlayer? = createBgm()

    private fun createBgm(): android.media.MediaPlayer? =
        android.media.MediaPlayer.create(context, R.raw.background_music)?.apply {
            isLooping = true; setVolume(0.15f, 0.15f)
            setOnErrorListener { mp, what, extra ->
                Log.w("WatchChess", "bgm error $what/$extra, recreating")
                mp.release()
                if (bgm === mp) bgm = null
                true
            }
        }

    // 这两个开关要记住。2026-09-22 殿下说调试的时候把音乐音效关了，翻出来才发现关了
    // 也白关：它们只存在内存里，应用一重启就又回到开着。菜单里明明有这个开关，关掉之后
    // 下次打开还响，那这个开关等于没有。存进跟存档、帮助提示同一个 SharedPreferences。
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var sfxOn = prefs.getBoolean(PREF_SFX_ON, true)

    // 背景音乐默认开着。2026-09-20 先按殿下的话关成默认不开，当天她改了主意说还是
    // 默认开，所以改回来。菜单里开关仍在，随时可关。
    var bgmOn = prefs.getBoolean(PREF_BGM_ON, true)

    fun startBgm() {
        if (!bgmOn) return
        if (bgm == null) bgm = createBgm()
        bgm?.start()
    }
    fun stopBgm() { bgm?.let { if (it.isPlaying) it.pause() } }
    fun toggleBgm(): Boolean {
        bgmOn = !bgmOn
        if (bgmOn) startBgm() else stopBgm()
        prefs.edit().putBoolean(PREF_BGM_ON, bgmOn).apply()
        return bgmOn
    }
    fun toggleSfx(): Boolean {
        sfxOn = !sfxOn
        prefs.edit().putBoolean(PREF_SFX_ON, sfxOn).apply()
        return sfxOn
    }
    fun playMove() { if (sfxOn) pool.play(moveId, 0.6f, 0.6f, 1, 0, 1f) }
    fun playCapture() { if (sfxOn) pool.play(captureId, 0.8f, 0.8f, 1, 0, 1f) }
    fun sayCapture() { if (sfxOn) pool.play(sayCaptureId, 1f, 1f, 2, 0, 1f) }
    fun sayCheck() { if (sfxOn) pool.play(sayCheckId, 1f, 1f, 2, 0, 1f) }
    fun release() { bgm?.release(); bgm = null; pool.release() }
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
    var retryNeeded by remember { mutableStateOf(false) }
    var gameStartTime by remember { mutableLongStateOf(0L) }
    var elapsedSec by remember { mutableIntStateOf(0) }
    var moveCount by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    // 上手提示。第一次开局时自动压在棋盘上，点掉之后再也不自己出现，想回看走长按菜单。
    var showHelp by remember { mutableStateOf(false) }
    var cursorIdx by remember { mutableIntStateOf(0) }
    var cancelledPiece by remember { mutableStateOf<Position?>(null) }
    // 这一局里每个局面走过哪些着法。键是局面本身，值是从这个局面走出去过的着法。
    // 引擎是死的，同一个局面会照原样再走一遍，两边来回推就卡住了，所以记下来让它换一着。
    // 只在本局有效，开新局清空。
    val playedFrom = remember { mutableMapOf<String, MutableSet<String>>() }
    // 每走一步之后的局面，跟着法历史一一对应，所以悔棋时一起回退，不会数错。
    // 仅记录盘面；裁决用引擎收到的完整 moveHistory，不以重复次数直接判和。
    var positionHistory by remember { mutableStateOf(listOf<String>()) }
    var rotaryAcc by remember { mutableFloatStateOf(0f) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var foreground by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val scope = rememberCoroutineScope()
    val searches = remember { GameSearchSession() }
    val sounds = remember { GameSounds(context) }
    // 初值取自 GameSounds，它已经从 SharedPreferences 读过了；这里再写死 true 的话，
    // 菜单上显示的开关状态会跟实际响不响对不上。声明要排在 sounds 之后才读得到。
    var bgmOn by remember { mutableStateOf(sounds.bgmOn) }
    var sfxOn by remember { mutableStateOf(sounds.sfxOn) }
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var saveReady by remember { mutableStateOf(false) }
    var lastSaved by remember { mutableStateOf(prefs.getString("saved_game_v1", null)) }
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
    LaunchedEffect(foreground) {
        if (!foreground) {
            searches.invalidate()
            aiThinking = false
            sounds.stopBgm()
            withContext(NonCancellable + Dispatchers.IO) {
                searches.withEngine { engine.stop() }
            }
            engineReady = false
            return@LaunchedEffect
        }
        searches.withEngine { engineReady = engine.start() }
        if (screen == "game") {
            sounds.startBgm()
            if (gameOverMsg == null) aiMoveTrigger++
        }
        if (!engineReady) Log.w("WatchChess", "engine unavailable: ${engine.lastError}")
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!foreground) { searches.invalidate(); aiThinking = false; sounds.stopBgm() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            searches.invalidate()
            sounds.release()
            scope.launch(NonCancellable + Dispatchers.IO) { searches.withEngine { engine.stop() } }
        }
    }

    // Timer
    LaunchedEffect(screen, gameOverMsg, foreground) {
        if (screen == "game" && gameOverMsg == null && foreground) {
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
        aiThinking = false; gameOverMsg = null; retryNeeded = false; showMenu = false
        playedFrom.clear(); positionHistory = listOf(board.toFen())
        gameStartTime = System.currentTimeMillis(); elapsedSec = 0; moveCount = 0
        screen = "game"; sounds.startBgm()
        // 第一次开局时把操作说明摆出来，就在人正要动手的那一刻，不必他自己去找。
        if (!prefs.getBoolean(PREF_HELP_SEEN, false)) {
            showHelp = true
            prefs.edit().putBoolean(PREF_HELP_SEEN, true).apply()
        }
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
        if (gameOverMsg != null || aiThinking || screen != "game" || !foreground) return
        retryNeeded = false
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
            // Returning from the background may only require retrying adjudication after an AI move.
            if (searchBoard.currentPlayer == playerColor) return@search TurnResult(null, null)
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
            val transientFailure = result.messageRes == R.string.result_unavailable || result.messageRes == R.string.engine_no_move
            if (screen == "game" && board === searchBoard) retryNeeded = transientFailure
            if (move == null && screen == "game" && board === searchBoard) gameOverMsg = result.messageRes.takeUnless { transientFailure }
            if (move != null && screen == "game" && board === searchBoard) {
                playedFrom.getOrPut(positionKey) { mutableSetOf() }.add(move.toUci())
                undoStack = undoStack + Snapshot(board, move)
                val nb = board.makeMove(move); nb.currentPlayer = board.currentPlayer.opposite()
                board = nb; lastMove = move; moveHistory = moveHistory + move
                positionHistory = positionHistory + nb.toFen()
                if (move.isCapture()) { sounds.playCapture(); sounds.sayCapture(); vibrateDouble(context) }
                else { sounds.playMove(); vibrate(context) }
                gameOverMsg = checkGameOver(board) ?: result.messageRes.takeUnless { transientFailure }
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
        if (aiThinking) return
        val keep = undoHistorySize(moveHistory, playerColor) ?: return
        searches.invalidate()
        board = undoStack[keep].board
        undoStack = undoStack.take(keep); moveHistory = moveHistory.take(keep)
        positionHistory = positionHistory.take(keep + 1)
        gameOverMsg = null
        retryNeeded = false
        playedFrom.clear()
        undoStack.filter { it.move.piece.color != playerColor }.forEach {
            playedFrom.getOrPut(it.board.toFen()) { mutableSetOf() }.add(it.move.toUci())
        }
        lastMove = moveHistory.lastOrNull(); selectedPos = null; legalMoves = emptyList()
        moveCount = moveHistory.count { it.piece.color == playerColor }
        vibrate(context, 20); showMenu = false
    }

    fun onTap(pos: Position) {
        if (aiThinking || gameOverMsg != null || retryNeeded || !foreground) return
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
        if (aiMoveTrigger > 0 && screen == "game") aiMove()
    }

    LaunchedEffect(Unit) {
        val saved = prefs.getString("saved_game_v1", null)?.let(SavedGame::decode)
        if (saved != null) runCatching {
            val replay = withContext(Dispatchers.Default) { saved.replay() }
            diffIdx = saved.difficulty; playerColor = saved.side
            val d = DIFFICULTIES[diffIdx]
            ai = ChessAI(maxDepth = d.depth, timeLimit = d.timeMs, quiescenceDepth = if (d.depth >= 6) 3 else 2)
            undoStack = replay.map { Snapshot(it.first, it.second) }
            moveHistory = replay.map { it.second }
            board = replay.lastOrNull()?.let { (before, move) ->
                before.makeMove(move).also { it.currentPlayer = before.currentPlayer.opposite() }
            } ?: Board.createInitialBoard()
            positionHistory = replay.map { it.first.toFen() } + board.toFen()
            lastMove = moveHistory.lastOrNull()
            moveCount = moveHistory.count { it.piece.color == playerColor }
            gameStartTime = saved.startedAt
            gameOverMsg = saved.terminalResultName()?.let {
                context.resources.getIdentifier(it, "string", context.packageName).takeIf { id -> id != 0 }
            }
            replay.filter { it.second.piece.color != playerColor }.forEach { (before, move) ->
                playedFrom.getOrPut(before.toFen()) { mutableSetOf() }.add(move.toUci())
            }
            screen = "game"
            if (foreground) sounds.startBgm()
            if (gameOverMsg == null) aiMoveTrigger++
        }.onFailure { Log.w("WatchChess", "Saved game could not be restored", it) }
        saveReady = true
    }

    SideEffect {
        if (saveReady) {
            val encoded = if (screen == "game") SavedGame(diffIdx, playerColor, gameStartTime,
                gameOverMsg?.let { context.resources.getResourceEntryName(it) }.orEmpty(),
                moveHistory.map { it.toUci() }).encode() else null
            if (encoded != lastSaved) {
                // apply immediately updates memory and queues ordered disk writes off the UI thread.
                prefs.edit().putString("saved_game_v1", encoded).apply()
                lastSaved = encoded
            }
        }
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

    // 取消选子时回到刚才那颗子；落子、悔棋和新选子仍从新列表起点开始。
    LaunchedEffect(selectedPos, board) {
        cursorIdx = if (selectedPos == null) restoredCursorIndex(movablePieces, cancelledPiece) else 0
        cancelledPiece = null
        rotaryAcc = 0f
    }

    fun onRotary(px: Float): Boolean {
        if (aiThinking || gameOverMsg != null || showMenu || showHelp) return false
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
        if (aiThinking || gameOverMsg != null || showHelp) return
        if (retryNeeded) { aiMove(); return }
        onTap(cursorPos ?: return)
    }

    // 右滑返回等于取消选中，退回挑子那一段
    BackHandler(enabled = screen == "game" && (showHelp || showMenu || selectedPos != null)) {
        if (showHelp) showHelp = false
        else if (showMenu) showMenu = false
        else {
            cancelledPiece = selectedPos
            cursorIdx = restoredCursorIndex(movablePieces, selectedPos)
            selectedPos = null; legalMoves = emptyList(); rotaryAcc = 0f
            vibrate(context, 15)
        }
    }

    if (!saveReady) {
        Box(Modifier.fillMaxSize().background(MenuBg), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.restoring_game), color = BtnText)
        }
    } else if (screen == "menu") {
        MainMenu(onPick = { idx -> diffIdx = idx; screen = "side" }, selectedIdx = diffIdx)
    } else if (screen == "side") {
        SideMenu(
            onPick = { side -> startGame(diffIdx, side) },
            onBack = { screen = "menu" },
        )
    } else {
        Box(Modifier.fillMaxSize()) {
            // Board layer
            BoardCanvas(
                board = board, selectedPos = selectedPos, legalMoves = legalMoves,
                lastMove = lastMove, aiThinking = aiThinking,
                gameOverMsg = gameOverMsg?.let { stringResource(it) } ?: if (retryNeeded) stringResource(R.string.engine_retry) else null,
                cursorPos = cursorPos, pickingDestination = selectedPos != null,
                menuShowing = showMenu,
                captured = capturedPieces,
                flipped = playerColor == PieceColor.BLACK,
                playerColor = playerColor,
                diffName = stringResource(DIFFICULTIES[diffIdx].nameRes),
                thinkingLabel = stringResource(R.string.thinking, stringResource(DIFFICULTIES[diffIdx].nameRes)),
                tapToReturn = stringResource(if (retryNeeded) R.string.tap_to_retry else R.string.tap_to_return),
                elapsedSec = elapsedSec,
                onConfirm = { onConfirm() },
                onRotary = { onRotary(it) },
                onLongPress = { showMenu = !showMenu },
                onGameOverTap = { if (retryNeeded) aiMove() else returnToMenu() },
            )
            // Menu overlay
            if (showMenu) {
                InGameMenu(
                    diffName = stringResource(DIFFICULTIES[diffIdx].nameRes),
                    elapsedSec = elapsedSec,
                    moveCount = moveCount,
                    canUndo = !aiThinking && undoHistorySize(moveHistory, playerColor) != null,
                    bgmOn = bgmOn,
                    sfxOn = sfxOn,
                    onToggleBgm = { bgmOn = sounds.toggleBgm() },
                    onToggleSfx = { sfxOn = sounds.toggleSfx() },
                    onUndo = { undo() },
                    onNewGame = { returnToMenu() },
                    onHelp = { showMenu = false; showHelp = true },
                    onDismiss = { showMenu = false },
                )
            }
            // 说明层压在菜单之上，这样从菜单点进来时盖得住它。
            if (showHelp) HelpOverlay(onDismiss = { showHelp = false })
        }
    }
}

// ── Main Menu ──────────────────────────────────────────────────────────────

/**
 * 开局分两屏：先挑难度，再挑执哪一方，挑完就开局。
 *
 * 一开始我把两样并在一屏，难度按钮按下去直接开局，红黑只是个不起眼的开关。
 * 殿下 2026-09-20 指出那样不对：人点了红或黑，屏幕上什么也没发生，会以为没点上。
 * 一屏之内有的按钮开局、有的只是切换，这件事本身就说不清楚。
 * 拆成两屏之后规矩只有一条：每点一下都往前走一步，最后那一下开局。
 * 顺序是难度在前，因为难度是每局都可能换的，执哪一方多半定下来就不动。
 */
@Composable
private fun MainMenu(onPick: (Int) -> Unit, selectedIdx: Int) {
    MenuScaffold {
        DIFFICULTIES.forEachIndexed { idx, diff ->
            item {
                WatchButton(
                    label = stringResource(diff.nameRes),
                    selected = idx == selectedIdx,
                    onClick = { onPick(idx) },
                )
            }
        }
    }
}

/** 第二屏：执红还是执黑。右滑退回去改难度。 */
@Composable
private fun SideMenu(onPick: (PieceColor) -> Unit, onBack: () -> Unit) {
    BackHandler(enabled = true) { onBack() }
    MenuScaffold {
        item { WatchButton(stringResource(R.string.side_red), onClick = { onPick(PieceColor.RED) }) }
        item { WatchButton(stringResource(R.string.side_black), onClick = { onPick(PieceColor.BLACK) }) }
    }
}

/**
 * 可滚的那几屏共用的外框。
 *
 * 2026-09-22 Play 一次退回三条，其中两条落在这里，原先那个「Column 加 verticalScroll」
 * 的写法两条都挡不住：
 *
 * 一是没有滚动条。Wear 要求可滚的界面在滚动时显示右侧那条位置指示条，自己写的滚动容器
 * 不带它，得由 Scaffold 配 PositionIndicator 来画。
 *
 * 二是滚到中途时内容被圆边切掉。审核员附的截图里，对局菜单滚到一半，最上面那个按钮的
 * 左右两端探到圆外；帮助那屏每行字的头一个和末一个字符也被切了。这不是留边不够的问题：
 * 一个等宽的纵列在滚动过程中必然要经过屏幕上下两端，而圆屏在那里可用的宽度只有中间的
 * 一半左右，切是一定会切的。ScalingLazyColumn 正是为这件事存在的，它会把靠近上下两端
 * 的条目按比例缩小并淡出，条目还没走到会被切的地方就已经收进去了，同时首尾两项都能滚
 * 到正中，不会卡在边上。
 *
 * 表冠仍旧自己接：ScalingLazyListState 也是一个 ScrollableState，scrollBy 照用。
 */
@Composable
private fun MenuScaffold(content: ScalingLazyListScope.() -> Unit) {
    val listState = rememberScalingLazyListState()
    val focus = remember { FocusRequester() }
    val rotaryEvents = remember { Channel<Float>(Channel.UNLIMITED) }
    LaunchedEffect(listState) {
        for (delta in rotaryEvents) {
            var total = delta
            while (true) total += rotaryEvents.tryReceive().getOrNull() ?: break
            listState.scrollBy(total)
        }
    }
    DisposableEffect(rotaryEvents) { onDispose { rotaryEvents.close() } }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = scrollPadding(),
            scalingParams = edgeFadeParams(),
            modifier = Modifier
                .fillMaxSize()
                .background(MenuBg)
                .onRotaryScrollEvent {
                    rotaryEvents.trySend(it.verticalScrollPixels)
                    true
                }
                .focusRequester(focus)
                .focusable(),
            content = content,
        )
    }
}

/**
 * 全应用唯一的按钮。
 *
 * 之前三处各写各的：难度那一屏高 38，对局菜单里高 34，帮助里的「知道了」高 30；颜色上
 * 多数是深褐，「继续」却是灰的，执黑那一项又近乎纯黑。殿下 2026-09-20 说「所有按钮风格
 * 好像不是特别的统一」，指的就是这个。现在只留这一个函数，尺寸圆角描边字号都在这里，
 * 想改一处就改这里，不会再各走各的。
 *
 * selected 为真时填青瓷色，用来标当前选中的那一项，别的语义一概不用这个颜色。
 *
 * 高度本来写死 34dp。2026-09-22 Play 以「Wear font size」退回 vc10：系统设置里把字号
 * 调大之后，字撑不开这个高度，上下就被切掉了。现在 34dp 只作下限，字多高按钮就多高，
 * 实在放不下还能折成两行。
 */
@Composable
private fun WatchButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth(BTN_WIDTH)
            .heightIn(min = BTN_HEIGHT)
            .border(1.dp, if (selected) Color.Transparent else BtnEdge, RoundedCornerShape(50)),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (selected) Celadon else BtnFill,
            disabledBackgroundColor = BtnFill,
        ),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            label,
            color = when {
                !enabled -> BtnTextOff
                selected -> CeladonText
                else -> BtnText
            },
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
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
    onHelp: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 两行字加五个按钮竖着排，比这块圆屏高，最底下那个「继续」会被切掉，殿下
    // 2026-09-20 说「最下面那个选项没有显示全」。圆屏上下还要各让出一块，可用的高度
    // 比看上去更少。所以这里让它能滚，并且把表冠接上去滚，跟棋盘那边同一套手势。
    // 上下各留一段空白，好让首尾两项都能滚到屏幕中间，不至于卡在圆边上。
    val listState = rememberScalingLazyListState()
    val menuFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { menuFocus.requestFocus() }

    Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        Box(
            Modifier.fillMaxSize().background(Color(0xF0000000)).clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            ScalingLazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = scrollPadding(),
                scalingParams = edgeFadeParams(),
                modifier = Modifier
                    .fillMaxSize()
                    .onRotaryScrollEvent {
                        scope.launch { listState.scrollBy(it.verticalScrollPixels) }
                        true
                    }
                    .focusRequester(menuFocus)
                    .focusable(),
            ) {
                item {
                    val min = elapsedSec / 60; val sec = elapsedSec % 60
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CenteredLine(diffName, MenuHeading, 13.sp)
                        CenteredLine(
                            stringResource(R.string.clock_moves, min, sec, moveCount + 1),
                            Color(0xAAFFFFFF), 11.sp,
                        )
                    }
                }
                item { WatchButton(stringResource(R.string.menu_undo), onUndo, enabled = canUndo) }
                item { WatchButton(stringResource(R.string.menu_music, stringResource(if (bgmOn) R.string.state_on else R.string.state_off)), onToggleBgm) }
                item { WatchButton(stringResource(R.string.menu_sound, stringResource(if (sfxOn) R.string.state_on else R.string.state_off)), onToggleSfx) }
                item { WatchButton(stringResource(R.string.menu_new_game), onNewGame) }
                item { WatchButton(stringResource(R.string.menu_help), onHelp) }
                item { WatchButton(stringResource(R.string.menu_resume), onDismiss) }
            }
        }
    }
}

/**
 * 菜单与帮助里的一行字。
 *
 * 宽度只给屏幕的 CONTENT_WIDTH，不铺满。审核员附的截图里帮助那几行正是因为铺满了宽度，
 * 滚到屏幕上端时每行的头尾各被圆边切掉一个字符。收窄之后折行点提前，行首行尾都落在
 * 圆里，字号调到最大也一样。
 */
@Composable
private fun CenteredLine(text: String, color: Color, fontSize: TextUnit) {
    Text(
        text,
        color = color,
        fontSize = fontSize,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(CONTENT_WIDTH),
    )
}

/**
 * 上手提示。
 *
 * 殿下 2026-09-20 说操作「并不是特别直白」，问说明该放哪。放进商店文案只解决装之前的
 * 事，放一个 about 页则没人会去翻：表上的设置页是人乱按过一通之后才会到的地方。所以
 * 提示出现在第一次开局的那一刻，直接压在棋盘上，四行字各是一个动作，点一下就消失，
 * 以后都不再出现。想回看的从长按菜单里那一项进来，位置跟悔棋、音乐、新局并排。
 */
@Composable
private fun HelpOverlay(onDismiss: () -> Unit) {
    // 四条说明改成完整短句之后比原来长，窄的那几款表上会折行，折完就顶到圆边外头去了。
    // 所以跟对局菜单一样让它能滚，表冠也接上，手势前后一致。
    val listState = rememberScalingLazyListState()
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        Box(
            Modifier.fillMaxSize().background(Color(0xF7000000)).clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            ScalingLazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                contentPadding = scrollPadding(),
                scalingParams = edgeFadeParams(),
                modifier = Modifier
                    .fillMaxSize()
                    .onRotaryScrollEvent {
                        scope.launch { listState.scrollBy(it.verticalScrollPixels) }
                        true
                    }
                    .focusRequester(focus)
                    .focusable(),
            ) {
                item { CenteredLine(stringResource(R.string.help_title), MenuHeading, 13.sp) }
                item { HelpLine(stringResource(R.string.help_step1)) }
                item { HelpLine(stringResource(R.string.help_step2)) }
                item { HelpLine(stringResource(R.string.help_step3)) }
                item { HelpLine(stringResource(R.string.help_step4)) }
                item { HelpLine(stringResource(R.string.help_cancel), Color(0x99FFFFFF), 11.sp) }
                item { HelpLine(stringResource(R.string.help_menu), Color(0x99FFFFFF), 11.sp) }
                item { WatchButton(stringResource(R.string.help_got_it), onDismiss) }
            }
        }
    }
}

@Composable
private fun HelpLine(text: String, color: Color = Color(0xEEFFFFFF), fontSize: TextUnit = 12.sp) =
    CenteredLine(text, color, fontSize)

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

    // 棋盘上的字是直接画在画布上的，不是 Compose 的 Text，所以不会自己跟随系统字号，
    // 也不会自己换行。2026-09-22 Play 以「Wear font size」退回 vc10 指的就是这件事。
    // 这里把系统设置里的字号倍率取出来，交给下面每一处画字的地方去乘。
    val fontScale = LocalDensity.current.fontScale
    val round = LocalConfiguration.current.isScreenRound
    val paints = remember { BoardPaints() }
    val pieces = remember(board) { board.getAllPieces() }
    val inCheck = remember(board) { board.isInCheck(board.currentPlayer) }

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
            .background(MenuBg)
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
        val cell = cellForRound(size.width, size.height, round)
        val bw = cell * 8f; val bh = cell * 9f
        val ox = (size.width - bw) / 2f; val oy = (size.height - bh) / 2f

        drawBoard(ox, oy, cell, paints.board)
        drawSelection(ox, oy, cell, selectedPos, flipped)
        drawLegalMoves(ox, oy, cell, legalMoves, flipped)
        drawPieces(ox, oy, cell, pieces, flipped, paints.piece)
        drawCaptured(ox, cell, captured, paints.captured)
        // 标出刚走的那一步，画在棋子上面才看得见。对方是蓝的，自己是绿的。
        lastMove?.let {
            drawMoveMarker(ox, oy, cell, it,
                if (it.piece.color == playerColor) OwnMoveColor else OpponentMoveColor, flipped)
        }
        drawCursor(ox, oy, cell, cursorPos, pickingDestination, flipped)

        if (inCheck && gameOverMsg == null)
            drawCheckGlow(ox, oy, cell, board, flipped)

        drawStatusLines(cell, oy, bh, diffName, thinkingLabel, elapsedSec, aiThinking, fontScale, round, paints.status)

        if (gameOverMsg != null) drawGameOver(gameOverMsg, tapToReturn, cell, fontScale, round, paints.result, paints.hint)
    }
}

// ── Drawing helpers ────────────────────────────────────────────────────────

private class BoardPaints {
    private fun paint(bold: Boolean = false) = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = if (bold) Typeface.create(Typeface.SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
    }
    val board = paint()
    val piece = paint(true)
    val captured = paint(true)
    val status = paint()
    val result = paint(true)
    val hint = paint()
}

private fun DrawScope.drawBoard(ox: Float, oy: Float, c: Float, p: android.graphics.Paint) {
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
    p.color = 0xFF52675F.toInt(); p.textSize = c * 0.34f
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

private fun DrawScope.drawPieces(ox: Float, oy: Float, c: Float, pieces: List<Piece>, flipped: Boolean, tp: android.graphics.Paint) {
    val r = c * 0.43f
    tp.textSize = c * 0.50f
    for (piece in pieces) {
        val cx = ox + flipCol(piece.position.col, flipped) * c
        val cy = oy + flipRow(piece.position.row, flipped) * c
        val ctr = Offset(cx, cy); val red = piece.color == PieceColor.RED
        val ink = if (red) RedPiece else BlackPiece
        drawCircle(PieceShadow, r, ctr + Offset(0f, c * 0.05f))
        drawCircle(PieceFace, r, ctr)
        drawCircle(ink, r, ctr, style = Stroke(1.8f))
        drawCircle(ink, r * 0.80f, ctr, style = Stroke(0.8f))
        tp.color = ink.toArgb()
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
private fun DrawScope.drawCaptured(ox: Float, c: Float, captured: List<Piece>, tp: android.graphics.Paint) {
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

        tp.apply {
            textSize = r * 1.15f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            color = colour.toArgb()
        }
        val fm = tp.fontMetrics
        for ((i, piece) in shown.withIndex()) {
            val y = top + i * step
            drawCircle(PieceFace, r, Offset(x, y))
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
/**
 * 把一行字画进圆屏里，保证不被边缘切掉。
 *
 * 画布上的字跟 Compose 的 Text 不一样，它既不会换行也不会省略，超出去的部分直接被屏幕
 * 切掉。而圆屏越靠近上下两端可用的宽度越窄：一行字画在离中心 dy 远的地方，能用的宽度
 * 是那个高度上的弦长，不是整个屏宽。所以这里先算出这一行实际能占多宽，再按需要把字号
 * 收下来。收到 minScale 仍放不下的才截断，那种情形只会出现在极窄的表加极大的字号上。
 *
 * band 给的是这一行可以落在的上下范围（棋盘之外那一条），字先在里头居中，字号大到
 * 塞不下时连带着一起缩，这样它永远不会压到棋盘上去。
 */
private fun DrawScope.drawTextFitted(
    text: String,
    bandTop: Float,
    bandBottom: Float,
    paint: android.graphics.Paint,
    round: Boolean,
    minScale: Float = 0.6f,
) {
    if (text.isEmpty()) return
    val bandH = bandBottom - bandTop
    if (bandH <= 0f) return

    // 竖向：字高不能超过这条带子
    val maxByBand = bandH * 0.78f
    if (paint.textSize > maxByBand) paint.textSize = maxByBand

    val baseline = (bandTop + bandBottom) / 2f + paint.textSize * 0.36f

    // 横向：取这一行上下两缘里更靠近屏幕端点的那一条来算弦长，才不会有半个字探出去
    val cy = size.height / 2f
    val glyphTop = baseline - paint.textSize * 0.82f
    val glyphBottom = baseline + paint.textSize * 0.22f
    val dy = maxOf(abs(glyphTop - cy), abs(glyphBottom - cy))
    val ry = size.height / 2f
    val avail = if (!round) size.width * 0.92f
        else if (dy >= ry) 0f
        else size.width * sqrt((1f - (dy / ry) * (dy / ry)).coerceAtLeast(0f)) - size.width * 0.05f
    if (avail <= 0f) return

    val want = paint.measureText(text)
    if (want > avail) paint.textSize = paint.textSize * (avail / want).coerceAtLeast(minScale)

    drawContext.canvas.nativeCanvas.drawText(text, size.width / 2f, baseline, paint)
}

/**
 * 棋盘上下两行字：上面是难度或者「思考中」，下面是用时。
 *
 * 字号乘上系统设置里的倍率，再交给 drawTextFitted 按所在高度的弦长收一收。两件事缺一
 * 不可：只跟随字号不收，调大之后英文那句「Beginner · thinking」会从圆边两侧探出去；
 * 只收不跟随，等于没听系统设置。棋盘本身的位置不动，这两行各自落在棋盘之外的空当里。
 */
private fun DrawScope.drawStatusLines(
    c: Float,
    boardTop: Float,
    boardHeight: Float,
    diffName: String,
    thinkingLabel: String,
    elapsedSec: Int,
    thinking: Boolean,
    fontScale: Float,
    round: Boolean,
    statusPaint: android.graphics.Paint,
) {
    fun paint(color: Int) = statusPaint.apply {
        textSize = c * 0.46f * fontScale
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = Typeface.SANS_SERIF
        isAntiAlias = true
        this.color = color
    }

    // 棋盘的木底比格线本身还各外扩半格，所以这里以它的外缘为界
    val boardPad = c * 0.48f
    val top = if (thinking) thinkingLabel else diffName
    drawTextFitted(top, 0f, boardTop - boardPad, paint(if (thinking) 0xFFE5D3A0.toInt() else 0xFFB5CCC4.toInt()), round)

    drawTextFitted(
        "%d:%02d".format(elapsedSec / 60, elapsedSec % 60),
        boardTop + boardHeight + boardPad, size.height,
        paint(0xFFB5CCC4.toInt()), round,
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
    drawCircle(Color(0x99C18B35), c * 0.52f, Offset(ox + flipCol(g.position.col, flipped) * c, oy + flipRow(g.position.row, flipped) * c), style = Stroke(3f))
}

/**
 * 终局盖在棋盘上的那两行。
 *
 * 结果那句多数时候很短（红胜、和棋），可「裁决失败，请重开」的英文是三十三个字符，
 * 按原先的字号画出去有屏宽的一倍半，两头全被切掉。现在同样先跟随系统字号，再按弦长收。
 */
private fun DrawScope.drawGameOver(msg: String, tapToReturn: String, c: Float, fontScale: Float, round: Boolean, p: android.graphics.Paint, s: android.graphics.Paint) {
    drawRect(Color(0x99000000))
    val cy = size.height / 2f
    p.apply {
        color = 0xFFFFFFFF.toInt(); textSize = c * 0.9f * fontScale
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    drawTextFitted(msg, cy - c * 1.3f, cy + c * 0.2f, p, round)

    s.apply {
        color = 0xAAFFFFFF.toInt(); textSize = c * 0.38f * fontScale
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = Typeface.SANS_SERIF; isAntiAlias = true
    }
    drawTextFitted(tapToReturn, cy + c * 0.35f, cy + c * 1.05f, s, round)
}
