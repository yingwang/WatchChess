# WatchChess 象棋

<p align="center"><img src="store-assets/icon-512.png" width="128" alt="WatchChess icon" /></p>

Chinese Chess (Xiangqi) for Wear OS — play on your wrist.

<p align="center">
  <img src="screenshot_menu.png" width="200" alt="Menu" />
  <img src="screenshot_game.png" width="200" alt="Game Board" />
</p>

## Features

- Full Chinese Chess board optimized for round watch displays
- AI opponent with 4 difficulty levels, played by a bundled Fairy-Stockfish
- Crown-driven move entry, so nothing depends on hitting a 3 mm target
- A how-to-play card on first launch, and from the in-game menu afterwards
- Plays entirely offline: no account, no network, no data collected
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

Games are played by a patched **Fairy-Stockfish**, run as a static arm64
subprocess. The watch's
Android userspace is 32-bit, but its kernel executes AArch64 binaries, so the
engine ships as a fully static arm64 executable while the app itself stays
32-bit. Two device quirks are worked around in code: `NumaPolicy` must be set
to `none`, because this kernel has no NUMA sysfs and the engine's processor
enumeration otherwise comes back empty and searches zero nodes; and the binary
has to live in `jniLibs` with `extractNativeLibs` enabled, because API 29+
forbids executing anything from the app's writable data directory.

Difficulty caps **nodes**, not depth, so a level means the same playing strength
on any CPU. The time limit is only a backstop so a move cannot cook the watch.

Run `scripts/build-fairy-engine.sh` before building. It cross-compiles the engine
from a pinned upstream revision and applies `scripts/fairy-adjudicate.patch`, which
adds the `watchresult` command the app uses to adjudicate repetitions. **A stock
Fairy-Stockfish build will not work**: without that command every position reports
as unadjudicable and the game stops with 裁决失败. The binary is not committed.

The original engine is kept as a fallback and answers whenever Fairy-Stockfish
fails to start or dies mid-game. It is pure Kotlin alpha-beta pruning with:
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

- [chinese_chess_mobile](https://github.com/yingwang/chinese_chess_mobile)：手机版中国象棋 (Android)
- [XiangqiBot](https://github.com/yingwang/XiangqiBot)：象棋 AI 对弈 Bot (Android)

---

# 手表象棋

Wear OS 上的中国象棋，一整局都在表上下完。

## 功能

- 九路十行的完整棋盘，按圆形表盘排布
- 对手是随包带的 Fairy-Stockfish，四档难度
- 转表冠选子选位置，不需要往三毫米的格子上戳
- 首次开局弹一次帮助卡，之后从长按菜单里随时再看
- 完全离线，不用账号，不用联网，不收集任何数据
- 走子与吃子音效，将军有报子音
- 每一步都有震动反馈
- 计时与步数
- 悔棋，连同引擎的应手一起撤回
- 背景音乐可开关
- 长按棋盘呼出对局菜单

## 操作

| 操作 | 手势 |
|------|------|
| 移动高亮 | 转动表冠 |
| 确认高亮处 | 点屏幕任意位置 |
| 取消选中 | 向右滑动 |
| 打开菜单 | 长按 |
| 终局后重开 | 点屏幕任意位置 |

走一步棋要点两次。先转表冠，高亮在你能动的子之间移动，点一下把这只子定下来；再转，高亮改在这只子能去的位置之间移动，再点一下，这步棋就走了。全程不看你点在哪里，所以不需要瞄准。候选里只会出现合法的着法，走不出违规的棋。

## 编译

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

引擎二进制不进仓库，编译前先跑 `scripts/build-fairy-engine.sh` 把它取下来编好。

## 许可

GPL-3。应用打包了 Fairy-Stockfish，它源自 Stockfish，按同样的条款分发。
