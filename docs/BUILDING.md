# Building and releasing

How to build FFmpeg GUI, its FFmpeg runtime, and how releases are made. The [README](../README.md) has the short version.

The project uses Gradle with a version catalog (`gradle/libs.versions.toml`): AGP 9.1, Gradle 9.3.1, JDK 17, compileSdk 37. It opens in Android Studio Panda 2 (2025.3.2) or newer.

FFmpeg comes from [FFmpegKitNext](https://github.com/arthenica/ffmpeg-kit-next), which publishes source only. Its AAR is built from the commits pinned in `scripts/ffmpeg-kit-next.lock` by a workflow that builds it twice, on two different runners, and publishes it only when both builds are byte-identical; `scripts/ffmpeg-kit-next-aar.txt` pins that AAR by its SHA-256, and the Gradle build takes no other. You can download it (no extra tools) or build it yourself with [Nix](https://github.com/arthenica/ffmpeg-kit-next/wiki/Nix) and check that you get the same file. See [`docs/ARCHITECTURE.md`](ARCHITECTURE.md), "FFmpeg runtime".

## What you need

- JDK 17 or newer and the Android SDK. Android Studio installs both; on the command line, point `ANDROID_HOME` (or `sdk.dir` in `local.properties`) at the SDK.
- `git` and `bash`.
- Only to build FFmpeg yourself: Nix, on Linux x86_64 (any distribution except NixOS) or macOS, and several GB of free disk space.

## 1. Get the sources

```bash
git clone https://github.com/marcomorosi06/FFmpegGUI
cd FFmpegGUI
./scripts/fetch-sherpa-onnx.sh   # the speech recognition AAR, if app/libs does not have it
```

## 2. Get FFmpeg

```bash
./scripts/fetch-ffmpeg-kit-next.sh
```

It downloads the AAR pinned in `scripts/ffmpeg-kit-next-aar.txt` into `media/libs/` and checks its SHA-256. To build it yourself instead, see "Building FFmpeg yourself" below.

## 3. Build the app

```bash
./gradlew assembleDebug     # APK in app/build/outputs/apk/debug/
./gradlew installDebug      # or install it on a connected phone (it sits next to a release install)
./gradlew :core:test        # backend unit tests, plain JVM
```

`./gradlew assembleRelease` makes an unsigned APK in `app/build/outputs/apk/release/`, to sign with your own key (`apksigner`; see "Releasing"). In Android Studio, open the project folder after step 2 and run the `app` configuration (shared in `.run/`: it opens `MainActivity`, whichever launcher entry the app has enabled).

## Building FFmpeg yourself

Install Nix once. On Fedora 44 or newer, Nix is in the official repositories:

```bash
sudo dnf install nix nix-daemon
sudo systemctl enable --now nix-daemon
```

On other Linux distributions with systemd, and on macOS, use the multi-user installer from [nixos.org/download](https://nixos.org/download/):

```bash
sh <(curl --proto '=https' --tlsv1.2 -L https://nixos.org/nix/install) --daemon
```

Then open a new terminal and check it:

```bash
nix --version
nix-shell -p hello --run hello   # optional: downloads a few packages the first time, then prints "Hello, world!"
```

Nothing else needs configuring: the build script turns on the Nix features it uses (flakes) by itself.

Then build it:

```bash
./scripts/build-ffmpeg-kit-next.sh
```

The first run can take an hour or more. The script:

1. clones FFmpegKitNext and the sources it needs into the work folder and checks that each one is the commit pinned in `scripts/ffmpeg-kit-next.lock`, before anything is compiled;
2. builds FFmpeg and FFmpegKitNext in a Nix shell (toolchain pinned by FFmpegKitNext's `flake.lock`), for arm64-v8a and x86_64;
3. packs the AAR with Gradle, whose distribution must have the SHA-256 in the lock and whose plugins and libraries must match `scripts/ffmpeg-kit-next.verification-metadata.xml`;
4. checks every source again and only then writes `media/libs/ffmpeg-kit-next-6.1.1.aar`, with `ffmpeg-kit-next-6.1.1.provenance.txt` beside it (its SHA-256, the commits and the options used), and says whether it is the pinned AAR.

The work folder is `.ffmpeg-kit-next/` in the project (ignored by git). The AAR records the work folder's path, so to rebuild the published AAR byte for byte use the folder it was built in:

```bash
FFMPEG_KIT_WORKDIR=/var/tmp/ffmpeg-kit-next ./scripts/build-ffmpeg-kit-next.sh
```

An AAR that is not the pinned one is refused by Gradle; for local experiments, build with `-Pffmpeg.unpinned=true` (never for a release).

`scripts/ffmpeg-kit-next.verification-metadata.xml` is kept in the repository. If it is not there yet (or FFmpegKitNext moved to other Gradle plugins), make it once, read it and commit it:

```bash
./scripts/build-ffmpeg-kit-next.sh --write-gradle-verification
```

It records the SHA-256 of everything Gradle downloads at that moment, and from then on Gradle refuses anything else. Run it on each kind of computer you build on (Linux, macOS): entries already in the file are kept, and the Android plugin downloads a different `aapt2` on each.

Later runs reuse the checked sources. `--clean` starts from an empty work folder, `--help` lists the options. To add a library the app does not use by default (for hand-written commands), pass it after `--`, for example `./scripts/build-ffmpeg-kit-next.sh -- --enable-libvpx`: its source is checked against the lock like the others.

## Troubleshooting

| Message | What to do |
|---|---|
| `media/libs/ffmpeg-kit-next-6.1.1.aar is missing` or `Could not find com.arthenica:ffmpeg-kit-next` | Get FFmpeg first (step 2). |
| `No AAR is pinned in scripts/ffmpeg-kit-next-aar.txt yet` | None is published yet: build it yourself, or let the "FFmpeg runtime (AAR)" workflow publish and pin it (see "Releasing"). |
| `The FFmpeg runtime pinned in ... was built from other inputs` | The lock, the build script or the Gradle verification file changed after the AAR was pinned: wait for the "FFmpeg runtime (AAR)" workflow to pin the new one, then tag again. |
| `... is not the pinned FFmpeg runtime` | `media/libs` holds another AAR: run `./scripts/fetch-ffmpeg-kit-next.sh`, or build with `-Pffmpeg.unpinned=true` to use your own (never for a release). |
| `error: Nix is needed` | Install Nix (see "Building FFmpeg yourself"), then open a new terminal. |
| `... the lock says ...: its source changed` | A source online is no longer the pinned commit. Do not edit the lock to get past it: the check exists for this case. Open an issue. |
| `ffmpeg-kit-next.verification-metadata.xml is missing` | Make it once with `--write-gradle-verification`, read it and commit it. |
| `Dependency verification failed` in `build.log` | Gradle downloaded something that differs from `scripts/ffmpeg-kit-next.verification-metadata.xml`. If it is a file of another host (Linux / macOS), run `--write-gradle-verification` there; otherwise do not regenerate the file to get past it. |
| `the build failed` | The details are in `build.log`, in the `ffmpeg-kit-next` folder of the work folder. |
| `refusing to use ... as the work folder` | `FFMPEG_KIT_WORKDIR` points at a folder that is not the script's (the project, your home, a folder with other files). Point it at a new or empty folder, or leave it unset. |
| `Activity class {...LauncherDynamic} does not exist` (Android Studio) | The app keeps one of its two launcher entries enabled, following the colours setting, and Android Studio tried the other. Run the shared `app` configuration (`.run/app.run.xml`), which opens `MainActivity` itself; or in *Run > Edit Configurations*, set *Launch* to *Specified Activity* `com.cuscus.ffmpeggui.MainActivity`. |
| `... everything built from it was deleted` | A source changed during the build. Run the script again: it fetches the pinned commit and builds everything anew. |

The script ignores the variables of your shell (only the ones listed in `KEEP_ENV`, at the top of the script, reach the build), so a variable left over from an experiment cannot change what is built. Each build starts from sources put back exactly as their pinned commits; libraries built earlier are only reused when sources, lock, options and script are all the same.

Once the AAR is in `media/libs`, the work folder can be deleted to free space (it is only needed to build FFmpeg again), and `nix-collect-garbage -d` removes what Nix downloaded.

## Releasing

Releases are reproducible: [IzzyOnDroid](https://izzyondroid.org/docs/reproducibleBuilds/)'s builder rebuilds the APK from the tag and compares it with the published one, byte for byte. The release key never leaves your machine.

1. **Tag.** `git tag v1.0 && git push origin v1.0`. The *Reproducible APK* workflow builds the unsigned APK in IzzyOnDroid's environment (Debian bookworm, OpenJDK 17, the pinned FFmpeg runtime), has IzzyOnDroid's own builder ([rbtlog](https://codeberg.org/IzzyOnDroid/rbtlog)) rebuild it from `.github/izzyondroid/`, and only if both are byte-identical leaves the APK in a **draft** release of `v1.0` (a pre-release for tags such as `v1.0-beta1`). Otherwise there is a diffoscope report and no draft.
2. **Sign and publish, on your machine.** Download `ffmpeg-gui-v1.0-unsigned.apk` from the draft (its SHA-256 is in the draft's notes) and sign it without re-aligning it, so the signed APK is that exact file plus the signature:

   ```bash
   apksigner sign --alignment-preserved true --v1-signing-enabled false \
     --ks ~/keystores/ffmpeggui-release.jks \
     --out ffmpeg-gui-v1.0.apk ffmpeg-gui-v1.0-unsigned.apk
   ```

   Attach `ffmpeg-gui-v1.0.apk` to the draft (the IzzyOnDroid recipe looks for that name), delete the unsigned APK from it and publish the release. Leave `ffmpeg-gui-v1.0-source.tar.xz` in it: it is the complete source of the APK (the app, FFmpegKitNext with every library the FFmpeg runtime is built from, sherpa-onnx with its dependencies), packed by `scripts/package-sources.sh`, which the GPL-3.0 asks to keep available next to the APK. Signing any other way (Android Studio, or `apksigner` without `--alignment-preserved true`) gives an APK IzzyOnDroid cannot reproduce.
3. **Check.** Publishing starts *Verify published release*: rbtlog downloads the published APK and repeats IzzyOnDroid's whole check.

**The FFmpeg runtime follows by itself.** After a push to `master` that changes `scripts/ffmpeg-kit-next.lock`, `scripts/build-ffmpeg-kit-next.sh` or the Gradle verification file, the *FFmpeg runtime (AAR)* workflow builds the AAR twice (it takes hours), publishes it as a pre-release if both builds match, and commits the new pin to `scripts/ffmpeg-kit-next-aar.txt` (`git pull` afterwards). If `master` is protected, it cannot push: its summary gives the three lines to commit. A tag pushed before the pin is refused (the pinned AAR was built from other inputs): delete it, wait for the pin, and tag again.

**IzzyOnDroid.** To have the app verified there, send them `.github/izzyondroid/com.cuscus.ffmpeggui.yml`, once: their server follows new releases by itself.
