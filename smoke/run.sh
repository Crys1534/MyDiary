#!/usr/bin/env bash
# Runs inside the emulator: installs the APK and drives the app (see smoke/steps.py).
set -x
mkdir -p smoke-out
APK=$(ls apk/*.apk | head -n 1)
adb install -r "$APK" 2>&1 | tee smoke-out/install.txt
adb shell getprop ro.build.version.release > smoke-out/android-version.txt
adb logcat -c
python3 smoke/steps.py 2>&1 | tee smoke-out/steps-output.txt
adb logcat -d -v time > smoke-out/logcat-full.txt
grep -E "FATAL EXCEPTION|AndroidRuntime|Process: com.kiminonawa|ANR in|UnsatisfiedLink|NoClassDefFound|NoSuchMethod|ClassNotFound|InflateException" -A 12 smoke-out/logcat-full.txt | head -300 > smoke-out/crashes.txt || true
exit 0
