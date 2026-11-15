#!/bin/bash
# The Fold's four shapes at its own dp (the emulator can't go past 1920 px, so the density is lowered
# to match): cover 411×960 dp and main 859×896 dp, each upright and turned. In each the probe opens
# from a fresh start, is photographed, and must not have crashed.
PKG=com.cerniera.giano
exec > >(tee walk.txt) 2>&1
adb install -r Giano.apk
adb logcat -c
fail=0
shape() { # name w h (px at density 280, i.e. 1.75 px per dp)
  adb shell wm size "$2x$3"; adb shell wm density 280; sleep 2
  adb shell am force-stop $PKG
  adb shell am start -W -n $PKG/.MainActivity >/dev/null
  sleep 6
  adb exec-out screencap -p > "shot-$1.png"
  if adb logcat -d | grep -A2 "FATAL EXCEPTION" | grep -q "Process: $PKG"; then echo "CRASH in $1"; fail=1; else echo "ok: $1"; fi
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml | grep -o 'text="[^"]*"' | head -12 | sed "s/^/  $1: /"
}
shape cover-upright 719 1680
shape cover-turned 1680 719
shape main-upright 1503 1568
shape main-turned 1568 1503
{ echo "Giano log"; adb logcat -d -s Giano:I AndroidRuntime:E; } > app-log.txt
adb shell wm size reset; adb shell wm density reset
exit $fail
