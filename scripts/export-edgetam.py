#!/usr/bin/env python3
"""
Exports EdgeTAM (Meta, Apache 2.0) to the two ONNX files the app's
correction tool and "lift the subject" gesture run (see
app/.../cutout/RefineModel.kt and core/cutout/PromptSegmentation.kt):

  edgetam-image-encoder.onnx
      in   image             float32 [1, 3, 1024, 1024]  RGB / 255, ImageNet
                                                         mean and deviation,
                                                         the picture stretched
                                                         to the square
      out  image_embed       float32 [1, 256, 64, 64]
           high_res_feats_0  float32 [1, 32, 256, 256]
           high_res_feats_1  float32 [1, 64, 128, 128]

  edgetam-mask-decoder.onnx (the prompt encoder and the mask decoder)
      in   image_embed, high_res_feats_0, high_res_feats_1 (as above)
           point_coords      float32 [1, N, 2]   pixels of the 1024 square
           point_labels      int64   [1, N]      1 in, 0 out
      out  masks             float32 [1, 3, 256, 256]  logits, above 0 is in
           iou_predictions   float32 [1, 3]

This is SAM 2's image predictor (sam2_image_predictor.py of the EdgeTAM
repository) cut in two at the same place: set_image() is the encoder,
predict(point_coords, point_labels, multimask_output=True) the decoder, which
gives the low-resolution masks before the predictor upscales them.

Nothing is trained or changed: the weights are Meta's checkpoint as it is,
checked against the SHA-256 in scripts/edgetam-onnx.lock. What the conversion
changes is said in the NOTICE written next to the files (Apache 2.0, section
4b). Constant folding is off, so that the files are the same from one
computer to the next: ONNX Runtime folds the constants when it loads them.

Every run checks its files against the PyTorch model it came from (random
inputs and, with --pictures, real pictures and points): it fails rather than
write files that answer differently.

Usage:
  scripts/export-edgetam.py --edgetam DIR --out DIR [--pictures A.jpg B.jpg]

DIR is a checkout of https://github.com/facebookresearch/EdgeTAM at the commit
pinned in scripts/edgetam-onnx.lock (with its checkpoints/edgetam.pt). Needs
the Python packages pinned in scripts/model-export-requirements.txt.

Next to the files it writes what goes out with them (the release that serves
them, see .github/workflows/models-onnx.yml): EdgeTAM's LICENSE as it is, the
NOTICE of what the conversion changes (Apache 2.0, section 4b) and a
PROVENANCE of everything they were made from, with SHA256SUMS and check.json. The licence and the notice
are also written into the ONNX file itself (its doc_string and its metadata:
"license", "notice", "source", "converted_by"), so that a copy that travels
alone still says what it is and under what terms.
"""

import argparse
import hashlib
import json
import os
import sys

LOCK = os.path.join(os.path.dirname(os.path.abspath(__file__)), "edgetam-onnx.lock")


def read_lock(path):
    """The lock's "key value" lines, comments and blank lines skipped."""
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


