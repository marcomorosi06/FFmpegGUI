#!/usr/bin/env bash
# Packs the complete source of the APK of the checked-out commit into one
# archive, which .github/workflows/release.yml attaches to the release.
#
# The APK is distributed under the GPL-3.0 (assets/licenses/ffmpeg-gui/NOTICE.txt),
# whose section 6 asks whoever distributes it to keep its Corresponding Source
# available for as long as the APK is offered. The third-party repositories the
# build fetches from can move or disappear: this archive keeps a copy next to
# the APK, on the same release page.
#
# The archive holds:
#   ffmpeg-gui/              FFmpeg GUI at the checked-out commit (git archive HEAD);
#   ffmpeg-kit-next/         FFmpegKitNext at the commit of scripts/ffmpeg-kit-next.lock;
#   ffmpeg-kit-next-sources/ every source scripts/build-ffmpeg-kit-next.sh builds the
#                            FFmpeg runtime from, each at its locked commit;
#   sherpa-onnx/             sherpa-onnx at the version of scripts/fetch-sherpa-onnx.sh;
#   sherpa-onnx-sources/     the third-party archives its Android build downloads, as
#                            downloaded, each checked against the SHA-256 its cmake/
#                            files give;
#   SOURCES.txt              where each one comes from, and how the APK is built.
#
# Every commit fetched is checked against the one asked for, and every archive
# against its SHA-256: a source that changed upstream stops the script.
#
# The list of FFmpeg runtime sources is read from scripts/build-ffmpeg-kit-next.sh
# itself (BUILD_OPTIONS and sources_for_option), so it follows the build. If that
# script changes the way it picks its sources, this one stops instead of
# packing an incomplete archive: update both together.
#
# Usage: scripts/package-sources.sh OUTPUT.tar.xz
# Needs bash, git, curl, tar, xz and sha256sum. Reads nothing outside the
# repository and writes nothing but OUTPUT (and a temporary folder it deletes).

set -euo pipefail

# No git configuration of the user or the system applies (hooks, fsmonitor, ...).
export GIT_NO_REPLACE_OBJECTS=1 GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCK="$ROOT/scripts/ffmpeg-kit-next.lock"
BUILD_SCRIPT="$ROOT/scripts/build-ffmpeg-kit-next.sh"
SHERPA_SCRIPT="$ROOT/scripts/fetch-sherpa-onnx.sh"

die() {
    echo "error: $*" >&2
    exit 1
}

info() {
    echo "==> $*"
}

