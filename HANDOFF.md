# WatchChess 引擎替换 交接说明

给接手的人：下面每一条设备事实都是在真机上实测出来的，不是推断，请不要重新验一遍，那会花掉大半天。

## 任务

给 `~/claude/WatchChess`（Pixel Watch 4 上的 Wear OS 中国象棋）换一个够强的引擎。现在内置的纯 Kotlin alpha-beta 实测每秒只算约 147 个局面，四档难度实际都跑不过第三层，形同虚设。

## 已确认的设备事实

表是 Pixel Watch 4，内存 **1.78 GB**（`MemTotal: 1780844 kB`）。注意别跟存储的 32 GB 混了，那是两回事。

**Android 用户态是 32 位的。** `ro.product.cpu.abilist` 是 `armeabi-v7a`，`abilist64` 为空，`ro.zygote` 是 `zygote32`，系统里既没有 `/system/bin/linker64` 也没有 `/system/lib64`。

**但内核执行得了 AArch64 程序。** 实测把一个静态链接的 arm64 小程序推到 `/data/local/tmp` 跑，正常输出。所以引擎要编成**完全静态的 arm64 可执行文件**，应用自身仍是 32 位进程，把引擎当子进程 exec 起来就行，内核不挑父子进程的位数。

**可执行文件只能放在应用的原生库目录。** API 29 起禁止从应用可写的数据目录 exec。做法是把二进制以 `libXXX.so` 的名义放进 `app/src/main/jniLibs/armeabi-v7a/`，manifest 里写 `android:extractNativeLibs="true"`，gradle 里 `packaging.jniLibs.useLegacyPackaging = true`。实测 PackageManager 不校验 ELF 的机器类型，arm64 的文件照样被释放到 `lib/arm/` 并且能执行。

**内核没有 NUMA 的那套 sysfs**，`/sys/devices/system/node` 根本不存在。Stockfish 一系的引擎自动枚举处理器时会得到空集合，结果线程建起来却不干活，搜索返回零个结点，表现是秒回一步棋、`nodes 0 nps 0`。必须 `setoption name NumaPolicy value none`。这是必需项，不是调优。

**静态链接会把 Scudo 分配器一起带进来**，它默认把释放掉的内存攥着不还给系统。同一份皮卡鱼、同一个网络、同样参数，在 macOS 上常驻 329 MB，在表上是 806 MB，整块记在一个匿名的 `libc_malloc` 区里。启动时设环境变量 `SCUDO_OPTIONS=release_to_os_interval_ms=0:may_return_null=true` 可以降到 128 到 256 MB。

## 皮卡鱼的结论：装不下，不要再试

实测自动下了一局十二手，引擎进程的 RSS 逐手爬升：

```
128 / 192 / 128 / 113 / 171 / 163 / 188 / 256 / 331 / 231 / 284 / 378  (MB)
```

同期表上 `MemAvailable` 从 478 MB 掉到 119 MB，`lowmemorykiller` 把前台的象棋连同 Fitbit、天气、Telewise 一并杀掉。

全程只有一个引擎进程，**不是孤儿进程泄漏**，是搜索随着局面加深而自然变重。官方的网络文件只有一个 48 MB 的版本，没有小号可选，这个地基拆不掉。所以皮卡鱼对这台表就是太重，调参数解决不了。

## 当前卡住的地方：Fairy-Stockfish

它是最合适的替代：支持象棋，`nnue=no` 时靠手写评估函数，在 macOS 上三秒算到第十五层、约 295k nps，内存是个位数的兆，而且不需要那个 48 MB 的网络，APK 能从 77 MB 缩回 5 MB 左右。

仓库已克隆在 `~/claude/fairy-stockfish`。静态 arm64 能编出来：

```bash
cd ~/claude/fairy-stockfish/src
export PATH=~/Library/Android/sdk/ndk/26.1.10909125/toolchains/llvm/prebuilt/darwin-x86_64/bin:$PATH
make -j8 KERNEL=Linux OS=Linux ARCH=armv8 COMP=ndk largeboards=yes nnue=no EXTRALDFLAGS="-static" build
```

`KERNEL=Linux OS=Linux` 是必须的。Stockfish 系的 Makefile 按宿主机判平台，在 macOS 上交叉编译安卓时会把 `-arch armv7`、`-mmacosx-version-min`、`-mdynamic-no-pic` 塞进来，还会加 `-pie`，那几样会让编译直接失败或者编出动态链接的东西。

**但推到表上跑不起来：**

```
error: "./fsf": executable's TLS segment is underaligned:
       alignment is 8, needs to be at least 64 for ARM64 Bionic
```

排查到的事实，按顺序：

