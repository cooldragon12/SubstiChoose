#!/bin/bash
# SubstiChoose: build, install and launch on the plugged-in phone.
# Run from VS Code's terminal (./build-and-run.command) or double-click in Finder.
# Everything is also written to build-log.txt so Claude can read it.
cd "$(dirname "$0")"
exec > >(tee "build-log.txt") 2>&1
echo "=== SubstiChoose build $(date) ==="

# Pick up ANDROID_HOME / PATH from your shell profile (a Finder double-click doesn't load it).
if [ -z "$ANDROID_HOME" ] && [ -x /bin/zsh ]; then
  eval "$(/bin/zsh -ic 'echo export ANDROID_HOME=\"$ANDROID_HOME\"; echo export SHELL_PATH=\"$PATH\"' 2>/dev/null)"
  [ -n "$SHELL_PATH" ] && export PATH="$SHELL_PATH:$PATH"
fi

# ---------- 1. Java: Gradle 8.11 + AGP 8.9 need JDK 17-23 (JDK 24+ / 27 will NOT work) ----------
java_major() { "$1/bin/java" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+)(\.[0-9]+)*.*/\1/; s/^1$/8/'; }
echo "--- JDKs installed on this Mac ---"
/usr/libexec/java_home -V 2>&1 | sed 's/^/  /'
CANDIDATES=(
  "$SUBSTI_JAVA_HOME"
  "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  "$(/usr/libexec/java_home -v 21 2>/dev/null)"
  "$(/usr/libexec/java_home -v 17 2>/dev/null)"
  "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
  "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
  "/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
  "/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
)
for d in /Library/Java/JavaVirtualMachines/*/Contents/Home "$HOME/Library/Java/JavaVirtualMachines"/*/Contents/Home; do CANDIDATES+=("$d"); done
JDK=""
for c in "${CANDIDATES[@]}"; do
  [ -n "$c" ] && [ -x "$c/bin/java" ] || continue
  m=$(java_major "$c")
  if [ "$m" -ge 17 ] 2>/dev/null && [ "$m" -le 23 ]; then JDK="$c"; break; fi
done
if [ -z "$JDK" ]; then
  echo "ERROR: No JDK 17-23 found. Your default Java is: $(java -version 2>&1 | head -1)"
  echo "Install JDK 21 (it can sit alongside JDK 27), then run this again:"
  echo "    brew install --cask temurin@21"
  echo "or download 'Temurin 21' from adoptium.net."
  exit 1
fi
export JAVA_HOME="$JDK"
echo "Using JAVA_HOME=$JAVA_HOME ($("$JAVA_HOME/bin/java" -version 2>&1 | head -1))"

# ---------- 2. Android SDK -> local.properties ----------
SDK=""
for c in "$ANDROID_HOME" "$ANDROID_SDK_ROOT" "$HOME/Library/Android/sdk" \
         "/opt/homebrew/share/android-commandlinetools" "/usr/local/share/android-commandlinetools" \
         "$HOME/Android/Sdk" "$HOME/android-sdk"; do
  [ -n "$c" ] && [ -d "$c/platforms" -o -d "$c/platform-tools" ] && { SDK="$c"; break; }
done
if [ -z "$SDK" ]; then
  echo "ERROR: Android SDK not found. Set ANDROID_HOME in ~/.zshrc or put sdk.dir=<path> in local.properties."
  [ -f local.properties ] && grep -q sdk.dir local.properties && SDK=$(sed -n 's/^sdk.dir=//p' local.properties)
  [ -z "$SDK" ] && exit 1
fi
echo "sdk.dir=$SDK" > local.properties
export ANDROID_HOME="$SDK"
export PATH="$SDK/platform-tools:$PATH"
echo "SDK: $SDK"
echo "Platforms: $(ls "$SDK/platforms" 2>/dev/null | tr '\n' ' ')"
echo "Build-tools: $(ls "$SDK/build-tools" 2>/dev/null | tr '\n' ' ')"
echo "adb: $(command -v adb || echo 'NOT FOUND')"

# ---------- 3. Build ----------
chmod +x gradlew
./gradlew --stop >/dev/null 2>&1   # drop any daemon started with the wrong Java
echo "=== assembleDebug ==="
./gradlew assembleDebug --console=plain
if [ $? -ne 0 ]; then echo "=== BUILD FAILED ==="; exit 1; fi
echo "=== BUILD OK ==="

# ---------- 4. Install + launch on the phone ----------
echo "=== adb devices ==="
adb devices -l
if [ "$(adb get-state 2>/dev/null)" = "device" ]; then
  echo "=== installDebug ==="
  ./gradlew installDebug --console=plain || { echo "=== INSTALL FAILED ==="; exit 1; }
  adb shell monkey -p com.substichoose -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  adb shell mkdir -p /sdcard/Android/data/com.substichoose/files
  echo "Model folder on phone:"; adb shell ls -la /sdcard/Android/data/com.substichoose/files/
  echo "=== INSTALLED AND LAUNCHED ==="
else
  echo "No phone ready. Plug it in, unlock it, allow USB debugging, then run again."
fi
