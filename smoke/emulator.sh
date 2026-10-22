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
echo no | env "JAVA_HOME=$JAVA_HOME" "$AVDM" create avd -n test -k "$IMG" -d pixel_6 --force -v 2>&1 | tail -n 30
if [ ! -f "$HOME/.android/avd/test.ini" ]; then
  echo "avdmanager did not create the AVD: writing it by hand"
  mkdir -p "$HOME/.android/avd/test.avd"
  cat > "$HOME/.android/avd/test.ini" <<INI
avd.ini.encoding=UTF-8
path=$HOME/.android/avd/test.avd
path.rel=avd/test.avd
target=android-${API}
INI
  cat > "$HOME/.android/avd/test.avd/config.ini" <<INI
AvdId=test
PlayStore.enabled=false
abi.type=x86_64
avd.ini.displayname=test
avd.ini.encoding=UTF-8
disk.dataPartition.size=4G
fastboot.forceColdBoot=yes
hw.accelerometer=yes
hw.audioInput=no
hw.audioOutput=no
hw.battery=yes
hw.camera.back=none
hw.camera.front=none
hw.cpu.arch=x86_64
hw.cpu.ncore=4
hw.dPad=no
hw.device.name=pixel_6
hw.gps=yes
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
hw.keyboard=yes
hw.lcd.density=420
hw.lcd.height=2400
hw.lcd.width=1080
hw.mainKeys=no
hw.ramSize=3072
hw.sdCard=no
hw.sensors.orientation=yes
hw.sensors.proximity=yes
hw.trackBall=no
image.sysdir.1=system-images/android-${API}/google_apis/x86_64/
runtime.network.latency=none
runtime.network.speed=full
tag.display=Google APIs
tag.id=google_apis
vm.heapSize=256
INI
fi
ls -la "$HOME/.android/avd" || true
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
PKG=com.kiminonawa.mydiary.fotos

run_steps() {
  # $1 = apk file, $2 = output folder
  export SMOKE_OUT="$2"
  mkdir -p "$SMOKE_OUT"
  adb uninstall "$PKG" > /dev/null 2>&1
  adb install -r "$1" 2>&1 | tee "$SMOKE_OUT/install.txt"
  adb logcat -c
  python3 smoke/steps.py 2>&1 | tee "$SMOKE_OUT/steps-output.txt"
  adb logcat -b crash -d -v time > "$SMOKE_OUT/crash-buffer.txt"
  adb logcat -d -v time | grep -E "kiminonawa|AndroidRuntime|FATAL|StrictMode" | tail -n 400 > "$SMOKE_OUT/logcat-app.txt"
  adb logcat -d -v time "*:E" | grep -vE "WindowManager|InputManager|SurfaceFlinger|ImeTracker" | tail -n 300 > "$SMOKE_OUT/logcat-errors.txt"
}

APK=$(ls apk/*.apk | head -n 1)
run_steps "$APK" smoke-out/new

# Optional: also run the same steps with an older build to compare (url in smoke/baseline-url.txt)
BASE_URL=$(cat smoke/baseline-url.txt 2>/dev/null | tr -d '\r\n ')
if [ -n "$BASE_URL" ]; then
  curl -sL -o /tmp/baseline.apk "$BASE_URL" && run_steps /tmp/baseline.apk smoke-out/base
fi
exit 0
