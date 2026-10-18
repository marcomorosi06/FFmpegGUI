#!/usr/bin/env bash
# Builds the FFmpeg runtime of the app, FFmpegKitNext, from source and puts its
# AAR in media/libs, where Gradle takes it from.
#
# FFmpegKitNext (https://github.com/arthenica/ffmpeg-kit-next) is the official,
# source-only continuation of FFmpegKit: no binary is downloaded. Its build runs
# in a Nix shell whose toolchain (Android SDK, NDK r27d, CMake) is pinned by
# FFmpegKitNext's own flake.lock, and every source (FFmpegKitNext, FFmpeg and each
# external library) must be the commit written in scripts/ffmpeg-kit-next.lock:
#
#   - the script runs with a clean environment (see KEEP_ENV): no variable left
#     in the shell reaches git or the build;
#   - FFmpegKitNext and the sources the options below need are fetched first and
#     checked against the lock, so code that is not the locked commit is never
#     compiled (a moved tag or a changed mirror stops the build here);
#   - before each build every source is put back exactly as its commit (files
#     changed or added in it are undone), and the libraries FFmpegKitNext kept
#     from an earlier build (prebuilt/) are only reused when nothing changed
#     since: same sources, lock, options and script;
#   - after the build, every source it used is checked again. If one is not the
#     locked commit, everything built is deleted and the AAR is not copied;
#   - nothing in the AAR depends on when the build ran: the build day that
#     FFmpegKitNext compiles into libffmpegkit.so is the day of its commit, and
#     SOURCE_DATE_EPOCH is that commit's time. The AAR does hold the work folder's
#     path (FFmpeg keeps its configure line), so a byte-identical AAR needs the
#     same one: CANONICAL_WORK (see below), the folder .github/workflows/ffmpeg-aar.yml
#     builds the published AAR in. The script says at the end whether its AAR is
#     the one pinned in scripts/ffmpeg-kit-next-aar.txt;
#   - the AAR is packed by FFmpegKitNext's Android project with Gradle: the
#     Gradle distribution must have the SHA-256 written in the lock, and every
#     plugin and library Gradle downloads must match
#     scripts/ffmpeg-kit-next.verification-metadata.xml (Gradle's dependency
#     verification), or Gradle stops.
#
# Usage:
#   scripts/build-ffmpeg-kit-next.sh                     build
#   scripts/build-ffmpeg-kit-next.sh --clean             build in an empty work folder
#   scripts/build-ffmpeg-kit-next.sh -- --enable-libvpx  build with more libraries
#   scripts/build-ffmpeg-kit-next.sh --write-gradle-verification
#       writes scripts/ffmpeg-kit-next.verification-metadata.xml from what Gradle
#       downloads now (no FFmpeg build). Needed once, and again when FFmpegKitNext
#       moves to other Gradle plugins; entries already in the file are kept, so
#       run it on each kind of host you build on (Linux, macOS: the Android
#       plugin's aapt2 differs). Read the result before committing it: the hashes
#       it records are trusted from then on.
#
# After "--" only --enable-<library> (any library of the lock), --jobs=N,
# --rebuild-<library> and --reconf-<library> are accepted: options that would
# take sources from elsewhere (--version-*, --redownload-*, custom libraries)
# are refused.
#
# Environment:
#   FFMPEG_KIT_WORKDIR  work folder (default: .ffmpeg-kit-next in the project,
#                       ignored by git). It must be a new or empty folder the
#                       first time; --clean only deletes a folder this script
#                       made. The first build can take an hour or more. To
#                       rebuild the published AAR byte for byte, use
#                       FFMPEG_KIT_WORKDIR=/var/tmp/ffmpeg-kit-next (only for
#                       that: systems delete old files in /var/tmp).
#
# Needs bash, git and Nix with flakes, on Linux (not NixOS) or macOS; see
# https://github.com/arthenica/ffmpeg-kit-next/wiki/Nix and
# https://github.com/arthenica/ffmpeg-kit-next/wiki/Host-Support

set -euo pipefail

