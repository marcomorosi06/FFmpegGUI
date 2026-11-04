#!/usr/bin/env python3
"""
Makes the files of the invisible AI marks the app writes into what its models
make (EU AI Act, Regulation (EU) 2024/1689, Article 50(2); see
app/.../marking/MarkerModel.kt and core/marking/):

  audioseal-generator.onnx   (AudioSeal by Meta, MIT: converted here)
      in   audio     float32 [1, 1, WINDOW]  speech at 16 kHz, -1..1
           message   int64   [1, 16]         the message: all 0
      out  watermark float32 [1, 1, WINDOW]  added to the speech

  audioseal-detector.onnx    (the same)
      in   audio     float32 [1, 1, WINDOW]
      out  detection float32 [1, 2, WINDOW]  per sample: not marked, marked
           message   float32 [1, 16]         per bit: how likely a 1

  trustmark-encoder-q.onnx and trustmark-decoder-q.onnx  (TrustMark Q by
      Adobe, MIT): Adobe's own ONNX files, byte for byte, renamed only.

WINDOW is the lock's window (5 s). AudioSeal's generator is get_watermark()
of AudioSealWM (no loudness normaliser: the 16-bit model has none), its
detector forward() of AudioSealDetector (softmax of the two detection
outputs, sigmoid of the message bits' mean): written as small wrappers that
call the same modules, so that the export traces plain ONNX.

The marks carry nothing but "made with AI": both payloads are all zeros, the
same for everyone (no identifier, no date, no random value). This script
checks that what the app does with these files works, with a Python copy of
the app's own scheme (core/marking/SpeechMarking.kt and PictureMarking.kt):

  - AudioSeal: the ONNX files answer as PyTorch does; speech said by eSpeak
    NG (a voice made by a machine, as the dubs are) is marked as the app
    marks it and found again: as it is, after MP3 and AAC at 128 kbit/s,
    with noise, at half the volume; the same speech unmarked is not found;
  - TrustMark: pictures are marked as the app marks them and found again,
    also by Adobe's own reader (its datalayer.py, at the pinned commit: the
    payload of zeros, found, schema BCH_SUPER); after JPEG at quality 75 and
    at half the size; the pictures unmarked are not found; the marked ones
    stay close to the originals (PSNR).

It fails rather than write files that do not do all of this.

Usage:
  scripts/export-ai-markers.py --generator generator_base.pth --detector detector_base.pth
      --encoder encoder_Q.onnx --decoder decoder_Q.onnx --audioseal-licence LICENSE
      --trustmark DIR --pictures A.jpg B.jpg --out DIR [--learn --trustmark-commit SHA]

DIR of --trustmark is a checkout of https://github.com/adobe/trustmark at the
commit the lock pins. Needs the Python packages of
scripts/model-export-requirements.txt (audioseal among them), espeak-ng and
ffmpeg on the PATH.

With --learn, the values of the lock that are zeros (not pinned yet) are
taken as found and written, with the rest, to ai-markers.lock.learned in
--out (see scripts/pin-ai-markers.py); a value that is pinned must still
match.

Next to the files it writes what goes out with them: AudioSeal's and
TrustMark's LICENSE as they are, the NOTICE (what was converted and how,
what is unchanged), a PROVENANCE of everything they were made from,
SHA256SUMS and check.json. The AudioSeal files also carry their licence and
the notice inside them (ONNX metadata); Adobe's files are left exactly as
they are.
"""

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile

LOCK = os.path.join(os.path.dirname(os.path.abspath(__file__)), "ai-markers.lock")

# The app's scheme (core/marking): keep the two the same.
RATE = 16000
OVERLAP = 8000
DETECTED = 0.5
MARKED_SHARE = 0.5
FRESH_SHARE = 0.8
VOICED_RMS = 0.003
FRAME = 320
SIZE = 256
STRENGTH = 1.0
PICTURE_CORRECTABLE = 8
PICTURE_FRESH_ERRORS = 3
LEAST_PSNR = 38.0


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


def die(message):
    print("error: " + message, file=sys.stderr)
    sys.exit(1)


