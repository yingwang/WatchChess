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
| Select piece | Tap |
| Move piece | Tap legal position (green dot) |
| Open menu | Long press |
| Reset after game over | Tap anywhere |

## AI Engine

Pure Kotlin alpha-beta pruning with:
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