# The only variables the script and the build get. FFmpegKitNext's build takes
# settings from many variables (version_<lib>, REDOWNLOAD_<lib>, REBUILD_<lib>,
# OK_<lib>, SKIP_ffmpeg, SKIP_ffmpeg_kit, NO_WORKSPACE_CLEANUP_<lib>, RECONF_<lib>,
# CFLAGS, PKG_CONFIG_PATH, ...) and nix develop passes the whole environment
# through, as git reads GIT_DIR and the like: a variable left over in the shell
# could make it fetch or compile other sources, or skip a step and pack an old
# binary. So the script starts again with these alone. Add a name here only if
# the build cannot work without it.
KEEP_ENV=(
    HOME USER LOGNAME PATH SHELL TERM LANG LC_ALL LC_CTYPE TZ TMPDIR
    XDG_CACHE_HOME XDG_CONFIG_HOME XDG_DATA_HOME XDG_STATE_HOME XDG_RUNTIME_DIR
    NIX_REMOTE NIX_SSL_CERT_FILE NIX_DAEMON_SOCKET_PATH SSL_CERT_FILE SSL_CERT_DIR
    http_proxy https_proxy no_proxy all_proxy HTTP_PROXY HTTPS_PROXY NO_PROXY ALL_PROXY
    FFMPEG_KIT_WORKDIR
)
if [[ "${1:-}" != "--in-clean-env" ]]; then
    kept=()
    for name in "${KEEP_ENV[@]}"; do
        if value="$(printenv "$name")"; then
            kept+=("$name=$value")
        fi
    done
    exec env -i ${kept[@]+"${kept[@]}"} "$BASH" "${BASH_SOURCE[0]}" --in-clean-env "$@"