NOTICE = """The AI marks of FFmpeg GUI
https://github.com/marcomorosi06/FFmpegGUI

These files write and find the invisible marks FFmpeg GUI puts into what its
AI models make (EU AI Act, Regulation (EU) 2024/1689, Article 50(2)). The
marks carry nothing but "made with AI": their payloads are all zeros, the
same in every file of every user (no identifier, no date, no random value).

audioseal-generator.onnx and audioseal-detector.onnx
  are converted from AudioSeal (https://github.com/facebookresearch/audioseal,
  package %(audioseal_package)s), Copyright (c) Meta Platforms, Inc. and
  affiliates, MIT License (its weights included): see LICENSE-audioseal.txt.
  Changes made: the PyTorch models (generator_base.pth, SHA-256
  %(audioseal_generator_sha256)s;
  detector_base.pth, SHA-256
  %(audioseal_detector_sha256)s)
  were converted to ONNX, opset %(opset)s, for a fixed window of %(window)s
  samples at 16 kHz, by scripts/export-ai-markers.py of FFmpeg GUI: the
  generator as AudioSealWM.get_watermark with its message as an input, the
  detector as AudioSealDetector.forward. No weight was trained, fine-tuned,
  pruned or quantised.

trustmark-encoder-q.onnx and trustmark-decoder-q.onnx
  are Adobe's encoder_Q.onnx and decoder_Q.onnx of TrustMark
  (https://github.com/adobe/trustmark, commit %(trustmark_commit)s),
  Copyright 2023 Adobe, MIT License: see LICENSE-trustmark.txt. "The MIT
  license covers both the code in this repo and the model files which are
  downloaded upon first use" (TrustMark's README); Adobe confirmed that they
  may be redistributed unchanged: %(trustmark_confirmation)s
  They are unchanged, byte for byte (SHA-256
  %(trustmark_encoder_sha256)s and
  %(trustmark_decoder_sha256)s,
  as served at %(trustmark_host)s); only their names differ.
  TrustMark: T. Bui, S. Agarwal, J. Collomosse, "TrustMark: Robust
  Watermarking and Watermark Removal for Arbitrary Resolution Images", IEEE
  International Conference on Computer Vision (ICCV), 2025.

Neither Meta nor Adobe has reviewed or endorsed these files or FFmpeg GUI.
They come with no warranty (see the licences).
"""


def notice_of(lock):
    host = lock["trustmark_encoder_url"].rsplit("/", 1)[0] + "/"
    return NOTICE % {
        "audioseal_package": lock["audioseal_package"],
        "audioseal_generator_sha256": lock["audioseal_generator_sha256"],
        "audioseal_detector_sha256": lock["audioseal_detector_sha256"],
        "opset": lock["opset"],
        "window": lock["window"],
        "trustmark_commit": lock["trustmark_commit"],
        "trustmark_confirmation": lock["trustmark_confirmation"],
        "trustmark_encoder_sha256": lock["trustmark_encoder_sha256"],
        "trustmark_decoder_sha256": lock["trustmark_decoder_sha256"],
        "trustmark_host": host,
    }


# -- The app's scheme, in Python --------------------------------------------------------

def resample(x, src, dst, length=None):
    """core/marking/Resampler.kt: Hann-windowed sinc, 16 zero crossings, cut at 0.94 of the lower Nyquist."""
    import numpy as np
    if length is None:
        length = len(x) * dst // src
    if src == dst:
        out = np.zeros(length, dtype=np.float32)
        out[: min(length, len(x))] = x[:length]
        return out
    cutoff = 0.94 * min(1.0, dst / src)
    reach = 16 / cutoff
    step = src / dst
    out = np.zeros(length, dtype=np.float64)
    width = int(np.ceil(2 * reach)) + 2
    x = np.asarray(x, dtype=np.float64)
    for start in range(0, length, 4096):
        n = np.arange(start, min(length, start + 4096))
        centre = n * step
        first = np.floor(centre - reach).astype(np.int64)
        k = first[:, None] + np.arange(width)[None, :]
        u = k - centre[:, None]
        valid = (k >= 0) & (k < len(x)) & (u > -reach) & (u < reach)
        arg = np.pi * cutoff * u
        sinc = np.where(u == 0, 1.0, np.sin(arg) / np.where(arg == 0, 1.0, arg))
        h = cutoff * sinc * (0.5 + 0.5 * np.cos(np.pi * u / reach))
        values = np.where(valid, x[np.clip(k, 0, len(x) - 1)], 0.0)
        out[n] = np.sum(values * np.where(valid, h, 0.0), axis=1)
    return out.astype(np.float32)


