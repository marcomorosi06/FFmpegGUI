#!/usr/bin/env bash
# Prints one SHA-256 over everything that decides the FFmpeg runtime AAR: the
# pinned sources, the build script and the Gradle verification file.
#
# .github/workflows/ffmpeg-aar.yml writes it into scripts/ffmpeg-kit-next-aar.txt
# ("inputs") next to the AAR it built from them, and .github/workflows/release.yml
# refuses a tag whose pinned AAR was built from other inputs.
#
#   scripts/ffmpeg-kit-next-inputs.sh           print the hash
#   scripts/ffmpeg-kit-next-inputs.sh --check   exit 1 unless the pin is current

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FILES=(
    scripts/ffmpeg-kit-next.lock
    scripts/build-ffmpeg-kit-next.sh
    scripts/ffmpeg-kit-next.verification-metadata.xml
)

sum() {
    if command -v sha256sum >/dev/null 2>&1; then sha256sum | cut -d' ' -f1; else shasum -a 256 | cut -d' ' -f1; fi
}

inputs() {
    local file
    for file in "${FILES[@]}"; do
        [[ -f "$ROOT/$file" ]] || { echo "error: $file is missing" >&2; exit 1; }
    done
    for file in "${FILES[@]}"; do
        # Name and content of each file, in a fixed order.
        printf '%s %s\n' "$(sum <"$ROOT/$file")" "$file"
    done | sum
}

case "${1:-}" in
    "")
        inputs
        ;;
    --check)
        current="$(inputs)"
        pinned="$(awk '$1 == "inputs" { print $2; exit }' "$ROOT/scripts/ffmpeg-kit-next-aar.txt")"
        if [[ "$pinned" != "$current" ]]; then
            echo "The FFmpeg runtime pinned in scripts/ffmpeg-kit-next-aar.txt was built from other inputs" >&2
            echo "(pinned: ${pinned:--}, now: $current)." >&2
            echo "The \"FFmpeg runtime (AAR)\" workflow builds and pins the new one after a push to master." >&2
            exit 1
        fi
        echo "The pinned FFmpeg runtime was built from these inputs ($current)."
        ;;
    *)
        echo "usage: $0 [--check]" >&2
        exit 2
        ;;
esac