fi
shift
# Objects of a work folder are read as they are (no "git replace" substitutes), and
# no git configuration of the user or the system applies (hooks, fsmonitor and
# other settings that run commands).
export GIT_NO_REPLACE_OBJECTS=1 GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1
# FFmpegKitNext's nix.conf accepts the settings a flake asks for (accept-flake-config);
# its flake.nix asks for none today, and none should be taken from a future one unseen.
export NIX_CONFIG="accept-flake-config = false"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="$ROOT/scripts/$(basename "${BASH_SOURCE[0]}")"
LOCK="$ROOT/scripts/ffmpeg-kit-next.lock"
WORK="${FFMPEG_KIT_WORKDIR:-$ROOT/.ffmpeg-kit-next}"
# The work folder of the published AAR (its path ends up in the AAR).
CANONICAL_WORK="/var/tmp/ffmpeg-kit-next"
# The AAR the app is built with: where it is published and its SHA-256.
AAR_PIN="$ROOT/scripts/ffmpeg-kit-next-aar.txt"
case "$WORK" in
    /*) ;;
    *) WORK="$PWD/$WORK" ;;
esac
KIT="$WORK/ffmpeg-kit-next"
# Gradle's home for FFmpegKitNext's Android project, in the work folder (its flake
# would pick the same folder; set here so that this script writes its settings).
export GRADLE_USER_HOME="$KIT/.gradle"
# Marks a work folder this script made: the only kind --clean deletes.
WORK_MARKER=".build-ffmpeg-kit-next"
# What the libraries in prebuilt/ were built from (see prepare_prebuilt).
INPUTS_STAMP="$KIT/prebuilt/.build-ffmpeg-kit-next-inputs"
# Gradle's dependency verification of FFmpegKitNext's Android project: kept in
# this repository, copied into the project before each build.
GRADLE_METADATA="$ROOT/scripts/ffmpeg-kit-next.verification-metadata.xml"
KIT_GRADLE_METADATA="$KIT/android/gradle/verification-metadata.xml"
WRAPPER_PROPERTIES="$KIT/android/gradle/wrapper/gradle-wrapper.properties"
# Marks a Gradle distribution the wrapper installed after checking its SHA-256.
GRADLE_DIST_STAMP=".build-ffmpeg-kit-next-verified"
TARGET_DIR="$ROOT/media/libs"
NIX_PROFILE="android-r27d"
API_LEVEL="24"
# Set when a source had to be fetched again: what prebuilt/ holds was not built from it.
REFETCHED=0

# What the app uses, and nothing else (see docs/ARCHITECTURE.md, "FFmpeg runtime").
# The libraries these depend on (libogg, libpng, expat, libuuid, libiconv) are
# added by FFmpegKitNext itself.
BUILD_OPTIONS=(
    --enable-gpl                  # x264, x265, xvidcore and vid.stab are GPL
    --enable-android-media-codec  # h264_mediacodec, the phone's own video encoder
    --enable-android-zlib         # the system zlib (PNG, TIFF deflate)
    --enable-x264                 # MP4 / MKV video
    --enable-x265                 # HEVC video
    --enable-xvidcore             # AVI video
    --enable-lame                 # MP3
    --enable-libvorbis            # OGG
    --enable-opus                 # Opus
    --enable-libass               # subtitles burnt in
    --enable-fontconfig           # fonts found by name (libass, drawtext)
    --enable-freetype             # drawtext
    --enable-fribidi              # right-to-left text
    --enable-harfbuzz             # text shaping
    --enable-libvidstab           # stabilisation
    --enable-zimg                 # zscale (HDR to SDR)
    --enable-dav1d                # AV1 sources (FFmpeg 6.1 has no software AV1 decoder of its own)
    --disable-arm-v7a             # the app ships arm64-v8a and x86_64 only
    --disable-arm-v7a-neon
    --disable-x86
    "--api-level=$API_LEVEL"
)

die() {
    echo "error: $*" >&2
    exit 1
}

info() {
    echo "==> $*"
}

usage() {
    sed -n '2,/^$/{s/^# \{0,1\}//;p;}' "$SCRIPT"
}

sha256_of() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | cut -d' ' -f1
    else
        shasum -a 256 "$1" | cut -d' ' -f1
    fi
}

# Field $3 (3 = repository, 4 = tag, 5 = commit) of the lock line of kind $1 and name $2.
lock_field() {
    awk -v kind="$1" -v name="$2" -v field="$3" '
        $1 == kind && $2 == name { print $field; found = 1; exit }
        END { if (!found) exit 1 }
    ' "$LOCK"
}

# The sources FFmpegKitNext downloads for one build option (it mirrors set_library
# in its scripts/function.sh). Fails for an --enable option it does not know.
sources_for_option() {
    case "$1" in
        --enable-gpl | --enable-android-media-codec | --enable-android-zlib) ;;
        --enable-x264) echo x264 ;;
        --enable-x265) echo x265 ;;
        --enable-xvidcore) echo xvidcore ;;
        --enable-libvidstab) echo libvidstab ;;
        --enable-lame) echo lame libiconv ;;
        --enable-libvorbis) echo libvorbis libogg ;;
        --enable-libtheora) echo libtheora libogg libvorbis ;;
        --enable-opus) echo opus ;;
        --enable-freetype) echo freetype libpng ;;
        --enable-fribidi) echo fribidi ;;
        --enable-harfbuzz) echo harfbuzz freetype libpng ;;
        --enable-fontconfig) echo fontconfig expat libiconv libuuid freetype libpng ;;
        --enable-libass) echo libass expat libuuid freetype libpng fribidi fontconfig harfbuzz libiconv ;;
        --enable-libiconv) echo libiconv ;;
        --enable-libxml2) echo libxml2 libiconv ;;
        --enable-zimg) echo zimg ;;
        --enable-dav1d) echo dav1d ;;
        --enable-libaom) echo libaom ;;
        --enable-libvpx) echo libvpx ;;
        --enable-libwebp) echo libwebp giflib jpeg tiff libpng ;;
        --enable-kvazaar) echo kvazaar ;;
        --enable-libilbc) echo libilbc ;;
        --enable-opencore-amr) echo opencore-amr ;;
        --enable-openh264) echo openh264 ;;
        --enable-shine) echo shine ;;
        --enable-snappy) echo snappy ;;
        --enable-soxr) echo soxr ;;
        --enable-speex) echo speex ;;
        --enable-twolame) echo twolame libsndfile ;;
        --enable-vo-amrwbenc) echo vo-amrwbenc ;;
        --enable-rubberband) echo rubberband libsndfile libsamplerate ;;
        --enable-chromaprint) echo chromaprint ;;
        --enable-gmp) echo gmp ;;
        --enable-gnutls) echo gnutls nettle gmp libiconv ;;
        --enable-openssl) echo openssl ;;
        --enable-srt) echo srt openssl ;;
        --enable-sdl) echo sdl ;;
        --enable-tesseract) echo tesseract leptonica libwebp giflib jpeg tiff libpng ;;
        --enable-*) return 1 ;;
        *) ;;
    esac
}

# The commit checked out at $1, or nothing.
head_of() {
    git -C "$1" rev-parse HEAD 2>/dev/null || true
}

# The real path of the existing folder $1.
real_path() {
    (cd "$1" && pwd -P)
}

# The work folder: refuses one that is not this script's (an existing folder
# with other things in it), deletes it with --clean, and marks a new one.
prepare_work_folder() {
    local real
    if [[ -e "$WORK" ]]; then
        [[ -d "$WORK" ]] || die "$WORK is not a folder"
        real="$(real_path "$WORK")" || die "cannot read $WORK"
        case "$real" in
            / | "$(real_path "$ROOT")" | "$(real_path "${HOME:-/}")")
                die "refusing to use $real as the work folder (FFMPEG_KIT_WORKDIR)"
                ;;
        esac
        if [[ ! -f "$real/$WORK_MARKER" && -n "$(ls -A "$real")" ]]; then
            # A work folder made before the marker existed holds ffmpeg-kit-next alone.
            if [[ "$(ls -A "$real")" != "ffmpeg-kit-next" || ! -d "$real/ffmpeg-kit-next/.git" ]]; then
                die "refusing to use $real as the work folder: it already holds other files. Point FFMPEG_KIT_WORKDIR at a new or empty folder."
            fi
        fi
        if ((CLEAN)); then
            info "Removing $real"
            rm -rf "$real"
            # Made again where it was, should $WORK be a link to it.
            mkdir -p "$real"
        fi
    fi
    mkdir -p "$WORK"
    : >"$WORK/$WORK_MARKER"
}

# Clones source $1 of the lock into $2 the way FFmpegKitNext clones it (so that
# its build scripts find what they expect), unless $2 already is the locked
# commit, then checks the commit. A source that is not the locked commit is
# deleted before anything can build it.
fetch_source() {
    local name="$1" dir="$2" url tag commit actual
    url="$(lock_field lib "$name" 3)" || die "$name is not in $LOCK"
    tag="$(lock_field lib "$name" 4)"
    commit="$(lock_field lib "$name" 5)"
    if [[ -d "$dir/.git" ]] && [[ "$(head_of "$dir")" == "$commit" ]]; then
        return
    fi
    info "Fetching $name ($tag)"
    REFETCHED=1
    rm -rf "$dir"
    mkdir -p "$(dirname "$dir")"
    if [[ "$tag" == "-" ]]; then
        git clone -q --depth 1 "$url" "$dir"
        git -C "$dir" fetch -q --depth 1 origin "$commit"
        git -C "$dir" -c advice.detachedHead=false checkout -q "$commit"
    else
        git -c advice.detachedHead=false clone -q --depth 1 --branch "$tag" "$url" "$dir"
    fi
    actual="$(head_of "$dir")"
    if [[ "$actual" != "$commit" ]]; then
        rm -rf "$dir"
        die "$name $tag is commit ${actual:-unknown} at $url, the lock says $commit: its source changed. Nothing was built from it."
    fi
}

# Deletes every source folder that is not a locked commit (an unknown one, or one
# left at another commit): the ones the build needs are fetched again.
drop_unlocked_sources() {
    local dir name commit
    for dir in "$KIT"/src/*/; do
        [[ -d "$dir" ]] || continue
        name="$(basename "$dir")"
        commit="$(lock_field lib "$name" 5)" || commit=""
        if [[ -z "$commit" || "$(head_of "$dir")" != "$commit" ]]; then
            info "Removing $name from the sources: not a locked commit"
            rm -rf "$dir"
            REFETCHED=1
        fi
    done
}

