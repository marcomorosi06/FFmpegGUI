#!/usr/bin/env python3
"""
Copies the pins a first run of .github/workflows/models-onnx.yml found for a
stem model (the build's artifact, e.g. "bandit-ubuntu-24.04": its ONNX file,
SHA256SUMS and check.json) into the places that must agree on them
(scripts/check-model-pins.py checks they do):

  - scripts/<model>-onnx.lock (only the values still zeros: a pinned value is
    never changed here; a learned one that differs from it is an error);
  - app/.../voice/StemModels.kt (the file's SHA-256 and size);
  - docs/MODELS.md (the record: the file's row, the checkpoint's SHA-256);
  - app/src/main/assets/licenses/<model>/NOTICE.txt (the NOTICE that goes out
    with the file names the checkpoint it was made from).

Usage:
  scripts/pin-stems.py bandit path/to/bandit-ubuntu-24.04
  scripts/pin-stems.py bandit-int8 path/to/bandit-int8-ubuntu-24.04

Use the folder of a run whose two builds were byte-identical (the workflow's
"Compare" step says so). Then read the diff, commit and push: the next run of
the workflow, with "publish" ticked, publishes the release.
"""

import hashlib
import importlib.util
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STEMS = os.path.join(ROOT, "app", "src", "main", "java", "com", "cuscus", "ffmpeggui", "voice", "StemModels.kt")
RECORD = os.path.join(ROOT, "docs", "MODELS.md")
KOTLIN = {"bandit": "BANDIT", "bandit-int8": "BANDIT_INT8", "spleeter": "INSTRUMENTS"}


def read_lock(path):
    values = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            key, _, value = line.partition(" ")
            values[key] = value.strip()
    return values


def unpinned(value):
    return set(value) == {"0"}


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def main():
    if len(sys.argv) != 3 or sys.argv[1] not in KOTLIN:
        sys.exit(__doc__)
    model, folder = sys.argv[1], sys.argv[2]
    lock_path = os.path.join(ROOT, "scripts", "%s-onnx.lock" % model)
    lock = read_lock(lock_path)

    path = os.path.join(folder, lock["file"])
    if not os.path.isfile(path):
        sys.exit("%s is not in %s" % (lock["file"], folder))
    digest, size = sha256(path), os.path.getsize(path)
    sums = os.path.join(folder, "SHA256SUMS")
    if os.path.isfile(sums):
        listed = dict(reversed(line.split("  ", 1)) for line in open(sums, encoding="ascii").read().splitlines() if "  " in line)
        if listed.get(lock["file"]) != digest:
            sys.exit("SHA256SUMS of %s does not list %s as it is: not that run's file" % (folder, lock["file"]))
    learned = {"file_sha256": digest, "file_bytes": str(size)}
    if "checkpoint_sha256" in lock:
        report = os.path.join(folder, "check.json")
        if not os.path.isfile(report):
            sys.exit("check.json is not in %s: it says which checkpoint the run read" % folder)
        learned["checkpoint_sha256"] = json.load(open(report, encoding="ascii"))["checkpoint_sha256"]
    for key, value in learned.items():
        if not re.fullmatch(r"[0-9a-f]{64}|[0-9]+", value) or unpinned(value):
            sys.exit("%s: %r is not a learned value" % (key, value))
        if not unpinned(lock[key]) and lock[key] != value:
            sys.exit("%s is pinned as %s, the run found %s: not changed here, look into it" % (key, lock[key], value))

    # The lock: each line keeps its layout, only its value changes.
    with open(lock_path, encoding="utf-8") as f:
        lines = f.read().split("\n")
    out = []
    for line in lines:
        m = re.match(r"^([a-z_0-9]+)(\s+)(\S+)$", line)
        if m and m.group(1) in learned and unpinned(m.group(3)):
            line = m.group(1) + m.group(2) + learned[m.group(1)]
        out.append(line)
    with open(lock_path, "w", encoding="utf-8") as f:
        f.write("\n".join(out))

    # The app's pin: name = "...", sha256 = "...", bytes = N_L.
    with open(STEMS, encoding="utf-8") as f:
        source = f.read()
    grouped = "{:,}".format(size).replace(",", "_")
    pattern = re.compile(r'(name = "%s",\s*sha256 = ")[0-9a-f]{64}(",\s*bytes = )[0-9_]+L' % re.escape(lock["file"]))
    source, n = pattern.subn(lambda m: m.group(1) + digest + m.group(2) + grouped + "L", source)
    if n != 1:
        sys.exit("StemModels.kt: no pin for %s" % lock["file"])
    with open(STEMS, "w", encoding="utf-8") as f:
        f.write(source)

    # The record: "| `file` | `sha256` | bytes |", and the checkpoint's line.
    with open(RECORD, encoding="utf-8") as f:
        record = f.read()
    pattern = re.compile(r"(\| `%s` \| `)[0-9a-f]{64}(` \| )[0-9,]+( \|)" % re.escape(lock["file"]))
    record, n = pattern.subn(lambda m: m.group(1) + digest + m.group(2) + "{:,}".format(size) + m.group(3), record)
    if n != 1:
        sys.exit("docs/MODELS.md: no row for %s" % lock["file"])
    if "checkpoint_sha256" in learned and "source_sha256" not in lock:
        # The int8 build names the checkpoint in the same words as the float one, whose line it is.
        pattern = re.compile(r"(`%s`[^`]*MD5 `[0-9a-f]{32}`, SHA-256\s+`)[0-9a-f]{64}(`)" % re.escape(lock["checkpoint"]))
        record, n = pattern.subn(lambda m: m.group(1) + learned["checkpoint_sha256"] + m.group(2), record)
        if n != 1:
            sys.exit("docs/MODELS.md: no line for %s" % lock["checkpoint"])
    with open(RECORD, "w", encoding="utf-8") as f:
        f.write(record)

    # The NOTICE, as the export script writes it from the lock now pinned.
    sys.dont_write_bytecode = True
    spec = importlib.util.spec_from_file_location("export_" + model, os.path.join(ROOT, "scripts", "export-%s.py" % model))
    script = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(script)
    notice = os.path.join(ROOT, "app", "src", "main", "assets", "licenses", model, "NOTICE.txt")
    with open(notice, "w", encoding="ascii") as f:
        f.write(script.notice_of(read_lock(lock_path)))
    print("Pinned: %s, %s, docs/MODELS.md, the bundled NOTICE. Read the diff, then commit and push."
          % (os.path.relpath(lock_path, ROOT), os.path.relpath(STEMS, ROOT)))


if __name__ == "__main__":
    main()
