#!/bin/bash
# 找到这只表现在能用的那个 adb 地址，打印出来；找不到就返回非零。
#
# 表在一台机器上会以几种形式出现，而且换来换去：
#   adb-55141WRCVL16L3-ZoqWxr._adb-tls-connect._tcp   真能连的那个，表关掉无线调试就没了
#   192.168.10.149:<端口>                             端口每次 adbd 重启都变
#   adb-55141WRCVL16L3-ZoqWxr._adb._tcp (5555)        只是个壳子，连过去被拒
#
# 必须逐台核对是不是那只表，不能图省事拿第一台设备就用。这机器上同时还挂着模拟器和
# 一部 USB 连着的手机，早先那版脚本抓了第一台，差点把给表准备的命令打到手机上去。
WATCH_SERIAL_HINT="55141WRCVL16L3"
WATCH_MODEL="Pixel_Watch"

is_watch() {
  case "$1" in *"$WATCH_SERIAL_HINT"*) return 0 ;; esac
  local m
  m=$(timeout 8 adb -s "$1" shell getprop ro.product.model 2>/dev/null | tr -d '\r ')
  case "$m" in *"$WATCH_MODEL"*) return 0 ;; esac
  return 1
}

find_watch() {
  local s
  for s in $(adb devices 2>/dev/null | awk '$2=="device"{print $1}'); do
    case "$s" in emulator-*) continue ;; esac
    if is_watch "$s"; then echo "$s"; return 0; fi
  done
  return 1
}

S=$(find_watch) && { echo "$S"; exit 0; }

for name in $(adb mdns services 2>/dev/null | grep "$WATCH_SERIAL_HINT" | awk '{print $1}'); do
  timeout 10 adb connect "$name" >/dev/null 2>&1
done
S=$(find_watch) && { echo "$S"; exit 0; }

for addr in $(adb mdns services 2>/dev/null | grep "$WATCH_SERIAL_HINT" | awk '{print $NF}'); do
  timeout 10 adb connect "$addr" >/dev/null 2>&1
done
S=$(find_watch) && { echo "$S"; exit 0; }

exit 1