# Puts source folder $1 back exactly as its commit: changed files restored, added
# ones deleted. FFmpegKitNext's build scripts clean each library before building
# it anyway, so nothing they need is lost.
restore_source() {
    git -C "$1" reset -q --hard HEAD
    git -C "$1" clean -q -ffdx
}

# FFmpegKitNext at the locked commit, its own files put back as the commit has
# them (its build edits some, and this script adds a line to one).
fetch_kit() {
    local url tag commit
    url="$(lock_field kit ffmpeg-kit-next 3)" || die "no kit line in $LOCK"
    tag="$(lock_field kit ffmpeg-kit-next 4)"
    commit="$(lock_field kit ffmpeg-kit-next 5)"
    if [[ ! -d "$KIT/.git" ]]; then
        info "Fetching FFmpegKitNext $tag"
        git -c advice.detachedHead=false clone -q --depth 1 --branch "$tag" "$url" "$KIT"
    fi
    if [[ "$(head_of "$KIT")" != "$commit" ]]; then
        die "FFmpegKitNext in $KIT is commit $(head_of "$KIT"), the lock says $commit ($tag). Run with --clean; if it still differs, the tag moved upstream."
    fi
    if [[ -n "$(git -C "$KIT" status --porcelain --untracked-files=no)" ]]; then
        info "Putting back the files of FFmpegKitNext changed since the last build"
        git -C "$KIT" reset -q --hard HEAD
    fi
    KIT_TAG="$tag"
    KIT_COMMIT="$commit"
}

