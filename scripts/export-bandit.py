#!/usr/bin/env python3
"""
Exports Bandit v2 (Karn N. Watcharasupat, Chih-Wei Wu, Iroro Orife; code
Apache 2.0, weights CC BY-SA 4.0) to the ONNX file the app's stem separation
runs (see app/.../voice/StemModels.kt, BANDIT): the sound of a video split
into dialogue, music and effects.

  bandit-v2-multi.onnx
      in   spectrogram  float32 [B, 1025, T, 2]     the STFT of mono 48 kHz
                                                    sound (see below), real
                                                    and imaginary parts
      out  masks        float32 [B, 3, 1025, T, 2]  a complex mask per stem
                                                    (speech, music, sfx), real
                                                    and imaginary parts

The STFT is the model's own (torchaudio, as configs/models/bandit-mus64.yaml
sets it): 2048 points, hop 512, a periodic Hann window of 2048, centred
(the sound padded with 1024 samples reflected at both ends), one-sided, each
frame divided by the square root of the window's energy ("normalized").
A stem is the mixture's spectrogram times its mask (complex product), back
to sound by the inverse transform (the same window, overlap-add divided by
the windows' squared sum, multiplied back by the square root of the window's
energy). The app does both transforms (core/stems/StemStft.kt), exactly as
stft() and istft() below: this script checks them against the model.

The model is mono (the dataset is): the app runs it once, on the middle of
the two channels, and applies the masks to each channel's own spectrogram,
so that the stems keep the stereo picture at the cost of one channel.

The network is written out of the author's modules with real numbers only
(the author's code builds the masks as complex tensors, which ONNX does not
have): the same layers, in the same order, with the same weights. Nothing is
trained or changed. The weights are the author's checkpoint-multi.ckpt
(Zenodo record 12701995, all the languages of Divide and Remaster v3),
checked against the MD5 Zenodo gives and the SHA-256 the lock pins.

Every run checks the file against the PyTorch model (random spectrograms)
and, end to end, sound through stft(), the file and istft() against the
author's model on the same sound: it fails rather than write a file that
answers differently. With --random (no checkpoint) the same checks run on a
model of random weights: for checking this script, never for the app.

Usage:
  scripts/export-bandit.py --bandit DIR --checkpoint FILE --out DIR
  scripts/export-bandit.py --bandit DIR --random --out DIR

DIR is a checkout of https://github.com/kwatcharasupat/bandit-v2 at the
commit pinned in scripts/bandit-onnx.lock. Needs the Python packages pinned
in scripts/model-export-requirements.txt (torch, onnx, onnxruntime, numpy):
the author's code also imports torchaudio, librosa and pytorch_lightning,
which are not needed for this and are replaced by the few lines of each it
uses (see stand_ins()).

Next to the file it writes what goes out with it (the release that serves it,
see .github/workflows/models-onnx.yml): the CC BY-SA 4.0 legal code as
LICENSE, a NOTICE of what the conversion changes (the attribution and the
changes CC BY-SA 4.0, section 3(a), asks for) and a PROVENANCE of everything
it was made from, with SHA256SUMS and check.json. The licence and the notice
are also written into the ONNX file itself.
"""

import argparse
import gc
import hashlib
import json
import os
import sys
import types

LOCK = os.path.join(os.path.dirname(os.path.abspath(__file__)), "bandit-onnx.lock")

# The model as configs/models/bandit-mus64.yaml makes it, at 48 kHz.
CONFIG = dict(
    in_channels=1, band_type="musical", n_bands=64, normalize_channel_independently=False,
    treat_channel_as_feature=True, n_sqm_modules=8, emb_dim=128, rnn_dim=256, bidirectional=True,
    rnn_type="GRU", mlp_dim=512, hidden_activation="Tanh", hidden_activation_kwargs=None,
    complex_mask=True, use_freq_weights=True, n_fft=2048, win_length=2048, hop_length=512,
    window_fn="hann_window", wkwargs=None, power=None, center=True, normalized=True,
    pad_mode="reflect", onesided=True, fs=48000,
)
STEMS = ["speech", "music", "sfx"]
N_FFT = 2048
HOP = 512


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


