#!/bin/bash
# 表一回来就把该干的干完，不用人守着。
#
# 这只表的调试通道会自己消失：睡着之后 _adb-tls-connect 那个服务就撤了，只剩一个
# 连不上的 _adb。所以这里反复试所有可能的地址形式，一通就干活，干完就退出。
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT=/Users/ying/claude/WatchChess/pikafish-bench.txt

for i in $(seq 1 240); do
  W=$("$HERE/watch-adb.sh" 2>/dev/null) || { sleep 15; continue; }

  # 已经量完了就直接取回来
  if adb -s "$W" shell "grep -q bestmove /data/local/tmp/nps.txt" >/dev/null 2>&1; then
    adb -s "$W" shell "cat /data/local/tmp/nps.txt" > "$OUT" 2>/dev/null
    echo "bench ready: $(grep -c 'info depth' "$OUT" 2>/dev/null) depth lines"
    grep -E "info depth|bestmove" "$OUT" | tail -4
    exit 0
  fi

  # 没量完就重新丢一次，脱离终端跑，线断了也不影响
  echo "[$(date +%H:%M:%S)] watch back as $W, launching bench"
  adb -s "$W" shell "svc power stayon true; pkill -f pikafish; cd /data/local/tmp && nohup sh -c '{ printf \"setoption name NumaPolicy value none\nsetoption name Threads value 1\nsetoption name Hash value 32\nisready\nposition startpos\ngo movetime 8000\n\"; sleep 15; printf \"quit\n\"; } | ./pikafish > nps.txt 2>&1' >/dev/null 2>&1 &" >/dev/null 2>&1
  sleep 30
done
echo "gave up waiting for the watch"
exit 1
