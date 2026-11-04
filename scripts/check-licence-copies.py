#!/usr/bin/env python3
"""
Checks the licence texts FFmpeg GUI keeps of other repositories whose code or
artwork it uses (scripts/licence-copies.lock): every copy must be byte for
byte the upstream file the lock pins, and must be listed in the licences'
README (app/src/main/assets/licenses/README.txt). Run, online, by
.github/workflows/release.yml for every v* tag. Run it by hand any time.

With --online it also reads the upstream file at the pinned commit (it must
be the pinned one) and today (a change is reported, not fixed: read it, then
update the copy and the lock).

Texts are compared with their line endings made LF (CRLF and CR become LF):
the same licence with Windows line endings is the same licence, and git may
change them on the way.

Exit status 0 when everything agrees, 1 otherwise. Not legal advice.
"""

import argparse
import hashlib
import os
import sys
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOCK = os.path.join(ROOT, "scripts", "licence-copies.lock")
README = os.path.join(ROOT, "app", "src", "main", "assets", "licenses", "README.txt")

problems = []


def problem(message):
    problems.append(message)
    print("FAIL  " + message)


def ok(message):
    print("ok    " + message)


def text_sha256(data):
    """SHA-256 of a text with its line endings made LF."""
    return hashlib.sha256(data.replace(b"\r\n", b"\n").replace(b"\r", b"\n")).hexdigest()


def entries():
    out = []
    with open(LOCK, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            fields = line.split()
            if len(fields) != 6:
                problem("scripts/licence-copies.lock: a line needs 6 fields: %s" % line)
                continue
            out.append(dict(zip(("name", "repository", "commit", "file", "sha256", "copy"), fields)))
    return out


def raw(repository, ref, path):
    url = repository.replace("https://github.com/", "https://raw.githubusercontent.com/") + "/%s/%s" % (ref, path)
    request = urllib.request.Request(url, headers={"User-Agent": "ffmpeg-gui-licence-copies"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--online", action="store_true")
    args = parser.parse_args()

    with open(README, encoding="utf-8") as f:
        readme = f.read()
    for e in entries():
        path = os.path.join(ROOT, e["copy"])
        if not os.path.isfile(path):
            problem("%s: %s is missing" % (e["name"], e["copy"]))
            continue
        with open(path, "rb") as f:
            got = text_sha256(f.read())
        if got != e["sha256"]:
            problem("%s: %s is not the upstream %s the lock pins" % (e["name"], e["copy"], e["file"]))
        else:
            ok("%s: %s as upstream" % (e["name"], e["copy"]))
        listed = e["copy"].split("assets/licenses/", 1)[-1]
        if listed not in readme:
            problem("%s: %s is not listed in assets/licenses/README.txt" % (e["name"], listed))
        if args.online:
            for ref, strict in ((e["commit"], True), ("HEAD", False)):
                try:
                    digest = text_sha256(raw(e["repository"], ref, e["file"]))
                except Exception as error:  # noqa: BLE001 - any failure is reported
                    problem("%s: cannot read %s at %s: %s" % (e["name"], e["file"], ref, error))
                    continue
                if digest == e["sha256"]:
                    ok("%s: %s at %s is the one pinned" % (e["name"], e["file"], ref[:12]))
                elif strict:
                    problem("%s: %s at the pinned commit is not the one pinned" % (e["name"], e["file"]))
                else:
                    problem("%s: %s upstream has changed since it was pinned: read it, then update the copy and the lock" % (e["name"], e["file"]))

    if problems:
        print("\n%d problem(s)." % len(problems))
        sys.exit(1)
    print("\nEvery licence copy agrees.")


if __name__ == "__main__":
    main()
