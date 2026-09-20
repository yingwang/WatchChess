#!/bin/bash
# 自己在表上下一整局，边下边记内存和进程数，看它会不会被系统杀掉。
# 表冠的转动没法用 adb 注入，但「点一下确认」可以。光标每轮都停在第一个可动的子上，
# 所以连点两下就是走一步合法棋。棋下得难看没关系，这是在压测不是在较量。
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
W=$("$HERE/watch-adb.sh") || { echo "watch unreachable"; exit 1; }
LOG="$HERE/../game-stress.log"
: > "$LOG"

adb -s "$W" shell "svc power stayon true; input keyevent KEYCODE_WAKEUP; am force-stop com.yingwang.watchchess" >/dev/null 2>&1
adb -s "$W" logcat -c >/dev/null 2>&1
adb -s "$W" shell "am start -n com.yingwang.watchchess/.MainActivity" >/dev/null 2>&1
sleep 6
adb -s "$W" shell "input tap 213 293" >/dev/null 2>&1   # 中级
sleep 4

for turn in $(seq 1 25); do
  adb -s "$W" shell "input keyevent KEYCODE_WAKEUP; input tap 213 213; sleep 1; input tap 213 213" >/dev/null 2>&1
  sleep 11
  MEM=$(adb -s "$W" shell "grep MemAvailable /proc/meminfo" 2>/dev/null | tr -d '\r')
  ENG=$(adb -s "$W" shell "ps -A -o PID,RSS,NAME 2>/dev/null | grep -c '[p]ikafish'" 2>/dev/null | tr -d '\r')
  RSS=$(adb -s "$W" shell "ps -A -o RSS,NAME 2>/dev/null | grep '[p]ikafish' | awk '{s+=\$1} END {print s}'" 2>/dev/null | tr -d '\r')
  APP=$(adb -s "$W" shell "pgrep -f com.yingwang.watchchess | wc -l" 2>/dev/null | tr -d '\r')
  echo "turn $turn | $MEM | engines=$ENG rss=${RSS}kB | appProcs=$APP" | tee -a "$LOG"
  if [ "$APP" = "0" ]; then
    echo "!! app died at turn $turn" | tee -a "$LOG"
    adb -s "$W" logcat -d 2>/dev/null | grep -E "lowmemorykiller: Kill|watchchess.*died" | tail -4 | tee -a "$LOG"
    break
  fi
done
echo "--- final engine processes ---" | tee -a "$LOG"
adb -s "$W" shell "ps -A -o PID,PPID,RSS,NAME 2>/dev/null | grep '[p]ikafish'" 2>/dev/null | tee -a "$LOG"