def digest(path, kind):
    h = hashlib.new(kind)
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def sha256(path):
    return digest(path, "sha256")


NOTICE = """Bandit v2 (multilingual), ONNX file of FFmpeg GUI
https://github.com/marcomorosi06/FFmpegGUI

bandit-v2-multi.onnx is made from "Model Weights for Remastering Divide and
Remaster: A Cinematic Audio Source Separation Dataset with Multilingual
Support" by Karn N. Watcharasupat, Chih-Wei Wu and Iroro Orife
(https://doi.org/10.5281/zenodo.12701995, file %(checkpoint)s,
MD5 %(checkpoint_md5)s, SHA-256 %(checkpoint_sha256)s), licensed under
Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0,
https://creativecommons.org/licenses/by-sa/4.0/legalcode): see LICENSE, next
to it. This file is an adaptation of those weights and is licensed under the
same licence, CC BY-SA 4.0. It must go together with that LICENSE (or its
address) and this NOTICE wherever it is copied.

The model's code is the authors' https://github.com/kwatcharasupat/bandit-v2
(commit %(commit)s), Apache License 2.0.

What was done to it (scripts/export-bandit.py of FFmpeg GUI):
- The weights were loaded as they are into the authors' code (the model of
  configs/models/bandit-mus64.yaml, at 48 kHz) and converted to ONNX,
  opset %(opset)s.
- The network was written with real numbers only: its input is the
  spectrogram (real and imaginary parts) and its output the three complex
  masks (speech, music, sfx), instead of sound in and sound out; the
  transforms between sound and spectrogram are left to the program that
  runs the file.
- The masks are put together with one scatter-add per stem rather than band
  by band, with the frequency weights as 32-bit floats (the authors keep
  them as 64-bit).
- No weight was trained, fine-tuned, pruned or quantised.

The authors and the licensor have not reviewed or endorsed this file. It
comes with no warranty (see LICENSE, section 5). The weights were trained on
Divide and Remaster v3 (CC BY-SA 4.0); see docs/MODELS.md of FFmpeg GUI.
"""


def stand_ins():
    """
    The modules the authors' model code imports that the export does not need,
    as the few lines of them it uses: librosa's hz_to_midi and midi_to_hz (the
    same formulas), torchaudio's Spectrogram and InverseSpectrogram (as
    stft() and istft() describe them, for the end-to-end check) and
    pytorch_lightning's LightningModule (a plain Module).
    """
    import numpy as np
    import torch

    librosa = types.ModuleType("librosa")
    librosa.hz_to_midi = lambda f: 12 * (np.log2(np.asanyarray(f)) - np.log2(440.0)) + 69
    librosa.midi_to_hz = lambda n: 440.0 * (2.0 ** ((np.asanyarray(n) - 69.0) / 12.0))

    class Spectrogram(torch.nn.Module):
        def __init__(self, n_fft, win_length, hop_length, window_fn, normalized, center, pad_mode, **_):
            super().__init__()
            self.n_fft, self.hop, self.center, self.pad_mode, self.normalized = n_fft, hop_length, center, pad_mode, normalized
            self.register_buffer("window", window_fn(win_length), persistent=False)

        def forward(self, x):
            shape = x.shape
            spec = torch.stft(x.reshape(-1, shape[-1]), self.n_fft, self.hop, window=self.window, center=self.center,
                              pad_mode=self.pad_mode, normalized=False, onesided=True, return_complex=True)
            if self.normalized:
                spec = spec / self.window.pow(2.0).sum().sqrt()
            return spec.reshape(shape[:-1] + spec.shape[-2:])

    class InverseSpectrogram(torch.nn.Module):
        def __init__(self, n_fft, win_length, hop_length, window_fn, normalized, center, **_):
            super().__init__()
            self.n_fft, self.hop, self.center, self.normalized = n_fft, hop_length, center, normalized
            self.register_buffer("window", window_fn(win_length), persistent=False)

        def forward(self, spec, length):
            shape = spec.shape
            if self.normalized:
                spec = spec * self.window.pow(2.0).sum().sqrt()
            wav = torch.istft(spec.reshape(-1, shape[-2], shape[-1]), self.n_fft, self.hop, window=self.window,
                              center=self.center, normalized=False, onesided=True, length=length)
            return wav.reshape(shape[:-2] + wav.shape[-1:])

    torchaudio = types.ModuleType("torchaudio")
    torchaudio.transforms = types.ModuleType("torchaudio.transforms")
    torchaudio.transforms.Spectrogram = Spectrogram
    torchaudio.transforms.InverseSpectrogram = InverseSpectrogram
    torchaudio.functional = types.ModuleType("torchaudio.functional")

    lightning = types.ModuleType("pytorch_lightning")
    lightning.LightningModule = torch.nn.Module

    for name, module in (("librosa", librosa), ("torchaudio", torchaudio), ("torchaudio.transforms", torchaudio.transforms),
                         ("torchaudio.functional", torchaudio.functional), ("pytorch_lightning", lightning)):
        sys.modules[name] = module


