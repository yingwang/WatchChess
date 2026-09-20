#!/bin/bash
# 表一连上就把包装上去，然后量一次引擎的内存，完事退出。
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK="$HERE/../app/build/outputs/apk/debug/app-debug.apk"
for i in $(seq 1 90); do
  W=$("$HERE/watch-adb.sh" 2>/dev/null) || { sleep 8; continue; }
  echo "[$(date +%H:%M:%S)] watch up as $W"
  adb -s "$W" shell "svc power stayon true" >/dev/null 2>&1
  if adb -s "$W" install -r "$APK" 2>&1 | tail -1 | grep -q Success; then
    echo "installed"
  else
    echo "install failed"; exit 1
  fi
  # 量一次加了 SCUDO 开关之后引擎的常驻内存
  adb -s "$W" shell "pkill -f pikafish" >/dev/null 2>&1
  adb -s "$W" shell "cd /data/local/tmp && SCUDO_OPTIONS=release_to_os_interval_ms=0:may_return_null=true nohup sh -c '{ printf \"setoption name NumaPolicy value none\nsetoption name Threads value 1\nsetoption name Hash value 4\nisready\nposition startpos\ngo movetime 6000\n\"; sleep 14; printf \"quit\n\"; } | ./pikafish > nps.txt 2>&1' >/dev/null 2>&1 &" >/dev/null 2>&1
  sleep 8
  echo "--- engine RSS with SCUDO release ---"
  adb -s "$W" shell "P=\$(pgrep pikafish|head -1); [ -n \"\$P\" ] && grep VmRSS /proc/\$P/status || echo 'engine gone'"
  sleep 14
  echo "--- search result ---"
  adb -s "$W" shell "grep -E 'info depth|bestmove' /data/local/tmp/nps.txt | tail -4"
  exit 0
done
echo "watch never came up"; exit 1
