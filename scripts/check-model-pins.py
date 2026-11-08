#!/usr/bin/env python3
"""
Checks that what the app says about its AI models agrees everywhere: the
SHA-256, sizes and release tags pinned in the Kotlin code, the locks the
ONNX files are made from (scripts/*-onnx.lock), the licences and notices that
come with the app (app/src/main/assets/licenses/), and the record of every
model (docs/MODELS.md). Run, online, by .github/workflows/release.yml for
every v* tag (a release is refused until everything agrees), and by
models-onnx.yml before it publishes a file. Run it by hand any time.

Offline (always):
  1. EdgeTAM: RefineModel.kt pins the files, sizes and release tag of
     scripts/edgetam-onnx.lock; BiRefNet-lite: CutoutModel.kt (FINEST) those
     of scripts/birefnet-onnx.lock; both download from this repository's
     release of that tag.
  2. The licences bundled with the app are byte for byte the upstream ones the
     locks pin, and the notices bundled are the ones the export scripts write
     (the same that go out with the files).
  3. Every SHA-256 the app pins for a model is in docs/MODELS.md, and so is
     every model's licence: a model cannot be added without its record.
  4. No retired model's folder (RetiredModels.kt, deleted at start) is the
     folder of a model in use: a slip there would delete a user's model.

  6. The stems (voice/StemModels.kt): Bandit v2 pins what scripts/bandit-onnx.lock
     does, Spleeter 5 stems what scripts/spleeter-onnx.lock does, each from
     this repository's release of its tag, with its licence and NOTICE bundled
     (assets/licenses/bandit/, assets/licenses/spleeter/). A lock of zeros is
     still waiting for its first build (run .github/workflows/models-onnx.yml
     without publishing, then scripts/pin-stems.py).

  5. The AI marks (marking/MarkerModel.kt) pin what scripts/ai-markers.lock
     does, every value pinned (a lock of zeros is still being learned: see
     .github/workflows/ai-markers.yml), with their licences and NOTICE
     bundled (assets/licenses/ai-markers/).

With --release-dir NAME=DIR (NAME edgetam, birefnet, bandit, spleeter or markers; the folder an export
script wrote, or a release downloaded): the files there are the pinned ones,
and LICENSE, NOTICE, PROVENANCE and SHA256SUMS are there and agree; with the
onnx package installed, each file carries the licence and notice inside it.

With --online: the release assets answer, and the upstream LICENSE today is
still the one pinned (a change is reported, not fixed: read it first).

Exit status 0 when everything agrees, 1 otherwise. Not legal advice: it checks
that the project keeps to what docs/MODELS.md says, not what the law says.
"""

import argparse
import hashlib
import importlib.util
import os
import re
import sys
import urllib.error
import urllib.request

# The export scripts are read as modules: no __pycache__ left in scripts/.
sys.dont_write_bytecode = True

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app", "src", "main", "java", "com", "cuscus", "ffmpeggui")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "licenses")
RECORD = os.path.join(ROOT, "docs", "MODELS.md")
REPOSITORY = "marcomorosi06/FFmpegGUI"

# The Kotlin files that pin models, and the record they must all be in.
MODEL_FILES = [
    "cutout/CutoutModel.kt",
    "cutout/RefineModel.kt",
    "erase/EraseModel.kt",
    "voice/StemModels.kt",
    "dub/DubModel.kt",
    "speech/SpeechModel.kt",
    "marking/MarkerModel.kt",
]

problems = []
notes = []


def problem(message):
    problems.append(message)
    print("FAIL  " + message)


def ok(message):
    print("ok    " + message)


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def read_lock(path):
    values = {}
    for line in read(path).splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, _, value = line.partition(" ")
        values[key] = value.strip()
    return values


