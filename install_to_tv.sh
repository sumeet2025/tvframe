#!/usr/bin/env bash
# Install TVFrame on any Android TV / Google TV over the network with adb,
# and optionally set it as the active screensaver.
#
# Usage:
#   ./install_to_tv.sh <TV-IP> [path/to/TVFrame.apk]
#   ./install_to_tv.sh --build <TV-IP>        # build a debug APK first (needs JDK 17 + Android SDK)
#
# Before running: on the TV enable Developer options (Settings > About > click "Build" 7 times),
# then turn on "USB debugging" / "Network debugging" (name varies by brand).
set -euo pipefail

BUILD=0
if [ "${1:-}" = "--build" ]; then BUILD=1; shift; fi

TV_IP="${1:-}"
APK_PATH="${2:-}"

if [ -z "$TV_IP" ]; then
    read -r -p "Enter your TV's IP address (Settings > Network on the TV): " TV_IP
fi
[ -n "$TV_IP" ] || { echo "Error: TV IP address is required."; exit 1; }
case "$TV_IP" in *:*) TARGET="$TV_IP" ;; *) TARGET="$TV_IP:5555" ;; esac

if ! command -v adb >/dev/null 2>&1; then
    if [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/platform-tools/adb" ]; then
        export PATH="$ANDROID_HOME/platform-tools:$PATH"
    else
        echo "Error: adb not found. Install Android platform-tools:"
        echo "  macOS:   brew install --cask android-platform-tools"
        echo "  Linux:   sudo apt install adb"
        echo "  Windows: https://developer.android.com/tools/releases/platform-tools"
        exit 1
    fi
fi

cd "$(dirname "$0")"

if [ "$BUILD" = "1" ]; then
    echo "[build] Building debug APK..."
    ./gradlew assembleDebug
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
fi

if [ -z "$APK_PATH" ]; then
    APK_PATH="$(ls -t dist/TVFrame-*.apk app/build/outputs/apk/release/*.apk app/build/outputs/apk/debug/*.apk 2>/dev/null | head -n1 || true)"
fi
[ -n "$APK_PATH" ] && [ -f "$APK_PATH" ] || { echo "Error: no APK found. Pass the APK path or use --build."; exit 1; }

echo "[1/3] Connecting to $TARGET ..."
adb connect "$TARGET"
echo ">>> If your TV shows 'Allow USB debugging?', choose 'Always allow from this computer'. <<<"
adb -s "$TARGET" wait-for-device

echo "[2/3] Installing $APK_PATH ..."
adb -s "$TARGET" install -r "$APK_PATH"

read -r -p "[3/3] Set TVFrame as the screensaver now? [y/N] " SET_SS
if [[ "$SET_SS" =~ ^[Yy]$ ]]; then
    adb -s "$TARGET" shell settings put secure screensaver_components com.nostr.tvframe/.service.PhotoDreamService
    adb -s "$TARGET" shell settings put secure screensaver_enabled 1
    echo "Screensaver set. To test it now run:"
    echo "  adb -s $TARGET shell cmd dreams start-dreaming"
fi
echo "Done. Open TVFrame from your TV's Apps list."
