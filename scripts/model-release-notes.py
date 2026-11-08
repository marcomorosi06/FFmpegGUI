#!/usr/bin/env python3
"""
Writes the page (Markdown) of a release of .github/workflows/models-onnx.yml:
what the model is, who made it and under which licence, a table of the files
with a link to each, what they were made from (the lock at the commit, the
workflow, the run), their SHA-256, the paper and the sources.

Usage:
  scripts/model-release-notes.py MODEL --commit SHA --sums SHA256SUMS
      [--assets NAMES_FILE] [--run URL] [--repo OWNER/NAME] [--server URL]

MODEL is edgetam, birefnet, bandit or spleeter. --assets lists the names the
release holds, one a line: a file beside the model is linked by the name it
has there (edgetam-onnx-1 keeps LICENSE, NOTICE, ... without ".txt"). The
page goes to standard output. Nothing here is legal advice, and the page says so.
"""

import argparse
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

MODELS = {
    "edgetam": {
        "intro": "**EdgeTAM** by Meta, a Segment Anything 2 made for phones, under the "
                 "[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0), converted to ONNX here. "
                 "From points marked \"part of it\" or \"not\", it finds the whole thing they point at: "
                 "[FFmpeg GUI]({repo}) uses it to correct a cut-out with brushes and for its \"lift the subject\" gesture.",
        "files": [
            ("edgetam-image-encoder.onnx", "The image encoder, converted: what the model sees in the picture, once per picture"),
            ("edgetam-mask-decoder.onnx", "The mask decoder, converted: the mask from the points, at every touch"),
        ],
        "licence": "The Apache License 2.0, as upstream",
        "notice": "What the conversion does (Apache License 2.0, section 4(b))",
        "papers": "Paper: [EdgeTAM: On-Device Track Anything Model](https://arxiv.org/abs/2501.07256) (Chong Zhou et al., Meta, CVPR 2025). "
                  "Source: [facebookresearch/EdgeTAM](https://github.com/facebookresearch/EdgeTAM).",
    },
    "birefnet": {
        "intro": "**BiRefNet-lite** (general use) by Zheng Peng et al., under the "
                 "[MIT License](https://opensource.org/license/mit), converted to ONNX here. "
                 "It finds the subject of a picture with fine edges, hair included: "
                 "[FFmpeg GUI]({repo}) uses it for its finest subject cut-out.",
        "files": [
            ("birefnet-lite-general.onnx", "The model, converted: from a picture, its subject's mask"),
        ],
        "licence": "The MIT License, as upstream",
        "notice": "What the conversion does",
        "papers": "Paper: [Bilateral Reference for High-Resolution Dichotomous Image Segmentation](https://arxiv.org/abs/2401.03407) "
                  "(Zheng Peng et al., CAAI AIR 2024). Source: [ZhengPeng7/BiRefNet](https://github.com/ZhengPeng7/BiRefNet).",
    },
    "bandit": {
        "intro": "**Bandit v2** (multilingual) by Karn N. Watcharasupat, Chih-Wei Wu and Iroro Orife, converted to ONNX here. "
                 "It splits any sound into its **dialogue**, its **music** and its **effects**: "
                 "[FFmpeg GUI]({repo}) uses it to set a voice apart (its level, the dub) and to lay each part on the video "
                 "as a track of its own.\n\n"
                 "> **Licence.** The weights are under [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) "
                 "([Zenodo record 12701995](https://zenodo.org/records/12701995)), the authors' code under the "
                 "Apache License 2.0. This ONNX file is an adaptation of those weights, and is licensed under CC BY-SA 4.0 too. "
                 "Attribution: \"Bandit v2, multilingual checkpoint\" by Karn N. Watcharasupat, Chih-Wei Wu and Iroro Orife, "
                 "from the record above, licensed CC BY-SA 4.0. Changed: converted to ONNX, with nothing trained or changed "
                 "in its weights (see NOTICE.txt).",
        "files": [
            ("bandit-v2-multi.onnx", "The network, converted: from a sound's spectrogram, the masks of dialogue, music and effects"),
        ],
        "licence": "The CC BY-SA 4.0 legal code (the weights' licence, and this file's)",
        "notice": "The attribution and what the conversion changes",
        "papers": "Paper: [Remastering Divide and Remaster: A Cinematic Audio Source Separation Dataset with Multilingual Support]"
                  "(https://arxiv.org/abs/2407.07275) (Karn N. Watcharasupat, Chih-Wei Wu, Iroro Orife, IEEE IS2 2024). "
                  "Sources: [kwatcharasupat/bandit-v2](https://github.com/kwatcharasupat/bandit-v2), "
                  "[the weights](https://zenodo.org/records/12701995).",
    },
    "spleeter": {
        "intro": "**Spleeter 5 stems** by Deezer, under the [MIT License](https://opensource.org/license/mit), converted to ONNX here. "
                 "It splits music into **singing**, **piano**, **drums**, **bass** and **the rest**: "
                 "[FFmpeg GUI]({repo}) runs it on the music Bandit v2 finds, to lay each instrument on the video as a track of its own.",
        "files": [
            ("spleeter-5stems.onnx", "Its five U-Nets in one file, converted from Deezer's TensorFlow checkpoint"),
        ],
        "licence": "The MIT License, as upstream",
        "notice": "What the conversion does",
        "papers": "Paper: [Spleeter: a fast and efficient music source separation tool with pre-trained models]"
                  "(https://doi.org/10.21105/joss.02154) (Romain Hennequin et al., Deezer Research, JOSS 2020). "
                  "Source: [deezer/spleeter](https://github.com/deezer/spleeter).",
    },
}