def load_script(name):
    """An export script as a module (only its top level runs: no heavy import)."""
    path = os.path.join(ROOT, "scripts", name)
    spec = importlib.util.spec_from_file_location(name.replace("-", "_").replace(".py", ""), path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def kotlin_file_block(source, name):
    """The arguments of `val NAME = ...ModelFile(` (or CutoutModel( ) up to its closing parenthesis."""
    match = re.search(r"val %s = [A-Za-z.]*\(" % re.escape(name), source)
    if not match:
        return None
    depth = 1
    i = match.end()
    while i < len(source) and depth:
        if source[i] == "(":
            depth += 1
        elif source[i] == ")":
            depth -= 1
        i += 1
    return source[match.end():i - 1]


def field(block, key):
    m = re.search(r'\b%s = "([^"]*)"' % re.escape(key), block or "")
    return m.group(1) if m else None


def number(block, key):
    m = re.search(r"\b%s = ([0-9_]+)L" % re.escape(key), block or "")
    return int(m.group(1).replace("_", "")) if m else None


def const(source, name):
    m = re.search(r'const val %s = "([^"]*)"' % re.escape(name), source)
    return m.group(1) if m else None


# -- 1. The pins against the locks ------------------------------------------------------

def check_edgetam(lock):
    source = read(os.path.join(APP, "cutout", "RefineModel.kt"))
    tag = const(source, "RELEASE_TAG")
    if tag != lock["release_tag"]:
        problem("RefineModel.RELEASE_TAG is %s, scripts/edgetam-onnx.lock says %s" % (tag, lock["release_tag"]))
    else:
        ok("EdgeTAM release tag %s" % tag)
    download = const(source, "DOWNLOAD_URL")
    if download != "https://github.com/%s/releases/download/$RELEASE_TAG" % REPOSITORY:
        problem("RefineModel's DOWNLOAD_URL is not this repository's release: %s" % download)
    for role in ("encoder", "decoder"):
        block = kotlin_file_block(source, role.upper())
        name, digest, size = field(block, "name"), field(block, "sha256"), number(block, "bytes")
        want = (lock[role + "_file"], lock[role + "_sha256"], int(lock[role + "_bytes"]))
        if (name, digest, size) != want:
            problem("RefineModel.%s is %s, the lock says %s" % (role.upper(), (name, digest, size), want))
        else:
            ok("EdgeTAM %s %s %s" % (role, name, digest[:12]))


def check_birefnet(lock):
    source = read(os.path.join(APP, "cutout", "CutoutModel.kt"))
    tag = const(source, "BIREFNET_RELEASE_TAG")
    if tag != lock["release_tag"]:
        problem("CutoutModel.BIREFNET_RELEASE_TAG is %s, scripts/birefnet-onnx.lock says %s" % (tag, lock["release_tag"]))
    else:
        ok("BiRefNet-lite release tag %s" % tag)
    block = kotlin_file_block(source, "FINEST")
    name, digest, size = field(block, "fileName"), field(block, "sha256"), number(block, "bytes")
    want = (lock["file"], lock["file_sha256"], int(lock["file_bytes"]))
    if (name, digest, size) != want:
        problem("CutoutModel.FINEST is %s, the lock says %s" % ((name, digest, size), want))
    else:
        ok("BiRefNet-lite %s %s" % (name, digest[:12]))
    url = field(block, "downloadUrl")
    if url != "https://github.com/%s/releases/download/$BIREFNET_RELEASE_TAG/%s" % (REPOSITORY, lock["file"]):
        problem("CutoutModel.FINEST downloads from %s, not this repository's release" % url)
    if field(block, "licence") != "MIT":
        problem("CutoutModel.FINEST does not say MIT")


def unpinned(value):
    return set(value) == {"0"}


MARKER_FILES = (("generator", "generator_sha256"), ("detector", "detector_sha256"),
                ("encoder", "trustmark_encoder_sha256"), ("decoder", "trustmark_decoder_sha256"))


def check_markers(lock):
    """The AI marks' files (MarkerModel.kt) against scripts/ai-markers.lock; nothing may be left unpinned."""
    waiting = [k for k, v in lock.items() if unpinned(v)]
    if waiting:
        problem("scripts/ai-markers.lock is not pinned yet (%s): run .github/workflows/ai-markers.yml, "
                "then scripts/pin-ai-markers.py with what it learned" % ", ".join(waiting))
    source = read(os.path.join(APP, "marking", "MarkerModel.kt"))
    tag = const(source, "RELEASE_TAG")
    if tag != lock["release_tag"]:
        problem("MarkerModel.RELEASE_TAG is %s, scripts/ai-markers.lock says %s" % (tag, lock["release_tag"]))
    else:
        ok("AI marks release tag %s" % tag)
    if const(source, "DOWNLOAD_URL") != "https://github.com/%s/releases/download/$RELEASE_TAG" % REPOSITORY:
        problem("MarkerModel's DOWNLOAD_URL is not this repository's release")
    for role, sha_key in MARKER_FILES:
        block = kotlin_file_block(source, role.upper())
        got = (field(block, "name"), field(block, "sha256"), number(block, "bytes"))
        want = (lock[role + "_file"], lock[sha_key], int(lock[role + "_bytes"]))
        if got != want:
            problem("MarkerModel.%s is %s, the lock says %s" % (role.upper(), got, want))
        else:
            ok("AI marks %s %s %s" % (role, got[0], got[1][:12]))


STEMS = (("bandit", "BANDIT", "BANDIT_RELEASE_TAG"), ("spleeter", "INSTRUMENTS_FILE", "INSTRUMENTS_RELEASE_TAG"))


def check_stems(name, lock, strict):
    """
    A stem model of voice/StemModels.kt against its lock: name, SHA-256, size,
    release tag, download address. A lock still of zeros is a problem when
    [strict] (the whole check, before a release, or a check of that model's
    own build); a build of another model is only told it is waiting, so that
    the run that is to learn its pins can get through.
    """
    kotlin = dict((n, (v, t)) for n, v, t in STEMS)[name]
    source = read(os.path.join(APP, "voice", "StemModels.kt"))
    if unpinned(lock["file_sha256"]) or lock["file_bytes"] == "0":
        message = ("scripts/%s-onnx.lock is not pinned yet: run .github/workflows/models-onnx.yml (without publishing), "
                   "then scripts/pin-stems.py with what it built" % name)
        if strict:
            problem(message)
        else:
            print("wait  " + message)
    tag = const(source, kotlin[1])
    if tag != lock["release_tag"]:
        problem("StemModels.%s is %s, scripts/%s-onnx.lock says %s" % (kotlin[1], tag, name, lock["release_tag"]))
    else:
        ok("%s release tag %s" % (name, tag))
    block = kotlin_file_block(source, kotlin[0])
    got = (field(block, "name"), field(block, "sha256"), number(block, "bytes"))
    want = (lock["file"], lock["file_sha256"], int(lock["file_bytes"]))
    if got != want:
        problem("StemModels.%s is %s, the lock says %s" % (kotlin[0], got, want))
    else:
        ok("%s %s %s" % (name, got[0], got[1][:12]))
    if "https://github.com/%s/releases/download/$" % REPOSITORY not in source:
        problem("StemModels.kt does not download from this repository's releases")


def stems_notice(name, lock):
    return load_script("export-%s.py" % name).notice_of(lock)


def markers_notice(lock):
    return load_script("export-ai-markers.py").notice_of(lock)


def check_markers_bundled(lock, notice):
    folder = os.path.join(ASSETS, "ai-markers")
    for name, key in (("LICENSE-audioseal.txt", "audioseal_licence_sha256"), ("LICENSE-trustmark.txt", "trustmark_licence_sha256")):
        path = os.path.join(folder, name)
        if not os.path.isfile(path):
            problem("assets/licenses/ai-markers/%s is missing" % name)
        elif sha256(path) != lock[key]:
            problem("assets/licenses/ai-markers/%s is not the upstream LICENSE the lock pins" % name)
        else:
            ok("AI marks %s bundled, as upstream" % name)
    path = os.path.join(folder, "NOTICE.txt")
    if not os.path.isfile(path):
        problem("assets/licenses/ai-markers/NOTICE.txt is missing")
    elif read(path) != notice:
        problem("assets/licenses/ai-markers/NOTICE.txt is not the NOTICE the export writes: run scripts/pin-ai-markers.py")
    else:
        ok("AI marks notice bundled, as the export writes it")
    if any(ord(c) > 127 for c in notice):
        problem("the AI marks' NOTICE is not plain ASCII")


def check_markers_release(folder, lock, notice):
    files = {lock[role + "_file"]: lock[key] for role, key in MARKER_FILES}
    for f, digest in files.items():
        path = os.path.join(folder, f)
        if not os.path.isfile(path):
            problem("%s: %s is missing" % (folder, f))
            continue
        got = sha256(path)
        if got != digest:
            problem("%s: %s is %s, pinned %s" % (folder, f, got, digest))
        else:
            ok("AI marks: %s as pinned" % f)
    try:
        import onnx
        for f in (lock["generator_file"], lock["detector_file"]):
            path = os.path.join(folder, f)
            if not os.path.isfile(path):
                continue
            model = onnx.load(path, load_external_data=False)
            meta = {p.key: p.value for p in model.metadata_props}
            if meta.get("notice") != notice or sha256_text(meta.get("license", "")) != lock["audioseal_licence_sha256"]:
                problem("%s: %s does not carry its licence and notice inside it" % (folder, f))
    except ImportError:
        notes.append("onnx is not installed: the licence inside the AudioSeal files was not checked")

    def side(name):
        for candidate in (name, name + ".txt"):
            path = os.path.join(folder, candidate)
            if os.path.isfile(path):
                return path
        return None

    for name, key in (("LICENSE-audioseal", "audioseal_licence_sha256"), ("LICENSE-trustmark", "trustmark_licence_sha256")):
        path = side(name)
        if path is None or sha256(path) != lock[key]:
            problem("%s: %s is missing or not the upstream one" % (folder, name))
    path = side("NOTICE")
    if path is None or read(path) != notice:
        problem("%s: NOTICE is missing or not the one the app bundles" % folder)
    for extra in ("PROVENANCE", "SHA256SUMS"):
        if side(extra) is None:
            problem("%s: %s is missing" % (folder, extra))
    path = side("SHA256SUMS")
    if path:
        listed = dict(reversed(line.split("  ", 1)) for line in read(path).splitlines() if "  " in line)
        for f, digest in files.items():
            if listed.get(f) != digest:
                problem("%s: SHA256SUMS does not list %s as pinned" % (folder, f))


def check_markers_online(lock):
    for asset in [lock[role + "_file"] for role, _ in MARKER_FILES] + ["LICENSE-audioseal.txt", "LICENSE-trustmark.txt", "NOTICE.txt", "PROVENANCE.txt"]:
        url = "https://github.com/%s/releases/download/%s/%s" % (REPOSITORY, lock["release_tag"], asset)
        try:
            status, _ = fetch(url, head=True)
            ok("%s answers (%d)" % (url, status))
        except Exception as e:  # noqa: BLE001
            problem("%s does not answer: %s (publish it: .github/workflows/ai-markers.yml)" % (url, e))
    checks = (
        (lock["audioseal_licence_url"], lambda body: hashlib.sha256(body).hexdigest() == lock["audioseal_licence_sha256"], "AudioSeal's LICENSE today differs from the one pinned"),
        ("https://raw.githubusercontent.com/adobe/trustmark/HEAD/LICENSE", lambda body: hashlib.sha256(body).hexdigest() == lock["trustmark_licence_sha256"], "TrustMark's LICENSE today differs from the one pinned"),
        ("https://raw.githubusercontent.com/adobe/trustmark/HEAD/README.md", lambda body: b"The MIT license covers both the code in this repo and the model files" in body, "TrustMark's README no longer says the MIT licence covers the model files"),
    )
    for url, good, failure in checks:
        try:
            _, body = fetch(url)
            if good(body):
                ok("%s: as pinned" % url)
            else:
                problem(failure + ": read it, then update the lock and docs/MODELS.md")
        except Exception as e:  # noqa: BLE001
            problem("could not read %s: %s" % (url, e))


# -- 2. The licences and notices bundled ------------------------------------------------

def edgetam_notice(lock):
    script = load_script("export-edgetam.py")
    return script.NOTICE % {"commit": lock["commit"], "config": lock["config"], "checkpoint": lock["checkpoint"],
                            "checkpoint_sha256": lock["checkpoint_sha256"], "opset": lock["opset"]}


def birefnet_notice(lock):
    script = load_script("export-birefnet.py")
    return script.NOTICE % {"commit": lock["commit"], "weights": os.path.basename(lock["weights_url"]),
                            "weights_sha256": lock["weights_sha256"], "opset": lock["opset"]}


def check_bundled(name, lock, notice):
    folder = os.path.join(ASSETS, name)
    licence = os.path.join(folder, "LICENSE.txt")
    if not os.path.isfile(licence):
        problem("assets/licenses/%s/LICENSE.txt is missing" % name)
    elif sha256(licence) != lock["licence_sha256"]:
        problem("assets/licenses/%s/LICENSE.txt is not the upstream LICENSE the lock pins" % name)
    else:
        ok("%s licence bundled, as upstream" % name)
    path = os.path.join(folder, "NOTICE.txt")
    if not os.path.isfile(path):
        problem("assets/licenses/%s/NOTICE.txt is missing" % name)
    elif read(path) != notice:
        problem("assets/licenses/%s/NOTICE.txt is not the NOTICE the export script writes: copy it again" % name)
    else:
        ok("%s notice bundled, as the export writes it" % name)
    for text, what in ((read(licence) if os.path.isfile(licence) else "", "LICENSE.txt"), (notice, "NOTICE")):
        if any(ord(c) > 127 for c in text) and what == "NOTICE":
            problem("%s's %s is not plain ASCII" % (name, what))


# -- 3. The record --------------------------------------------------------------------

def check_record():
    if not os.path.isfile(RECORD):
        problem("docs/MODELS.md is missing")
        return
    record = read(RECORD)
    count = 0
    for relative in MODEL_FILES:
        source = read(os.path.join(APP, relative))
        for digest in sorted(set(re.findall(r'"([0-9a-f]{64})"', source))):
            if unpinned(digest):
                continue
            count += 1
            if digest not in record:
                problem("%s pins %s, which docs/MODELS.md does not record" % (relative, digest))
        for licence in set(re.findall(r'LICEN[CS]E[A-Z_]* = "([^"]+)"', source)) | set(re.findall(r'licence = "([^"]+)"', source)):
            if licence not in record:
                problem("%s says licence %s, which docs/MODELS.md does not mention" % (relative, licence))
    ok("%d pinned hashes, all in docs/MODELS.md" % count)


# -- 4. Retired folders ----------------------------------------------------------------

def check_retired():
    retired_source = read(os.path.join(APP, "cutout", "RetiredModels.kt"))
    retired = re.findall(r'folder = "([^"]+)"', retired_source)
    live = set()
    for root, _, files in os.walk(APP):
        for f in files:
            if not f.endswith(".kt") or f == "RetiredModels.kt":
                continue
            live |= set(re.findall(r'folder = "([^"]+)"', read(os.path.join(root, f))))
    for folder in retired:
        clash = [l for l in live if l == folder or l.startswith(folder + "/") or folder.startswith(l + "/")]
        if clash:
            problem("retired folder %s overlaps the folder of a model in use: %s" % (folder, clash))
    ok("%d retired folder(s), none in use" % len(retired))


# -- --release-dir ---------------------------------------------------------------------

def check_release(name, folder, lock, notice):
    files = {}
    if name == "edgetam":
        files = {lock["encoder_file"]: lock["encoder_sha256"], lock["decoder_file"]: lock["decoder_sha256"]}
    else:
        files = {lock["file"]: lock["file_sha256"]}
    for f, digest in files.items():
        path = os.path.join(folder, f)
        if not os.path.isfile(path):
            problem("%s: %s is missing" % (folder, f))
            continue
        got = sha256(path)
        if got != digest:
            problem("%s: %s is %s, pinned %s" % (folder, f, got, digest))
        else:
            ok("%s: %s as pinned" % (name, f))
        try:
            import onnx
        except ImportError:
            notes.append("onnx is not installed: the licence inside %s was not checked" % f)
            continue
        model = onnx.load(path, load_external_data=False)
        meta = {p.key: p.value for p in model.metadata_props}
        if meta.get("notice") != notice or model.doc_string != notice:
            problem("%s: %s does not carry the notice inside it" % (folder, f))
        if sha256_text(meta.get("license", "")) != lock["licence_sha256"]:
            problem("%s: %s does not carry the upstream licence inside it" % (folder, f))
        else:
            ok("%s: %s carries its licence and notice" % (name, f))
    # The export scripts write LICENSE, NOTICE, ...; the releases carry them as
    # LICENSE.txt, NOTICE.txt, ... (see models-onnx.yml): either is taken.
    def side(name):
        for candidate in (name, name + ".txt"):
            path = os.path.join(folder, candidate)
            if os.path.isfile(path):
                return path
        return None

    found = {}
    for extra in ("LICENSE", "NOTICE", "PROVENANCE", "SHA256SUMS"):
        found[extra] = side(extra)
        if found[extra] is None:
            problem("%s: %s (or %s.txt) is missing" % (folder, extra, extra))
    if found["LICENSE"] and sha256(found["LICENSE"]) != lock["licence_sha256"]:
        problem("%s: LICENSE is not the upstream one" % folder)
    if found["NOTICE"] and read(found["NOTICE"]) != notice:
        problem("%s: NOTICE is not the one the app bundles" % folder)
    if found["SHA256SUMS"]:
        listed = dict(reversed(line.split("  ", 1)) for line in read(found["SHA256SUMS"]).splitlines() if "  " in line)
        for f, digest in files.items():
            if listed.get(f) != digest:
                problem("%s: SHA256SUMS does not list %s as pinned" % (folder, f))


def sha256_text(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


# -- --online --------------------------------------------------------------------------

class _NoRedirect(urllib.request.HTTPRedirectHandler):
    """A release asset answers with a redirect to its storage: that is enough, it is not followed (nor downloaded)."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def fetch(url, head=False):
    request = urllib.request.Request(url, method="HEAD" if head else "GET", headers={"User-Agent": "ffmpeg-gui-model-pins"})
    if not head:
        with urllib.request.urlopen(request, timeout=60) as response:
            return response.status, response.read()
    opener = urllib.request.build_opener(_NoRedirect)
    try:
        with opener.open(request, timeout=60) as response:
            return response.status, b""
    except urllib.error.HTTPError as e:
        if e.code in (301, 302, 303, 307, 308):
            return e.code, b""
        raise


def check_online(locks):
    for name, lock in locks.items():
        assets = [lock["encoder_file"], lock["decoder_file"]] if name == "edgetam" else [lock["file"]]
        # The side files go out as LICENSE.txt, ...; edgetam-onnx-1 was published
        # with LICENSE, ... and keeps them: either name answers.
        for asset in assets + ["LICENSE", "NOTICE", "PROVENANCE"]:
            names = [asset] if asset.endswith(".onnx") else [asset + ".txt", asset]
            answered = None
            for candidate in names:
                url = "https://github.com/%s/releases/download/%s/%s" % (REPOSITORY, lock["release_tag"], candidate)
                try:
                    status, _ = fetch(url, head=True)
                    answered = (url, status)
                    break
                except Exception as e:  # noqa: BLE001 - any failure is reported as such
                    failure = e
            if answered:
                ok("%s answers (%d)" % answered)
            else:
                problem("%s of %s does not answer: %s (publish it: .github/workflows/models-onnx.yml)" % (" or ".join(names), lock["release_tag"], failure))
        if "licence" in lock:
            upstream = lock["repository"].replace("https://github.com/", "https://raw.githubusercontent.com/") + "/HEAD/" + lock["licence"]
        else:
            # The weights' licence is not in the code's repository (Bandit v2: CC BY-SA 4.0 on Zenodo).
            upstream = lock["licence_url"]
        try:
            _, body = fetch(upstream)
            if hashlib.sha256(body).hexdigest() != lock["licence_sha256"]:
                problem("%s's LICENSE today differs from the one pinned: read it, then update the lock and docs/MODELS.md" % name)
            else:
                ok("%s's LICENSE today is still the one pinned" % name)
        except Exception as e:  # noqa: BLE001
            problem("could not read %s: %s" % (upstream, e))


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--release-dir", action="append", default=[], metavar="NAME=DIR")
    parser.add_argument("--online", action="store_true")
    args = parser.parse_args()

    locks = {
        "edgetam": read_lock(os.path.join(ROOT, "scripts", "edgetam-onnx.lock")),
        "birefnet": read_lock(os.path.join(ROOT, "scripts", "birefnet-onnx.lock")),
        "bandit": read_lock(os.path.join(ROOT, "scripts", "bandit-onnx.lock")),
        "spleeter": read_lock(os.path.join(ROOT, "scripts", "spleeter-onnx.lock")),
    }
    notices = {"edgetam": edgetam_notice(locks["edgetam"]), "birefnet": birefnet_notice(locks["birefnet"]),
               "bandit": stems_notice("bandit", locks["bandit"]), "spleeter": stems_notice("spleeter", locks["spleeter"])}
    markers = read_lock(os.path.join(ROOT, "scripts", "ai-markers.lock"))
    markers_text = markers_notice(markers)

    check_edgetam(locks["edgetam"])
    check_birefnet(locks["birefnet"])
    check_markers(markers)
    # Only the models whose builds are checked here, when some are: else every one.
    checked = [item.partition("=")[0] for item in args.release_dir]
    for name, _, _ in STEMS:
        check_stems(name, locks[name], strict=not checked or name in checked)
    for name in locks:
        check_bundled(name, locks[name], notices[name])
    check_markers_bundled(markers, markers_text)
    check_record()
    check_retired()
    for item in args.release_dir:
        name, _, folder = item.partition("=")
        if name == "markers" and folder:
            check_markers_release(folder, markers, markers_text)
            continue
        if name not in locks or not folder:
            problem("--release-dir takes edgetam=DIR, birefnet=DIR, bandit=DIR, spleeter=DIR or markers=DIR, not %s" % item)
            continue
        check_release(name, folder, locks[name], notices[name])
    if args.online:
        check_online(locks)
        check_markers_online(markers)

    for note in notes:
        print("note  " + note)
    if problems:
        print("\n%d problem(s)." % len(problems))
        sys.exit(1)
    print("\nEverything agrees.")


if __name__ == "__main__":
    main()
