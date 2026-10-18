#!/usr/bin/env bash
# Downloads the FFmpeg runtime AAR pinned in scripts/ffmpeg-kit-next-aar.txt into
# media/libs and checks its SHA-256 (the same one the Gradle build checks).
#
# That AAR was built from source by .github/workflows/ffmpeg-aar.yml, twice, with
# the same result. To build it yourself instead, run scripts/build-ffmpeg-kit-next.sh.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PIN="$ROOT/scripts/ffmpeg-kit-next-aar.txt"

field() {
    awk -v key="$1" '$1 == key { print $2; exit }' "$PIN"
}

sum() {
    if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

URL="$(field url)"
SHA256="$(field sha256)"
VERSION="$(sed -n 's/^ffmpegKitNext *= *"\([^"]*\)".*/\1/p' "$ROOT/gradle/libs.versions.toml")"
TARGET="$ROOT/media/libs/ffmpeg-kit-next-$VERSION.aar"

if [[ ! "$SHA256" =~ ^[0-9a-f]{64}$ || "$URL" != https://* ]]; then
    echo "No AAR is pinned in scripts/ffmpeg-kit-next-aar.txt yet: build it with scripts/build-ffmpeg-kit-next.sh." >&2
    exit 1
fi

if [[ -f "$TARGET" ]] && [[ "$(sum "$TARGET")" == "$SHA256" ]]; then
    echo "media/libs/ffmpeg-kit-next-$VERSION.aar is already there and checks out."
    exit 0
fi

mkdir -p "$ROOT/media/libs"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
echo "Downloading the FFmpeg runtime ($URL)..."
# wget or curl, whichever there is (the IzzyOnDroid builder has wget).
if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 -o "$TMP" "$URL"
else
    wget -q --tries=3 -O "$TMP" "$URL"
fi
ACTUAL="$(sum "$TMP")"
if [[ "$ACTUAL" != "$SHA256" ]]; then
    echo "SHA-256 mismatch: got $ACTUAL, expected $SHA256" >&2
    exit 1
fi
mv "$TMP" "$TARGET"
chmod 644 "$TARGET"
trap - EXIT
echo "Saved media/libs/ffmpeg-kit-next-$VERSION.aar (SHA-256 checked)."
