#!/usr/bin/env bash
# dev-run.sh — builds the plugin and pushes it to the device
# Run this before launching the app from Android Studio.
# Usage: ./dev-run.sh [emulator|device]
set -e

TARGET=${1:-""}
ADB="adb${TARGET:+ -$( [[ "$TARGET" == "emulator" ]] && echo 'e' || echo 'd')}"

PLUGIN_DIR="$(dirname "$0")/zstream-plugin"
PLUGIN_APK="$PLUGIN_DIR/plugin/build/outputs/apk/debug/plugin-debug.apk"
DEST="/sdcard/Android/data/com.zstream.android/files/plugin-debug.apk"

echo "▶ Building plugin..."
(cd "$PLUGIN_DIR" && ./gradlew assembleDebug -q)

echo "▶ Pushing plugin APK..."
$ADB push "$PLUGIN_APK" "$DEST"

echo "✓ Done. Launch the app and tap 'Dev: Sideload plugin'."
