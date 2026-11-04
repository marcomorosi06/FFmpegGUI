#!/usr/bin/env bash
# Writes into OUTDIR what goes next to the FFmpeg runtime (the AAR) on its
# release, as the GPL-3.0 asks of whoever distributes it (sections 4 and 6):
#
#   ffmpeg-kit-next-<version>-source.tar.xz  the Corresponding Source of the AAR
#                                            (scripts/package-sources.sh
#                                            --ffmpeg-runtime, of the commit
#                                            checked out);
#   COPYING-GPL-3.0.txt                      the licence the AAR as a whole is
#                                            distributed under;
#   NOTICE.txt                               what the AAR holds and under which
#                                            licences (section 1 of the app's
#                                            licences/README.txt).
#
# Run it on the commit the AAR was built from: .github/workflows/ffmpeg-aar.yml
# does, both for a new release and for one published before this script (it
# then checks out that release's tag, after checking its inputs are the pin's).
# The licence texts come from LICENCES_FROM (default: this checkout's
# app/src/main/assets/licenses): an older commit may not have them yet.
#
# Usage: scripts/ffmpeg-aar-release-files.sh VERSION OUTDIR

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LICENCES="${LICENCES_FROM:-$ROOT/app/src/main/assets/licenses}"
export LICENCES_FROM="$LICENCES"

die() {
    echo "error: $*" >&2
    exit 1
}

[[ $# -eq 2 ]] || die "usage: scripts/ffmpeg-aar-release-files.sh VERSION OUTDIR"
VERSION="$1"
OUTDIR="$2"
mkdir -p "$OUTDIR"
OUTDIR="$(cd "$OUTDIR" && pwd)"

GPL="$LICENCES/ffmpeg-libraries/ffmpeg-kit-next-GPL-3.0.txt"
[[ -f "$GPL" ]] || die "no $GPL"
cp "$GPL" "$OUTDIR/COPYING-GPL-3.0.txt"

# Section 1 of the licences' README: the FFmpeg runtime and what is built into it.
section="$(awk '/^1\. FFmpeg runtime/ { on = 1 } /^2\. / { on = 0 } on' "$LICENCES/README.txt")"
[[ -n "$section" ]] || die "no section 1 in assets/licenses/README.txt"
commit="$(git -C "$ROOT" rev-parse 'HEAD^{commit}')"
{
    cat <<EOF
FFmpeg runtime of FFmpeg GUI: FFmpegKitNext $VERSION (ffmpeg-kit-next-$VERSION.aar)
https://github.com/marcomorosi06/FFmpegGUI

The AAR holds FFmpeg built with GPL libraries (x264, x265, Xvid, vid.stab):
as a whole it is distributed under the GNU General Public License, version 3
(COPYING-GPL-3.0.txt, next to it). Each component keeps its own licence, as
below; their licence texts are in the source archive next to it (licenses/,
and inside each source folder).

Its complete Corresponding Source, the sources of every component at the
commits it was built from with the scripts that build it, is
ffmpeg-kit-next-$VERSION-source.tar.xz, on the same release page (built at
FFmpeg GUI commit $commit).

It comes without any warranty, to the extent permitted by law (GPL-3.0,
sections 15 and 16).

EOF
    echo "$section"
} >"$OUTDIR/NOTICE.txt"

bash "$ROOT/scripts/package-sources.sh" --ffmpeg-runtime "$OUTDIR/ffmpeg-kit-next-$VERSION-source.tar.xz"
ls -l "$OUTDIR"