[[ $# -eq 1 ]] || die "usage: scripts/package-sources.sh OUTPUT.tar.xz"
OUT="$1"
case "$OUT" in
    /*) ;;
    *) OUT="$PWD/$OUT" ;;
esac
[[ "$OUT" == *.tar.xz ]] || die "the output must be a .tar.xz file"
[[ -d "$(dirname "$OUT")" ]] || die "$(dirname "$OUT") does not exist"
for tool in git curl tar xz sha256sum; do
    command -v "$tool" >/dev/null 2>&1 || die "$tool is needed"
done

TOP="$(basename "$OUT" .tar.xz)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
STAGE="$WORK/stage/$TOP"
mkdir -p "$STAGE"
ENTRIES="$WORK/entries.txt"
: >"$ENTRIES"

# One line of SOURCES.txt: folder, origin, version, commit or SHA-256.
entry() {
    printf '%-70s %s\n    %s  %s\n' "$1" "$2" "$3" "$4" >>"$ENTRIES"
}

# Field $3 (3 = repository, 4 = tag, 5 = commit) of the lock line of kind $1 and name $2.
lock_field() {
    awk -v kind="$1" -v name="$2" -v field="$3" '
        $1 == kind && $2 == name { print $field; found = 1; exit }
        END { if (!found) exit 1 }
    ' "$LOCK"
}

# Fetches $3 (a commit, or refs/tags/<tag>) of repository $2 and puts its files,
# without .git, in $STAGE/$4. With $5, the commit must be $5. Prints the commit.
add_git_tree() {
    local name="$1" url="$2" ref="$3" dest="$4" expected="${5:-}" repo actual
    repo="$WORK/git/$name"
    git -c init.defaultBranch=main init -q "$repo"
    git -C "$repo" fetch -q --depth 1 "$url" "$ref" || die "cannot fetch $name $ref from $url"
    actual="$(git -C "$repo" rev-parse 'FETCH_HEAD^{commit}')"
    if [[ -n "$expected" && "$actual" != "$expected" ]]; then
        die "$name: $url gave commit $actual, the lock says $expected. Its source changed: nothing was packed."
    fi
    # Checked out rather than "git archive", which leaves out the files a
    # repository marks export-ignore: the archive has what the build compiles.
    mkdir -p "$STAGE/$dest"
    git -C "$repo" --work-tree="$STAGE/$dest" -c core.autocrlf=false checkout -q -f "$actual" -- .
    rm -rf "$repo"
    echo "$actual"
}

# Downloads $2 into $STAGE/$3/$1 and checks its SHA-256 is $4.
add_archive() {
    local file="$1" url="$2" dest="$3" sha256="$4" actual
    mkdir -p "$STAGE/$dest"
    curl -fsSL --retry 3 --proto '=https' -o "$STAGE/$dest/$file" "$url" || die "cannot download $url"
    actual="$(sha256sum "$STAGE/$dest/$file" | cut -d' ' -f1)"
    [[ "$actual" == "$sha256" ]] || die "$url has SHA-256 $actual, expected $sha256"
}

# "URL SHA256" of the dependency of prefix $1 in the cmake text on stdin
# (set(<prefix>_URL "...") and set(<prefix>_HASH "SHA256=...")).
cmake_dep() {
    local text url hash
    text="$(cat)"
    url="$(sed -n "s/^[[:space:]]*set($1_URL[[:space:]][[:space:]]*\"\([^\"\$]*\)\").*/\1/p" <<<"$text" | head -n 1)"
    hash="$(sed -n "s/^[[:space:]]*set($1_HASH[[:space:]][[:space:]]*\"SHA256=\([0-9a-f]*\)\").*/\1/p" <<<"$text" | head -n 1)"
    [[ -n "$url" && ${#hash} -eq 64 ]] || return 1
    echo "$url $hash"
}

# --- FFmpeg GUI ---------------------------------------------------------------

APP_COMMIT="$(git -C "$ROOT" rev-parse 'HEAD^{commit}')"
APP_TAG="$(git -C "$ROOT" describe --tags --exact-match HEAD 2>/dev/null || git -C "$ROOT" rev-parse --short HEAD)"
info "Packing FFmpeg GUI $APP_TAG ($APP_COMMIT)"
mkdir -p "$STAGE/ffmpeg-gui"
git -C "$ROOT" archive --format=tar HEAD | tar -x -C "$STAGE/ffmpeg-gui"
entry ffmpeg-gui/ "https://github.com/marcomorosi06/FFmpegGUI" "$APP_TAG" "$APP_COMMIT"

# --- FFmpeg runtime -------------------------------------------------------------

# The sources the build script fetches: "ffmpeg cpu-features", those of each
# BUILD_OPTIONS entry, and config. Checked to still be its way of picking them.
# shellcheck disable=SC2016 # the build script's own text, matched literally
if ! grep -qx 'SOURCES="ffmpeg cpu-features"' "$BUILD_SCRIPT" ||
    ! grep -q 'SOURCES="$SOURCES $(sources_for_option "$option")"' "$BUILD_SCRIPT" ||
    ! grep -q '^fetch_source config ' "$BUILD_SCRIPT"; then
    die "scripts/build-ffmpeg-kit-next.sh no longer picks its sources the way this script reads them: update scripts/package-sources.sh"
fi
BUILD_OPTIONS=()
# BUILD_OPTIONS uses API_LEVEL; a variable it starts to use later stops the
# script here (set -u) rather than going unseen.
eval "$(grep -E '^API_LEVEL="[0-9]+"$' "$BUILD_SCRIPT")"
eval "$(awk '/^BUILD_OPTIONS=\(/,/^\)/' "$BUILD_SCRIPT")"
eval "$(awk '/^sources_for_option\(\) \{/,/^\}/' "$BUILD_SCRIPT")"
((${#BUILD_OPTIONS[@]})) || die "no BUILD_OPTIONS in scripts/build-ffmpeg-kit-next.sh"
declare -F sources_for_option >/dev/null || die "no sources_for_option in scripts/build-ffmpeg-kit-next.sh"
SOURCES="ffmpeg cpu-features config"
for option in "${BUILD_OPTIONS[@]}"; do
    SOURCES="$SOURCES $(sources_for_option "$option")" || die "unknown build option $option"
done

url="$(lock_field kit ffmpeg-kit-next 3)" || die "no kit line in the lock"
tag="$(lock_field kit ffmpeg-kit-next 4)"
commit="$(lock_field kit ffmpeg-kit-next 5)"
info "Fetching FFmpegKitNext $tag"
add_git_tree ffmpeg-kit-next "$url" "$commit" ffmpeg-kit-next "$commit" >/dev/null
entry ffmpeg-kit-next/ "$url" "$tag" "$commit"

for name in $(tr ' ' '\n' <<<"$SOURCES" | grep -v '^$' | sort -u); do
    url="$(lock_field lib "$name" 3)" || die "$name is not in the lock"
    tag="$(lock_field lib "$name" 4)"
    commit="$(lock_field lib "$name" 5)"
    info "Fetching $name ($tag)"
    add_git_tree "$name" "$url" "$commit" "ffmpeg-kit-next-sources/$name" "$commit" >/dev/null
    entry "ffmpeg-kit-next-sources/$name/" "$url" "$tag" "$commit"
done

# --- sherpa-onnx ------------------------------------------------------------------

SHERPA_VERSION="$(sed -n 's/^VERSION="\([^"]*\)"$/\1/p' "$SHERPA_SCRIPT")"
[[ -n "$SHERPA_VERSION" ]] || die "no VERSION in scripts/fetch-sherpa-onnx.sh"
SHERPA_URL="https://github.com/k2-fsa/sherpa-onnx"
info "Fetching sherpa-onnx v$SHERPA_VERSION"
SHERPA_COMMIT="$(add_git_tree sherpa-onnx "$SHERPA_URL" "refs/tags/v$SHERPA_VERSION" sherpa-onnx)"
entry sherpa-onnx/ "$SHERPA_URL" "v$SHERPA_VERSION" "$SHERPA_COMMIT"

# The third-party sources of its Android build (CMakeLists.txt, cmake/*.cmake):
# cmake file and variable prefix. kaldifst comes from kaldi-decoder's own cmake/.
SHERPA_DEPS=(
    "kaldi-native-fbank kaldi_native_fbank"
    "kaldi-decoder kaldi_decoder"
    "simple-sentencepiece simple-sentencepiece"
    "json json"
    "espeak-ng-for-piper espeak_ng"
    "piper-phonemize piper_phonemize"
    "hclust-cpp hclust_cpp"
    "openfst openfst"
    "eigen eigen"
)
for dep in "${SHERPA_DEPS[@]}"; do
    read -r cmake prefix <<<"$dep"
    file="$STAGE/sherpa-onnx/cmake/$cmake.cmake"
    [[ -f "$file" ]] || die "sherpa-onnx v$SHERPA_VERSION has no cmake/$cmake.cmake: update SHERPA_DEPS"
    read -r url sha256 < <(cmake_dep "$prefix" <"$file") || die "no ${prefix}_URL or ${prefix}_HASH in cmake/$cmake.cmake"
    info "Downloading $cmake"
    add_archive "$cmake-$(basename "$url")" "$url" sherpa-onnx-sources "$sha256"
    entry "sherpa-onnx-sources/$cmake-$(basename "$url")" "$url" "cmake/$cmake.cmake" "sha256 $sha256"
    if [[ "$cmake" == "kaldi-decoder" ]]; then
        kaldi_decoder_archive="$STAGE/sherpa-onnx-sources/$cmake-$(basename "$url")"
    fi
done
read -r url sha256 < <(tar -xzOf "$kaldi_decoder_archive" --wildcards '*/cmake/kaldifst.cmake' | cmake_dep kaldifst) ||
    die "no kaldifst_URL or kaldifst_HASH in kaldi-decoder's cmake/kaldifst.cmake"
info "Downloading kaldifst"
add_archive "kaldifst-$(basename "$url")" "$url" sherpa-onnx-sources "$sha256"
entry "sherpa-onnx-sources/kaldifst-$(basename "$url")" "$url" "kaldi-decoder cmake/kaldifst.cmake" "sha256 $sha256"

# --- SOURCES.txt and the archive ----------------------------------------------------

{
    cat <<EOF
FFmpeg GUI $APP_TAG - complete source of the APK
================================================

This archive is the Corresponding Source of the FFmpeg GUI APK of release
$APP_TAG, which is distributed under the GNU General Public License,
version 3 (see ffmpeg-gui/app/src/main/assets/licenses/ffmpeg-gui/NOTICE.txt).
Every component keeps its own licence: see
ffmpeg-gui/app/src/main/assets/licenses/README.txt.

HOW THE APK IS BUILT
- The app: ffmpeg-gui/README.md, "Building" and "Releasing". The release APK
  is built with: ./gradlew clean assembleRelease --no-build-cache
  --no-configuration-cache --no-daemon
- The FFmpeg runtime (media/libs/ffmpeg-kit-next-*.aar): built from
  ffmpeg-kit-next/ and ffmpeg-kit-next-sources/ by
  ffmpeg-gui/scripts/build-ffmpeg-kit-next.sh, with these options:
  ${BUILD_OPTIONS[*]}
  The script fetches each source itself, at the commits below. To build from
  this archive instead, put each folder of ffmpeg-kit-next-sources/ in
  ffmpeg-kit-next/src/ (config in ffmpeg-kit-next/.tmp/source/config).
- Speech recognition (app/libs/sherpa-onnx-$SHERPA_VERSION.aar): the release
  AAR of sherpa-onnx v$SHERPA_VERSION, built by the sherpa-onnx project from
  sherpa-onnx/ (build-android-*.sh). Its CMake build downloads the archives in
  sherpa-onnx-sources/; put them where its cmake/*.cmake files look for them
  (for example next to CMakeLists.txt) to build without a network. ONNX
  Runtime (MIT) comes as a prebuilt library: https://github.com/microsoft/onnxruntime

SOURCES
EOF
    cat "$ENTRIES"
} >"$STAGE/SOURCES.txt"

info "Writing $OUT"
# Same order, owners and dates on every run: the archive depends only on its sources.
EPOCH="$(git -C "$ROOT" log -1 --format=%ct HEAD)"
tar --sort=name --owner=0 --group=0 --numeric-owner --mtime="@$EPOCH" \
    --pax-option=exthdr.name=%d/PaxHeaders/%f,delete=atime,delete=ctime \
    -cf - -C "$WORK/stage" "$TOP" | xz -T0 -6 >"$OUT.partial"
mv "$OUT.partial" "$OUT"
info "Done: $(du -h "$OUT" | cut -f1), SHA-256 $(sha256sum "$OUT" | cut -d' ' -f1)"
