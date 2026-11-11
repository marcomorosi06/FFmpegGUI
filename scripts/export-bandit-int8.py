#!/usr/bin/env python3
"""
Makes the int8 build of Bandit v2 (Karn N. Watcharasupat, Chih-Wei Wu, Iroro
Orife; weights CC BY-SA 4.0) that the app's stem separation runs by default
(see app/.../voice/StemModels.kt, BANDIT_INT8), from the float file this
project publishes (bandit-onnx-2, scripts/bandit-onnx.lock): the same network,
the same input and output, about a quarter of the size, loaded about three
times quicker and run quicker where the processor has int8 instructions
(1.7 times on the desktop processor it was first measured on).

  bandit-v2-multi-int8.onnx
      in   spectrogram  float32 [B, 1025, T, 2]     as bandit-v2-multi.onnx
      out  masks        float32 [B, 3, 1025, T, 2]  as bandit-v2-multi.onnx

Four steps, each checked:

  1. Constants folded (ONNX Runtime's basic level): the weights of each GRU
     become plain tensors. Nothing else changes.
  2. Each GRU (16 of them, bidirectional, 256 units, "linear before reset")
     written out as what it computes: the input times its weights for every
     frame at once (a MatMul), then a Scan over the frames, forwards and
     backwards, whose step is the GRU cell (the state times the recurrent
     weights, a MatMul, then the gates). ONNX Runtime's GRU kernel cannot be
     quantised; these MatMuls can. The masks must match the float file's
     to within MAX_REWRITE_DIFF.
  3. Dynamic quantisation (onnxruntime.quantization.quantize_dynamic): every
     MatMul's weights, those inside the Scans too, as signed 8-bit integers
     with one scale per output channel; the activations are quantised as the
     model runs. Layer norms, gates and the masks' sums stay in float.
  4. The check: the masks of random spectrograms, and three stems of a sound
     (tones, noise, chirps and clicks) through the app's transforms
     (export-bandit.py's stft() and istft()), against the float file: each
     stem of the int8 file must be within MIN_STEM_SDR_DB of the float one's.

Usage:
  scripts/export-bandit-int8.py --source bandit-v2-multi.onnx --licence FILE --out DIR

--source is the file of the release bandit-onnx-2 (its SHA-256 is checked
against both locks), --licence the CC BY-SA 4.0 legal code the lock pins.
Needs the packages pinned in scripts/model-export-requirements.txt (onnx,
onnxruntime, numpy). Next to the file it writes, as export-bandit.py does,
LICENSE, NOTICE, PROVENANCE, SHA256SUMS and check.json; the licence and the
notice also go inside the ONNX file.
"""

import argparse
import gc
import importlib.util
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
LOCK = os.path.join(HERE, "bandit-int8-onnx.lock")
SOURCE_LOCK = os.path.join(HERE, "bandit-onnx.lock")

# The float rewrite must answer as the float file does (rounding only).
MAX_REWRITE_DIFF = 1e-4
# How close each int8 stem must stay to the float file's (signal to difference, dB).
MIN_STEM_SDR_DB = 18.0
# The masks of random spectrograms: mean difference against the mean mask.
MAX_MASK_MEAN_REL = 0.02


