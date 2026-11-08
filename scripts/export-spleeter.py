#!/usr/bin/env python3
"""
Exports Spleeter's five-stem model (Deezer, MIT) to the ONNX file the app
runs to split the music of a video into its instruments (see
app/.../voice/StemModels.kt, INSTRUMENTS):

  spleeter-5stems.onnx
      in   magnitudes  float32 [2, S, 512, 1024]     the STFT magnitudes of
                                                     stereo 44.1 kHz sound:
                                                     both channels, S windows
                                                     of 512 frames, the first
                                                     1024 bins (4096 points,
                                                     hop 1024, periodic Hann)
      out  estimates   float32 [5, 2, S, 512, 1024]  each instrument's
                                                     magnitudes (vocals,
                                                     piano, drums, bass, other)

Deezer's model is five U-Nets (ELU activations) whose last layers give
logits, a softmax across the instruments, and each instrument's softmax
times the input (unet.softmax_unet in Spleeter's code, configs/5stems): this
file is exactly that. The app then keeps, as Spleeter does, the share
e^2 / sum(e^2) of every bin for each instrument (core/stems/SpleeterSeparation.kt).

Deezer publishes TensorFlow checkpoints. Their weights are read here
without TensorFlow (read_checkpoint(): the tensor bundle format, an SSTable
of names and the raw bytes) into the same U-Nets written in PyTorch, then
exported. Nothing is trained or changed: every weight loads, as it is.

The conversion is checked on Deezer's two-stem model too: the same code must
give, on random input, what sherpa-onnx's own ONNX files of that model give
(Apache 2.0; the app used them until now). Then the five-stem file is checked
against PyTorch. It fails rather than write a file that answers differently.

Usage:
  scripts/export-spleeter.py --checkpoint DIR5 --two-stems DIR2 --sherpa DIRS --licence FILE --out DIR

DIR5 and DIR2 are Deezer's 5stems.tar.gz and 2stems.tar.gz unpacked (release
v1.4.0 of deezer/spleeter, pinned in scripts/spleeter-onnx.lock), DIRS
sherpa-onnx's sherpa-onnx-spleeter-2stems.tar.bz2 unpacked, FILE Spleeter's
LICENSE at the pinned commit. Needs the Python packages pinned in
scripts/model-export-requirements.txt (torch, onnx, onnxruntime, numpy).

Next to the file: Spleeter's LICENSE, a NOTICE of what the conversion changes
and a PROVENANCE, with SHA256SUMS and check.json. The licence and the notice
are also written into the ONNX file itself.
"""

import argparse
import gc
import hashlib
import json
import os
import struct
import sys

LOCK = os.path.join(os.path.dirname(os.path.abspath(__file__)), "spleeter-onnx.lock")
INSTRUMENTS = ["vocals", "piano", "drums", "bass", "other"]


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


NOTICE = """Spleeter 5 stems, ONNX file of FFmpeg GUI
https://github.com/marcomorosi06/FFmpegGUI

spleeter-5stems.onnx is Spleeter's five-stem model by Deezer
(https://github.com/deezer/spleeter, commit %(commit)s; weights 5stems.tar.gz
of release v1.4.0, SHA-256 %(weights_sha256)s), Copyright (c) 2019-present,
Deezer SA, under the MIT License: see LICENSE, next to it. It must go
together with that LICENSE wherever it is copied.

What was done to it (scripts/export-spleeter.py of FFmpeg GUI):
- The weights of the TensorFlow checkpoint were read as they are into the
  same five U-Nets written in PyTorch, with the softmax across the
  instruments of Spleeter's softmax_unet, and converted to ONNX, opset
  %(opset)s.
- The file takes the spectrogram's magnitudes and gives each instrument's
  (vocals, piano, drums, bass, other); the transforms between sound and
  spectrogram are left to the program that runs the file.
- No weight was trained, fine-tuned, pruned or quantised.

Deezer has not reviewed or endorsed this file. It comes with no warranty
(see LICENSE). Deezer trained the model on music of its own catalogue; see
docs/MODELS.md of FFmpeg GUI.
"""


# -- Reading a TensorFlow checkpoint ---------------------------------------------------

_MAGIC = 0xDB4775248B80FB57


def _varint(buf, pos):
    result = shift = 0
    while True:
        b = buf[pos]
        pos += 1
        result |= (b & 0x7F) << shift
        if not b & 0x80:
            return result, pos
        shift += 7


