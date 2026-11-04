#!/usr/bin/env python3
"""
Copies the pins a learning run of .github/workflows/ai-markers.yml found (the
artifact "ai-markers-learned": ai-markers.lock.learned) into the three places
that must agree on them (scripts/check-model-pins.py checks they do):

  - scripts/ai-markers.lock (only the values still zeros: a pinned value is
    never changed here; a learned one that differs from it is an error);
  - app/.../marking/MarkerModel.kt (each file's SHA-256 and size);
  - docs/MODELS.md (the record: "The AI marks");
  - app/src/main/assets/licenses/ai-markers/NOTICE.txt (the NOTICE that goes
    out with the files names the files they were made from).

Usage:
  scripts/pin-ai-markers.py path/to/ai-markers.lock.learned

Then read the diff, commit and push: the next run of the workflow publishes.
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOCK = os.path.join(ROOT, "scripts", "ai-markers.lock")
MODEL = os.path.join(ROOT, "app", "src", "main", "java", "com", "cuscus", "ffmpeggui", "marking", "MarkerModel.kt")
RECORD = os.path.join(ROOT, "docs", "MODELS.md")
NOTICE = os.path.join(ROOT, "app", "src", "main", "assets", "licenses", "ai-markers", "NOTICE.txt")


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


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    learned = read_lock(sys.argv[1])
    lock = read_lock(LOCK)
    for key, value in lock.items():
        if key not in learned:
            sys.exit("the learned lock lacks %s" % key)
        if not unpinned(value) and learned[key] != value:
            sys.exit("%s is pinned as %s, the run found %s: not changed here, look into it" % (key, value, learned[key]))
        if not re.fullmatch(r"[0-9A-Za-z_.:/=+-]+", learned[key]):
            sys.exit("%s: %r is not a plain value" % (key, learned[key]))
    if any(unpinned(v) for v in learned.values()):
        sys.exit("the learned lock still has values of zeros: the run did not finish")

    # The lock: each line keeps its layout, only its value changes.
    with open(LOCK, encoding="utf-8") as f:
        lines = f.read().split("\n")
    out = []
    for line in lines:
        m = re.match(r"^([a-z_0-9]+)(\s+)(\S+)$", line)
        if m and unpinned(m.group(3)):
            line = m.group(1) + m.group(2) + learned[m.group(1)]
        out.append(line)
    with open(LOCK, "w", encoding="utf-8") as f:
        f.write("\n".join(out))

    files = {
        learned["generator_file"]: (learned["generator_sha256"], learned["generator_bytes"]),
        learned["detector_file"]: (learned["detector_sha256"], learned["detector_bytes"]),
        learned["encoder_file"]: (learned["trustmark_encoder_sha256"], learned["encoder_bytes"]),
        learned["decoder_file"]: (learned["trustmark_decoder_sha256"], learned["decoder_bytes"]),
    }

    # The app's pins: name = "...", sha256 = "...", bytes = N_L.
    with open(MODEL, encoding="utf-8") as f:
        source = f.read()
    for name, (digest, size) in files.items():
        pattern = re.compile(r'(name = "%s",\s*sha256 = ")[0-9a-f]{64}(",\s*bytes = )[0-9_]+L' % re.escape(name))
        grouped = "{:,}".format(int(size)).replace(",", "_")
        source, n = pattern.subn(lambda m: m.group(1) + digest + m.group(2) + grouped + "L", source)
        if n != 1:
            sys.exit("MarkerModel.kt: no pin for %s" % name)
    with open(MODEL, "w", encoding="utf-8") as f:
        f.write(source)

    # The record: the lines "| `file` | ... | `sha256` | bytes |" of the table, and the upstream files.
    with open(RECORD, encoding="utf-8") as f:
        record = f.read()
    upstream = {
        "generator_base.pth": learned["audioseal_generator_sha256"],
        "detector_base.pth": learned["audioseal_detector_sha256"],
        "encoder_Q.onnx": learned["trustmark_encoder_sha256"],
        "decoder_Q.onnx": learned["trustmark_decoder_sha256"],
    }
    for name, (digest, size) in files.items():
        pattern = re.compile(r"(\| `%s` \| [^|]*\| `)[0-9a-f]{64}(` \| )[0-9,]+( \|)" % re.escape(name))
        record, n = pattern.subn(lambda m: m.group(1) + digest + m.group(2) + "{:,}".format(int(size)) + m.group(3), record)
        if n != 1:
            sys.exit("docs/MODELS.md: no row for %s" % name)
    for name, digest in upstream.items():
        pattern = re.compile(r"(`%s`, SHA-256 `)[0-9a-f]{64}(`)" % re.escape(name))
        record, n = pattern.subn(lambda m: m.group(1) + digest + m.group(2), record)
        if n != 1:
            sys.exit("docs/MODELS.md: no line for %s" % name)
    record, n = re.subn(r"(TrustMark at commit `)0{40}(`)", lambda m: m.group(1) + learned["trustmark_commit"] + m.group(2), record)
    with open(RECORD, "w", encoding="utf-8") as f:
        f.write(record)

    # The NOTICE, as the export script writes it from the lock now pinned.
    import importlib.util
    sys.dont_write_bytecode = True
    spec = importlib.util.spec_from_file_location("export_ai_markers", os.path.join(ROOT, "scripts", "export-ai-markers.py"))
    script = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(script)
    with open(NOTICE, "w", encoding="ascii") as f:
        f.write(script.notice_of(read_lock(LOCK)))
    print("Pinned: scripts/ai-markers.lock, %s, docs/MODELS.md, the bundled NOTICE. Read the diff, then commit and push." % os.path.relpath(MODEL, ROOT))


if __name__ == "__main__":
    main()
