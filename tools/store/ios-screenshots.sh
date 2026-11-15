#!/bin/zsh
# Captures the App Store screens on an iOS simulator (run on the Mac, from a copy of the repository): StoreScreensTests
# stops on each screen and this script shoots it, so the iPhone Duo is shot closed or open with its real status bar.
#
#   tools/store/ios-screenshots.sh <simulator udid> iphone|duo-closed|duo-open <output folder>
#
# The app is reinstalled with no data, and the two mockups are added to the simulator's photos.
# Needs: Xcode, XcodeGen's project in ios/, and ~/tools/duo/duo for the Duo poses.
set -euo pipefail
udid=$1 mode=$2 out=$3
root=$(cd "$(dirname "$0")/../.." && pwd)
work=/tmp/appkin-store
mkdir -p "$out"; rm -rf $work; mkdir -p $work; touch $work/run

xcrun simctl boot $udid 2>/dev/null || true
xcrun simctl bootstatus $udid >/dev/null
xcrun simctl status_bar $udid override --time 9:41 --batteryState charged --batteryLevel 100 --cellularBars 4 --wifiBars 3
xcrun simctl ui $udid appearance light
# Folds the Duo; a second try when the last fold is still "in flight".
fold() { ~/tools/duo/duo $1 $udid -deviceOrientation=portrait </dev/null >/dev/null 2>&1 ||
  { sleep 5; ~/tools/duo/duo $1 $udid -deviceOrientation=portrait </dev/null >/dev/null; }; }
display=()
case $mode in
  duo-closed) fold close; display=(--display=1) ;;
  duo-open) fold open; sleep 5; display=(--display=3) ;;
esac
xcrun simctl uninstall $udid app.wakirox.appkin 2>/dev/null || true
# On a new simulator addmedia waits forever until Photos has set its library up: open it once first (the Duo
# simulator has no Photos app, and doesn't need this).
xcrun simctl launch $udid com.apple.mobileslideshow >/dev/null 2>&1 || true
sleep 10; xcrun simctl terminate $udid com.apple.mobileslideshow 2>/dev/null || true
xcrun simctl addmedia $udid "$root/app/src/androidTest/assets/store/mockup-dark.png"
xcrun simctl addmedia $udid "$root/app/src/androidTest/assets/store/mockup-amoled.png"

export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
(cd "$root/ios" && xcodebuild -project Appkin.xcodeproj -scheme Appkin -destination "id=$udid" \
  -derivedDataPath "$root/ios/build/store" -only-testing:AppkinUITests/StoreScreensTests test > $work/test.log 2>&1) &
test=$!
while kill -0 $test 2>/dev/null; do
  for request in $work/req-*(N); do
    name=${request#$work/req-}
    xcrun simctl io $udid screenshot $display "$out/$name.png" >/dev/null 2>&1
    rm "$request"; touch "$work/done-$name"; echo "shot $name"
  done
  sleep 0.5
done
wait $test || { grep -E "error:|failed" $work/test.log | head; exit 1; }
echo "captured $out"