def _block(data, offset, size):
    if data[offset + size] != 0:
        raise ValueError("compressed checkpoint blocks are not read here")
    return data[offset:offset + size]


def _entries(buf):
    """An SSTable block's key/value pairs (keys share prefixes; the restart array ends it)."""
    restarts = struct.unpack_from("<I", buf, len(buf) - 4)[0]
    end = len(buf) - 4 - 4 * restarts
    pos, key = 0, b""
    while pos < end:
        shared, pos = _varint(buf, pos)
        own, pos = _varint(buf, pos)
        size, pos = _varint(buf, pos)
        key = key[:shared] + buf[pos:pos + own]
        pos += own
        yield key, buf[pos:pos + size]
        pos += size


def _message(buf):
    """A protocol buffer as {field: [values]}."""
    out, pos = {}, 0
    while pos < len(buf):
        tag, pos = _varint(buf, pos)
        number, wire = tag >> 3, tag & 7
        if wire == 0:
            value, pos = _varint(buf, pos)
        elif wire == 1:
            value, pos = struct.unpack_from("<Q", buf, pos)[0], pos + 8
        elif wire == 2:
            n, pos = _varint(buf, pos)
            value, pos = buf[pos:pos + n], pos + n
        elif wire == 5:
            value, pos = struct.unpack_from("<I", buf, pos)[0], pos + 4
        else:
            raise ValueError("wire type %d" % wire)
        out.setdefault(number, []).append(value)
    return out


def read_checkpoint(prefix):
    """{name: array} of a TensorFlow checkpoint (prefix.index and prefix.data-00000-of-00001), float32 only."""
    import numpy as np
    with open(prefix + ".index", "rb") as f:
        index = f.read()
    footer = index[-48:]
    if struct.unpack_from("<Q", footer, 40)[0] != _MAGIC:
        raise ValueError("%s.index is not a tensor bundle" % prefix)
    pos = 0
    for _ in range(2):  # the meta-index handle, unused
        _, pos = _varint(footer, pos)
    offset, pos = _varint(footer, pos)
    size, pos = _varint(footer, pos)
    entries = {}
    for _, handle in _entries(_block(index, offset, size)):
        block_offset, p = _varint(handle, 0)
        block_size, _ = _varint(handle, p)
        for key, value in _entries(_block(index, block_offset, block_size)):
            if key:
                entries[key.decode()] = _message(value)
    with open(prefix + ".data-00000-of-00001", "rb") as f:
        data = f.read()
    tensors = {}
    for name, entry in entries.items():
        if entry.get(3, [0])[0] != 0:
            raise ValueError("%s is in another shard" % name)
        dtype = entry.get(1, [1])[0]
        if dtype == 9:  # global_step
            continue
        if dtype != 1:
            raise ValueError("%s is not float32" % name)
        dims = [_message(d).get(1, [0])[0] for d in _message(entry[2][0]).get(2, [])] if 2 in entry else []
        start, length = entry.get(4, [0])[0], entry.get(5, [0])[0]
        tensors[name] = np.frombuffer(data[start:start + length], dtype=np.float32).reshape(dims).copy()
    return tensors


# -- The U-Nets ----------------------------------------------------------------------

def unet_class():
    import torch
    import torch.nn.functional as F
    from torch import nn

    class UNet(nn.Module):
        """One instrument's U-Net (unet.apply_unet), TensorFlow's "same" padding written out."""

        def __init__(self, elu, logits):
            super().__init__()
            f = [16, 32, 64, 128, 256, 512]
            self.elu, self.logits = elu, logits
            self.down = nn.ModuleList([nn.Conv2d(2 if i == 0 else f[i - 1], f[i], 5, stride=2) for i in range(6)])
            self.down_bn = nn.ModuleList([nn.BatchNorm2d(f[i], eps=1e-3) for i in range(5)])
            ins, outs = [512, 512, 256, 128, 64, 32], [256, 128, 64, 32, 16, 1]
            self.up = nn.ModuleList([nn.ConvTranspose2d(ins[i], outs[i], 5, stride=2) for i in range(6)])
            self.up_bn = nn.ModuleList([nn.BatchNorm2d(outs[i], eps=1e-3) for i in range(6)])
            self.last = nn.Conv2d(1, 2, 4, dilation=2, padding=3)

        def forward(self, x):
            # x: [S, 2, 512, 1024] (windows, audio channels, frames, bins)
            skips, h = [], x
            for i in range(6):
                c = self.down[i](F.pad(h, (1, 2, 1, 2)))
                skips.append(c)
                if i < 5:
                    b = self.down_bn[i](c)
                    h = F.elu(b) if self.elu else F.leaky_relu(b, 0.2)
            h = skips[5]
            for i in range(6):
                u = self.up[i](h)[:, :, 1:-2, 1:-2]
                u = self.up_bn[i](F.elu(u) if self.elu else F.relu(u))
                h = torch.cat([skips[4 - i], u], dim=1) if i < 5 else u
            out = self.last(h)
            return out if self.logits else torch.sigmoid(out) * x

    return UNet


