#!/usr/bin/env bash
# dev-run.sh — builds the plugin and pushes it to all connected devices/emulators
# Run this before launching the app from Android Studio.

PLUGIN_DIR="$(dirname "$0")/zstream-plugin"
PLUGIN_APK="$PLUGIN_DIR/plugin/build/outputs/apk/release/plugin-release.apk"
DEST="/sdcard/Android/data/com.zstream.android/files/plugin-debug.apk"

echo "▶ Building plugin..."
if [[ ! -f "$PLUGIN_DIR/plugin/src/main/cpp/CMakeLists.txt" ]]; then
    echo "  zstream-plugin submodule not found, initializing..."
    git -C "$(dirname "$0")" submodule update --init --recursive
fi
(cd "$PLUGIN_DIR" && ./gradlew assembleRelease -q) || exit 1

mapfile -t DEVICES < <(adb devices | awk '/\tdevice$/{print $1}')

if [[ ${#DEVICES[@]} -eq 0 ]]; then
    echo "✗ No devices connected."
    exit 1
fi

echo "▶ Pushing plugin APK to ${#DEVICES[@]} device(s)..."
SUCCESS_COUNT=0
for SERIAL in "${DEVICES[@]}"; do
    echo "  → $SERIAL"
    if adb -s "$SERIAL" push "$PLUGIN_APK" "$DEST" >/dev/null 2>&1; then
        ((SUCCESS_COUNT++))
    fi
done

if [[ $SUCCESS_COUNT -eq 0 ]]; then
    echo "✗ No devices received the APK."
    exit 1
fi

echo "✓ Done. Pushed to $SUCCESS_COUNT device(s). Launch the app and tap 'Dev: Sideload plugin'."

