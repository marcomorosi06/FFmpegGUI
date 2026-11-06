# Security

## Reporting a vulnerability

Please report it privately, by email to **marcomorosi.dev@gmail.com**, with a
subject that starts with `[SECURITY ISSUE]`. Say what you found,
how to see it happen, and the version of the app. Please do not open a public
issue for it.

FFmpeg GUI is made by one person in spare time: you will get an answer as soon
as possible. Once a fix is released, it is named in the release notes, with
your name if you wish.

## Supported versions

FFmpeg GUI is in alpha: only the latest release gets fixes.

## What the app can and cannot reach

- **No network.** The app has no internet permission (the build fails if any
  network permission ever appears in it), so nothing it opens can be sent
  anywhere by it.
- **Only the files you give it.** No storage, media, camera or microphone
  permission: it sees only what you open with the system's picker or share to
  it, and its own files. Files handed over by other apps are taken only as
  those apps' content, never as a path to the app's own files.
- **No shell.** FFmpeg runs inside the app with a list of arguments, never
  through a shell: a command written by hand cannot start other programs.
  FFmpeg's filters that load plugin libraries are not built in.
- **Checked downloads.** The FFmpeg runtime is built from sources pinned by
  commit and checked by hash (see [docs/BUILDING.md](docs/BUILDING.md)); the
  AI models are checked by SHA-256 when they are imported, wherever their
  authors publish one.

## Known issues

The FFmpeg runtime is FFmpegKitNext 6.1.1 (FFmpeg 6.1.6). Some of the
libraries it bundles are older than their latest releases, which fix known
vulnerabilities in the reading of media and font files. Meanwhile, beyond the
limits above, fonts embedded in subtitle files are discarded before subtitles
are burnt in (fonts are picked in the app instead).

The runtime will move to FFmpegKitNext 7.1 before the app leaves alpha.

As with any media app processing complex file formats, always prefer files from sources you trust.
