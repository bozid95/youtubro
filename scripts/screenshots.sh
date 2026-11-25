#!/usr/bin/env bash
# Takes README screenshots on a running emulator. Used by .github/workflows/screenshots.yml.
set -euo pipefail

PKG=pro.iyou.music.debug
ACTIVITY="$PKG/pro.iyou.MainActivity"
OUT=docs/screenshots
mkdir -p "$OUT"

adb install -r app/build/outputs/apk/debug/*.apk
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true

# clean status bar: fixed clock, full battery, no notification icons
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" > /dev/null; }
demo enter
demo clock -e hhmm 0941
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e mobile hide
demo notifications -e visible false

shot() {
  sleep "$2"
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "captured $1"
}
open() { adb shell am start -W -n "$ACTIVITY" "$@" > /dev/null; }

open
shot home 30

open -a android.intent.action.VIEW -d "iyou://search?q=lofi"
shot search 15

# a song, opened the way a shared YouTube Music link would be
open -a android.intent.action.VIEW -d "iyou://play?v=${SCREENSHOT_VIDEO:-kJQP7kiw5Fk}"
shot player 30

adb shell input keyevent KEYCODE_BACK
shot miniplayer 4

adb logcat -d -s StreamResolver:* PlaybackService:* AndroidRuntime:E > "$OUT/../screenshots.log" || true