# Field $2 (1 = repository, 2 = tag or commit, 3 = TAG or COMMIT) that FFmpegKitNext's
# own scripts/source.sh gives for library $1.
kit_source_field() {
    (
        set +eu
        # shellcheck source=/dev/null
        source "$KIT/scripts/source.sh"
        get_library_source "$1" "$2"
    )
}

# The lock must say what FFmpegKitNext's scripts/source.sh asks for, library by library.
check_lock_against_kit() {
    local kind name url tag commit kit_url kit_id kit_type
    while read -r kind name url tag commit; do
        [[ "$kind" == "lib" ]] || continue
        kit_url="$(kit_source_field "$name" 1)"
        kit_id="$(kit_source_field "$name" 2)"
        kit_type="$(kit_source_field "$name" 3)"
        [[ "$url" == "$kit_url" ]] || die "the lock takes $name from $url, FFmpegKitNext from ${kit_url:-nowhere}"
        if [[ "$kit_type" == "COMMIT" ]]; then
            [[ "$tag" == "-" && "$commit" == "$kit_id" ]] || die "FFmpegKitNext asks for $name commit $kit_id, the lock says $tag $commit"
        else
            [[ "$tag" == "$kit_id" ]] || die "FFmpegKitNext asks for $name $kit_id, the lock says $tag"
        fi
    done < <(grep -v '^[[:space:]]*#' "$LOCK" | grep -v '^[[:space:]]*$')
}

# The Gradle distribution of FFmpegKitNext's Android project must be the locked
# one. The wrapper checks a zip it downloads against distributionSha256Sum, added
# here to its gradle-wrapper.properties (fetch_kit puts the file back each run).
# It does not check a distribution it installed before, and deletes the zip once
# unpacked: so a distribution is only kept if this script saw the wrapper install
# it with that check on (see stamp_gradle_distributions).
pin_gradle_distribution() {
    local url kit_url dist
    url="$(lock_field gradle distribution 3)" || die "no gradle line in $LOCK"
    GRADLE_VERSION="$(lock_field gradle distribution 4)"
    GRADLE_SHA256="$(lock_field gradle distribution 5)"
    kit_url="$(sed -n 's/^distributionUrl=//p' "$WRAPPER_PROPERTIES" | sed 's/\\:/:/g')"
    [[ "$kit_url" == "$url" ]] || die "FFmpegKitNext's Android project takes Gradle from ${kit_url:-nowhere}, the lock says $url"
    grep -q '^distributionSha256Sum=' "$WRAPPER_PROPERTIES" && die "$WRAPPER_PROPERTIES already sets distributionSha256Sum: check FFmpegKitNext's value against the lock"
    printf 'distributionSha256Sum=%s\n' "$GRADLE_SHA256" >>"$WRAPPER_PROPERTIES"
    for dist in "$GRADLE_USER_HOME"/wrapper/dists/gradle-"$GRADLE_VERSION"-bin/*/; do
        [[ -d "$dist" ]] || continue
        if [[ "$(cat "$dist/$GRADLE_DIST_STAMP" 2>/dev/null)" != "$GRADLE_SHA256" ]]; then
            info "Removing a Gradle $GRADLE_VERSION distribution not checked against the lock"
            rm -rf "$dist"
        fi
    done
}

# After a successful run of the wrapper: a distribution it installed (its .ok
# marker is there) was checked against distributionSha256Sum on download.
stamp_gradle_distributions() {
    local dist
    for dist in "$GRADLE_USER_HOME"/wrapper/dists/gradle-"$GRADLE_VERSION"-bin/*/; do
        [[ -f "$dist/gradle-$GRADLE_VERSION-bin.zip.ok" ]] || continue
        [[ -f "$dist/$GRADLE_DIST_STAMP" ]] || echo "$GRADLE_SHA256" >"$dist/$GRADLE_DIST_STAMP"
    done
}

# The Gradle settings of the work folder, written anew each run: nothing left in
# its init.d or gradle.properties takes part. The init script keeps the unit tests
# of FFmpegKitNext's Android project from running: they do not change the AAR, and
# a test framework may download what it needs outside Gradle's dependency
# verification (later FFmpegKitNext releases use Robolectric, which fetches its
# Android jars by itself and trusts any copy in ~/.m2). What the tests compile is
# still resolved, and checked, by Gradle.
prepare_gradle_home() {
    rm -rf "$GRADLE_USER_HOME/init.d" "$GRADLE_USER_HOME/gradle.properties"
    mkdir -p "$GRADLE_USER_HOME/init.d"
    cat >"$GRADLE_USER_HOME/init.d/build-ffmpeg-kit-next.gradle" <<'EOF'
// Written by scripts/build-ffmpeg-kit-next.sh on each run.
allprojects {
    tasks.withType(Test).configureEach { enabled = false }
}
EOF
}

