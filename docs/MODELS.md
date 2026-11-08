# The AI models: a record

Every AI model FFmpeg GUI can use, what it is for, who made it, under which
licence, where its files come from, what the app checks them against, and
what is known of the data it was trained on. `scripts/check-model-pins.py`
checks before every release (`release.yml`) that this record holds every hash
the app pins and every licence it names, so the record and the app cannot
drift apart.

> **Not legal advice.** This page records what the project does and why, as
> carefully as it can, from the licences and the pages of each model as they
> were when the model was added (linked below). It is not a legal opinion. If
> what you make with the app matters legally (a commercial product, a client's
> work), read the licences yourself, and ask a lawyer where it counts.

## How the app gets a model

- **None is in the app.** The APK holds no model weights (only Parakeet's
  token list, a small text file: see below). FFmpeg GUI has no internet
  permission at all: it cannot download anything.
- **The user's browser downloads it**, from the page this record names, when
  the user taps the link in Settings, Smart features.
- **The app imports it and checks it**: every file against the SHA-256 pinned
  in the app's code (listed below), before it is ever loaded; a file that does
  not match is refused. "Check again" in the settings re-reads every file.
- **Two kinds of file.** Most are published by their makers or by a known
  converter (rembg, sherpa-onnx, Xenova, Carve): the app only points to them.
  Four are converted to ONNX **by this project** and served from its own
  releases, because their makers publish them for PyTorch or TensorFlow only,
  or in a form a phone cannot run: EdgeTAM, BiRefNet-lite, Bandit v2 and
  Spleeter's five stems. For those the project is a distributor, and keeps
  every condition of their licences (below).
  Each release's page (written by `scripts/model-release-notes.py`) gives
  the model's makers and licence (for Bandit v2, the CC BY-SA attribution and
  the changes), links every file it holds, the commit and the lock it was
  made from, its SHA-256, the paper and the sources.

## At a glance

| Feature | Model | Made by | Licence of the model | ONNX file by | Served from |
|---|---|---|---|---|---|
| Subtitles from speech | Parakeet TDT 0.6B v3 | NVIDIA | CC BY 4.0 | sherpa-onnx (int8); pantinor (SmoothQuant) | Hugging Face, GitHub |
| Cut-out, Fast | U2-NetP | Xuebin Qin et al. | Apache 2.0 | rembg (MIT) | rembg's GitHub releases |
| Cut-out, Precise | IS-Net (general use) | Xuebin Qin et al. (DIS) | Apache 2.0 | rembg (MIT) | rembg's GitHub releases |
| Cut-out, Finest | BiRefNet-lite (general use) | Zheng Peng et al. | MIT | **this project** | **this repository's releases** |
| Cut-out correction, lift the subject | EdgeTAM | Meta | Apache 2.0 | **this project** | **this repository's releases** |
| Magic eraser, quick | MI-GAN | Picsart AI Research | MIT | its author | Hugging Face |
| Magic eraser, precise | LaMa (big-lama) | Samsung AI Center | Apache 2.0 | Carve | Hugging Face |
| Stems: dialogue, music, effects (the voice levels, the dub, the tracks) | Bandit v2 (multilingual) | Karn N. Watcharasupat, Chih-Wei Wu, Iroro Orife | CC BY-SA 4.0 (weights), Apache 2.0 (code) | **this project** | **this repository's releases** |
| Stems: the music's instruments | Spleeter 5 stems | Deezer | MIT | **this project** | **this repository's releases** |
| AI dubbing | Kokoro-82M v1.0 | hexgrad | Apache 2.0 | sherpa-onnx (Apache 2.0) | sherpa-onnx's GitHub releases |
| AI dubbing, extra voices | Piper voices | their authors | each its own | Piper / sherpa-onnx | Hugging Face, GitHub |
| Subtitle translation | Opus-MT | University of Helsinki | CC BY 4.0 | Xenova | Hugging Face |
| **AI marks** (voices), before any other model | AudioSeal | Meta | MIT | **this project** | **this repository's releases** |
| **AI marks** (pictures), before any other model | TrustMark Q | Adobe | MIT | Adobe | **this repository's releases** (Adobe's files, unchanged) |

## The project's own conversions

### EdgeTAM: our ONNX files

