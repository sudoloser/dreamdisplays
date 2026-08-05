# Native

Rust native kernels.

## Contents

- `dreamdisplays_native` (`src/`) — low-level helpers for Kotlin media code: pixel-format `convert` and the `session`
  bridge.
- `dreamdisplays_lav` (`lav/src/`) — optional in-process video decode path through `FFmpeg` / `libav`:
  `session`, `surface`, and a rolling packet `cache`.
- C ABI declarations consumed from Kotlin through `Project Panama`.

## Boundaries

- Kotlin orchestration stays in `media:player` and `platform:client:common`
- No Minecraft, `Fabric`, `NeoForge`, or `Paper` code belongs here
- Native code must expose a small stable C ABI and keep ownership / lifetime rules explicit

## Android

Each Android ABI (`android-arm` / armeabi-v7a, `android-aarch64` / arm64-v8a, `android-x86`,
`android-x64` / x86_64) is cross-compiled in CI from an `ubuntu-latest` runner using the pinned
Android NDK (`r27c`). The bundle for those platforms contains:

- `libdreamdisplays_native.so` — the full native pipeline kernel, no FFmpeg link dependency
- `libdreamdisplays_lav.so` — the in-process libav decode backend, linked against the bundled
  FFmpeg shared libraries
- `libavutil.so`, `libswresample.so`, `libswscale.so`, `libavcodec.so`, `libavformat.so` — FFmpeg
  8.1 cross-compiled for the matching ABI (FFmpeg installs these without version suffixes on
  Android, matching the DT_NEEDED entries of `dreamdisplays_lav`; the `ffmpeg-shared.txt` manifest
  lists them for the runtime extractor). 32-bit x86 disables asm because its text relocations are
  rejected by modern Android; the other ABIs keep SIMD enabled.
- `libsqlitejdbc.so` — a prebuilt `sqlite-jdbc` native for the matching ABI (pulled from the last
  `sqlite-jdbc` release that still shipped Android binaries, `3.51.3.0`), since `sqlite-jdbc` 3.5x
  dropped its own `Linux-Android` natives
- `licenses/` — the LGPL metadata copied from the FFmpeg source tree

FFmpeg is cross-compiled with the NDK toolchain in CI (the desktop jobs fetch prebuilt BtbN
builds instead); `ffmpeg-sys-next` links it through `FFMPEG_DIR` and `bindgen` is pointed at the
Android target + sysroot via `BINDGEN_EXTRA_CLANG_ARGS`. Because the whole native stack still
needs the Java foreign-function API, it only activates on runtimes that provide it (Java 21+);
`NativeMedia` falls back to the JVM pipeline otherwise.

## Build

```sh
./gradlew :native:buildHostNatives   # cargo build --release -> native/target/release
./gradlew :native:testHostNatives    # cargo test
```

The auto-build needs a Rust toolchain (`cargo` on `PATH`, or `~/.cargo/bin/cargo`). Machines without Rust — or CI using
the `native/build/ci-bundle/` artifacts — skip it automatically; force-disable with
`-Pdreamdisplays.autoBuildNatives=false`.

> [WARNING]
> `cargo test` builds a separate debug test binary — it does **not** refresh the release
> `.dylib` / `.so` / `.dll` the game loads. The client build uses the release build, so just run the
> client to verify a change in-game.
