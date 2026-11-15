#!/usr/bin/env bash
# Makes the Play Store screenshots and feature graphic from scratch: captures every screen from the real app on an
# emulator (StoreScreenshots, an instrumented test), then frames and captions them with render.sh.
#
#   tools/store/screenshots.sh             # uses the running emulator, or starts one with no window
#   AVD=my_avd tools/store/screenshots.sh  # the AVD to start when none is running (default: the first one)
#   ANDROID_SERIAL=emulator-5556 tools/store/screenshots.sh  # that emulator, when several are running
#
# The app is reinstalled with its data cleared, so this only ever runs on an emulator.
# Needs: the Android SDK (ANDROID_HOME or ~/Android/Sdk), JDK 17 for Gradle, and what render.sh needs.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
adb="$sdk/platform-tools/adb"
package=it.sapienza.androidratio.appratio
raw="$root/tools/store/raw"

serial="${ANDROID_SERIAL:-$("$adb" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')}"
if [ -z "$serial" ]; then
  avd="${AVD:-$("$sdk/emulator/emulator" -list-avds | head -1)}"
  [ -n "$avd" ] || { echo "No emulator running and no AVD to start" >&2; exit 1; }
  echo "Starting $avd"
  # No window and a software renderer, so it works without a desktop session.
  nohup "$sdk/emulator/emulator" -avd "$avd" -no-window -gpu swangle_indirect -no-audio -no-boot-anim \
    -no-snapshot-load > "${TMPDIR:-/tmp}/store-emulator.log" 2>&1 &
  for _ in $(seq 90); do
    serial=$("$adb" devices | awk '$1 ~ /^emulator-/ { print $1; exit }')
    [ -n "$serial" ] && [ "$("$adb" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break
    sleep 2
  done
  [ "$("$adb" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] ||
    { echo "The emulator didn't boot, see ${TMPDIR:-/tmp}/store-emulator.log" >&2; exit 1; }
fi
echo "Capturing on $serial"

(cd "$root" && ./gradlew -q assembleDebug assembleDebugAndroidTest)
"$adb" -s "$serial" uninstall "$package" > /dev/null 2>&1 || true
"$adb" -s "$serial" install -r "$root/app/build/outputs/apk/debug/app-debug.apk" > /dev/null
"$adb" -s "$serial" install -r "$root/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" > /dev/null

result=$("$adb" -s "$serial" shell am instrument -w -e store true \
  -e class it.sapienza.androidratio.appratio.StoreScreenshots "$package.test/androidx.test.runner.AndroidJUnitRunner")
grep -q "OK (1 test)" <<< "$result" || { echo "$result" >&2; exit 1; }

mkdir -p "$raw"
for shot in home ai gallery detail ideas form shortcut editor; do
  "$adb" -s "$serial" pull "/sdcard/Android/data/$package/files/store/$shot.png" "$raw/$shot.png" > /dev/null 2>&1
done
echo "Captured $raw"

"$root/tools/store/render.sh"
