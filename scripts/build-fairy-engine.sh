#!/bin/bash
# 只在本地编译和打包，不拉取源码、不安装、不触碰正在运行的应用。
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FAIRY_SRC="${FAIRY_SRC:-/Users/ying/claude/fairy-stockfish}"
FAIRY_REV=2e591089558a5afa72ab5a22192208e71848a30c
NDK="${ANDROID_NDK:-/Users/ying/Library/Android/sdk/ndk/26.1.10909125}"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin"
test -x "$TOOLCHAIN/aarch64-linux-android30-clang++"
git -C "$FAIRY_SRC" cat-file -e "$FAIRY_REV^{commit}"
mkdir -p "$ROOT/build"
FAIRY_BUILD="$(mktemp -d "$ROOT/build/fairy-engine.XXXXXX")"
# 使用固定版本，保留上游工作树里已有的 TLS 实验，不修改它。
git -C "$FAIRY_SRC" archive "$FAIRY_REV" src | tar -x -C "$FAIRY_BUILD"
make -C "$FAIRY_BUILD/src" -f Makefile -f "$ROOT/scripts/fairy-android.mk" -j8 \
    KERNEL=Linux OS=Linux ARCH=armv8 COMP=ndk \
    CXX="$TOOLCHAIN/aarch64-linux-android30-clang++" \
    largeboards=yes nnue=no EXTRALDFLAGS=-static all > "$FAIRY_BUILD/build.log" 2>&1
"$TOOLCHAIN/llvm-readelf" -l "$FAIRY_BUILD/src/stockfish" > "$FAIRY_BUILD/segments.txt"
python3 "$ROOT/scripts/check-fairy-elf.py" "$FAIRY_BUILD/src/stockfish"
mkdir -p "$ROOT/app/src/main/jniLibs/armeabi-v7a"
cp "$FAIRY_BUILD/src/stockfish" "$ROOT/app/src/main/jniLibs/armeabi-v7a/libfairystockfish.so"
"$TOOLCHAIN/llvm-strip" "$ROOT/app/src/main/jniLibs/armeabi-v7a/libfairystockfish.so"
printf 'Source: %s\nBuild: %s\n' "$FAIRY_REV" "$FAIRY_BUILD"
ls -lh "$ROOT/app/src/main/jniLibs/armeabi-v7a/libfairystockfish.so"