def build(code):
    """The authors' Bandit, as configured for the released checkpoints."""
    sys.path.insert(0, code)
    from src.models.bandit.bandit import Bandit
    return Bandit(stems=STEMS, **CONFIG)


def masks_module(model):
    """
    The network from spectrogram to masks, with real numbers (see the module's
    docstring). The masks are put together as the authors' code does (each
    band's mask times its frequency weights, added where bands overlap), but
    with one scatter-add per stem over every band's bins at once, and with the
    frequency weights as 32-bit floats, as the masks are (the authors keep them
    as float64 and store the sums as complex64 again). Band by band and in
    float64, the graph had some 23,000 nodes, took 12 s to load and gave its
    masks as doubles (bandit-onnx-1); this way it has under 9,000, loads in
    a third of the time and gives floats.
    """
    import torch
    from torch import nn

    class Masks(nn.Module):
        def __init__(self, m):
            super().__init__()
            self.m = m
            self.specs = [tuple(int(v) for v in s) for s in m.band_specs.get_band_specs()]
            self.freqs = N_FFT // 2 + 1
            # Where each band's bins go, every band one after the other, and each bin's weight.
            self.register_buffer("index", torch.cat([torch.arange(s, e) for s, e in self.specs]))
            for stem in STEMS:
                estimator = m.mask_estim[stem]
                if estimator.use_freq_weights:
                    weights = torch.cat([estimator.get_buffer("freq_weights/%d" % b).float() for b in range(len(self.specs))])
                else:
                    weights = torch.ones(self.index.numel())
                self.register_buffer("weights_" + stem, weights)

        def forward(self, x):
            # x: [B, F, T, 2] -> [B, 3, F, T, 2]
            batch, freqs, frames, _ = x.shape
            m = self.m
            xt = x.permute(0, 2, 1, 3)
            # Band split: each band's real and imaginary parts as one vector a frame (view_as_real order).
            bands = []
            for i, nfm in enumerate(m.band_split.norm_fc_modules):
                start, end = self.specs[i]
                bands.append(nfm.combined(xt[:, :, start:end, :].reshape(batch, frames, -1)))
            z = torch.stack(bands, dim=1)
            # Sequence modelling: residual RNNs across time, then across bands, in turn.
            for layer in m.tf_model.seqband:
                if hasattr(layer, "rnn"):
                    y = layer.norm(z)
                    b, u, a, e = y.shape
                    y = layer.rnn(y.reshape(b * u, a, e))[0].reshape(b, u, a, -1)
                    z = layer.fc(y) + z
                else:
                    z = z.transpose(layer.dim0, layer.dim1)
            # Each stem's mask: every band's, weighted, added into the bins it covers.
            index = self.index[None, None, :, None].expand(batch, frames, -1, 2)
            stems = []
            for stem in STEMS:
                estimator = m.mask_estim[stem]
                parts = [estimator.norm_mlp[b].combined(z[:, b, :, :]).reshape(batch, frames, end - start, 2)
                         for b, (start, end) in enumerate(self.specs)]
                weighted = torch.cat(parts, dim=2) * getattr(self, "weights_" + stem)[None, None, :, None]
                masks = torch.zeros(batch, frames, self.freqs, 2, dtype=weighted.dtype).scatter_add(2, index, weighted)
                stems.append(masks)
            return torch.stack(stems, dim=1).permute(0, 1, 3, 2, 4)

    return Masks(model).eval()


