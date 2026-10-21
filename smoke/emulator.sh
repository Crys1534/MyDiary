#!/usr/bin/env bash
# Starts an Android emulator, installs the APK and drives the app (smoke/steps.py).
# Everything is logged in smoke-out/ so a failure can be diagnosed from the smoke-logs branch.
set +e
mkdir -p smoke-out
exec > >(tee -a smoke-out/emu-setup.log) 2>&1
API="${SMOKE_API:-34}"
IMG="system-images;android-${API};google_apis;x86_64"
echo "== machine"; uname -a; nproc; free -m; ls -l /dev/kvm; id
SDK="${ANDROID_HOME:-/usr/local/lib/android/sdk}"
SDKM=$(ls "$SDK"/cmdline-tools/*/bin/sdkmanager 2>/dev/null | head -n 1)
AVDM=$(ls "$SDK"/cmdline-tools/*/bin/avdmanager 2>/dev/null | head -n 1)
echo "SDK=$SDK SDKM=$SDKM AVDM=$AVDM JAVA_HOME=$JAVA_HOME"
yes | sudo env "JAVA_HOME=$JAVA_HOME" "$SDKM" --licenses > /dev/null
echo "== install emulator + image $IMG"
sudo env "JAVA_HOME=$JAVA_HOME" "$SDKM" "emulator" "platform-tools" "$IMG" 2>&1 | tail -n 15
ls "$SDK/emulator" | head
echo "== create avd"
echo no | env "JAVA_HOME=$JAVA_HOME" "$AVDM" create avd -n test -k "$IMG" -d pixel_6 --force
echo "== start emulator"
nohup "$SDK/emulator/emulator" -avd test -no-window -no-audio -no-boot-anim -no-snapshot \
  -gpu swiftshader_indirect -accel on > smoke-out/emulator.log 2>&1 &
export PATH="$SDK/platform-tools:$PATH"
adb start-server
timeout 120 adb wait-for-device
BOOTED=""
for i in $(seq 1 90); do
  BOOTED=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  [ "$BOOTED" = "1" ] && break
  sleep 5
done
echo "boot_completed=$BOOTED"
if [ "$BOOTED" != "1" ]; then
  echo "EMULATOR DID NOT BOOT"; tail -n 40 smoke-out/emulator.log; exit 0
fi
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell getprop ro.build.version.release > smoke-out/android-version.txt
APK=$(ls apk/*.apk | head -n 1)
adb install -r "$APK" 2>&1 | tee smoke-out/install.txt
adb logcat -c
python3 smoke/steps.py 2>&1 | tee smoke-out/steps-output.txt
adb logcat -d -v time > smoke-out/logcat-full.txt
grep -E "FATAL EXCEPTION|AndroidRuntime|ANR in|UnsatisfiedLink|NoClassDefFound|NoSuchMethod|ClassNotFound|InflateException" -A 14 smoke-out/logcat-full.txt | head -300 > smoke-out/crashes.txt
tail -n 3000 smoke-out/logcat-full.txt > smoke-out/logcat-tail.txt
rm -f smoke-out/logcat-full.txt
exit 0