# Runs command $* in FFmpegKitNext's Nix shell, from the kit folder, as its
# nix-android.sh does (same nix.conf).
in_kit_nix_shell() {
    (
        cd "$KIT"
        export NIX_USER_CONF_FILES="$KIT/nix.conf"
        nix develop ".#$NIX_PROFILE" -c "$@"
    )
}

# --write-gradle-verification: runs the Gradle tasks of FFmpegKitNext's build
# (those of its scripts/start-android.sh) writing what they download into the
# verification file, starting from the one in this repository if there is one.
write_gradle_verification() {
    rm -f "$KIT_GRADLE_METADATA"
    if [[ -f "$GRADLE_METADATA" ]]; then
        cp "$GRADLE_METADATA" "$KIT_GRADLE_METADATA"
    fi
    info "Running FFmpegKitNext's Gradle tasks to record what they download"
    in_kit_nix_shell bash -c 'cd android && ./gradlew --write-verification-metadata sha256 ffmpeg-kit-next-android-lib:clean ffmpeg-kit-next-android-lib:assembleRelease ffmpeg-kit-next-android-lib:testReleaseUnitTest' ||
        die "Gradle failed: nothing was written"
    stamp_gradle_distributions
    [[ -f "$KIT_GRADLE_METADATA" ]] || die "Gradle wrote no $KIT_GRADLE_METADATA"
    cp "$KIT_GRADLE_METADATA" "$GRADLE_METADATA.part"
    mv "$GRADLE_METADATA.part" "$GRADLE_METADATA"
    info "Written: scripts/$(basename "$GRADLE_METADATA")"
    info "Read it (it lists every artifact and its SHA-256), then commit it."
}

# FFmpegKitNext compiles the day of the build into libffmpegkit.so
# (FFMPEG_KIT_BUILD_DATE, from date), so builds on two days would differ. The day
# of FFmpegKitNext's commit is written instead (fetch_kit puts the file back each
# run), and SOURCE_DATE_EPOCH is that commit's time, for any tool that reads it.
pin_build_date() {
    local file="$KIT/scripts/function-android.sh" needle replacement content changed
    BUILD_DAY="$(git -C "$KIT" log -1 --format=%cd --date=format:%Y%m%d)"
    # The text of FFmpegKitNext's script, as it is (nothing to expand here).
    # shellcheck disable=SC2016
    needle='FFMPEG_KIT_BUILD_DATE=$(date +%Y%m%d 2>>"${BASEDIR}"/build.log)'
    replacement="FFMPEG_KIT_BUILD_DATE=$BUILD_DAY"
    content="$(cat "$file"; echo x)"
    content="${content%x}"
    [[ "$content" == *"$needle"* ]] || die "$file no longer takes the build day from date as this script expects: check how this FFmpegKitNext release sets FFMPEG_KIT_BUILD_DATE"
    changed="${content/"$needle"/"$replacement"}"
    [[ "$changed" != *"$needle"* ]] || die "$file takes the build day from date more than once: check this FFmpegKitNext release"
    printf '%s' "$changed" >"$file"
    SOURCE_DATE_EPOCH="$(git -C "$KIT" log -1 --format=%ct)"
    export SOURCE_DATE_EPOCH
}

# Field $1 (url, sha256) of scripts/ffmpeg-kit-next-aar.txt, or nothing.
aar_pin_field() {
    [[ -f "$AAR_PIN" ]] || return 0
    awk -v key="$1" '$1 == key { print $2; exit }' "$AAR_PIN"
}

# What the libraries in prebuilt/ must have been built from to be reused.
build_inputs() {
    echo "script $(sha256_of "$SCRIPT")"
    echo "lock $(sha256_of "$LOCK")"
    echo "kit $KIT_COMMIT"
    echo "profile $NIX_PROFILE"
    echo "options ${BUILD_OPTIONS[*]}${EXTRA_ENABLES[*]+ ${EXTRA_ENABLES[*]}}"
}

