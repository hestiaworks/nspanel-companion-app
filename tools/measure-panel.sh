#!/usr/bin/env bash
# Measure the app's resident memory on a panel, reproducibly.
#
# The comparison between two builds is only meaningful if both are measured the
# same way. Three false results during the Compose evaluation came from varying
# the protocol by accident: a fresh process compared against one that had run
# for hours, a crash-looping build, and a display that had gone to sleep.
#
#   ./tools/measure-panel.sh <address> <label> [runs]
#
# Prints one line per run and a mean, in kB of total PSS.
set -euo pipefail

ADDRESS="${1:?Usage: measure-panel.sh <address> <label> [runs]}"
LABEL="${2:?Usage: measure-panel.sh <address> <label> [runs]}"
RUNS="${3:-3}"
PACKAGE="dev.hacompanion.panel"
SERIAL="${ADDRESS}:5555"

adb connect "$SERIAL" >/dev/null 2>&1 || true
adb -s "$SERIAL" get-state >/dev/null

on_device_sleep() { adb -s "$SERIAL" shell sleep "$1"; }

measure_once() {
  # A cold start each time: a long-running process accumulates and is not
  # comparable with one that has just launched.
  adb -s "$SERIAL" shell am force-stop "$PACKAGE"
  on_device_sleep 3
  # The panel honours the system display timeout. A sleeping screen releases
  # surfaces and understates the footprint, so hold it awake throughout.
  adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
  adb -s "$SERIAL" shell am start -n "$PACKAGE/.MainActivity" >/dev/null 2>&1
  on_device_sleep 10
  # Visit every page so each one is actually built and drawn.
  for _ in 1 2 3 4; do
    adb -s "$SERIAL" shell input swipe 400 240 80 240 200 >/dev/null 2>&1
    on_device_sleep 3
  done
  adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
  on_device_sleep 15
  adb -s "$SERIAL" shell "dumpsys meminfo $PACKAGE | grep 'TOTAL:' || true" 2>/dev/null |
    head -1 | awk '{print $2}'
}

echo "measuring $LABEL on $ADDRESS ($RUNS runs)"
version="$(adb -s "$SERIAL" shell "dumpsys package $PACKAGE | grep versionName" | head -1 | tr -d ' \r')"
echo "  installed: $version"

values=()
for run in $(seq 1 "$RUNS"); do
  value="$(measure_once)"
  # A build that crashes reports a plausible number for an app doing nothing.
  crashes="$(adb -s "$SERIAL" shell "logcat -d -t 200 2>/dev/null | grep -c 'FATAL EXCEPTION' || true" | tr -d ' \r')"
  if [ "${crashes:-0}" != "0" ]; then
    echo "  run $run: $value kB  *** $crashes FATAL EXCEPTION in log - result is not valid ***"
  else
    echo "  run $run: $value kB"
  fi
  values+=("$value")
done

printf '  mean: %s kB\n' "$(printf '%s\n' "${values[@]}" | awk '{t+=$1} END {printf "%.0f", t/NR}')"
