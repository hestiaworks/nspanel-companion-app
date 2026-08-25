#!/usr/bin/env bash
# Measure the app on a panel, reproducibly: responsiveness first, memory second.
#
# This app exists because a WebView-based dashboard was unusable on this
# hardware, so frame timing is the number that matters. A build can be cheap in
# memory and still miss every frame deadline.
#
# The comparison between two builds is only meaningful if both are measured the
# same way. Three false results during the Compose evaluation came from varying
# the protocol by accident: a fresh process compared against one that had run
# for hours, a crash-looping build, and a display that had gone to sleep.
#
#   ./tools/measure-panel.sh <address> <label> [runs]
set -euo pipefail

ADDRESS="${1:?Usage: measure-panel.sh <address> <label> [runs]}"
LABEL="${2:?Usage: measure-panel.sh <address> <label> [runs]}"
RUNS="${3:-3}"
PACKAGE="dev.hacompanion.panel"
SERIAL="${ADDRESS}:5555"

adb connect "$SERIAL" >/dev/null 2>&1 || true
adb -s "$SERIAL" get-state >/dev/null

on_device_sleep() { adb -s "$SERIAL" shell sleep "$1"; }

wake() { adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true; }

# A cold start each time: a long-running process accumulates and is not
# comparable with one that has just launched. The display is held awake because
# a sleeping screen releases surfaces and understates both numbers.
cold_start_ms() {
  adb -s "$SERIAL" shell am force-stop "$PACKAGE"
  on_device_sleep 3
  wake
  adb -s "$SERIAL" shell am start -W -n "$PACKAGE/.MainActivity" 2>/dev/null |
    awk -F': *' '/^TotalTime/ {print $2}'
}

# Fixed interaction sequence, so frame counts are comparable between builds.
# Enough interactions that a handful of slow frames does not swing the result:
# the panel renders nothing while idle, so frame count tracks real work.
exercise() {
  for _ in $(seq 1 14); do
    adb -s "$SERIAL" shell input swipe 400 240 80 240 200 >/dev/null 2>&1
    on_device_sleep 1
  done
  for _ in $(seq 1 8); do
    adb -s "$SERIAL" shell input tap 240 240 >/dev/null 2>&1
    on_device_sleep 1
  done
}

read_metric() { adb -s "$SERIAL" shell "dumpsys gfxinfo $PACKAGE 2>/dev/null | grep '$1' || true" | head -1; }

crashed() {
  adb -s "$SERIAL" shell "logcat -d -t 400 2>/dev/null | grep -c 'FATAL EXCEPTION' || true" | tr -d ' \r'
}

echo "measuring $LABEL on $ADDRESS ($RUNS runs)"
echo "  installed: $(adb -s "$SERIAL" shell "dumpsys package $PACKAGE | grep versionName" | head -1 | tr -d ' \r')"

starts=(); jank=(); p95=(); p99=(); pss=()
for run in $(seq 1 "$RUNS"); do
  start_ms="$(cold_start_ms)"
  on_device_sleep 8
  # Reset so the counters describe the interaction, not the launch.
  adb -s "$SERIAL" shell dumpsys gfxinfo "$PACKAGE" reset >/dev/null 2>&1
  exercise
  wake
  on_device_sleep 5

  total_frames="$(read_metric 'Total frames rendered' | awk -F': ' '{print $2}' | tr -d ' \r')"
  janky_pct="$(read_metric 'Janky frames' | sed 's/.*(\(.*\)%).*/\1/' | tr -d ' \r')"
  pct95="$(read_metric '95th percentile' | awk -F': ' '{print $2}' | tr -d ' \r')"
  pct99="$(read_metric '99th percentile' | awk -F': ' '{print $2}' | tr -d ' \r')"

  on_device_sleep 10
  mem="$(adb -s "$SERIAL" shell "dumpsys meminfo $PACKAGE | grep 'TOTAL:' || true" 2>/dev/null | head -1 | awk '{print $2}')"

  note=""
  # A build that crashes reports plausible numbers for an app doing nothing.
  [ "$(crashed)" != "0" ] && note="  *** FATAL EXCEPTION in log - run is not valid ***"

  printf '  run %s: start %sms | frames %s, janky %s%%, p95 %s, p99 %s | pss %s kB%s\n' \
    "$run" "$start_ms" "$total_frames" "$janky_pct" "$pct95" "$pct99" "$mem" "$note"

  starts+=("$start_ms"); jank+=("$janky_pct"); p95+=("$pct95"); p99+=("$pct99"); pss+=("$mem")
done

mean() { printf '%s\n' "$@" | awk '{gsub(/[^0-9.]/,""); if ($0 != "") {t+=$0; n++}} END {if (n) printf "%.1f", t/n; else printf "?"}'; }
printf '  mean: start %sms | janky %s%% | p95 %sms | p99 %sms | pss %s kB\n' \
  "$(mean "${starts[@]}")" "$(mean "${jank[@]}")" "$(mean "${p95[@]}")" "$(mean "${p99[@]}")" "$(mean "${pss[@]}")"
