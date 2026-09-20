#!/bin/bash
# 生成「吃」和「将军」两句报子音，放进 res/raw。
# 单字「将」在普通话里有 jiāng 和 jiàng 两读，合成出来听着含糊，所以报的是「将军」，
# 也是棋盘上真正会喊的那一句。
set -euo pipefail
OUT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../app/src/main/res/raw" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
gen() {  # gen <文本> <文件名>
  python3 -m edge_tts --voice zh-CN-YunxiNeural --rate=+10% --text "$1" --write-media "$TMP/$2.mp3"
  # 掐掉首尾静音再提一点音量，表上的小喇叭本来就不响；转成短 wav 是因为 SoundPool
  # 对小 wav 起播最快，报子音晚半拍就失去意义了。
  ffmpeg -y -i "$TMP/$2.mp3" -af \
    "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.02,areverse,silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.02,areverse,volume=2.0" \
    -ar 22050 -ac 1 -c:a pcm_s16le "$OUT/$2.wav" >/dev/null 2>&1
  printf '%s -> %s (%s 秒)\n' "$1" "$OUT/$2.wav" \
    "$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$OUT/$2.wav")"
}
gen "吃" say_capture
gen "将军" say_check
