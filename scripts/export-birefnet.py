#!/usr/bin/env python3
"""
Exports BiRefNet-lite (Zheng Peng et al., MIT) to the ONNX file the app's
"finest" subject cut-out runs (see app/.../cutout/CutoutModel.kt):

  birefnet-lite-general.onnx
      in   input_image   float32 [1, 3, 1024, 1024]  RGB divided by its largest
                                                     value, ImageNet mean and
                                                     deviation (as rembg feeds it)
      out  output_image  float32 [1, 1, 1024, 1024]  logits: the sigmoid is how
                                                     much of each pixel is the
                                                     subject

Why our own export: the author's ONNX (release v1, opset 17) writes the
deformable convolutions of its decoder as gathers, which at 1024 x 1024 need
more than 5.7 GB of memory: no phone runs it. Here they are written as ONNX's
own DeformConv (opset 19), which ONNX Runtime 1.28, the app's, runs natively:
the same convolution, far less memory. The author recommends it ("the native
DeformConv in opset-22 has a large improvement on memory / inference-time
cost").

Nothing is trained or changed: the weights are the author's
BiRefNet-general-bb_swin_v1_tiny-epoch_232.pth (release v1), checked against
the SHA-256 in scripts/birefnet-onnx.lock, loaded strictly. The NOTICE next to
the files says what the conversion changes. Constant folding is off, so that
the file is the same from one computer to the next.

Every run checks the file against the PyTorch model (random inputs; with
--pictures, real photos must give the same masks) and, with --author-onnx,
against the author's own ONNX of the same weights (which needs more than
5.7 GB): it fails rather than write a file that answers differently.

Usage:
  scripts/export-birefnet.py --birefnet DIR --weights FILE --out DIR
                             [--pictures A.jpg B.jpg] [--author-onnx FILE]

DIR is a checkout of https://github.com/ZhengPeng7/BiRefNet at the commit
pinned in scripts/birefnet-onnx.lock. Needs the Python packages pinned in
scripts/model-export-requirements.txt (einops, kornia, huggingface_hub and
opencv are only imported by BiRefNet's code, not used).

Next to the file it writes what goes out with it (the release that serves it,
see .github/workflows/models-onnx.yml): BiRefNet's LICENSE as it is, a NOTICE
of what the conversion changes and a PROVENANCE of everything it was made
from, with SHA256SUMS and check.json. The licence and the notice
are also written into the ONNX file itself (its doc_string and its metadata:
"license", "notice", "source", "converted_by"), so that a copy that travels
alone still says what it is and under what terms.
"""

import argparse
import gc
import hashlib
import json
import os
import shutil
import sys
import tempfile

LOCK = os.path.join(os.path.dirname(os.path.abspath(__file__)), "birefnet-onnx.lock")


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


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


NOTICE = """BiRefNet-lite (general use), ONNX file of FFmpeg GUI
https://github.com/marcomorosi06/FFmpegGUI

birefnet-lite-general.onnx is BiRefNet by Zheng Peng et al.
(https://github.com/ZhengPeng7/BiRefNet, commit %(commit)s),
Copyright (c) 2024 ZhengPeng, under the MIT License: see LICENSE, next to it.
It must go together with that LICENSE wherever it is copied.

What was done to it:
- The author's weights, %(weights)s
  (release v1 of the repository, SHA-256 %(weights_sha256)s),
  were loaded as they are into the author's code (backbone Swin-T, picked in
  config.py) and converted to ONNX, opset %(opset)s, by
  scripts/export-birefnet.py of FFmpeg GUI.
- Only the last, finest prediction of the model is kept as the output.
- The deformable convolutions are written as ONNX's own DeformConv.
- No weight was trained, fine-tuned, pruned or quantised.

The author has not reviewed or endorsed this file. It comes with no warranty
(see LICENSE). The weights were trained by the author on public datasets
(listed in the repository's README) that have terms of their own; see
docs/MODELS.md of FFmpeg GUI.
"""


def stamp(path, notice, licence_text, source):
    """
    Writes the licence and the notice of the changes into the ONNX file itself
    (its doc_string and metadata), so that a copy that travels without the
    files next to it still says what it is, whose it is and under what terms.
    """
    import onnx
    model = onnx.load(path)
    model.doc_string = notice
    del model.metadata_props[:]
    for key, value in (("license", licence_text), ("notice", notice), ("source", source),
                       ("converted_by", "FFmpeg GUI, https://github.com/marcomorosi06/FFmpegGUI")):
        entry = model.metadata_props.add()
        entry.key = key
        entry.value = value
    onnx.save(model, path)