1. `llvm-readelf -l` 看到 `PT_TLS` 是 size 8、align 8。这个 TLS 段来自静态链接进来的 NDK 运行时，不是引擎自己的代码（逐个 `.o` 查过，没有一个带 `.tdata` 或 `.tbss`）。
2. 引擎自己只有 `movepick.cpp:62-63` 两个 `thread_local`，默认走的是 **emulated TLS**，符号形如 `__emutls_v.*`，落在堆上，压根不进 TLS 段。所以「加一个 `alignas(64) thread_local` 变量把段对齐顶上去」这条路无效，试过，段对齐纹丝不动；给初值、加 `used`、关掉 LTO 都试过，一样没用。
3. 加 `-fno-emulated-tls` 之后 `PT_TLS` 的对齐确实变成 `0x40`，加载器放行了，但**一启动就 Segmentation fault**，刚打印完 banner、还没处理任何命令就崩。怀疑跟静态 bionic 下原生 TLS 与线程创建的配合有关，没有继续往下查。

请从第 3 点接手。

## 另一条更稳的路

如果上面那个坑啃不动，就把 `~/claude/WatchChess` 里原有的 Kotlin 引擎修快。

它慢的根子**不在搜索**。搜索那一层该有的都有：迭代加深、渴望窗口、置换表配 Zobrist、空着裁剪、后期着法削减、杀手着法、历史表、MVV-LVA 排序、静态搜索，还有四十来个开局定式。慢在棋盘表示：

- `app/src/main/java/com/yingwang/watchchess/model/Board.kt` 用 `mutableMapOf<Position, Piece>()` 存棋子。
- `makeMove` 每次 new 一个新 Board 再 `putAll` 整张表。
- `getAllLegalMoves` 对每一个伪合法着法都调一次 `makeMove` 造测试盘，用来验自将。

一个搜索结点要复制约四十份棋盘、插上千个键值对，时间全花在分配内存上，所以只有每秒 147 个局面。

改成长度 90 的扁平数组、走子改成就地落子再撤回、合法性验证也在原盘上做，**搜索代码一行都不用动**，预计能提两到三个数量级，达到第六到八层。纯 Kotlin，不占内存，不改许可证，APK 不变大。这条路风险最低。

## 已经做好的部分，不要推翻

- **表冠输入**：转表冠挑、点屏幕任意一处确认、右滑取消、长按开菜单。一步棋两下点击。两段候选都来自 `getAllLegalMoves`，所以界面里走不出违规棋，被将时无合法着法的子会被直接跳过，不需要特判也不需要报错弹窗。`ROTARY_STEP = 68`。
- **对方的着法用蓝线标出**：起点空心圈、杆身、终点粗圈。原先只给两格铺一层淡黄，在三毫米的格子上根本看不出来。
- **背景音乐默认开**。
- `ai/PikafishEngine.kt` 与 `ai/UciCoords.kt`：UCI 子进程管理与坐标换算。

**坐标换算规则**（换引擎基本可直接复用，Fairy-Stockfish 的象棋坐标与皮卡鱼一致）：本项目 `row 0` 在最上面（黑方底线），引擎那边 `rank 0` 在最下面（红方底线）。`Position(row, col)` 对应 `('a' + col)` 与 `(9 - row)`。局面串里十组棋子自上而下排列，正好等于 `row 0` 到 `row 9`，不需要倒序。`app/src/test/` 下有单元测试，期望值全部取自引擎自己的输出（`d` 命令打印的局面串、走一步 `b0c2` 之后的局面串、`go perft 1` 列出的着法），不是我推的。

## 许可证

皮卡鱼与 Fairy-Stockfish 都是 GPL-3，而 WatchChess 是 MIT。二进制不进仓库（`.gitignore` 已配好），所以仓库本身不受影响。但只要分发带着它的 APK，那一次分发就落在 GPL-3 之下，整个应用要跟着转成 GPL-3 并提供源码。上架之前必须先把这件事定下来。

## 怎么连上表

表的 adb 通道很不稳，会自己消失。用 `~/claude/WatchChess/scripts/watch-adb.sh`，它会把 mDNS 名字、IP 加端口、5555 三种形式都试一遍，并且核对 `ro.product.model` 确认连上的确实是表。**这一步不能省**：这台机器上同时挂着两个模拟器和一部 USB 连着的 Pixel 4 XL，早先那版脚本图省事取第一台设备，差点把给表准备的命令打到手机上去。

表的无线调试服务会自己撤掉，只剩一个连不上的 `_adb._tcp` 空壳在广播。必要时请机主在表上打开设置、开发者选项、无线调试，并停在那一页不要退出。另外表闲置时会断 Wi-Fi，`ping` 得通但所有端口都不听，那种情况只能等它醒。