# FFmpegKitNext reuses any library it finds in prebuilt/ without checking which
# source built it. So prebuilt/ is emptied unless it was made from the very same
# inputs, with no source fetched again since; the stamp is written before the
# build, so an interrupted build can go on from where it stopped.
prepare_prebuilt() {
    local inputs
    inputs="$(build_inputs)"
    if ((REFETCHED)) || [[ ! -f "$INPUTS_STAMP" ]] || [[ "$(cat "$INPUTS_STAMP")" != "$inputs" ]]; then
        if [[ -d "$KIT/prebuilt" ]]; then
            info "Sources, lock, options or script changed: building every library again"
        fi
        rm -rf "$KIT/prebuilt"
    fi
    mkdir -p "$KIT/prebuilt"
    echo "$inputs" >"$INPUTS_STAMP"
    # The AAR is always made anew by this build.
    rm -rf "$KIT/prebuilt/bundle-android-aar-$API_LEVEL-maven"
}

# Every source the build used must still be the locked commit. Prints what is
# wrong, if anything.
built_sources_problem() {
    local dir name commit actual
    for dir in "$KIT"/src/*/; do
        [[ -d "$dir" ]] || continue
        name="$(basename "$dir")"
        if ! commit="$(lock_field lib "$name" 5)"; then
            echo "the build used $name, which is not in the lock"
            return
        fi
        actual="$(head_of "$dir")"
        if [[ "$actual" != "$commit" ]]; then
            echo "$name was built from ${actual:-an unknown commit}, the lock says $commit"
            return
        fi
    done
    actual="$(head_of "$KIT/.tmp/source/config")"
    if [[ "$actual" != "$(lock_field lib config 5)" ]]; then
        echo "gnu config is ${actual:-unknown}, not the locked commit"
    fi
}

# ---------------------------------------------------------------------------

CLEAN=0
WRITE_GRADLE_VERIFICATION=0
EXTRA_OPTIONS=()
while (($# > 0)); do
    case "$1" in
        --clean)
            CLEAN=1
            shift
            ;;
        --write-gradle-verification)
            WRITE_GRADLE_VERIFICATION=1
            shift
            ;;
        -h | --help)
            usage
            exit 0
            ;;
        --)
            shift
            EXTRA_OPTIONS=("$@")
            break
            ;;
        *)
            die "unknown option $1 (build options go after --; see --help)"
            ;;
    esac
done

EXTRA_ENABLES=()
for option in ${EXTRA_OPTIONS[@]+"${EXTRA_OPTIONS[@]}"}; do
    case "$option" in
        --enable-*)
            sources_for_option "$option" >/dev/null || die "$option is not a library this script knows (see sources_for_option)"
            EXTRA_ENABLES+=("$option")
            ;;
        --jobs=* | --rebuild-* | --reconf-*) ;;
        *)
            die "$option is not accepted: only --enable-<library>, --jobs=N, --rebuild-<library> and --reconf-<library>"
            ;;
    esac
done

command -v git >/dev/null 2>&1 || die "git is needed"
command -v nix >/dev/null 2>&1 || die "Nix is needed: https://github.com/arthenica/ffmpeg-kit-next/wiki/Nix"
[[ -f "$LOCK" ]] || die "$LOCK is missing"

prepare_work_folder
fetch_kit
check_lock_against_kit
pin_gradle_distribution
pin_build_date

prepare_gradle_home
if ((WRITE_GRADLE_VERIFICATION)); then
    write_gradle_verification
    exit 0
fi
[[ -f "$GRADLE_METADATA" ]] || die "scripts/$(basename "$GRADLE_METADATA") is missing: run this script with --write-gradle-verification once, read the file and commit it (see --help)"
cp "$GRADLE_METADATA" "$KIT_GRADLE_METADATA"
# No other setting can make Gradle's dependency verification lenient or turn it off.
printf 'org.gradle.dependency.verification=strict\n' >"$GRADLE_USER_HOME/gradle.properties"

SOURCES="ffmpeg cpu-features"
for option in "${BUILD_OPTIONS[@]}" ${EXTRA_ENABLES[@]+"${EXTRA_ENABLES[@]}"}; do
    SOURCES="$SOURCES $(sources_for_option "$option")"
done
drop_unlocked_sources
for name in $(echo "$SOURCES" | tr ' ' '\n' | grep -v '^$' | sort -u); do
    fetch_source "$name" "$KIT/src/$name"
done
fetch_source config "$KIT/.tmp/source/config"

for dir in "$KIT"/src/*/; do
    [[ -d "$dir" ]] && restore_source "$dir"
done
restore_source "$KIT/.tmp/source/config"

prepare_prebuilt

info "Building FFmpegKitNext $KIT_TAG (log: $KIT/build.log)"
BUILD_STATUS=0
(
    cd "$KIT"
    ./nix-android.sh -p "$NIX_PROFILE" "${BUILD_OPTIONS[@]}" ${EXTRA_OPTIONS[@]+"${EXTRA_OPTIONS[@]}"}
) || BUILD_STATUS=$?

PROBLEM="$(built_sources_problem)"
if [[ -n "$PROBLEM" ]]; then
    rm -rf "$KIT/prebuilt"
    die "$PROBLEM: everything built from it was deleted and the AAR was not copied"
fi
((BUILD_STATUS == 0)) || die "the build failed: see $KIT/build.log (a Gradle dependency verification failure means a download differs from scripts/$(basename "$GRADLE_METADATA"))"
stamp_gradle_distributions

VERSION="$(sed -n 's/.*#define FFMPEG_KIT_VERSION "\([^"]*\)".*/\1/p' "$KIT/android/ffmpeg-kit-next-android-lib/src/main/cpp/ffmpegkit.h")"
EXPECTED="$(sed -n 's/^ffmpegKitNext *= *"\([^"]*\)".*/\1/p' "$ROOT/gradle/libs.versions.toml")"
[[ -n "$VERSION" && "$VERSION" == "$EXPECTED" ]] || die "FFmpegKitNext built version ${VERSION:-unknown}, gradle/libs.versions.toml asks for ${EXPECTED:-nothing} (ffmpegKitNext)"

AAR="$KIT/prebuilt/bundle-android-aar-$API_LEVEL-maven/com/arthenica/ffmpeg-kit-next/$VERSION/ffmpeg-kit-next-$VERSION.aar"
[[ -f "$AAR" ]] || die "the build made no $AAR"

TARGET="$TARGET_DIR/ffmpeg-kit-next-$VERSION.aar"
mkdir -p "$TARGET_DIR"
cp "$AAR" "$TARGET.part"
mv "$TARGET.part" "$TARGET"
AAR_SHA256="$(sha256_of "$TARGET")"

{
    echo "# How media/libs/ffmpeg-kit-next-$VERSION.aar was built (scripts/build-ffmpeg-kit-next.sh)."
    echo "aar_sha256      $AAR_SHA256"
    echo "built_at        $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "host            $(uname -sm)"
    echo "ffmpeg_kit_next $KIT_TAG $KIT_COMMIT"
    echo "nix_profile     $NIX_PROFILE"
    echo "flake_lock      $(sha256_of "$KIT/flake.lock")"
    echo "source_lock     $(sha256_of "$LOCK")"
    echo "script          $(sha256_of "$SCRIPT")"
    echo "work_folder     $(real_path "$WORK")"
    echo "build_day       $BUILD_DAY (SOURCE_DATE_EPOCH $SOURCE_DATE_EPOCH)"
    echo "gradle          $GRADLE_VERSION $GRADLE_SHA256"
    echo "gradle_verify   $(sha256_of "$GRADLE_METADATA")"
    echo "options         ${BUILD_OPTIONS[*]}${EXTRA_OPTIONS[*]+ ${EXTRA_OPTIONS[*]}}"
    for dir in "$KIT"/src/*/; do
        [[ -d "$dir" ]] || continue
        echo "source          $(basename "$dir") $(head_of "$dir")"
    done
} >"$TARGET_DIR/ffmpeg-kit-next-$VERSION.provenance.txt"

info "Done: media/libs/ffmpeg-kit-next-$VERSION.aar"
info "SHA-256 $AAR_SHA256 (details in media/libs/ffmpeg-kit-next-$VERSION.provenance.txt)"
PINNED="$(aar_pin_field sha256)"
if [[ "$PINNED" == "$AAR_SHA256" ]]; then
    info "Byte-identical to the AAR pinned in scripts/$(basename "$AAR_PIN"): reproduced."
elif [[ "$PINNED" =~ ^[0-9a-f]{64}$ ]]; then
    echo "warning: not the AAR pinned in scripts/$(basename "$AAR_PIN") ($PINNED)." >&2
    if [[ "$(real_path "$WORK")" != "$CANONICAL_WORK" ]]; then
        echo "warning: the AAR holds the work folder's path: build with FFMPEG_KIT_WORKDIR=$CANONICAL_WORK to reproduce the published one." >&2
    fi
    echo "warning: the Gradle build only takes the pinned AAR (scripts/fetch-ffmpeg-kit-next.sh fetches it), or this one with -Pffmpeg.unpinned=true." >&2
fi
