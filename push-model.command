#!/bin/bash
# Copies the Gemma .litertlm model to the phone. Looks in this folder and ~/Downloads.
cd "$(dirname "$0")"
exec > >(tee "push-model-log.txt") 2>&1
[ -z "$ANDROID_HOME" ] && [ -f local.properties ] && export ANDROID_HOME=$(sed -n 's/^sdk.dir=//p' local.properties)
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
export PATH="$SDK/platform-tools:$PATH"
MODEL=$(ls -1 ./*.litertlm "$HOME/Downloads"/*.litertlm 2>/dev/null | head -1)
if [ -z "$MODEL" ]; then echo "No .litertlm file found here or in ~/Downloads."; exit 1; fi
echo "Pushing $MODEL ($(du -h "$MODEL" | cut -f1)) ..."
adb shell mkdir -p /sdcard/Android/data/com.substichoose/files
adb push "$MODEL" /sdcard/Android/data/com.substichoose/files/ && echo "=== MODEL PUSHED ==="
adb shell am force-stop com.substichoose
adb shell monkey -p com.substichoose -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
