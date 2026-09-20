#!/bin/bash
# 表一空闲就关 Wi-Fi，adb 通道跟着断。这个脚本自己重连，接上之后把测量
# 丢到表上后台跑、结果写进表里的文件，所以线再断也不影响它跑完。
OUT=/Users/ying/claude/WatchChess/pikafish-bench.txt
launched=0
for i in $(seq 1 120); do
  PORT=$(adb mdns services 2>/dev/null | grep "55141WRCVL16L3.*_adb-tls-connect" | sed -E 's/.*:([0-9]+).*/\1/' | head -1)
  [ -n "$PORT" ] && timeout 12 adb connect 192.168.10.149:$PORT >/dev/null 2>&1
  W=$(adb devices 2>/dev/null | grep 192.168.10.149 | grep -w device | awk '{print $1}' | head -1)
  if [ -n "$W" ]; then
    if [ "$launched" = 0 ]; then
      echo "[$(date +%H:%M:%S)] connected $W, launching detached bench"
      timeout 30 adb -s "$W" shell "svc power stayon true; pkill -f pikafish; cd /data/local/tmp && nohup sh -c '{ printf \"setoption name NumaPolicy value none\nsetoption name Threads value 1\nsetoption name Hash value 32\nisready\nposition startpos\ngo movetime 8000\n\"; sleep 20; printf \"quit\n\"; } | ./pikafish > bench1.txt 2>&1' >/dev/null 2>&1 &" >/dev/null 2>&1
      launched=1
      sleep 35
    fi
    if timeout 25 adb -s "$W" pull /data/local/tmp/bench1.txt "$OUT" >/dev/null 2>&1; then
      if grep -q bestmove "$OUT" 2>/dev/null; then
        echo "[$(date +%H:%M:%S)] bench complete"
        timeout 20 adb -s "$W" shell "cat /proc/loadavg; dumpsys battery | grep -E 'level|temperature'" 2>/dev/null
        exit 0
      fi
    fi
  fi
  sleep 12
done
echo "timed out waiting for watch"
exit 1