def windows(samples, window, hop):
    out = []
    start = 0
    while samples > 0:
        out.append((start, min(window, samples - start)))
        if start + window >= samples:
            break
        start += hop
    return out


def mark_speech(gen, speech, rate, window):
    """What DubEngine does to each line: its watermark at 16 kHz, back at its own rate, added."""
    import numpy as np
    at16 = resample(speech, rate, RATE)
    ws = windows(len(at16), window, window - OVERLAP)
    joined = np.zeros(len(at16), dtype=np.float32)
    for i, (start, length) in enumerate(ws):
        x = np.zeros((1, 1, window), dtype=np.float32)
        x[0, 0, :length] = at16[start:start + length]
        mark = gen.run(None, {"audio": x, "message": np.zeros((1, 16), dtype=np.int64)})[0][0, 0]
        weight = np.ones(length, dtype=np.float32)
        j = np.arange(length, dtype=np.float32)
        if i > 0:
            weight = np.where(j < OVERLAP, (j + 0.5) / OVERLAP, weight)
        if i < len(ws) - 1:
            weight = np.where(j >= length - OVERLAP, 1 - (j - (length - OVERLAP) + 0.5) / OVERLAP, weight)
        joined[start:start + length] += mark[:length] * weight
    back = resample(joined, RATE, rate, len(speech))
    return np.clip(speech + back, -1, 1).astype(np.float32)


def check_speech(det, speech, rate, window):
    """The share of the speech's samples the detector finds marked, and the message it reads."""
    import numpy as np
    at16 = resample(speech, rate, RATE)
    voiced = np.zeros(len(at16), dtype=bool)
    for s in range(0, len(at16), FRAME):
        part = at16[s:s + FRAME].astype(np.float64)
        voiced[s:s + FRAME] = np.sqrt(np.mean(part * part)) > VOICED_RMS
    probabilities = []
    messages = []
    weights = []
    for start, length in windows(len(at16), window, window):
        x = np.zeros((1, 1, window), dtype=np.float32)
        x[0, 0, :length] = at16[start:start + length]
        detection, message = det.run(None, {"audio": x})
        probabilities.append(detection[0, 1, :length])
        messages.append(message[0])
        weights.append(int(voiced[start:start + length].sum()))
    p = np.concatenate(probabilities)
    speech_samples = int(voiced.sum())
    if speech_samples < RATE // 4:
        return None, None
    share = float(((p > DETECTED) & voiced).sum()) / speech_samples
    total = max(sum(weights), 1)
    message = sum(m * (w / total) for m, w in zip(messages, weights))
    return share, message


def region(w, h):
    if max(w, h) / min(w, h) <= 2.0:
        return 0, 0, w, h
    side = min(w, h)
    return (w - side) // 2, (h - side) // 2, side, side