EdgeTAM (["EdgeTAM: On-Device Track Anything Model"](https://arxiv.org/abs/2501.07256),
Meta, CVPR 2025) is a Segment Anything 2 made for phones: given points on a
picture marked "this is part of it" or "this is not", it finds the whole thing
they point at. The app uses it to correct a cut-out with brushes, in the
magic eraser's and the HDR look's magic brushes, and in the photo editor's
long press that lifts a subject off the picture as a sticker. It replaced
SlimSAM (see "Retired models").

- **Licence.** "The EdgeTAM model checkpoints and code are licensed under
  Apache 2.0" (the repository's README); its `LICENSE` is the Apache License
  2.0, SHA-256 `c71d239df91726fc519c6eb72d318ec65820627232b2f796219e87dcf35d0ab4`.
- **Made from**, all pinned in `scripts/edgetam-onnx.lock`: the repository
  https://github.com/facebookresearch/EdgeTAM at commit
  `7711e012a30a2402c4eaab637bdb00a521302c91`, its checkpoint
  `checkpoints/edgetam.pt` (SHA-256
  `ed2d4850b8792c239689b043c47046ec239b6e808a3d9b6ae676c803fd8780df`) and its
  config `configs/edgetam.yaml`.
- **What the conversion does** (`scripts/export-edgetam.py`): it loads Meta's
  checkpoint strictly (every weight from it; the ImageNet weights the backbone
  would fetch through timm are not used), cuts the model in two where SAM 2's
  own image predictor cuts it (the image encoder; the prompt encoder with the
  mask decoder) and writes both as ONNX, opset 17. No weight is trained,
  fine-tuned, pruned or quantised. Each run checks the files against the
  PyTorch model, on random inputs and on real photos, and fails rather than
  write files that answer differently.
- **Reproducible.** Constant folding is off and every tool is pinned by hash
  (`scripts/model-export-requirements.txt`): two runs give the same bytes. The
  workflow that publishes them builds them twice, on two runner images, and
  publishes only byte-identical results.
- **The files**, pinned in `app/.../cutout/RefineModel.kt`:

  | File | SHA-256 | Bytes |
  |---|---|---|
  | `edgetam-image-encoder.onnx` | `692c9365d13fbbd8fd5b32bdd0c7bd0377696bb0b20fa7d97f8a3a3c1e776f0d` | 20,164,359 |
  | `edgetam-mask-decoder.onnx` | `df71726f03f1eb11ab0fda4648ad024569fbbe26ddd69e2a16354432158035a2` | 16,574,577 |

  Served as the pre-release
  [`edgetam-onnx-1`](https://github.com/marcomorosi06/FFmpegGUI/releases/tag/edgetam-onnx-1)
  of this repository, made by `.github/workflows/models-onnx.yml`.
- **How the Apache License 2.0 is kept** (its section 4, for a Derivative
  Work):
  - (a) *a copy of the licence* goes with the files: `LICENSE` in the
    release, inside each ONNX file (its metadata, key `license`), and in the
    app (`assets/licenses/edgetam/LICENSE.txt`, readable in About, Licences);
  - (b) *prominent notices that the files were changed*: `NOTICE` in the
    release says what the conversion changed; the same text is the ONNX
    files' `doc_string` and their `notice` metadata, and is in the app
    (`assets/licenses/edgetam/NOTICE.txt`);
  - (c) *the copyright notices are kept*: "Copyright (c) Meta Platforms, Inc.
    and affiliates", as the source files carry it, is in the NOTICE;
  - (d) *the upstream NOTICE file*: the EdgeTAM repository has none;
  - section 6, *trademarks*: Meta's and EdgeTAM's names are used only to say
    where the files come from; the NOTICE says Meta has not reviewed or
    endorsed them;
  - sections 7 and 8: the files come with no warranty, as the licence says.
- **Training data.** From the paper: the image encoder was trained on SA-1B
  ("we train on the SA-1B dataset for 2 epochs"); the video stages on SA-V, a
  10% sample of SA-1B, DAVIS, MOSE and YouTube-VOS, distilling from SAM 2.
  [SA-1B](https://ai.meta.com/datasets/segment-anything/) is published by Meta
  under its own data licence, for research; SA-V under CC BY 4.0 (the SAM 2
  paper). Those terms are for whoever uses the datasets: this project uses
  none of them, only the checkpoint, under the licence Meta gave it.

### BiRefNet-lite: our ONNX file

BiRefNet (["Bilateral Reference for High-Resolution Dichotomous Image
Segmentation"](https://arxiv.org/abs/2401.03407), Zheng Peng et al., CAAI AIR
2024), the "lite" model of its author's general-use weights (Swin-T
backbone): it finds the subject of a picture with finer edges than IS-Net
(hair, fur, thin parts come out whole) and keeps to the main subject. It is
the cut-out's "Finest" choice.

- **Licence.** MIT ("Copyright (c) 2024 ZhengPeng"); its `LICENSE`, SHA-256
  `92a7089e0915fc32bc40067560b398f1e6a7a5958abd7d04eda393629a5acefb`.
- **Made from**, all pinned in `scripts/birefnet-onnx.lock`: the repository
  https://github.com/ZhengPeng7/BiRefNet at commit
  `f7079632d681ecf12161fcea8905682934e59e51`, and the author's weights
  `BiRefNet-general-bb_swin_v1_tiny-epoch_232.pth` from the repository's
  release v1 (SHA-256
  `6a1e050c6ec2697e5ed268455df544782b023acf8643ab771250979094875ab1`).
- **Why our own file.** The author also publishes an ONNX of these weights
  (release v1, opset 17, SHA-256
  `5600024376f572a557870a5eb0afb1e5961636bef4e1e22132025467d0f03333`), but
  it writes the decoder's deformable convolutions as gathers, which need more
  than 5.7 GB of memory at 1024 x 1024: no phone runs it. Ours writes them as
  ONNX's own `DeformConv` (opset 19), which ONNX Runtime 1.28 (the app's) runs
  natively: the same convolution, about 2.1 GB at its peak.
- **What the conversion does** (`scripts/export-birefnet.py`): it picks the
  lite backbone in a copy of the author's `config.py`, loads the weights
  strictly, keeps the last (finest) prediction as the output and writes ONNX,
  opset 19. No weight is trained, fine-tuned, pruned or quantised. Each run
  checks the file against the PyTorch model (random inputs and real photos),
  and can check it against the author's own ONNX too.
- **The file**, pinned in `app/.../cutout/CutoutModel.kt` (`FINEST`):

  | File | SHA-256 | Bytes |
  |---|---|---|
  | `birefnet-lite-general.onnx` | `8b75d639b11e984fc7d3a6c0818a2dd2f62df0b19997e4e822099e72f8d9b774` | 180,240,681 |

  Served as the pre-release
  [`birefnet-lite-onnx-1`](https://github.com/marcomorosi06/FFmpegGUI/releases/tag/birefnet-lite-onnx-1)
  of this repository, made by `.github/workflows/models-onnx.yml`, built twice
  to the same bytes as EdgeTAM's.
- **How the MIT License is kept**: "the above copyright notice and this
  permission notice shall be included in all copies or substantial portions"
  of it: the `LICENSE` is in the release, inside the ONNX file (metadata
  `license`) and in the app (`assets/licenses/birefnet/LICENSE.txt`); a
  `NOTICE` says what the conversion did (MIT does not ask for it; it is said
  all the same), also inside the file and in the app.
- **Training data.** The author lists, for these weights: DIS5K-TR, DIS-TEs,
  DUTS-TR_TE, HRSOD-TR_TE, UHRSD-TR_TE, HRS10K-TR_TE, TR-P3M-10k,
  TE-P3M-500-NP, TE-P3M-500-P and TR-humans (the repository's README, "model
  zoo"). These datasets come with terms of their own (DIS5K's, for one, asks
  commercial users to sign separate terms). They bind whoever uses the
  datasets: this project uses none of them, only the weights, under the
  licence their author gave them.

### Bandit v2: our ONNX file

Bandit v2 (["Remastering Divide and Remaster: A Cinematic Audio Source
Separation Dataset with Multilingual Support"](https://arxiv.org/abs/2407.07275),
Karn N. Watcharasupat, Chih-Wei Wu and Iroro Orife, IEEE IS2 2024) splits
any sound into its dialogue, its music and its effects. The app uses it to
set the voice apart (the voice levels, the dub keeping or lowering the
original voice) and to lay each part on the video as a track of its own.
It replaced Spleeter's two stems (see "Retired models").

- **Licences.** The weights: Creative Commons Attribution-ShareAlike 4.0
  International (CC BY-SA 4.0), as Zenodo record
  [12701995](https://zenodo.org/records/12701995) gives them; its legal code
  as SPDX keeps it (`CC-BY-SA-4.0.txt`, SPDX license list v3.27.0), SHA-256
  `cde7883b9050a1104f4ac19a1572aafd6e5d7323b68351aaf51fbf4beba54966`. The
  code: Apache 2.0, its `LICENSE` SHA-256
  `c71d239df91726fc519c6eb72d318ec65820627232b2f796219e87dcf35d0ab4`. CC
  BY-SA allows commercial use; it asks for the attribution, a link to the
  licence, a note of the changes, and that an adaptation goes out under the
  same licence: the ONNX file is an adaptation and goes out under CC BY-SA
  4.0 too. It reaches only the model's file, never the sound a user makes
  with it, nor FFmpeg GUI's own code.
- **Made from**, all pinned in `scripts/bandit-onnx.lock`: the repository
  https://github.com/kwatcharasupat/bandit-v2 at commit
  `d5563d9031e95fdaa3e5a73d5020b9a0df61adb6` and the multilingual
  checkpoint `checkpoint-multi.ckpt` (MD5 `fea2868787551b0cff36cfcf7c3622a3`, SHA-256
  `abcfccf65446752a057f4a302c941479a54b7560ebf8d7bca039d2ea98e64cfc`), the
  MD5 as Zenodo gives it, the SHA-256 learned by the first build
  (`scripts/pin-stems.py`).
- **Why our own file.** The authors publish a PyTorch Lightning checkpoint
  only, and their model builds its masks as complex tensors, which ONNX does
  not have.
- **What the conversion does** (`scripts/export-bandit.py`): it builds the
  authors' model as `configs/models/bandit-mus64.yaml` does (48 kHz),
  loads the checkpoint strictly, and writes the network from the
  spectrogram to the three complex masks with real numbers only: the same
  layers, in the same order, with the same weights, ONNX opset 17. The
  short-time Fourier transforms stay out of the file: the app does them
  (`core/stems/StemSeparation.kt`, `BanditMasks.kt`), as the script's
  `stft()` and `istft()`, which each build checks against the authors' own
  model end to end. No weight is trained, fine-tuned, pruned or quantised.
  The model is mono: the app runs it on the middle of the two channels and
  lays its masks on each channel, so the stems stay stereo.
- **The file**, pinned in `app/.../voice/StemModels.kt` (`BANDIT`):

  | File | SHA-256 | Bytes |
  |---|---|---|
  | `bandit-v2-multi.onnx` | `7836f01622021adc2fe2570f263ec365ef7677d598b51d27b015b94d869f2ff2` | 149,293,980 |

  Zeros until its first build: `.github/workflows/models-onnx.yml` (without
  publishing) builds it twice, the two must be byte-identical, then
  `scripts/pin-stems.py bandit <the build's folder>` writes the pins here,
  in the lock, in the app and in the bundled NOTICE; the next run publishes
  the pre-release
  [`bandit-onnx-2`](https://github.com/marcomorosi06/FFmpegGUI/releases/tag/bandit-onnx-2).
  The first file, [`bandit-onnx-1`](https://github.com/marcomorosi06/FFmpegGUI/releases/tag/bandit-onnx-1)
  (SHA-256 `1601a8dc45ddea3b0d3d16378c9e0d42ba7e2b6cef3bd832d5f8e93808a65fd4`,
  150,610,260 bytes), put the masks together band by band, with the
  authors' float64 frequency weights: some 23,000 nodes, 12 s to load on a
  computer, masks as doubles. bandit-onnx-2 is the same weights with one
  scatter-add per stem and float32 throughout: under 9,000 nodes, about 3
  times quicker to load, some 20% quicker to run.
  The app retires the first (see "Retired models").
  Until then the app says the model is on its way and imports nothing.
- **How CC BY-SA 4.0 is kept**: the attribution (the authors, the title of
  the work, the record's address), the licence's legal code and the note of
  the changes are in the release (`LICENSE.txt`, `NOTICE.txt`), inside the
  ONNX file (metadata `license`, `notice`) and in the app
  (`assets/licenses/bandit/`, shown in About, Licences); no technical measure
  restricts the file.
- **Training data.** Divide and Remaster v3, the authors' dataset: synthetic
  mixtures of speech in more than 30 languages, music and effects, which the
  paper says it drew only from content that permits commercial use (for the
  music, the Free Music Archive's public-domain and CC BY, CC BY-SA, Open
  Audio and Free Art tracks). This project uses none of it, only the weights.

### Spleeter 5 stems: our ONNX file

Spleeter (["Spleeter: a fast and efficient music source separation tool with
pre-trained models"](https://doi.org/10.21105/joss.02154), Romain Hennequin
et al., Deezer Research, JOSS 2020), its five-stem model: singing, piano,
drums, bass and the rest. The app runs it on the music Bandit finds, to lay
each instrument on the video as a track of its own.

- **Licence.** MIT ("Copyright (c) 2019-present, Deezer SA"); its `LICENSE`,
  SHA-256 `b15e40c921ec6fe997e0d88ddab9c26fb915bebbbb9496530d3f98f317d4325a`.
- **Made from**, all pinned in `scripts/spleeter-onnx.lock`: the repository
  https://github.com/deezer/spleeter at commit
  `6f2c8a2e283914faa544631430f65b793f1b1218`, Deezer's TensorFlow
  checkpoints `5stems.tar.gz` and (to check the conversion) `2stems.tar.gz`
  of release v1.4.0, and sherpa-onnx's ONNX of the two-stem model, as the
  lock pins them.
- **Why our own file.** Deezer publishes TensorFlow checkpoints only, and
  sherpa-onnx converted the two-stem model alone.
- **What the conversion does** (`scripts/export-spleeter.py`): it reads the
  checkpoints' weights without TensorFlow (the tensor bundle format) into
  the same five U-Nets written in PyTorch (ELU, softmax across the
  instruments, as Spleeter's `configs/5stems`) and writes them as one ONNX
  file, opset 17. The same code must give, on the two-stem model, what
  sherpa-onnx's files give; then the five-stem file is checked against
  PyTorch. No weight is trained, changed or quantised.
- **The file**, pinned in `app/.../voice/StemModels.kt` (`INSTRUMENTS_FILE`):

  | File | SHA-256 | Bytes |
  |---|---|---|
  | `spleeter-5stems.onnx` | `533f0f260e6bfd14f55027594ba443e9d5c1cd8a1507a67000fa312d5b2374cf` | 196,647,290 |

  Served as the pre-release
  [`spleeter-onnx-1`](https://github.com/marcomorosi06/FFmpegGUI/releases/tag/spleeter-onnx-1)
  of this repository, made by `.github/workflows/models-onnx.yml`, built twice
  to the same bytes.
- **How the MIT License is kept**: the `LICENSE` is in the release, inside the
  file (metadata `license`) and in the app (`assets/licenses/spleeter/`), with
  a `NOTICE` of the conversion.
- **Training data.** Deezer's own, as its paper says (not published). This
  project uses only the weights, under the licence Deezer gave them.

### The AI marks: AudioSeal and TrustMark

What the app's models make carries a mark that cannot be seen or heard (EU
AI Act, Regulation (EU) 2024/1689, Article 50(2); see docs/ARCHITECTURE.md,
"Marked as made with AI"). These four files write and find it. They are
imported before any other model (`MarkingGate`); without them no model stays
on the phone (`MarkingSweep`). The marks carry nothing but "made with AI":
their payloads are constants, the same for everyone (`core/marking/Marks.kt`).
That they carry no fingerprint is the design, not a certainty: see
docs/ARCHITECTURE.md, "That they carry nothing is not certain".

- **AudioSeal** (["Proactive Detection of Voice Cloning with Localized
  Watermarking"](https://arxiv.org/abs/2401.17264), Meta, ICML 2024), which
  marks voices. **Licence:** MIT, "We have updated our license to full MIT
  license (including the license for the model weights)" (its README); its
  `LICENSE`, SHA-256 `da6d3703ed11cbe42bd212c725957c98da23cbff1998c05fa4b3d976d1a58e93`.
  **Made from**, pinned in `scripts/ai-markers.lock`: the package
  `audioseal==0.2.1` (the network's code) and the weights
  `generator_base.pth`, SHA-256 `7a845b5fbe9364a63a3909d8ab3fe064d13a76ae4c2e983573e08c69b7b51748`, and
  `detector_base.pth`, SHA-256 `8a78e8a83584113523e161fc599fcab10fd0e94c04d2eb9d2fa1e9ec91ab69d9`
  (https://huggingface.co/facebook/audioseal). **What the conversion does**
  (`scripts/export-ai-markers.py`): the generator as `get_watermark` with its
  message as an input, the detector as `forward`, both written as ONNX
  (opset 17) for a fixed window of 5 s at 16 kHz; nothing trained, fine-tuned,
  pruned or quantised; checked against PyTorch, and checked to hold on speech
  through MP3, AAC, noise and volume and not to be found where it is not.
  **Training data.** "Our watermark generator and detector are trained on a
  4.5K hours subset from the VoxPopuli (Wang et al., 2021) dataset" (the
  paper): speech of the European Parliament, published by Meta for research.
- **TrustMark Q** (["TrustMark: Robust Watermarking and Watermark Removal for
  Arbitrary Resolution Images"](https://arxiv.org/abs/2311.18297), T. Bui,
  S. Agarwal, J. Collomosse, Adobe and the University of Surrey, ICCV 2025),
  which marks pictures. **Licence:** MIT, "The MIT license covers both the
  code in this repo and the model files which are downloaded upon first use"
  (its README); its `LICENSE`, SHA-256
  `58d47a5cdfaaceda98925d845e8207d90336c62347a2383f95962b667b1cdaf5`. Adobe
  confirmed that the files may be redistributed unchanged, with this
  attribution: https://github.com/adobe/trustmark/issues/48. **Made from**:
  Adobe's own ONNX files, unchanged byte for byte, renamed only:
  `encoder_Q.onnx`, SHA-256 `19b3d1b25836130ffd78775a8f61539f993375d1823ef0e59ba5b8dffb4f892d`, and
  `decoder_Q.onnx`, SHA-256 `ee3268f057c9dabef680e169302f5973d0589feea86189ed229a896cc3aa88df`
  (https://cai-watermark.adobe.net/watermarking/trustmark-models/, the
  location in Adobe's own code), Adobe's reader checked with TrustMark at commit `f0df43ed760bf3305f98f495f990b8c2315e7983`.
  **Training data.** "We follow the same settings of [7] to train our models
  on 101K images from the MIRFlickR 1M dataset" (the paper).
- **The files**, pinned in `app/.../marking/MarkerModel.kt`:

  | File | Made from | SHA-256 | Bytes |
  |---|---|---|---|
  | `audioseal-generator.onnx` | AudioSeal, converted | `ecb230f2d38de821f72f04eda9c40b82f102e058263848f892be4f6e189dd0e7` | 58,960,229 |
  | `audioseal-detector.onnx` | AudioSeal, converted | `a389f9a5e3fa67a6a3a13480c3799875a5f3251d2d542fada6bd93b5c2bb53fa` | 34,754,353 |
  | `trustmark-encoder-q.onnx` | Adobe's `encoder_Q.onnx` | `19b3d1b25836130ffd78775a8f61539f993375d1823ef0e59ba5b8dffb4f892d` | 17,312,208 |
  | `trustmark-decoder-q.onnx` | Adobe's `decoder_Q.onnx` | `ee3268f057c9dabef680e169302f5973d0589feea86189ed229a896cc3aa88df` | 47,401,222 |

  A value of zeros is not pinned yet: `.github/workflows/ai-markers.yml`
  learns it on its first run (building and checking everything, publishing
  nothing), `scripts/pin-ai-markers.py` copies what it learned here, into the
  lock and into the app; the next run publishes the pre-release
  [`ai-markers-1`](https://github.com/marcomorosi06/FFmpegGUI/releases/tag/ai-markers-1).
  Until then nothing can be imported (no file matches a pin of zeros), and
  `scripts/check-model-pins.py` refuses an app release.
- **How the MIT licences are kept**: each `LICENSE` with its copyright
  notice goes with the files (`LICENSE-audioseal.txt`,
  `LICENSE-trustmark.txt` in the release; inside the AudioSeal ONNX files,
  metadata key `license`) and is in the app
  (`assets/licenses/ai-markers/`, readable in About, Licences), with the
  NOTICE of what was converted and what is unchanged.

## Files published by others, which the app points to

The app only links to these and checks them; it does not host or change them.

### Parakeet TDT 0.6B v3 (subtitles from speech)

By NVIDIA, CC BY 4.0 (https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3).
CC BY asks for credit: the app gives it in About, Licences, and in Smart
features. Its token list ships with the app
(`assets/licenses/parakeet/ATTRIBUTION.txt`, with the licence).

- **int8**, as converted by sherpa-onnx (csukuangfj, Hugging Face, or the
  sherpa-onnx `asr-models` release archive):
  `encoder.int8.onnx` `acfc2b4456377e15d04f0243af540b7fe7c992f8d898d751cf134c3a55fd2247`,
  `decoder.int8.onnx` `179e50c43d1a9de79c8a24149a2f9bac6eb5981823f2a2ed88d655b24248db4e`,
  the archive `5793d0fd397c5778d2cf2126994d58e9d56b1be7c04d13c7a15bb1b4eafb16bf`.
- **SmoothQuant**, by pantinor on Hugging Face, the same licence:
  `encoder.int8.onnx` `1db13b37849829eecce166bf2e1c7d4d334ceed9e3b9b95c515188dd870301ae`,
  `decoder.int8.onnx` `0018457edb28af7715cfd86ae47a6d67775492d7d32de58a41e7aa09305121e1`.
- Both: `joiner.int8.onnx` `3164c13fc2821009440d20fcb5fdc78bff28b4db2f8d0f0b329101719c0948b3`,
  `tokens.txt` `d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d`
  (in the app), and the speed test's recordings, from the archive:
  `en.wav` `148b936b43ce7c546a866e64da059f0458aee2d65e617f16e9d94f06e8d99ed6`,
  `de.wav` `36d3c4845b9808a1656a2a2e92d884590e2db94389e6fe559643291ae0cd3710`,
  `fr.wav` `b59be4349b92d344fb903677165eaf4694025d1ab119c608726ecbcb3164b528`,
  `es.wav` `49fd2cfa4b62db7068143c582b35de9d31ec2733495ece3611105131d21de06c`.

### IS-Net and U2-NetP (subject cut-out, Precise and Fast)

By Xuebin Qin et al. (https://github.com/xuebinqin/DIS,
https://github.com/xuebinqin/U-2-Net), as converted to ONNX by rembg
(Daniel Gatis, MIT, https://github.com/danielgatis/rembg) and served from
rembg's GitHub releases. Both repositories are under Apache 2.0; DIS's page
does not name a separate licence for its weights, which are taken here, as
rembg takes them, as covered by the repository's.

- `isnet-general-use.onnx` `60920e99c45464f2ba57bee2ad08c919a52bbf852739e96947fbb4358c0d964a` (178,648,008 bytes).
- `u2netp.onnx` `309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8` (4,574,861 bytes).
- Trained on DIS5K (IS-Net) and DUTS-TR (U2-Net), datasets with their own
  terms (see BiRefNet above for DIS5K's).

### MI-GAN and LaMa (magic eraser)

- **MI-GAN**, by Picsart AI Research, code and weights MIT
  (https://github.com/Picsart-AI-Research/MI-GAN); its ONNX pipeline is its
  author's, on Hugging Face (andraniksargsyan/migan):
  `migan_pipeline_v2.onnx` `6f1f3530a1a2324b19752018ce756088b07973cda8d7d890034ace5c8a48c40b`.
- **LaMa** ("big-lama"), by Samsung AI Center, Apache 2.0
  (https://github.com/advimman/lama), as converted to ONNX by Carve
  (huggingface.co/Carve/LaMa-ONNX). Not published with a hash: the app takes
  its SHA-256 when it is imported and shows it, and checks it against that
  afterwards.
- Both learnt from Places2, a set of photos published for research.

### Kokoro-82M (AI dubbing)

By hexgrad, Apache 2.0 (https://huggingface.co/hexgrad/Kokoro-82M), in the
multi-language build of sherpa-onnx (Apache 2.0), with eSpeak NG's data
(GPL 3.0 or later) and jieba's dictionaries (MIT): the archive
`kokoro-multi-lang-v1_0.tar.bz2`
`c5f7e2d2caf082bc1d20fb70334a61d99d20b484500aad32e7cf84c128ea3298`, holding
`model.onnx` `b40f62b166ac8164b0627ef48a0b358eda0985e272fb03ef5252e7206305da11`,
`voices.bin` `1c5a5b983d3d50d8586d437a51f3faa2da7919ce76a013c081e65671a3447c29`,
`tokens.txt` `6ebb6bb288f20f3ae8d004d3c2ca27697da27c037d75e81a60e2a6a663f95425`,
`lexicon-us-en.txt` `7daaab53a181be9885b853a8582bf1838186317e5dadacbcef9c426d6fa0da14`,
`lexicon-gb-en.txt` `c4cbb37316f62210dff52718a7afcaae24f50c032cc75ab47ae67b831d1049e7`,
`lexicon-zh.txt` `902bc2d20ac7c449c5ecbbbb23c89d12119c03ed5b8ed27f114f0260c9e35229`
and eSpeak NG's `phontab` `886f3fa402cb0ba73d483aa8ad000af47a6b7cc06293c75a97913fba68a530f6`.
Licences in the app: `assets/licenses/kokoro/`, `assets/licenses/sherpa-onnx/espeak-ng/`.

### Piper voices (AI dubbing, voices the user imports)

Each voice is under the licence its model card states; the app reads the card
and, for a voice not free for every use (not for commercial use, research
only, or not stated), says so before every dub with it, and keeps the
responsibility with whoever publishes the video. Piper itself is MIT
(`assets/licenses/piper/LICENSE.txt`). Their hashes are taken at import.

### Opus-MT (subtitle translation)

By the University of Helsinki (Jorg Tiedemann et al.), CC BY 4.0
(https://github.com/Helsinki-NLP/Opus-MT), as converted to ONNX by Xenova
on Hugging Face. Any language pair can be added, so their hashes are taken at
import and checked again on request. CC BY asks for credit: About, Licences.

## Retired models

Models the app used before. When a newer version starts and finds one still
on the phone, it deletes it (it would never be loaded again) and says so once,
with what took its place and how much space came back
(`app/.../cutout/RetiredModels.kt`, `RetiredModelsDialog.kt`).

| Model | Licence | Was used for | Folder deleted | Replaced by |
|---|---|---|---|---|
| SlimSAM-77 (Zigeng Chen et al., ONNX by Xenova) | Apache 2.0 | cut-out correction | `files/refine/slimsam-77` | EdgeTAM |
| Bandit v2's first file, `bandit-onnx-1` (see above) | CC BY-SA 4.0 | stems | `files/stems/bandit-v2` | Bandit v2, `bandit-onnx-2` |
| Spleeter 2stems (Deezer, MIT; int8 ONNX by sherpa-onnx, Apache 2.0: `sherpa-onnx-spleeter-2stems-int8.tar.bz2` `c5dc8edfa08cc9a6d82670fcca15cfe671e446385e1a28c9da8c1816d238ea17`, `vocals.int8.onnx` `6e9721864c0c1a6d86eaae92de71637b2daeb3df769c9c536053b46be8dd10aa`, `accompaniment.int8.onnx` `53a96a0d1520b45b8f3cc53262e3725defb686fc3a17490932f050721f4583f1`) | MIT | voice separation | `files/voice/spleeter-2stems-int8` | Bandit v2, and Spleeter 5 stems for the instruments |

The voices Spleeter's two stems set apart stay (they are results, in
`files/voice-stems`): projects keep using them; splitting such a file into
tracks makes it again with Bandit.

## Considered and left out

- **RMBG-1.4 and RMBG-2.0** (BRIA AI): very good cut-outs, but their weights
  are for non-commercial use only (RMBG-2.0 is BiRefNet trained on BRIA's own
  data). FFmpeg GUI's users may well work commercially: left out.
- **ViTMatte and other matting models** trained on Adobe's Composition-1k:
  that dataset's terms are for research only, and the weights' own licence is
  unclear. Left out until both are clear. BiRefNet's own matting weights are
  MIT, but work at 2048 x 2048, beyond a phone's memory.
- **SAM 2 / SAM 2.1** (Meta, Apache 2.0): far heavier than EdgeTAM for the
  same job on a phone.
- **The author's ONNX of BiRefNet-lite**: the same licence, but more than
  5.7 GB of memory (see above).

## Adding or changing a model

1. Read its licence and its model card, and those of its ONNX file if someone
   else converted it. Note what it was trained on. A licence that forbids
   commercial use, or none at all, keeps a model out unless the app warns
   before every use (as for Piper voices).
2. Pin every file by SHA-256 (and size where it is known) in its Kotlin
   object, and its licence in the object (`LICENCE`, `licence`).
3. Record it here: what for, who, licence, source, every hash, training data.
4. Credit it where its licence asks: the README's Credits, About, Licences
   (`settings/AboutScreen.kt`), `assets/licenses/README.txt` ("Not part of the
   APK"), and its licence text in `assets/licenses/` when the app shows it.
5. If the project converts it: a lock (`scripts/*-onnx.lock`), an export
   script that writes `LICENSE`, `NOTICE` and `PROVENANCE` next to the files
   and the licence and notice inside them, the workflow
   (`models-onnx.yml`), and the bundled notice
   (`assets/licenses/<model>/NOTICE.txt`, the same text).
6. If it replaces a model, add the old one to `RetiredModels.ALL` (never take
   one away) and to "Retired models" above.
7. Run `scripts/check-model-pins.py`: it fails until 2 to 6 agree.

## Making the project's files again

```
python3.13 -m venv venv
venv/bin/pip install --require-hashes --no-deps -r scripts/model-export-requirements.txt
git clone https://github.com/facebookresearch/EdgeTAM && git -C EdgeTAM checkout 7711e012a30a2402c4eaab637bdb00a521302c91
venv/bin/python -I scripts/export-edgetam.py --edgetam EdgeTAM --out out-edgetam --pictures EdgeTAM/notebooks/images/truck.jpg
git clone https://github.com/ZhengPeng7/BiRefNet && git -C BiRefNet checkout f7079632d681ecf12161fcea8905682934e59e51
curl -LO https://github.com/ZhengPeng7/BiRefNet/releases/download/v1/BiRefNet-general-bb_swin_v1_tiny-epoch_232.pth
venv/bin/python -I scripts/export-birefnet.py --birefnet BiRefNet --weights BiRefNet-general-bb_swin_v1_tiny-epoch_232.pth --out out-birefnet
python3 scripts/check-model-pins.py --release-dir edgetam=out-edgetam --release-dir birefnet=out-birefnet
# The stems (the upstream files as the locks and models-onnx.yml say):
git clone https://github.com/kwatcharasupat/bandit-v2 && git -C bandit-v2 checkout d5563d9031e95fdaa3e5a73d5020b9a0df61adb6
venv/bin/python -I scripts/export-bandit.py --bandit bandit-v2 --checkpoint checkpoint-multi.ckpt --licence CC-BY-SA-4.0.txt --out out-bandit
venv/bin/python -I scripts/export-spleeter.py --checkpoint 5stems --two-stems 2stems --sherpa sherpa-onnx-spleeter-2stems --licence spleeter/LICENSE --out out-spleeter
python3 scripts/check-model-pins.py --release-dir bandit=out-bandit --release-dir spleeter=out-spleeter
# The AI marks (needs espeak-ng and ffmpeg; the upstream files as the lock and ai-markers.yml say):
venv/bin/python -I scripts/export-ai-markers.py --generator generator_base.pth --detector detector_base.pth \
  --encoder encoder_Q.onnx --decoder decoder_Q.onnx --audioseal-licence LICENSE-audioseal --trustmark trustmark \
  --pictures EdgeTAM/notebooks/images/truck.jpg --out out-markers
python3 scripts/check-model-pins.py --release-dir markers=out-markers
```

The last line says whether your files are the very ones the app pins.
