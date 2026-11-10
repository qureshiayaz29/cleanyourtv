#!/usr/bin/env bash
# Build Daily Wallpaper, install it on the TV and let it set the launcher's wallpaper.
#   TV_HOST=<tv ip> addons/daily-wallpaper/install.sh
set -euo pipefail
: "${TV_HOST:?set TV_HOST to the TV IP address}"
cd "$(dirname "$0")"
TOOLS="$(cd ../../tools && pwd)"
PY=$TOOLS/venv/bin/python
[ -x "$PY" ] || PY=$TOOLS/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
PKG=com.ayazqureshi.dailywallpaper
# Script paths stay relative: with MSYS_NO_PATHCONV, Git Bash would hand Windows Python a /c/... path.
tv() { "$PY" -I ../../tools/tv.py "$@"; }

[ -f local.properties ] || { [ -n "${ANDROID_HOME:-}" ] && echo "sdk.dir=${ANDROID_HOME//\\//}" > local.properties; } || true
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
./gradlew assembleRelease -q --console=plain

"$PY" -I ../../tools/b64push.py app/build/outputs/apk/release/app-release.apk /data/local/tmp/daily-wallpaper.apk
OUT=$(tv 'pm install -r /data/local/tmp/daily-wallpaper.apk; rm -f /data/local/tmp/daily-wallpaper.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: pm install failed" >&2; exit 1 ;; esac

# The launcher's theme provider takes a wallpaper only from a caller that holds this; only ADB can grant it.
tv "pm grant $PKG android.permission.WRITE_SECURE_SETTINGS; echo permission granted"
# Opening it once registers the daily job.
tv "am start -n $PKG/.MainActivity >/dev/null; echo opened"
echo "Installed. Daily Wallpaper is open on the TV: press Update now for the first picture, then Home."