NOTICE = """EdgeTAM, ONNX files of FFmpeg GUI
https://github.com/marcomorosi06/FFmpegGUI

edgetam-image-encoder.onnx and edgetam-mask-decoder.onnx are a Derivative
Work of EdgeTAM (https://github.com/facebookresearch/EdgeTAM, commit
%(commit)s), Copyright (c) Meta Platforms, Inc. and affiliates,
licensed under the Apache License, Version 2.0: see LICENSE, next to them.
They are distributed under that same licence.

Changes made (Apache License 2.0, section 4(b)):
- The PyTorch model (%(config)s with %(checkpoint)s,
  SHA-256 %(checkpoint_sha256)s) was converted
  to ONNX, opset %(opset)s, by scripts/export-edgetam.py of FFmpeg GUI.
- It was cut in two where SAM 2's image predictor cuts it: the image encoder
  (set_image) and the prompt encoder with the mask decoder (predict, three
  masks, the low-resolution masks before they are upscaled).
- The ImageNet weights the backbone would fetch through timm were not used:
  every weight comes from the checkpoint above, loaded strictly.
- No weight was trained, fine-tuned, pruned or quantised.

The EdgeTAM repository has no NOTICE file of its own. Meta has not reviewed
or endorsed these files. They come with no warranty (see LICENSE). See
docs/MODELS.md of FFmpeg GUI for what the model was trained on.
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
    for package in ("torch", "torchvision", "onnx", "onnxruntime", "numpy", "timm", "hydra-core", "iopath"):
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
    parser.add_argument("--edgetam", required=True, help="checkout of facebookresearch/EdgeTAM")
    parser.add_argument("--out", required=True, help="folder the ONNX files are written to")
    parser.add_argument("--pictures", nargs="*", default=[], help="pictures to check the files on")
    args = parser.parse_args()

    lock = read_lock(LOCK)
    checkout = os.path.abspath(args.edgetam)
    checkpoint = os.path.join(checkout, "checkpoints", "edgetam.pt")
    if not os.path.isfile(checkpoint):
        die("no checkpoints/edgetam.pt in " + checkout)
    got = sha256(checkpoint)
    if got != lock["checkpoint_sha256"]:
        die("checkpoints/edgetam.pt is %s, the lock says %s" % (got, lock["checkpoint_sha256"]))
    licence = os.path.join(checkout, "LICENSE")
    if sha256(licence) != lock["licence_sha256"]:
        die("EdgeTAM's LICENSE is not the one the lock pins: read it again before exporting")

    import numpy as np
    import onnxruntime as ort
    import torch

    sys.path.insert(0, checkout)
    # EdgeTAM's backbone asks timm for ImageNet weights (pretrained=True) that the
    # checkpoint then replaces entirely. They are not fetched: every weight of the
    # files comes from Meta's checkpoint (loaded strictly, so a missing one fails).
    import timm
    real_create_model = timm.create_model

    def create_model(*a, **k):
        k["pretrained"] = False
        return real_create_model(*a, **k)

    timm.create_model = create_model
    import sam2.modeling.backbones.timm as edgetam_timm
    edgetam_timm.create_model = create_model
    from sam2.build_sam import build_sam2
    from sam2.sam2_image_predictor import SAM2ImagePredictor

    torch.manual_seed(0)
    model = build_sam2(lock["config"], checkpoint, device="cpu", mode="eval")
    model.eval()

    class Encoder(torch.nn.Module):
        """set_image() of SAM2ImagePredictor, from the normalised square on."""

        def __init__(self, sam):
            super().__init__()
            self.sam = sam

        def forward(self, image):
            backbone_out = self.sam.forward_image(image)
            _, vision_feats, _, feat_sizes = self.sam._prepare_backbone_features(backbone_out)
            if self.sam.directly_add_no_mem_embed:
                vision_feats[-1] = vision_feats[-1] + self.sam.no_mem_embed
            feats = [
                feat.permute(1, 2, 0).reshape(1, -1, *size)
                for feat, size in zip(vision_feats[::-1], feat_sizes[::-1])
            ][::-1]
            return feats[-1], feats[0], feats[1]

    class Decoder(torch.nn.Module):
        """predict() of SAM2ImagePredictor with points only, multimask, low-resolution logits."""

        def __init__(self, sam):
            super().__init__()
            self.sam = sam
            self.prompt = sam.sam_prompt_encoder

        def embed_points(self, coords, labels):
            # PromptEncoder._embed_points with pad=True, written with torch.where so
            # that it exports as plain ONNX (the same sums, the same order).
            coords = coords + 0.5
            pad_point = torch.zeros((coords.shape[0], 1, 2), dtype=coords.dtype)
            pad_label = -torch.ones((labels.shape[0], 1), dtype=labels.dtype)
            coords = torch.cat([coords, pad_point], dim=1)
            labels = torch.cat([labels, pad_label], dim=1).unsqueeze(-1)
            embedding = self.prompt.pe_layer.forward_with_coords(coords, self.prompt.input_image_size)
            embedding = torch.where(labels == -1, torch.zeros_like(embedding), embedding)
            embedding = embedding + (labels == -1).to(embedding.dtype) * self.prompt.not_a_point_embed.weight
            for k in range(4):
                embedding = embedding + (labels == k).to(embedding.dtype) * self.prompt.point_embeddings[k].weight
            return embedding

        def forward(self, image_embed, high_res_feats_0, high_res_feats_1, point_coords, point_labels):
            sparse = self.embed_points(point_coords, point_labels)
            size = self.prompt.image_embedding_size
            dense = self.prompt.no_mask_embed.weight.reshape(1, -1, 1, 1).expand(1, -1, size[0], size[1])
            masks, iou, _, _ = self.sam.sam_mask_decoder(
                image_embeddings=image_embed,
                image_pe=self.prompt.get_dense_pe(),
                sparse_prompt_embeddings=sparse,
                dense_prompt_embeddings=dense,
                multimask_output=True,
                repeat_image=False,
                high_res_features=[high_res_feats_0, high_res_feats_1],
            )
            return masks, iou

    encoder = Encoder(model).eval()
    decoder = Decoder(model).eval()
    os.makedirs(args.out, exist_ok=True)
    encoder_path = os.path.join(args.out, lock["encoder_file"])
    decoder_path = os.path.join(args.out, lock["decoder_file"])
    opset = int(lock["opset"])

    with torch.no_grad():
        image = torch.randn(1, 3, 1024, 1024)
        torch.onnx.export(
            encoder, (image,), encoder_path,
            input_names=["image"],
            output_names=["image_embed", "high_res_feats_0", "high_res_feats_1"],
            opset_version=opset, do_constant_folding=False, dynamo=False,
        )
        embed, hr0, hr1 = encoder(image)
        coords = torch.tensor([[[500.0, 400.0], [100.0, 900.0]]])
        labels = torch.tensor([[1, 0]], dtype=torch.int64)
        torch.onnx.export(
            decoder, (embed, hr0, hr1, coords, labels), decoder_path,
            input_names=["image_embed", "high_res_feats_0", "high_res_feats_1", "point_coords", "point_labels"],
            output_names=["masks", "iou_predictions"],
            dynamic_axes={"point_coords": {1: "points"}, "point_labels": {1: "points"}},
            opset_version=opset, do_constant_folding=False, dynamo=False,
        )

    # -- The check: the files answer as the model they came from ------------------------
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    # The licence and the notice go into the files too (checked with them below).
    with open(licence, encoding="utf-8") as f:
        licence_text = f.read()
    notice = NOTICE % {"commit": lock["commit"], "config": lock["config"], "checkpoint": lock["checkpoint"],
                       "checkpoint_sha256": lock["checkpoint_sha256"], "opset": lock["opset"]}
    for p in (encoder_path, decoder_path):
        stamp(p, notice, licence_text, "%s at %s" % (lock["repository"], lock["commit"]))
    enc = ort.InferenceSession(encoder_path, options, providers=["CPUExecutionProvider"])
    dec = ort.InferenceSession(decoder_path, options, providers=["CPUExecutionProvider"])
    report = {"encoder": [], "decoder": [], "pictures": []}

    def compare(name, ours, theirs, tolerance):
        worst = float(np.max(np.abs(ours - theirs)))
        # Relative to the values, but never to values near zero.
        scale = max(float(np.max(np.abs(theirs))), 1.0)
        report[name].append(round(worst, 6))
        if worst > tolerance * scale:
            die("%s differs from PyTorch by %g (largest value %g)" % (name, worst, scale))

    with torch.no_grad():
        for seed in range(3):
            torch.manual_seed(seed)
            image = torch.randn(1, 3, 1024, 1024)
            ref = encoder(image)
            got = enc.run(None, {"image": image.numpy()})
            for r, g in zip(ref, got):
                compare("encoder", g, r.numpy(), 1e-3)
            n = 1 + seed
            coords = torch.rand(1, n, 2) * 1024
            labels = torch.randint(0, 2, (1, n), dtype=torch.int64)
            ref = decoder(*ref, coords, labels)
            got = dec.run(None, {
                "image_embed": got[0], "high_res_feats_0": got[1], "high_res_feats_1": got[2],
                "point_coords": coords.numpy(), "point_labels": labels.numpy(),
            })
            compare("decoder", got[0], ref[0].numpy(), 1e-2)
            compare("decoder", got[1], ref[1].numpy(), 1e-2)

        # Real pictures, against SAM 2's own predictor from end to end: the ONNX pair,
        # fed as the app feeds it, must find the same thing for a point.
        if args.pictures:
            from PIL import Image
            predictor = SAM2ImagePredictor(model)
            mean = np.array([0.485, 0.456, 0.406], dtype=np.float32)
            deviation = np.array([0.229, 0.224, 0.225], dtype=np.float32)
            for path in args.pictures:
                picture = Image.open(path).convert("RGB")
                w, h = picture.size
                predictor.set_image(np.asarray(picture))
                point = np.array([[w * 0.5, h * 0.5]], dtype=np.float32)
                _, ref_scores, ref_low = predictor.predict(point_coords=point, point_labels=np.array([1]), multimask_output=True, return_logits=True)
                square = np.asarray(picture.resize((1024, 1024), Image.BILINEAR), dtype=np.float32) / 255.0
                x = ((square - mean) / deviation).transpose(2, 0, 1)[None].astype(np.float32)
                feats = enc.run(None, {"image": x})
                coords = (point * np.array([1024.0 / w, 1024.0 / h], dtype=np.float32))[None]
                masks, scores = dec.run(None, {
                    "image_embed": feats[0], "high_res_feats_0": feats[1], "high_res_feats_1": feats[2],
                    "point_coords": coords, "point_labels": np.array([[1]], dtype=np.int64),
                })
                best = int(np.argmax(ref_scores))
                a = ref_low[best] > 0
                b = masks[0, best] > 0
                iou = float((a & b).sum()) / max(float((a | b).sum()), 1.0)
                report["pictures"].append({"picture": os.path.basename(path), "mask_iou_vs_pytorch": round(iou, 4)})
                # The resize differs a little (PIL here, torchvision in SAM 2): near, not equal.
                if iou < 0.97:
                    die("on %s the ONNX mask matches SAM 2's by only %.3f" % (path, iou))

    # -- What goes next to the files --------------------------------------------------
    sums = {os.path.basename(p): sha256(p) for p in (encoder_path, decoder_path)}
    import shutil
    shutil.copyfile(licence, os.path.join(args.out, "LICENSE"))
    with open(os.path.join(args.out, "NOTICE"), "w", encoding="ascii") as f:
        f.write(notice)
    write_provenance(os.path.join(args.out, "PROVENANCE"), LOCK, sums, report)
    with open(os.path.join(args.out, "SHA256SUMS"), "w", encoding="ascii") as f:
        for name, digest in sorted(sums.items()):
            f.write("%s  %s\n" % (digest, name))
    with open(os.path.join(args.out, "check.json"), "w", encoding="ascii") as f:
        json.dump(report, f, indent=2)
    print(json.dumps({"files": sums, "check": report}, indent=2))


if __name__ == "__main__":
    main()
