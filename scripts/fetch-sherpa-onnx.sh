#!/usr/bin/env bash
# Downloads the sherpa-onnx release AAR the app is built with into app/libs and
# checks its SHA-256 (the same one the Gradle build checks).
#
# sherpa-onnx (Apache 2.0, with ONNX Runtime, MIT) runs the speech model of the
# smart subtitles. It is not published on Maven Central, so the AAR of its GitHub
# release is used. When moving to another version, change the version and the
# hash here and in app/build.gradle.kts (SHERPA_ONNX_VERSION, SHERPA_ONNX_SHA256).

set -euo pipefail

VERSION="1.13.8"
SHA256="633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${VERSION}/sherpa-onnx-${VERSION}.aar"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="$ROOT/app/libs/sherpa-onnx-${VERSION}.aar"

sum() {
    if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

if [ -f "$TARGET" ] && [ "$(sum "$TARGET")" = "$SHA256" ]; then
    echo "app/libs/sherpa-onnx-${VERSION}.aar is already there and checks out."
    exit 0
fi

mkdir -p "$ROOT/app/libs"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
echo "Downloading sherpa-onnx ${VERSION}..."
curl -fL --retry 3 -o "$TMP" "$URL"
ACTUAL="$(sum "$TMP")"
if [ "$ACTUAL" != "$SHA256" ]; then
    echo "SHA-256 mismatch: got $ACTUAL, expected $SHA256" >&2
    exit 1
fi
mv "$TMP" "$TARGET"
chmod 644 "$TARGET"
trap - EXIT
echo "Saved app/libs/sherpa-onnx-${VERSION}.aar (SHA-256 checked)."
