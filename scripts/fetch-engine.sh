#!/bin/bash
# 取回并编出这只表用得上的皮卡鱼。
#
# 产物两样，都不在仓库里（见 .gitignore），每次要打包前跑一次即可：
#   app/src/main/jniLibs/armeabi-v7a/libpikafish.so   静态 arm64 可执行文件
#   app/src/main/assets/pikafish.nnue                 与该版本配套的网络，约五十兆
#
# 为什么是 arm64，而这只表明明是三十二位的：
#   Pixel Watch 4 的安卓层确实只有 armeabi-v7a，abilist64 是空的，系统里既没有
#   linker64 也没有 /system/lib64。但象棋要九十格的位棋盘，皮卡鱼的 Bitboard 是
#   __uint128_t，三十二位的 clang 不提供这个类型，armv7 目标连编都编不过。
#   好在内核是肯执行 AArch64 程序的（拿一个静态的小程序在表上实测过），所以做法是
#   编成完全静态的 arm64 可执行文件，不依赖系统里任何六十四位的库，应用自身仍是
#   三十二位，把它当子进程叫起来说话。
#
# 为什么要 KERNEL=Linux OS=Linux：
#   皮卡鱼的 Makefile 按宿主机判平台，在 macOS 上交叉编译安卓时会把 -arch armv7、
#   -mmacosx-version-min、-mdynamic-no-pic 这些宿主机的参数塞进来，还会加 -pie。
#   把这两个变量按目标平台写死，那些参数就不会出现，-static 也才留得住。
#
# 许可证：皮卡鱼派生自 Stockfish，按 GPL-3 发布。编出来的二进制只留在本地，不进这个
#   仓库，所以仓库本身不受影响。但只要把带着它的安装包发给别人（上架、传给朋友都算），
#   那一份分发就落在 GPL-3 之下，整个应用要跟着转成 GPL-3 并提供源码。发包之前先想清楚。

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PIKAFISH_SRC="${PIKAFISH_SRC:-$HOME/claude/pikafish}"
NDK="${ANDROID_NDK:-$HOME/Library/Android/sdk/ndk/26.1.10909125}"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin"

if [ ! -d "$PIKAFISH_SRC" ]; then
  echo "皮卡鱼源码不在 $PIKAFISH_SRC，先克隆一份：" >&2
  echo "  git clone https://github.com/official-pikafish/Pikafish.git $PIKAFISH_SRC" >&2
  exit 1
fi
if [ ! -d "$TOOLCHAIN" ]; then
  echo "找不到 NDK 工具链：$TOOLCHAIN" >&2
  echo "装一个 NDK，或者用 ANDROID_NDK 指到你自己的那份。" >&2
  exit 1
fi

cd "$PIKAFISH_SRC"
echo "== 更新源码 =="
git fetch origin --quiet
git merge --ff-only origin/master
echo "当前版本：$(git log --oneline -1)"

cd src
echo "== 取配套网络 =="
make net

echo "== 交叉编译（静态 arm64）=="
export PATH="$TOOLCHAIN:$PATH"
make clean >/dev/null
make -j"$(sysctl -n hw.ncpu)" KERNEL=Linux OS=Linux ARCH=armv8 COMP=ndk EXTRALDFLAGS="-static" build
"$TOOLCHAIN/llvm-strip" pikafish

if ! file pikafish | grep -q "statically linked"; then
  echo "编出来的不是静态的，装到表上会因为没有 linker64 而起不来：" >&2
  file pikafish >&2
  exit 1
fi
if ! file pikafish | grep -q "ARM aarch64"; then
  echo "编出来的不是 arm64：" >&2
  file pikafish >&2
  exit 1
fi

echo "== 放进工程 =="
mkdir -p "$REPO_ROOT/app/src/main/jniLibs/armeabi-v7a" "$REPO_ROOT/app/src/main/assets"
cp pikafish "$REPO_ROOT/app/src/main/jniLibs/armeabi-v7a/libpikafish.so"
cp pikafish.nnue "$REPO_ROOT/app/src/main/assets/pikafish.nnue"

echo
echo "完成："
ls -la "$REPO_ROOT/app/src/main/jniLibs/armeabi-v7a/libpikafish.so" "$REPO_ROOT/app/src/main/assets/pikafish.nnue"