def load_bandit_script():
    """export-bandit.py: its lock reader, transforms, notice and stamp, shared."""
    sys.dont_write_bytecode = True
    spec = importlib.util.spec_from_file_location("export_bandit", os.path.join(HERE, "export-bandit.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


bandit = load_bandit_script()
read_lock = bandit.read_lock
sha256 = bandit.sha256


NOTICE = """Bandit v2 (multilingual), int8 ONNX file of FFmpeg GUI
https://github.com/marcomorosi06/FFmpegGUI

bandit-v2-multi-int8.onnx is made from "Model Weights for Remastering Divide
and Remaster: A Cinematic Audio Source Separation Dataset with Multilingual
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

It is made from FFmpeg GUI's float file of the same weights, %(source_file)s
of the release %(source_tag)s (SHA-256 %(source_sha256)s), whose own NOTICE
says how that was converted to ONNX. What was done to it here
(scripts/export-bandit-int8.py of FFmpeg GUI):
- Its constants were folded, and each of its GRU layers was written out as
  the products and gates it computes (a matrix product for the input, then
  a scan over the frames whose step is the GRU cell): the same results, to
  rounding.
- Every matrix product's weights were quantised to signed 8-bit integers,
  one scale per output channel (dynamic quantisation, ONNX Runtime
  %(onnxruntime)s); the values in between are quantised as the model runs.
  The masks therefore differ a little from the float file's: each stem of a
  test sound stays within %(min_sdr)s dB of the float file's (see check.json).
- No weight was trained or fine-tuned.

The authors and the licensor have not reviewed or endorsed this file. It
comes with no warranty (see LICENSE, section 5). The weights were trained on
Divide and Remaster v3 (CC BY-SA 4.0); see docs/MODELS.md of FFmpeg GUI.
"""


def notice_of(lock):
    return NOTICE % {
        "checkpoint": lock["checkpoint"], "checkpoint_md5": lock["checkpoint_md5"],
        "checkpoint_sha256": lock["checkpoint_sha256"], "commit": lock["commit"],
        "source_file": lock["source_file"], "source_tag": lock["source_tag"], "source_sha256": lock["source_sha256"],
        "onnxruntime": lock["onnxruntime"], "min_sdr": "%g" % MIN_STEM_SDR_DB,
    }


def die(message):
    print("error: " + message, file=sys.stderr)
    sys.exit(1)


# -- 1. Constants folded ---------------------------------------------------------------

def fold(source, target):
    import onnxruntime as ort
    options = ort.SessionOptions()
    options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_BASIC
    options.optimized_model_filepath = target
    options.intra_op_num_threads = 1
    ort.InferenceSession(source, options, providers=["CPUExecutionProvider"])


# -- 2. The GRUs written out -----------------------------------------------------------

def cell(prefix, recurrent, recurrent_bias, hidden):
    """The Scan's step: state h [batch, H] and the frame's input product [batch, 3H] -> h', y (= h')."""
    import numpy as np
    from onnx import TensorProto, helper, numpy_helper

    def n(s):
        return prefix + s

    nodes = [
        helper.make_node("MatMul", [n("h"), n("RT")], [n("hr0")]),
        helper.make_node("Add", [n("hr0"), n("Rb")], [n("hr")]),
        # ONNX's gate order: update (z), reset (r), hidden (h).
        helper.make_node("Split", [n("xw"), n("thirds")], [n("xz"), n("xr"), n("xh")], axis=1),
        helper.make_node("Split", [n("hr"), n("thirds")], [n("hz"), n("hrr"), n("hh")], axis=1),
        helper.make_node("Add", [n("xz"), n("hz")], [n("z0")]),
        helper.make_node("Sigmoid", [n("z0")], [n("z")]),
        helper.make_node("Add", [n("xr"), n("hrr")], [n("r0")]),
        helper.make_node("Sigmoid", [n("r0")], [n("r")]),
        # Linear before reset: the reset gate times (R h + Rb) of the hidden gate.
        helper.make_node("Mul", [n("r"), n("hh")], [n("rh")]),
        helper.make_node("Add", [n("xh"), n("rh")], [n("c0")]),
        helper.make_node("Tanh", [n("c0")], [n("c")]),
        # h' = (1 - z) c + z h = c + z (h - c)
        helper.make_node("Sub", [n("h"), n("c")], [n("d")]),
        helper.make_node("Mul", [n("z"), n("d")], [n("zd")]),
        helper.make_node("Add", [n("c"), n("zd")], [n("h1")]),
        helper.make_node("Identity", [n("h1")], [n("y")]),
    ]
    constants = [
        numpy_helper.from_array(np.ascontiguousarray(recurrent.T).astype(np.float32), n("RT")),
        numpy_helper.from_array(recurrent_bias.astype(np.float32), n("Rb")),
        numpy_helper.from_array(np.array([hidden] * 3, dtype=np.int64), n("thirds")),
    ]
    f = TensorProto.FLOAT
    return helper.make_graph(
        nodes, prefix + "cell",
        [helper.make_tensor_value_info(n("h"), f, ["batch", hidden]),
         helper.make_tensor_value_info(n("xw"), f, ["batch", 3 * hidden])],
        [helper.make_tensor_value_info(n("h1"), f, ["batch", hidden]),
         helper.make_tensor_value_info(n("y"), f, ["batch", hidden])],
        constants,
    )


def write_out_grus(model):
    """Every GRU of [model] as MatMul + Scan (see the module's docstring). Returns how many."""
    import numpy as np
    from onnx import helper, numpy_helper

    graph = model.graph
    constants = {t.name: t for t in graph.initializer}
    read_by = {}
    for node in graph.node:
        for name in node.input:
            read_by.setdefault(name, []).append(node)
    outputs = {o.name for o in graph.output}
    nodes, added, count = [], [], 0
    for node in graph.node:
        if node.op_type != "GRU":
            nodes.append(node)
            continue
        attrs = {a.name: helper.get_attribute_value(a) for a in node.attribute}
        if attrs.get("linear_before_reset") != 1 or attrs.get("direction") != b"bidirectional" or attrs.get("layout", 0) != 0:
            die("%s is not the GRU this script writes out: %s" % (node.name, attrs))
        if any(k in attrs for k in ("activations", "activation_alpha", "activation_beta", "clip")):
            die("%s has activations of its own: %s" % (node.name, attrs))
        if len(node.input) > 4 and node.input[4]:
            die("%s has sequence lengths" % node.name)
        for name in node.input[1:4]:
            if name not in constants:
                die("%s: %s is not a constant after folding" % (node.name, name))
        if len(node.input) > 5 and node.input[5]:
            # The initial state: only zeros (what PyTorch exports when none is given).
            first = node.input[5]
            if first in constants and np.any(numpy_helper.to_array(constants[first])):
                die("%s starts from a state that is not zero" % node.name)
        if len(node.output) > 1 and node.output[1] and (read_by.get(node.output[1]) or node.output[1] in outputs):
            die("%s: its last state is read, which this script does not write out" % node.name)
        weights = numpy_helper.to_array(constants[node.input[1]])
        recurrent = numpy_helper.to_array(constants[node.input[2]])
        bias = numpy_helper.to_array(constants[node.input[3]])
        hidden = int(attrs["hidden_size"])
        x = node.input[0]
        p = "gru%d_" % count
        count += 1
        ys = []
        for d in range(2):
            wt, wb = p + "WT%d" % d, p + "Wb%d" % d
            added += [numpy_helper.from_array(np.ascontiguousarray(weights[d].T).astype(np.float32), wt),
                      numpy_helper.from_array(bias[d][:3 * hidden].astype(np.float32), wb)]
            xw0, xw = p + "xw0_%d" % d, p + "xw_%d" % d
            nodes += [helper.make_node("MatMul", [x, wt], [xw0], name=p + "in%d" % d),
                      helper.make_node("Add", [xw0, wb], [xw])]
            # The first state: zeros [batch, H], the batch being the input's second axis.
            shape, batch, size, h0 = p + "shape%d" % d, p + "batch%d" % d, p + "size%d" % d, p + "h0_%d" % d
            one, units = p + "one%d" % d, p + "units%d" % d
            added += [numpy_helper.from_array(np.array([1], dtype=np.int64), one),
                      numpy_helper.from_array(np.array([hidden], dtype=np.int64), units)]
            nodes += [helper.make_node("Shape", [x], [shape]),
                      helper.make_node("Gather", [shape, one], [batch], axis=0),
                      helper.make_node("Concat", [batch, units], [size], axis=0),
                      helper.make_node("ConstantOfShape", [size], [h0],
                                       value=numpy_helper.from_array(np.array([0.0], dtype=np.float32)))]
            last, y = p + "last%d" % d, p + "y%d" % d
            nodes.append(helper.make_node(
                "Scan", [h0, xw], [last, y], name=p + "scan%d" % d, num_scan_inputs=1,
                body=cell(p + "d%d_" % d, recurrent[d], bias[d][3 * hidden:], hidden),
                scan_input_directions=[d], scan_output_directions=[d]))
            axis, yd = p + "axis%d" % d, p + "yd%d" % d
            added.append(numpy_helper.from_array(np.array([1], dtype=np.int64), axis))
            nodes.append(helper.make_node("Unsqueeze", [y, axis], [yd]))
            ys.append(yd)
        # GRU's Y: [frames, directions, batch, H].
        nodes.append(helper.make_node("Concat", ys, [node.output[0]], axis=1))
    del graph.node[:]
    graph.node.extend(nodes)
    used = {name for node in graph.node for name in node.input}
    kept = [t for t in graph.initializer if t.name in used]
    del graph.initializer[:]
    graph.initializer.extend(kept + added)
    return count


# -- 4. The check ----------------------------------------------------------------------

def test_sound(np):
    """Ten seconds of what a film's sound is made of, from fixed seeds: tones, noise, chirps, clicks."""
    rate = 48000
    rng = np.random.default_rng(11)
    t = np.arange(rate * 10) / rate
    tones = sum(0.08 * np.sin(2 * np.pi * f * t) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.3 * k * t))
                for k, f in enumerate((110.0, 220.0, 277.2, 329.6, 440.0), 1))
    chirp = 0.1 * np.sin(2 * np.pi * (200.0 * t + 300.0 * t * t)) * (np.sin(2 * np.pi * 2.0 * t) > 0)
    noise = 0.04 * rng.standard_normal(len(t))
    clicks = np.zeros(len(t))
    for start in rng.integers(0, len(t) - rate // 4, 30):
        length = rate // 8
        clicks[start:start + length] += 0.3 * rng.standard_normal(length) * np.exp(-np.arange(length) / rate * 30)
    return (tones + chirp + noise + clicks).astype(np.float32)


def stems_of(session, spec, np):
    masks = session.run(None, {"spectrogram": spec[None].astype(np.float32)})[0][0]
    return bandit.apply_masks(spec, masks)


def sdr(reference, estimate, np):
    return float(10 * np.log10(np.sum(reference ** 2) / max(np.sum((reference - estimate) ** 2), 1e-20)))


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--source", required=True, help="bandit-v2-multi.onnx of the release bandit-onnx-2")
    parser.add_argument("--licence", required=True, help="the CC BY-SA 4.0 legal code the lock pins (licence_url)")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    lock = read_lock(LOCK)
    source_lock = read_lock(SOURCE_LOCK)
    # The float file it starts from is the one both locks pin, and the one the app pins.
    if (lock["source_file"], lock["source_sha256"], lock["source_tag"]) != (
            source_lock["file"], source_lock["file_sha256"], source_lock["release_tag"]):
        die("scripts/bandit-int8-onnx.lock does not start from the float file scripts/bandit-onnx.lock pins")
    for key in ("checkpoint", "checkpoint_md5", "checkpoint_sha256", "commit", "licence_url", "licence_sha256"):
        if lock[key] != source_lock[key]:
            die("%s differs between the two Bandit locks" % key)
    if sha256(args.source) != lock["source_sha256"]:
        die("%s is not the float file the lock pins" % args.source)
    if sha256(args.licence) != lock["licence_sha256"]:
        die("the licence is not the CC BY-SA 4.0 legal code the lock pins")

    import numpy as np
    import onnx
    import onnxruntime as ort
    from onnxruntime.quantization import QuantType, quantize_dynamic

    if ort.__version__ != lock["onnxruntime"]:
        die("onnxruntime %s, the lock pins %s: the quantised bytes depend on it" % (ort.__version__, lock["onnxruntime"]))

    os.makedirs(args.out, exist_ok=True)
    work = os.path.join(args.out, "work")
    os.makedirs(work, exist_ok=True)
    folded = os.path.join(work, "folded.onnx")
    written = os.path.join(work, "written-out.onnx")
    path = os.path.join(args.out, lock["file"])

    fold(args.source, folded)
    model = onnx.load(folded)
    count = write_out_grus(model)
    if count != int(lock["grus"]):
        die("%d GRUs written out, the lock expects %s" % (count, lock["grus"]))
    onnx.checker.check_model(model)
    onnx.save(model, written)
    del model
    gc.collect()

    quantize_dynamic(written, path, op_types_to_quantize=["MatMul"], per_channel=True,
                     weight_type=QuantType.QInt8, extra_options={"EnableSubgraph": True})
    quantised = onnx.load(path)
    kinds = {}
    for node in quantised.graph.node:
        kinds[node.op_type] = kinds.get(node.op_type, 0) + 1
        for attribute in node.attribute:
            if attribute.g is not None:
                for inner in attribute.g.node:
                    kinds[inner.op_type] = kinds.get(inner.op_type, 0) + 1
    if kinds.get("MatMul", 0) != 0 or kinds.get("GRU", 0) != 0 or kinds.get("MatMulInteger", 0) == 0:
        die("not every matrix product was quantised: %s" % kinds)
    # ONNX Runtime's saved graph declares every domain it knows: only those its nodes use are kept.
    used = set()
    for node in quantised.graph.node:
        used.add(node.domain)
        for attribute in node.attribute:
            if attribute.g is not None:
                used.update(inner.domain for inner in attribute.g.node)
    kept = [o for o in quantised.opset_import if o.domain in used or o.domain in ("", "ai.onnx")]
    del quantised.opset_import[:]
    quantised.opset_import.extend(kept)
    onnx.checker.check_model(quantised)
    onnx.save(quantised, path)
    del quantised
    notice = notice_of(lock)
    with open(args.licence, encoding="utf-8") as f:
        licence_text = f.read()
    bandit.stamp(path, notice, licence_text, "%s of the release %s (SHA-256 %s), quantised to int8"
                 % (lock["source_file"], lock["source_tag"], lock["source_sha256"]))

    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    report = {"rewrite_max_diff": [], "mask_mean_rel_diff": [], "stem_sdr_db": {}, "matmul_integer": kinds["MatMulInteger"],
              # As export-bandit.py's check.json: which checkpoint the weights are (scripts/pin-stems.py reads it).
              "checkpoint_sha256": lock["checkpoint_sha256"]}
    cases = [(np.random.default_rng(seed).standard_normal((1, 1025, frames, 2)) * 0.05).astype(np.float32)
             for seed, frames in ((0, 64), (1, 333))]
    spec = bandit.stft(test_sound(np))
    reference = ort.InferenceSession(args.source, options, providers=["CPUExecutionProvider"])
    float_masks = [reference.run(None, {"spectrogram": x})[0] for x in cases]
    float_stems = stems_of(reference, spec, np)
    del reference
    gc.collect()

    rewritten = ort.InferenceSession(written, options, providers=["CPUExecutionProvider"])
    for x, want in zip(cases, float_masks):
        worst = float(np.max(np.abs(rewritten.run(None, {"spectrogram": x})[0] - want)))
        report["rewrite_max_diff"].append(float("%.3g" % worst))
        if worst > MAX_REWRITE_DIFF * max(float(np.max(np.abs(want))), 1.0):
            die("the GRUs written out answer differently from the float file, by %g" % worst)
    del rewritten
    gc.collect()

    session = ort.InferenceSession(path, options, providers=["CPUExecutionProvider"])
    for x, want in zip(cases, float_masks):
        got = session.run(None, {"spectrogram": x})[0]
        rel = float(np.mean(np.abs(got - want)) / max(float(np.mean(np.abs(want))), 1e-9))
        report["mask_mean_rel_diff"].append(float("%.3g" % rel))
        if rel > MAX_MASK_MEAN_REL:
            die("the int8 masks differ from the float ones by %.3g on average (at most %g)" % (rel, MAX_MASK_MEAN_REL))
    int8_stems = stems_of(session, spec, np)
    length = len(test_sound(np))
    for k, stem in enumerate(bandit.STEMS):
        a = bandit.istft(float_stems[k], length)
        b = bandit.istft(int8_stems[k], length)
        value = sdr(a, b, np)
        report["stem_sdr_db"][stem] = float("%.1f" % value)
        if value < MIN_STEM_SDR_DB:
            die("the int8 %s stem is %.1f dB from the float one (at least %g)" % (stem, value, MIN_STEM_SDR_DB))
    del session
    gc.collect()
    for name in os.listdir(work):
        os.remove(os.path.join(work, name))
    os.rmdir(work)

    value = sha256(path)
    with open(args.licence, "rb") as src, open(os.path.join(args.out, "LICENSE"), "wb") as dst:
        dst.write(src.read())
    with open(os.path.join(args.out, "NOTICE"), "w", encoding="ascii") as f:
        f.write(notice)
    bandit.write_provenance(os.path.join(args.out, "PROVENANCE"), LOCK, {lock["file"]: value}, report)
    with open(os.path.join(args.out, "SHA256SUMS"), "w", encoding="ascii") as f:
        f.write("%s  %s\n" % (value, os.path.basename(path)))
    with open(os.path.join(args.out, "check.json"), "w", encoding="ascii") as f:
        json.dump(report, f, indent=2)
    print(json.dumps({"file": {os.path.basename(path): value}, "check": report}, indent=2))


if __name__ == "__main__":
    main()