def load_unet(unet, tensors, k):
    """Instrument k's weights: 7 convolutions, 12 batch normalisations and 6 transposed convolutions each, in order."""
    import torch

    def tensor(name):
        if name not in tensors:
            raise KeyError(name)
        return torch.from_numpy(tensors.pop(name))

    def numbered(base, i):
        return "%s_%d" % (base, i) if i else base

    def bn(module, i):
        p = numbered("batch_normalization", i)
        module.weight.data, module.bias.data = tensor(p + "/gamma"), tensor(p + "/beta")
        module.running_mean.data, module.running_var.data = tensor(p + "/moving_mean"), tensor(p + "/moving_variance")

    for i in range(6):
        name = numbered("conv2d", 7 * k + i)
        unet.down[i].weight.data = tensor(name + "/kernel").permute(3, 2, 0, 1).contiguous()
        unet.down[i].bias.data = tensor(name + "/bias")
        bn_index = 12 * k + i
        if i < 5:
            bn(unet.down_bn[i], bn_index)
        else:
            # The batch normalisation after the sixth convolution feeds nothing (its output is
            # dropped in Spleeter's code): read, not used.
            for part in ("gamma", "beta", "moving_mean", "moving_variance"):
                tensor("%s/%s" % (numbered("batch_normalization", bn_index), part))
    for i in range(6):
        name = numbered("conv2d_transpose", 6 * k + i)
        unet.up[i].weight.data = tensor(name + "/kernel").permute(3, 2, 0, 1).contiguous()
        unet.up[i].bias.data = tensor(name + "/bias")
        bn(unet.up_bn[i], 12 * k + 6 + i)
    name = numbered("conv2d", 7 * k + 6)
    unet.last.weight.data = tensor(name + "/kernel").permute(3, 2, 0, 1).contiguous()
    unet.last.bias.data = tensor(name + "/bias")
    return unet.eval()


def five_stems(tensors):
    import torch
    from torch import nn
    UNet = unet_class()
    nets = [load_unet(UNet(elu=True, logits=True), tensors, k) for k in range(len(INSTRUMENTS))]
    if tensors:
        raise ValueError("weights left over: %s" % sorted(tensors)[:5])

    class FiveStems(nn.Module):
        def __init__(self):
            super().__init__()
            self.nets = nn.ModuleList(nets)

        def forward(self, magnitudes):
            # [2, S, 512, 1024] -> [5, 2, S, 512, 1024]
            x = magnitudes.permute(1, 0, 2, 3)
            logits = torch.stack([net(x) for net in self.nets], dim=0)
            estimates = torch.softmax(logits, dim=0) * x.unsqueeze(0)
            return estimates.permute(0, 2, 1, 3, 4)

    return FiveStems().eval()


def stamp(path, notice, licence_text, source):
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
    import platform
    from importlib import metadata
    with open(lock_path, encoding="utf-8") as f:
        lock_text = f.read()
    lines = ["Made by %s" % os.path.basename(sys.argv[0]), ""]
    for name, value in sorted(files.items()):
        lines.append("file %s sha256 %s" % (name, value))
    lines += ["", "python %s on %s" % (platform.python_version(), platform.platform())]
    for package in ("torch", "onnx", "onnxruntime", "numpy"):
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


def notice_of(lock):
    return NOTICE % {"commit": lock["commit"], "weights_sha256": lock["weights_sha256"], "opset": lock["opset"]}