# -- The transforms, as the app does them (core/stems/StemStft.kt) ----------------------

def window():
    import numpy as np
    n = np.arange(N_FFT)
    return (0.5 - 0.5 * np.cos(2.0 * np.pi * n / N_FFT)).astype(np.float64)


def stft(sound):
    """[samples] -> [1025, T, 2]: centred (reflect), periodic Hann, one-sided, normalised."""
    import numpy as np
    w = window()
    padded = np.pad(sound.astype(np.float64), (N_FFT // 2, N_FFT // 2), mode="reflect")
    frames = 1 + (len(padded) - N_FFT) // HOP
    out = np.empty((N_FFT // 2 + 1, frames, 2))
    scale = 1.0 / np.sqrt(np.sum(w * w))
    for t in range(frames):
        spectrum = np.fft.rfft(padded[t * HOP:t * HOP + N_FFT] * w) * scale
        out[:, t, 0], out[:, t, 1] = spectrum.real, spectrum.imag
    return out


def istft(spec, length):
    """[1025, T, 2] -> [length]: the inverse of stft()."""
    import numpy as np
    w = window()
    frames = spec.shape[1]
    total = N_FFT + HOP * (frames - 1)
    out = np.zeros(total)
    envelope = np.zeros(total)
    scale = np.sqrt(np.sum(w * w))
    for t in range(frames):
        frame = np.fft.irfft(spec[:, t, 0] + 1j * spec[:, t, 1], N_FFT) * scale
        out[t * HOP:t * HOP + N_FFT] += frame * w
        envelope[t * HOP:t * HOP + N_FFT] += w * w
    out = out[N_FFT // 2:N_FFT // 2 + length]
    envelope = envelope[N_FFT // 2:N_FFT // 2 + length]
    return out / np.where(envelope > 1e-11, envelope, 1.0)


def apply_masks(spec, masks):
    """The mixture's spectrogram [F, T, 2] times each mask [3, F, T, 2]: [3, F, T, 2]."""
    import numpy as np
    xr, xi = spec[..., 0], spec[..., 1]
    mr, mi = masks[..., 0], masks[..., 1]
    return np.stack([xr * mr - xi * mi, xr * mi + xi * mr], axis=-1)


def stamp(path, notice, licence_text, source):
    """The licence and the notice inside the ONNX file (doc_string and metadata), as for the app's other files."""
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
    return NOTICE % {"checkpoint": lock["checkpoint"], "checkpoint_md5": lock["checkpoint_md5"],
                     "checkpoint_sha256": lock["checkpoint_sha256"], "commit": lock["commit"], "opset": lock["opset"]}


def die(message):
    print("error: " + message, file=sys.stderr)
    sys.exit(1)


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--bandit", required=True, help="checkout of kwatcharasupat/bandit-v2")
    parser.add_argument("--checkpoint", help="checkpoint-multi.ckpt (Zenodo 12701995)")
    parser.add_argument("--licence", help="the CC BY-SA 4.0 legal code the lock pins (licence_url)")
    parser.add_argument("--random", action="store_true", help="random weights: checks this script only")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    lock = read_lock(LOCK)
    if not args.random:
        if not args.checkpoint or not args.licence:
            die("--checkpoint and --licence are needed (or --random to check the script)")
        if digest(args.checkpoint, "md5") != lock["checkpoint_md5"]:
            die("%s is not the checkpoint Zenodo serves (MD5)" % args.checkpoint)
        # Learned from the file the first time (Zenodo gives MD5 only), checked ever after.
        learned = sha256(args.checkpoint)
        if set(lock["checkpoint_sha256"]) != {"0"} and learned != lock["checkpoint_sha256"]:
            die("%s is not the pinned checkpoint (SHA-256)" % args.checkpoint)
        lock["checkpoint_sha256"] = learned
        if sha256(args.licence) != lock["licence_sha256"]:
            die("the licence is not the CC BY-SA 4.0 legal code the lock pins")
    if sha256(os.path.join(args.bandit, "LICENSE")) != lock["code_licence_sha256"]:
        die("bandit-v2's LICENSE is not the one the lock pins: read it again before exporting")

    import numpy as np
    import onnxruntime as ort
    import torch

    stand_ins()
    torch.manual_seed(0)
    model = build(args.bandit)
    if not args.random:
        state = torch.load(args.checkpoint, map_location="cpu", weights_only=False)["state_dict"]
        weights = {k[len("model."):]: v for k, v in state.items() if k.startswith("model.")}
        missing, unexpected = model.load_state_dict(weights, strict=False)
        # Only the transforms' windows (rebuilt here) may be left out; every weight must load.
        missing = [k for k in missing if not k.startswith(("stft.", "istft."))]
        unexpected = [k for k in unexpected if not k.startswith(("stft.", "istft."))]
        if missing or unexpected:
            die("the checkpoint does not fit the model: missing %s, unexpected %s" % (missing[:5], unexpected[:5]))
    model.eval()
    masks = masks_module(model)

    os.makedirs(args.out, exist_ok=True)
    path = os.path.join(args.out, lock["file"])
    opset = int(lock["opset"])
    with torch.no_grad():
        example = torch.randn(1, N_FFT // 2 + 1, 64, 2)
        torch.onnx.export(
            masks, (example,), path, input_names=["spectrogram"], output_names=["masks"],
            dynamic_axes={"spectrogram": {0: "batch", 2: "frames"}, "masks": {0: "batch", 3: "frames"}},
            opset_version=opset, do_constant_folding=False, dynamo=False,
        )
        notice = notice_of(lock)
        if args.random:
            licence_text = "Random weights: a check of scripts/export-bandit.py, not a model."
        else:
            with open(args.licence, encoding="utf-8") as f:
                licence_text = f.read()
        stamp(path, notice, licence_text, "%s at %s; weights %s" % (lock["repository"], lock["commit"], lock["checkpoint_url"]))

        # References from PyTorch first: random spectrograms through the masks, and
        # sound through the authors' whole model (their transforms included).
        cases = []
        for seed, frames in ((0, 64), (1, 751)):
            g = torch.Generator().manual_seed(seed)
            cases.append(torch.randn(1, N_FFT // 2 + 1, frames, 2, generator=g) * 0.05)
        references = [masks(x).numpy() for x in cases]
        g = torch.Generator().manual_seed(7)
        length = 48000 * 3 + 123
        t = torch.arange(length) / 48000.0
        sound = (0.2 * torch.sin(2 * np.pi * 220.0 * t) + 0.05 * torch.randn(length, generator=g)).float()
        whole = model({"mixture": {"audio": sound[None, None, :]}})["estimates"]
        sound_references = [whole[stem]["audio"][0, 0].numpy() for stem in STEMS]
    del model, masks
    gc.collect()

    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    session = ort.InferenceSession(path, options, providers=["CPUExecutionProvider"])
    report = {"masks_max_diff": [], "sound_max_diff": [], "checkpoint_sha256": lock["checkpoint_sha256"]}
    for x, ref in zip(cases, references):
        got = session.run(None, {"spectrogram": x.numpy()})[0]
        worst = float(np.max(np.abs(got - ref)))
        report["masks_max_diff"].append(float("%.3g" % worst))
        if worst > 1e-3 * max(float(np.max(np.abs(ref))), 1.0):
            die("the ONNX file differs from PyTorch by %g" % worst)
    spec = stft(sound.numpy())
    got = session.run(None, {"spectrogram": spec[None].astype(np.float32)})[0][0]
    stems = apply_masks(spec, got)
    for k, ref in enumerate(sound_references):
        mine = istft(stems[k], len(ref))
        worst = float(np.max(np.abs(mine - ref)))
        report["sound_max_diff"].append(float("%.3g" % worst))
        if worst > 1e-3 * max(float(np.max(np.abs(ref))), 1e-3):
            die("%s through stft(), the file and istft() differs from the model by %g" % (STEMS[k], worst))
    del session
    gc.collect()

    value = sha256(path)
    if args.random:
        print(json.dumps({"random": True, "check": report}, indent=2))
        return
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
