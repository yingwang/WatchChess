# WatchChess 象棋

Chinese Chess (Xiangqi) for Wear OS — play on your wrist.

<p align="center">
  <img src="screenshot_menu.png" width="200" alt="Menu" />
  <img src="screenshot_game.png" width="200" alt="Game Board" />
</p>

## Features

- Full Chinese Chess board optimized for round watch displays
- AI opponent with 4 difficulty levels (alpha-beta search with opening book)
- Move & capture sound effects
- Haptic feedback on every interaction
- Game timer & move counter
- Undo (reverts both your move and AI's response)
- Background music (toggleable)
- Long press to open in-game menu

## Controls

| Action | Gesture |
|--------|---------|
| Move the highlight | Rotate the crown |
| Confirm the highlight | Tap anywhere on screen |
| Cancel a selection | Swipe right (back) |
| Open menu | Long press |
| Reset after game over | Tap anywhere |

A move takes two taps: turn the crown to highlight one of your movable pieces and
tap to lock it in, then turn again to highlight a destination and tap to play it.
Tap position is never used, so no aiming is required on a 3 mm grid. Only legal
candidates are ever highlighted, so an illegal move cannot be entered.

## AI Engine

Games are played by **Pikafish**, run as a static arm64 subprocess. The watch's
Android userspace is 32-bit, but its kernel executes AArch64 binaries, so the
engine ships as a fully static arm64 executable while the app itself stays
32-bit. Two device quirks are worked around in code: `NumaPolicy` must be set
to `none`, because this kernel has no NUMA sysfs and the engine's processor
enumeration otherwise comes back empty and searches zero nodes; and the binary
has to live in `jniLibs` with `extractNativeLibs` enabled, because API 29+
forbids executing anything from the app's writable data directory.

Difficulty caps **nodes**, not depth, so a level means the same playing strength
on any CPU. The time limit is only a backstop so a move cannot cook the watch.

Run `scripts/fetch-engine.sh` before building - it cross-compiles the engine and
downloads the matching network. Neither artefact is committed.

> **Licensing.** Pikafish derives from Stockfish and is GPL-3. The binary is not
> in this repository, so the repository itself is unaffected. But distributing an
> APK that contains it - publishing, or just handing the file to someone - puts
> that distribution under GPL-3, which would require relicensing this app and
> offering its source. Settle that before shipping a build.

The original engine is kept as a fallback and answers whenever Pikafish fails to
start or dies mid-game. It is pure Kotlin alpha-beta pruning with:
- Iterative deepening & aspiration windows
- Transposition tables (Zobrist hashing)
- Null move pruning & late move reductions
- Killer move & history heuristics
- MVV-LVA move ordering
- Quiescence search
- Opening book (~40 patterns)

## Requirements

- Wear OS 3.0+ (API 30)
- Tested on Pixel Watch 4

## Build

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## See Also

- [chinese_chess_mobile](https://github.com/yingwang/chinese_chess_mobile) — 手机版中国象棋 (Android)
- [XiangqiBot](https://github.com/yingwang/XiangqiBot) — 象棋 AI 对弈 Bot (Android)

---

# 手表象棋

Wear OS 中国象棋 — 在手腕上下棋。

## 功能

- 适配圆形手表屏幕的完整象棋棋盘
- AI 对手，4 个难度等级（Alpha-Beta 搜索 + 开局库）
- 走子/吃子音效
- 触觉震动反馈
- 计时器 & 步数统计
- 悔棋（同时撤回你和 AI 的最后一步）
- 背景音乐（可开关）
- 长按呼出游戏内菜单

## 操作

| 操作 | 手势 |
|------|------|
| 选棋子 | 点击 |
| 走子 | 点击绿点位置 |
| 打开菜单 | 长按 |
| 结束后重开 | 点击任意位置 |

## 编译

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```