def die(message):
    print("error: " + message, file=sys.stderr)
    sys.exit(1)


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--checkpoint", required=True, help="5stems.tar.gz unpacked")
    parser.add_argument("--two-stems", required=True, help="2stems.tar.gz unpacked")
    parser.add_argument("--sherpa", required=True, help="sherpa-onnx-spleeter-2stems.tar.bz2 unpacked")
    parser.add_argument("--licence", required=True, help="Spleeter's LICENSE at the pinned commit")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    lock = read_lock(LOCK)
    if sha256(args.licence) != lock["licence_sha256"]:
        die("Spleeter's LICENSE is not the one the lock pins: read it again before exporting")
    for name in ("vocals.onnx", "accompaniment.onnx"):
        if sha256(os.path.join(args.sherpa, name)) != lock["sherpa_%s_sha256" % name.split(".")[0]]:
            die("sherpa-onnx's %s is not the pinned one" % name)

    import numpy as np
    import onnxruntime as ort
    import torch

    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    report = {"two_stems_vs_sherpa_max_diff": [], "five_stems_max_diff": []}

    # 1. The conversion, on the two-stem model: the same answers as sherpa-onnx's files.
    UNet = unet_class()
    two = read_checkpoint(os.path.join(args.two_stems, "model"))
    rng = np.random.default_rng(20230821)
    x = np.abs(rng.normal(size=(2, 1, 512, 1024))).astype(np.float32)
    for k, name in enumerate(("vocals", "accompaniment")):
        net = load_unet(UNet(elu=False, logits=False), two, k)
        with torch.no_grad():
            mine = net(torch.from_numpy(x).permute(1, 0, 2, 3)).permute(1, 0, 2, 3).numpy()
        session = ort.InferenceSession(os.path.join(args.sherpa, name + ".onnx"), options, providers=["CPUExecutionProvider"])
        theirs = session.run(None, {session.get_inputs()[0].name: x})[0]
        worst = float(np.max(np.abs(mine - theirs)))
        report["two_stems_vs_sherpa_max_diff"].append(float("%.3g" % worst))
        if worst > 1e-4 * max(float(np.max(np.abs(theirs))), 1.0):
            die("the conversion gives %s differently from sherpa-onnx's file, by %g" % (name, worst))
        del session, net
    if two:
        die("two-stem weights left over: %s" % sorted(two)[:5])
    gc.collect()

    # 2. The five-stem model.
    model = five_stems(read_checkpoint(os.path.join(args.checkpoint, "model")))
    os.makedirs(args.out, exist_ok=True)
    path = os.path.join(args.out, lock["file"])
    with torch.no_grad():
        example = torch.rand(2, 1, 512, 1024)
        torch.onnx.export(
            model, (example,), path, input_names=["magnitudes"], output_names=["estimates"],
            dynamic_axes={"magnitudes": {1: "windows"}, "estimates": {2: "windows"}},
            opset_version=int(lock["opset"]), do_constant_folding=False, dynamo=False,
        )
        cases = [np.abs(np.random.default_rng(seed).normal(size=(2, windows, 512, 1024))).astype(np.float32)
                 for seed, windows in ((1, 1), (2, 2))]
        references = [model(torch.from_numpy(c)).numpy() for c in cases]
    del model
    gc.collect()
    with open(args.licence, encoding="utf-8") as f:
        licence_text = f.read()
    notice = notice_of(lock)
    stamp(path, notice, licence_text, "%s at %s; weights %s" % (lock["repository"], lock["commit"], lock["weights_url"]))
    session = ort.InferenceSession(path, options, providers=["CPUExecutionProvider"])
    for c, ref in zip(cases, references):
        got = session.run(None, {"magnitudes": c})[0]
        worst = float(np.max(np.abs(got - ref)))
        report["five_stems_max_diff"].append(float("%.3g" % worst))
        if worst > 1e-4 * max(float(np.max(np.abs(ref))), 1.0):
            die("the ONNX file differs from PyTorch by %g" % worst)
    del session
    gc.collect()

    value = sha256(path)
    with open(args.licence, "rb") as src, open(os.path.join(args.out, "LICENSE"), "wb") as dst:
        dst.write(src.read())
    with open(os.path.join(args.out, "NOTICE"), "w", encoding="ascii") as f:
        f.write(notice)
    write_provenance(os.path.join(args.out, "PROVENANCE"), LOCK, {lock["file"]: value}, report)
    with open(os.path.join(args.out, "SHA256SUMS"), "w", encoding="ascii") as f:
        f.write("%s  %s\n" % (value, os.path.basename(path)))
    with open(os.path.join(args.out, "check.json"), "w", encoding="ascii") as f:
        json.dump(report, f, indent=2)
    print(json.dumps({"file": {os.path.basename(path): value}, "check": report}, indent=2))


if __name__ == "__main__":
    main()
