#!/bin/bash
# 表一回来就装包并截一张菜单图，装好即退出。表的 adb 通道会自己消失，所以这里反复等。
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK="$HERE/../app/build/outputs/apk/debug/app-debug.apk"
SHOT="$HERE/../build/menu-after-fix.png"
mkdir -p "$(dirname "$SHOT")"
for i in $(seq 1 150); do
  W=$("$HERE/watch-adb.sh" 2>/dev/null) || { sleep 8; continue; }
  echo "[$(date +%H:%M:%S)] watch up as $W"
  adb -s "$W" install -r "$APK" 2>&1 | tail -1
  adb -s "$W" shell "input keyevent KEYCODE_WAKEUP; svc power stayon true; am force-stop com.yingwang.watchchess; am start -n com.yingwang.watchchess/.MainActivity" >/dev/null 2>&1
  sleep 6
  adb -s "$W" shell "input tap 213 220" >/dev/null 2>&1      # 初级
  sleep 4
  adb -s "$W" shell "input swipe 213 213 213 213 800" >/dev/null 2>&1   # 长按开菜单
  sleep 2
  adb -s "$W" exec-out screencap -p > "$SHOT" 2>/dev/null
  adb -s "$W" shell "svc power stayon false" >/dev/null 2>&1
  echo "installed; screenshot at $SHOT ($(wc -c < "$SHOT") bytes)"
  exit 0
done
echo "watch never came back"; exit 1
