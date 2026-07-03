#!/usr/bin/env bash
# dev-run.sh — builds the plugin and pushes it to all connected devices/emulators
# Run this before launching the app from Android Studio.
set -e

PLUGIN_DIR="$(dirname "$0")/zstream-plugin"
PLUGIN_APK="$PLUGIN_DIR/plugin/build/outputs/apk/debug/plugin-debug.apk"
DEST="/sdcard/Android/data/com.zstream.android/files/plugin-debug.apk"

echo "▶ Building plugin..."
(cd "$PLUGIN_DIR" && ./gradlew assembleDebug -q)

mapfile -t DEVICES < <(adb devices | awk '/\tdevice$/{print $1}')

if [[ ${#DEVICES[@]} -eq 0 ]]; then
    echo "✗ No devices connected."
    exit 1
fi

echo "▶ Pushing plugin APK to ${#DEVICES[@]} device(s)..."
for SERIAL in "${DEVICES[@]}"; do
    echo "  → $SERIAL"
    adb -s "$SERIAL" push "$PLUGIN_APK" "$DEST"
done

echo "✓ Done. Launch the app and tap 'Dev: Sideload plugin'."