# The name a file beside the model had before the side files went out as .txt.
FORMER = {
    "LICENSE.txt": "LICENSE",
    "NOTICE.txt": "NOTICE",
    "PROVENANCE.txt": "PROVENANCE",
    "PROVENANCE-second-build.txt": "PROVENANCE.second-build",
    "SHA256SUMS.txt": "SHA256SUMS",
}


def read_lock(path):
    values = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#"):
                key, _, value = line.partition(" ")
                values[key] = value.strip()
    return values


def page(model, commit, sums, assets, run, repo_url):
    spec = MODELS[model]
    lock_path = "scripts/%s-onnx.lock" % model
    tag = read_lock(os.path.join(ROOT, lock_path))["release_tag"]
    at = "%s/blob/%s" % (repo_url, commit)
    dl = "%s/releases/download/%s" % (repo_url, tag)

    def link(name, text=None):
        held = name
        if assets is not None and name not in assets and FORMER.get(name) in assets:
            held = FORMER[name]
        return "[%s](%s/%s)" % (text or held, dl, held)

    rows = [(link(name), what) for name, what in spec["files"]]
    rows += [
        (link("LICENSE.txt"), spec["licence"]),
        (link("NOTICE.txt"), spec["notice"]),
        (link("PROVENANCE.txt") + ", " + link("PROVENANCE-second-build.txt", "second build"), "Everything the files were made from, by each build"),
        (link("check.json"), "How the files compare with the authors' model, as each build checked them"),
        (link("SHA256SUMS.txt"), "The files' SHA-256, also below"),
    ]
    out = [spec["intro"].format(repo=repo_url), ""]
    out += ["| File | What it is |", "|---|---|"]
    out += ["| %s | %s |" % row for row in rows]
    out += [
        "",
        "Made from what [`%s`](%s/%s) pins (the upstream code, weights and licence, every hash checked) at "
        "[%s](%s/commit/%s), by [the workflow](%s/.github/workflows/models-onnx.yml)%s, twice "
        "(ubuntu-24.04, and ubuntu-26.04 on two cores), with byte-identical results. Nothing was trained "
        "or changed: the NOTICE says what the conversion does. The licence and the notice are also inside each ONNX file (its metadata)."
        % (lock_path, at, lock_path, commit[:7], repo_url, commit, at, " ([this run](%s))" % run if run else ""),
        "",
        "```",
    ]
    with open(sums, encoding="ascii") as f:
        out += [line for line in f.read().splitlines() if line.strip()]
    out += [
        "```",
        "",
        spec["papers"] + " The app never downloads a model by itself: the user's browser does, and the app checks "
        "the file against the SHA-256 it pins. Every model the app can use: [docs/MODELS.md](%s/docs/MODELS.md). "
        "This is not legal advice." % at,
        "",
    ]
    text = "\n".join(out)
    text.encode("ascii")
    return text


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("model", choices=sorted(MODELS))
    parser.add_argument("--commit", required=True)
    parser.add_argument("--sums", required=True)
    parser.add_argument("--assets")
    parser.add_argument("--run")
    parser.add_argument("--repo", default="marcomorosi06/FFmpegGUI")
    parser.add_argument("--server", default="https://github.com")
    args = parser.parse_args()
    assets = None
    if args.assets:
        with open(args.assets, encoding="utf-8") as f:
            assets = {line.strip() for line in f if line.strip()}
    sys.stdout.write(page(args.model, args.commit, args.sums, assets, args.run, "%s/%s" % (args.server, args.repo)))


if __name__ == "__main__":
    main()