def write_provenance(path, lock_path, files, report):
    """Everything the files were made from: the lock, the tools, the files' hashes."""
    import platform
    from importlib import metadata
    with open(lock_path, encoding="utf-8") as f:
        lock_text = f.read()
    lines = ["Made by %s" % os.path.basename(sys.argv[0]), ""]
    for name, digest in sorted(files.items()):
        lines.append("file %s sha256 %s" % (name, digest))
    lines += ["", "python %s on %s" % (platform.python_version(), platform.platform())]
    for package in ("torch", "torchvision", "onnx", "onnxruntime", "numpy", "timm"):
        try:
            lines.append("%s %s" % (package, metadata.version(package)))
        except metadata.PackageNotFoundError:
            lines.append("%s (not installed)" % package)
    for variable in ("GITHUB_REPOSITORY", "GITHUB_SHA", "GITHUB_RUN_ID", "GITHUB_WORKFLOW"):
        if os.environ.get(variable):
            lines.append("%s %s" % (variable.lower(), os.environ[variable]))
    lines += ["", "check " + json.dumps(report, sort_keys=True), "", "--- %s ---" % os.path.basename(lock_path), lock_text]
    with open(path, "w", encoding="ascii") as f:
        f.write("\n".join(lines))


def die(message):
    print("error: " + message, file=sys.stderr)
    sys.exit(1)


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--birefnet", required=True, help="checkout of ZhengPeng7/BiRefNet")
    parser.add_argument("--weights", required=True, help="BiRefNet-general-bb_swin_v1_tiny-epoch_232.pth")
    parser.add_argument("--out", required=True)
    parser.add_argument("--author-onnx", help="the author's own ONNX of the same weights, to compare with")
    parser.add_argument("--pictures", nargs="*", default=[])
    args = parser.parse_args()

    lock = read_lock(LOCK)
    if sha256(args.weights) != lock["weights_sha256"]:
        die("%s is not the pinned weights file" % args.weights)
    if sha256(os.path.join(args.birefnet, "LICENSE")) != lock["licence_sha256"]:
        die("BiRefNet's LICENSE is not the one the lock pins: read it again before exporting")

    import numpy as np
    import onnxruntime as ort
    import torch
    from torch.onnx import register_custom_op_symbolic
    from torch.onnx.symbolic_helper import parse_args

    # torchvision's deform_conv2d as ONNX DeformConv: the same inputs (the offsets
    # in the same order), the bias always given, the mask when there is one.
    @parse_args("v", "v", "v", "v", "v", "i", "i", "i", "i", "i", "i", "i", "i", "b")
    def deform_conv(g, x, weight, offset, mask, bias, sh, sw, ph, pw, dh, dw, groups, offset_groups, use_mask):
        inputs = [x, weight, offset, bias] + ([mask] if use_mask else [])
        return g.op("DeformConv", *inputs, dilations_i=[dh, dw], group_i=groups,
                    offset_group_i=offset_groups, pads_i=[ph, pw, ph, pw], strides_i=[sh, sw])

    register_custom_op_symbolic("torchvision::deform_conv2d", deform_conv, int(lock["opset"]))

    # The code reads its backbone from config.py, edited by hand there: a copy with
    # the lite model's backbone (Swin-T) picked, the rest as the author has it.
    work = tempfile.mkdtemp(prefix="birefnet-")
    try:
        code = os.path.join(work, "BiRefNet")
        shutil.copytree(args.birefnet, code, ignore=shutil.ignore_patterns(".git"))
        config_path = os.path.join(code, "config.py")
        with open(config_path, encoding="utf-8") as f:
            config = f.read()
        marker = "        ][3]\n        self.freeze_bb"
        if config.count(marker) != 1:
            die("config.py no longer picks its backbone as expected: check the pinned commit")
        config = config.replace(marker, "        ][%s]\n        self.freeze_bb" % lock["backbone_index"])
        with open(config_path, "w", encoding="utf-8") as f:
            f.write(config)
        sys.path.insert(0, code)
        cwd = os.getcwd()
        os.chdir(code)
        from config import Config
        if Config().bb != lock["backbone"]:
            die("the backbone picked is %s, not %s" % (Config().bb, lock["backbone"]))
        from models.birefnet import BiRefNet
        from utils import check_state_dict
        os.chdir(cwd)

        model = BiRefNet(bb_pretrained=False)
        state = torch.load(args.weights, map_location="cpu", weights_only=True)
        model.load_state_dict(check_state_dict(state), strict=True)
        model.eval()

        os.makedirs(args.out, exist_ok=True)
        path = os.path.join(args.out, lock["file"])
        with torch.no_grad():
            example = torch.randn(1, 3, 1024, 1024)

            class Last(torch.nn.Module):
                """The model's last (finest) prediction, as the author's ONNX gives it."""

                def __init__(self, net):
                    super().__init__()
                    self.net = net

                def forward(self, x):
                    return self.net(x)[-1]

            wrapped = Last(model).eval()
            torch.onnx.export(
                wrapped, (example,), path,
                input_names=["input_image"], output_names=["output_image"],
                opset_version=int(lock["opset"]), do_constant_folding=False, dynamo=False,
            )

            # The licence and the notice go into the file too (checked with it below).
            with open(os.path.join(args.birefnet, "LICENSE"), encoding="utf-8") as f:
                licence_text = f.read()
            notice = NOTICE % {"commit": lock["commit"], "weights": os.path.basename(lock["weights_url"]),
                               "weights_sha256": lock["weights_sha256"], "opset": lock["opset"]}
            stamp(path, notice, licence_text, "%s at %s" % (lock["repository"], lock["commit"]))

            # Every answer PyTorch gives first, then the model let go: the two
            # side by side need more memory than a CI runner has.
            def picture_input(picture):
                from PIL import Image
                image = Image.open(picture).convert("RGB").resize((1024, 1024), Image.BILINEAR)
                a = np.asarray(image, dtype=np.float64)
                a = a / max(a.max(), 1e-6)
                mean = np.array([0.485, 0.456, 0.406])
                deviation = np.array([0.229, 0.224, 0.225])
                return ((a - mean) / deviation).transpose(2, 0, 1)[None].astype(np.float32)

            cases = []
            for seed in range(2):
                torch.manual_seed(seed)
                cases.append(("seed %d" % seed, torch.randn(1, 3, 1024, 1024).numpy()))
            for picture in args.pictures:
                cases.append((os.path.basename(picture), picture_input(picture)))
            references = [wrapped(torch.from_numpy(x)).numpy() for _, x in cases]
        del wrapped, model, state
        gc.collect()

        options = ort.SessionOptions()
        options.intra_op_num_threads = 2
        session = ort.InferenceSession(path, options, providers=["CPUExecutionProvider"])
        report = {"pytorch_max_diff": [], "pictures": []}
        answers = []
        for (name, x), ref in zip(cases, references):
            got = session.run(None, {"input_image": x})[0]
            answers.append(got)
            if name.startswith("seed "):
                worst = float(np.max(np.abs(got - ref)))
                report["pytorch_max_diff"].append(round(worst, 5))
                if worst > 1e-2 * max(float(np.max(np.abs(ref))), 1.0):
                    die("the ONNX file differs from PyTorch by %g" % worst)
        del session
        gc.collect()

        def agreement(a, b):
            return float((a & b).sum()) / max(float((a | b).sum()), 1.0)

        # The author's ONNX needs more than 5.7 GB at this size: only where asked.
        author = ort.InferenceSession(args.author_onnx, options, providers=["CPUExecutionProvider"]) if args.author_onnx else None
        for (name, x), ref, got in zip(cases, references, answers):
            if name.startswith("seed "):
                continue
            ours = got > 0
            entry = {"picture": name, "mask_iou_vs_pytorch": round(agreement(ours, ref > 0), 4)}
            if author is not None:
                entry["mask_iou_vs_author_onnx"] = round(agreement(ours, author.run(None, {"input_image": x})[0] > 0), 4)
            report["pictures"].append(entry)
            if min(v for k, v in entry.items() if k != "picture") < 0.99:
                die("on %s the masks agree by only %s" % (name, entry))
    finally:
        shutil.rmtree(work, ignore_errors=True)

    digest = sha256(path)
    shutil.copyfile(os.path.join(args.birefnet, "LICENSE"), os.path.join(args.out, "LICENSE"))
    with open(os.path.join(args.out, "NOTICE"), "w", encoding="ascii") as f:
        f.write(notice)
    write_provenance(os.path.join(args.out, "PROVENANCE"), LOCK, {lock["file"]: digest}, report)
    with open(os.path.join(args.out, "SHA256SUMS"), "w", encoding="ascii") as f:
        f.write("%s  %s\n" % (digest, os.path.basename(path)))
    with open(os.path.join(args.out, "check.json"), "w", encoding="ascii") as f:
        json.dump(report, f, indent=2)
    print(json.dumps({"file": {os.path.basename(path): digest}, "check": report}, indent=2))


if __name__ == "__main__":
    main()
