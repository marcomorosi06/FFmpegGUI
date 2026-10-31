Magic eraser models (not in the APK)
-------------------------------------------------------------------------
The magic eraser fills in what is painted over a picture with one of two
models the user downloads and imports. Neither is in the APK; the app runs
them with ONNX Runtime (onnxruntime/), with its own code for the crop, the
mask and the blend.

MI-GAN, by Picsart AI Research (ICCV 2023)
  https://github.com/Picsart-AI-Research/MI-GAN
  Code and weights: MIT License, Copyright (c) 2024 Picsart AI Research (PAIR).
  ONNX pipeline: migan_pipeline_v2.onnx, by its author on Hugging Face
  (andraniksargsyan/migan), MIT; pinned by its SHA-256.

LaMa (big-lama), by Samsung AI Center (WACV 2022)
  https://github.com/advimman/lama
  Code and weights: Apache License 2.0.
  ONNX: lama_fp32.onnx, by Carve on Hugging Face (Carve/LaMa-ONNX),
  Apache 2.0. Published without a checksum: its SHA-256 is taken at import
  and shown.

Both models learnt from Places2, a set of photos published for research and
education. Their code and weights are under the licences above, which allow
commercial use; whether what a model learnt from such a set is free of it is
not settled law. Pictures made with the eraser are the user's to publish.

MIT License (MI-GAN)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