def squeeze(rgb, size):
    """PictureMarking.Squeezer: each square the average of the pixels whose centres fall in it."""
    import numpy as np
    h, w, _ = rgb.shape
    ys = (np.arange(h) * size // h)
    xs = (np.arange(w) * size // w)
    sums = np.zeros((size, size, 3))
    counts = np.zeros((size, size))
    np.add.at(sums, (ys[:, None], xs[None, :]), rgb.astype(np.float64))
    np.add.at(counts, (ys[:, None], xs[None, :]), 1)
    if (counts == 0).any():
        # A small picture: the nearest pixel.
        yy = (np.arange(size) * h // size).clip(0, h - 1)
        xx = (np.arange(size) * w // size).clip(0, w - 1)
        nearest = rgb[yy][:, xx].astype(np.float64)
        empty = counts == 0
        sums[empty] = nearest[empty]
        counts[empty] = 1
    out = sums / counts[..., None] / 127.5 - 1.0
    return out.transpose(2, 0, 1)[None].astype(np.float32)


def stretch(residual, w, h):
    """PictureMarking.residualRow: bilinear, align_corners false (PyTorch's interpolate)."""
    import numpy as np

    def source(length):
        at = np.maximum((np.arange(length) + 0.5) * SIZE / length - 0.5, 0)
        first = np.minimum(np.floor(at).astype(np.int64), SIZE - 1)
        second = np.minimum(first + 1, SIZE - 1)
        return first, second, (at - first)[:]

    y0, y1, wy = source(h)
    x0, x1, wx = source(w)
    r = residual[0].transpose(1, 2, 0)
    top = r[y0][:, x0] * (1 - wx)[None, :, None] + r[y0][:, x1] * wx[None, :, None]
    bottom = r[y1][:, x0] * (1 - wx)[None, :, None] + r[y1][:, x1] * wx[None, :, None]
    return top * (1 - wy)[:, None, None] + bottom * wy[:, None, None]


def feather_weights(w, h):
    import numpy as np
    f = int(min(min(w, h) * 0.01, 50))
    f = max(1, f)
    g = min(f, w, h)
    weight = np.ones((h, w), dtype=np.float32)
    ramp = lambda i: (i + 1) / f  # noqa: E731
    # Rows first, then columns (the column fade wins where both apply), as feather_paste.
    for i in range(g):
        weight[i, :] = ramp(i)
        weight[h - 1 - i, :] = ramp(i)
    for i in range(g):
        weight[:, i] = ramp(i)
        weight[:, w - 1 - i] = ramp(i)
    return weight


def mark_picture(enc, picture):
    """What EraseEngine does to its result: TrustMark's mark, as Adobe's encode() lays it."""
    import numpy as np
    rgb = np.asarray(picture.convert("RGB")).astype(np.float64)
    h, w, _ = rgb.shape
    left, top, rw, rh = region(w, h)
    part = rgb[top:top + rh, left:left + rw]
    cover = squeeze(part, SIZE)
    names = {i.name: i for i in enc.get_inputs()}
    image_name = next(n for n, i in names.items() if len(i.shape) == 4)
    bits_name = next(n for n, i in names.items() if len(i.shape) == 2)
    stego = enc.run(None, {image_name: cover, bits_name: np.zeros((1, 100), dtype=np.float32)})[0]
    residual = np.clip(stego, -1, 1) - cover
    residual = residual - residual.mean(axis=(2, 3), keepdims=True)
    mark = stretch(residual, rw, rh)
    marked = np.clip(part / 127.5 - 1 + mark * STRENGTH, -1, 1) * 127.5 + 127.5
    weight = feather_weights(rw, rh)[..., None]
    blended = np.rint(marked * weight + part * (1 - weight)).clip(0, 255)
    out = rgb.copy()
    out[top:top + rh, left:left + rw] = blended
    return out.astype(np.uint8)


def read_picture(dec, rgb):
    """The decoder's 100 logits for the picture (its region squeezed to the decoder's size)."""
    h, w, _ = rgb.shape
    left, top, rw, rh = region(w, h)
    size = dec.get_inputs()[0].shape[-1]
    size = size if isinstance(size, int) else SIZE
    x = squeeze(rgb[top:top + rh, left:left + rw], size)
    return dec.run(None, {dec.get_inputs()[0].name: x})[0][0]


def picture_errors(logits):
    """core/marking/Marks.pictureErrors."""
    if logits[99] > 0 or logits[98] > 0:
        return None
    return int((logits[:96] > 0).sum())


# -- The export ---------------------------------------------------------------------------

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


def write_provenance(path, lock, files, report):
    import platform
    from importlib import metadata
    with open(LOCK, encoding="utf-8") as f:
        lock_text = f.read()
    lines = ["Made by %s" % os.path.basename(sys.argv[0]), ""]
    for name, digest in sorted(files.items()):
        lines.append("file %s sha256 %s" % (name, digest))
    lines += ["", "python %s on %s" % (platform.python_version(), platform.platform())]
    for package in ("torch", "onnx", "onnxruntime", "numpy", "audioseal", "omegaconf", "einops", "pillow"):
        try:
            lines.append("%s %s" % (package, metadata.version(package)))
        except metadata.PackageNotFoundError:
            lines.append("%s (not installed)" % package)
    for tool in (["espeak-ng", "--version"], ["ffmpeg", "-hide_banner", "-version"]):
        try:
            lines.append(subprocess.run(tool, capture_output=True, text=True, check=False).stdout.splitlines()[0])
        except (OSError, IndexError):
            lines.append("%s (not found)" % tool[0])
    for variable in ("GITHUB_REPOSITORY", "GITHUB_SHA", "GITHUB_RUN_ID", "GITHUB_WORKFLOW"):
        if os.environ.get(variable):
            lines.append("%s %s" % (variable.lower(), os.environ[variable]))
    lines += ["", "check " + json.dumps(report, sort_keys=True), "", "--- ai-markers.lock (as pinned for these files) ---"]
    lines += ["%s %s" % (k, v) for k, v in lock.items()]
    lines += ["", "--- ai-markers.lock (in the source) ---", lock_text]
    with open(path, "w", encoding="ascii") as f:
        f.write("\n".join(lines))


def say(text, rate):
    """A few sentences said by eSpeak NG, as the app's dubs are said by a machine: mono, -1..1, at [rate]."""
    with tempfile.TemporaryDirectory() as tmp:
        raw = os.path.join(tmp, "said.wav")
        subprocess.run(["espeak-ng", "-v", "en", "-s", "150", "-w", raw, text], check=True, capture_output=True)
        return decode(raw, rate)


def decode(path, rate):
    import numpy as np
    out = subprocess.run(["ffmpeg", "-hide_banner", "-nostdin", "-loglevel", "error", "-i", path,
                          "-ac", "1", "-ar", str(rate), "-f", "f32le", "-"], check=True, capture_output=True).stdout
    return np.frombuffer(out, dtype=np.float32).copy()


def through(speech, rate, args):
    """[speech] encoded with ffmpeg [args] and decoded again."""
    import numpy as np
    with tempfile.TemporaryDirectory() as tmp:
        raw = os.path.join(tmp, "in.f32")
        speech.astype(np.float32).tofile(raw)
        coded = os.path.join(tmp, "coded" + args[-1])
        subprocess.run(["ffmpeg", "-hide_banner", "-nostdin", "-loglevel", "error", "-f", "f32le", "-ar", str(rate), "-ac", "1",
                        "-i", raw] + args[:-1] + [coded], check=True, capture_output=True)
        return decode(coded, rate)[: len(speech)]


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--generator", required=True)
    parser.add_argument("--detector", required=True)
    parser.add_argument("--encoder", required=True)
    parser.add_argument("--decoder", required=True)
    parser.add_argument("--audioseal-licence", required=True)
    parser.add_argument("--trustmark", required=True, help="checkout of adobe/trustmark")
    parser.add_argument("--trustmark-commit", help="the checkout's commit (to learn it)")
    parser.add_argument("--pictures", nargs="+", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--learn", action="store_true")
    args = parser.parse_args()

    lock = read_lock(LOCK)
    learned = dict(lock)

    def pin(key, found):
        want = lock[key]
        if unpinned(want):
            if not args.learn:
                die("%s is not pinned yet: run with --learn (the workflow does when the lock has zeros)" % key)
            learned[key] = found
            print("learned %s %s" % (key, found))
        elif want != found:
            die("%s is %s, the lock pins %s" % (key, found, want))

    pin("audioseal_generator_sha256", sha256(args.generator))
    pin("audioseal_detector_sha256", sha256(args.detector))
    pin("trustmark_encoder_sha256", sha256(args.encoder))
    pin("trustmark_decoder_sha256", sha256(args.decoder))
    if args.trustmark_commit:
        pin("trustmark_commit", args.trustmark_commit)
    elif unpinned(lock["trustmark_commit"]):
        die("trustmark_commit is not pinned: give --trustmark-commit")
    if sha256(args.audioseal_licence) != lock["audioseal_licence_sha256"]:
        die("AudioSeal's LICENSE is not the one the lock pins: read it again before exporting")
    trustmark_licence = os.path.join(args.trustmark, lock["trustmark_licence"])
    if sha256(trustmark_licence) != lock["trustmark_licence_sha256"]:
        die("TrustMark's LICENSE is not the one the lock pins: read it again before exporting")
    with open(os.path.join(args.trustmark, "README.md"), encoding="utf-8") as f:
        if "The MIT license covers both the code in this repo and the model files" not in f.read():
            die("TrustMark's README no longer says the MIT licence covers the model files: read it again")

    os.environ["NO_TORCH_COMPILE"] = "1"
    import numpy as np
    import onnxruntime as ort
    import torch
    from omegaconf import OmegaConf
    from PIL import Image
    import audioseal.loader as loader
    from audioseal.builder import AudioSealDetectorConfig, AudioSealWMConfig, create_detector, create_generator

    window = int(lock["window"])
    opset = int(lock["opset"])

    def build(card, kind, create, weights):
        config = OmegaConf.to_container(loader.load_local_model_config(card))
        config.pop("checkpoint")
        checkpoint = loader._safe_load_checkpoint(weights)
        if "xp.cfg" in checkpoint:
            config = {**checkpoint["xp.cfg"], **config}
        model_config = loader.AudioSeal.parse_config(config, config_type=kind)
        if "best_state" in checkpoint:
            checkpoint = checkpoint["best_state"]["model"]
        elif "model" in checkpoint:
            checkpoint = checkpoint["model"]
        model = create(model_config)
        loader.load_state_dict(model, state_dict=loader.convert_state_dict_for_scriptable_model(checkpoint))
        return model.eval()

    generator = build("audioseal_wm_16bits", AudioSealWMConfig, create_generator, args.generator)
    detector = build("audioseal_detector_16bits", AudioSealDetectorConfig, create_detector, args.detector)
    if getattr(generator, "normalizer", None) is not None or getattr(detector, "normalizer", None) is not None:
        die("the 16-bit AudioSeal models have no loudness normaliser: this one does, read it again")

    class Generator(torch.nn.Module):
        def __init__(self, g):
            super().__init__()
            self.g = g

        def forward(self, audio, message):
            return self.g.get_watermark(audio, message=message)

    class Detector(torch.nn.Module):
        def __init__(self, d):
            super().__init__()
            self.d = d

        def forward(self, audio):
            out = self.d.detector(audio)
            return torch.softmax(out[:, :2, :], dim=1), torch.sigmoid(out[:, 2:, :].mean(dim=-1))

    os.makedirs(args.out, exist_ok=True)
    generator_path = os.path.join(args.out, lock["generator_file"])
    detector_path = os.path.join(args.out, lock["detector_file"])
    with torch.no_grad():
        x = torch.zeros(1, 1, window)
        message = torch.zeros(1, 16, dtype=torch.int64)
        torch.onnx.export(Generator(generator).eval(), (x, message), generator_path, input_names=["audio", "message"],
                          output_names=["watermark"], opset_version=opset, do_constant_folding=False, dynamo=False)
        torch.onnx.export(Detector(detector).eval(), (x,), detector_path, input_names=["audio"],
                          output_names=["detection", "message"], opset_version=opset, do_constant_folding=False, dynamo=False)

    with open(args.audioseal_licence, encoding="utf-8") as f:
        audioseal_licence_text = f.read()
    notice = notice_of(learned)
    for p in (generator_path, detector_path):
        stamp(p, notice, audioseal_licence_text, "%s, %s" % (lock["audioseal_repository"], lock["audioseal_package"]))
    encoder_path = os.path.join(args.out, lock["encoder_file"])
    decoder_path = os.path.join(args.out, lock["decoder_file"])
    shutil.copyfile(args.encoder, encoder_path)
    shutil.copyfile(args.decoder, decoder_path)

    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    gen = ort.InferenceSession(generator_path, options, providers=["CPUExecutionProvider"])
    det = ort.InferenceSession(detector_path, options, providers=["CPUExecutionProvider"])
    enc = ort.InferenceSession(encoder_path, options, providers=["CPUExecutionProvider"])
    dec = ort.InferenceSession(decoder_path, options, providers=["CPUExecutionProvider"])
    report = {"audioseal_parity": [], "speech": {}, "pictures": []}

    # -- AudioSeal: the ONNX files answer as PyTorch ----------------------------------------
    with torch.no_grad():
        for seed in range(3):
            torch.manual_seed(seed)
            x = torch.randn(1, 1, window) * 0.1
            ref = Generator(generator)(x, torch.zeros(1, 16, dtype=torch.int64)).numpy()
            got = gen.run(None, {"audio": x.numpy(), "message": np.zeros((1, 16), dtype=np.int64)})[0]
            d_ref = [t.numpy() for t in Detector(detector)(x)]
            d_got = det.run(None, {"audio": x.numpy()})
            worst = max(float(np.max(np.abs(got - ref))), float(np.max(np.abs(d_got[0] - d_ref[0]))), float(np.max(np.abs(d_got[1] - d_ref[1]))))
            report["audioseal_parity"].append(round(worst, 8))
            if worst > 1e-3:
                die("AudioSeal's ONNX files differ from PyTorch by %g" % worst)

    # -- AudioSeal: speech said by a machine, marked as the app does, and found again --------
    rate = 22050
    speech = say("The quick brown fox jumps over the lazy dog. This voice was made by a machine, and it says so. "
                 "Every dub the app makes carries a mark that cannot be heard, the same for everyone.", rate)
    marked = mark_speech(gen, speech, rate, window)
    cases = {
        "fresh": marked,
        "mp3_128k": through(marked, rate, ["-c:a", "libmp3lame", "-b:a", "128k", ".mp3"]),
        "aac_128k": through(marked, rate, ["-c:a", "aac", "-b:a", "128k", ".m4a"]),
        "noise": (marked + np.random.default_rng(0).normal(0, 0.01, len(marked))).astype(np.float32),
        "half_volume": (marked * 0.5).astype(np.float32),
    }
    for name, audio in cases.items():
        share, message = check_speech(det, audio, rate, window)
        report["speech"][name] = round(share, 4) if share is not None else None
        need = FRESH_SHARE if name == "fresh" else MARKED_SHARE
        if share is None or share < need:
            die("the speech mark is found on only %s of the speech (%s), %s needed" % (share, name, need))
        if name == "fresh" and not all(m < 0.5 for m in message):
            die("the speech mark's message is not the zeros it was given: %s" % list(message))
    share, _ = check_speech(det, speech, rate, window)
    report["speech"]["unmarked"] = round(share, 4)
    if share >= MARKED_SHARE:
        die("unmarked speech is found marked (%g)" % share)
    error = marked - speech
    report["speech"]["snr_db"] = round(float(10 * np.log10(np.sum(speech ** 2) / max(np.sum(error ** 2), 1e-12))), 2)

    # -- TrustMark: pictures marked as the app does, and found again --------------------------
    # Adobe's own reader of the bits (datalayer.py with its BCH), at the pinned commit, alone:
    # the rest of its package (PyTorch models) is not needed for it.
    reader = tempfile.mkdtemp()
    os.makedirs(os.path.join(reader, "tmreader"))
    for name in ("datalayer.py", "bchecc.py"):
        shutil.copyfile(os.path.join(args.trustmark, "python", "trustmark", name), os.path.join(reader, "tmreader", name))
    open(os.path.join(reader, "tmreader", "__init__.py"), "w").close()
    sys.path.insert(0, reader)
    from tmreader.datalayer import DataLayer
    layer = DataLayer(100, False, 0)
    import io
    for path in args.pictures:
        picture = Image.open(path).convert("RGB")
        original = np.asarray(picture)
        marked = mark_picture(enc, picture)
        entry = {"picture": os.path.basename(path)}
        logits = read_picture(dec, marked)
        entry["fresh_errors"] = picture_errors(logits)
        if entry["fresh_errors"] is None or entry["fresh_errors"] > PICTURE_FRESH_ERRORS:
            die("on %s the picture mark reads with %s wrong bits" % (path, entry["fresh_errors"]))
        secret, found, version = layer.decode_bitstream((logits > 0)[None].astype(np.float32), "binary")[0]
        if not found or version != 0 or set(secret) != {"0"}:
            die("Adobe's reader does not find the payload of zeros on %s: %s" % (path, (secret, found, version)))
        buffer = io.BytesIO()
        Image.fromarray(marked).save(buffer, "JPEG", quality=75)
        jpeg = np.asarray(Image.open(io.BytesIO(buffer.getvalue())).convert("RGB"))
        entry["jpeg75_errors"] = picture_errors(read_picture(dec, jpeg))
        half = np.asarray(Image.fromarray(marked).resize((marked.shape[1] // 2, marked.shape[0] // 2), Image.BILINEAR))
        entry["half_size_errors"] = picture_errors(read_picture(dec, half))
        for key in ("jpeg75_errors", "half_size_errors"):
            if entry[key] is None or entry[key] > PICTURE_CORRECTABLE:
                die("on %s the picture mark is lost (%s: %s)" % (path, key, entry[key]))
        unmarked = picture_errors(read_picture(dec, original))
        entry["unmarked_errors"] = unmarked
        if unmarked is not None and unmarked <= PICTURE_CORRECTABLE:
            die("the unmarked %s reads as marked" % path)
        mse = np.mean((marked.astype(np.float64) - original) ** 2)
        entry["psnr_db"] = round(float(10 * np.log10(255 ** 2 / max(mse, 1e-12))), 2)
        if entry["psnr_db"] < LEAST_PSNR:
            die("the mark changes %s too much (PSNR %s dB)" % (path, entry["psnr_db"]))
        report["pictures"].append(entry)
    # Plain pictures with nothing in them are not marked either.
    for colour in (0, 128, 255):
        flat = np.full((300, 400, 3), colour, dtype=np.uint8)
        e = picture_errors(read_picture(dec, flat))
        if e is not None and e <= PICTURE_CORRECTABLE:
            die("a plain picture (%d) reads as marked" % colour)

    # -- What goes next to the files ----------------------------------------------------------
    paths = (generator_path, detector_path, encoder_path, decoder_path)
    sums = {os.path.basename(p): sha256(p) for p in paths}
    pin("generator_sha256", sums[lock["generator_file"]])
    pin("detector_sha256", sums[lock["detector_file"]])
    for key, p in (("generator_bytes", generator_path), ("detector_bytes", detector_path),
                   ("encoder_bytes", encoder_path), ("decoder_bytes", decoder_path)):
        size = str(os.path.getsize(p))
        if lock[key] == "0":
            if not args.learn:
                die("%s is not pinned yet" % key)
            learned[key] = size
        elif lock[key] != size:
            die("%s is %s, the lock pins %s" % (key, size, lock[key]))
    shutil.copyfile(args.audioseal_licence, os.path.join(args.out, "LICENSE-audioseal"))
    shutil.copyfile(trustmark_licence, os.path.join(args.out, "LICENSE-trustmark"))
    with open(os.path.join(args.out, "NOTICE"), "w", encoding="ascii") as f:
        f.write(notice_of(learned))
    write_provenance(os.path.join(args.out, "PROVENANCE"), learned, sums, report)
    with open(os.path.join(args.out, "SHA256SUMS"), "w", encoding="ascii") as f:
        for name, digest in sorted(sums.items()):
            f.write("%s  %s\n" % (digest, name))
    with open(os.path.join(args.out, "check.json"), "w", encoding="ascii") as f:
        json.dump(report, f, indent=2)
    if args.learn:
        with open(os.path.join(args.out, "ai-markers.lock.learned"), "w", encoding="ascii") as f:
            for k, v in learned.items():
                f.write("%s %s\n" % (k, v))
    print(json.dumps({"files": sums, "check": report}, indent=2))


if __name__ == "__main__":
    main()
