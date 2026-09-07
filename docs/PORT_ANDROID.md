# Android port (DKC2Recomp-Android)

## What this port is

A native Android build of the same static recompilation that ships on
Windows and macOS: the 65816 game program runs as AOT-generated C (3,475
exact variants), the PPU/SPC700/S-DSP/DMA hardware models are the shared
runtime, and the whole stack links into one `libdkc2.so` together with a
statically linked SDL 2.30.9. There is no emulator, no Java emulation
layer, and no game data in the repository — the verified North American
v1.0 ROM stays private and is loaded from device storage at runtime,
exactly like the desktop releases.

## Requirements

| Item | Value |
| --- | --- |
| ABI | arm64-v8a only |
| Minimum Android | 8.0 (API 26) |
| Target Android | 14 (API 34) |
| ROM | DKC2 USA v1.0, 4 MiB headerless, SHA-256 `35421a9a…` |
| NDK | r27 (27.2.12479018) |
| CMake | 3.31.6 (SDK), Gradle 8.9, AGP 8.7.3, JDK 21 |

## Repository layout added by the port

```
android/                    Gradle project (app module + wrapper)
android/CMakeLists.txt      native build: runner + generated AOT + SDL2 + recomp-ui
android/app/src/main/java/org/libsdl/app/   SDL2 2.30.9 Java glue (vendored)
android/app/src/main/java/com/deivid22srk/dkc2recomp/
    MainActivity.java       SAF ROM picker, JNI bridge, library wiring
    TouchControlsView.java  on-screen SNES controls (virtual gamepad client)
runner/android_main.c       SDL_main entry (chdir to app storage, ROM argv)
runner/android_virtualpad.c SDL2 virtual gamepad behind the touch overlay
runner/desktop_present_sdl.c  __ANDROID__ GLES2 branches (see below)
recomp-ui/                  vendored at the pinned revision, plus one
                            Android guard fix in launcher_files.c
```

## Platform adaptations

1. **Video (GLES2).** The desktop presenter requests a GL 2.1 compatibility
   context and draws with fixed-function `glBegin/glEnd`. Android exposes
   GLES2 only, so on `__ANDROID__` the presenter requests an ES2 context and
   draws every frame through shaders: a mandatory base textured-quad program
   (nearest/bilinear still come from the samplers) and an ES 1.00 port of the
   reconstruct upscaler. The PPU emits BGRA bytes; the frame is uploaded as
   `GL_RGBA` and the shaders swizzle to RGBA. Offscreen captures use the
   core framebuffer-object entry points.
2. **Entry point.** `SDLActivity` dlopens the last library returned by
   `getLibraries()` (overridden to `dkc2`) and calls the exported
   `SDL_main`. `android_main.c` chdirs into the app's internal storage
   (the desktop anchor-to-executable policy has no meaning on Android) and
   forwards to the shared `RunGame` loop through a new `Dkc2RunGameHost`
   shim in `sdl_main.c`. The ImGui pre-boot launcher is not used on
   Android; the ROM path arrives as `argv[1]`.
3. **In-game overlay.** The ImGui pause overlay initializes with
   `#version 100` and the vendored ImGui compiles with
   `IMGUI_IMPL_OPENGL_ES2=1` (recomp-ui sets this automatically on
   `ANDROID`, together with `LNG_GLES2=1`).
4. **Input.** Physical Bluetooth/USB gamepads work through SDL's Android
   controller stack with no changes. The touch overlay drives an SDL2
   *virtual gamepad* (`SDL_JoystickAttachVirtualEx`,
   `SDL_JoystickSetVirtualButton`), so touch and physical controllers
   share one input path in `desktop_input.c`. Logical button indexes match
   the SDL standard-layout order already used by the desktop host.
5. **File access.** No storage permissions. On first launch (and via the
   ROM button on the overlay) the app opens the system document picker
   (SAF), copies the selected file into app-internal storage as
   `rom.sfc`, and restarts the activity so the SDL thread boots with the
   path. The runtime still verifies the ROM SHA-256 exactly like the
   desktop builds — unsupported dumps are rejected with the same message.
6. **File dialogs.** recomp-ui's Linux desktop picker spawns
   `zenity`/`kdialog` via `posix_spawnp`, which bionic only provides from
   API 28; the vendored copy guards those paths off on `__ANDROID__` (the
   launcher UI itself is not shown on Android).
7. **Paths.** `posix_spawn`-free builds also avoid the executable-relative
   anchor: settings, `rom.cfg`, and `saves/` land in internal storage
   root (writable, backed up with `allowBackup`).

## Building locally

```sh
git clone --recurse-submodules <this repository>
python3 scripts/generate_snesrecomp.py --rom /private/path/dkc2.sfc   # requires Rust or uses the Python backend
cd android
sdkmanager --install "ndk;27.2.12479018" "cmake;3.31.6"  # and platform/build-tools 34
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

The CMake configure fetches SDL 2.30.9 (pinned, shallow) on first run.

## Continuous integration

`.github/workflows/build.yml` on every push:

1. installs JDK 21, Python, Rust (native analyzer), NDK r27 + CMake 3.31.6;
2. downloads the verified ROM ZIP from the **private** archive repository
   using the `ROM_TOKEN` secret and validates size + SHA-256 **before**
   anything is generated — a wrong dump fails the job with an explicit
   message and nothing ROM-derived is produced;
3. runs `scripts/generate_snesrecomp.py` (the ROM itself is never
   committed, and the generated sources stay out of Git);
4. builds `app-debug.apk` and `app-release.apk` (arm64-v8a) and uploads
   both as workflow artifacts;
5. release signing uses the `DKC2_RELEASE_*` secrets when present and
   falls back to the debug keystore otherwise (installable, re-signable).

## Known limitations

- arm64-v8a only (per project decision); 32-bit devices are unsupported.
- The ImGui pre-boot launcher is bypassed; launcher-only features (aspect
  ratio picker, MSU-1, netplay) remain desktop features.
- Rewind/save-state hotkeys are keyboard-oriented; mobile users have the
  in-game pause overlay once opened from a gamepad Start mapping.
- First-run experience requires the SAF picker; no direct scan of shared
  storage (scoped-storage friendly by design).

## Known behaviors (mobile)

- **Rejected ROM parking:** any non-zero game exit parks the loaded file as
  `rom.sfc.rejected` so the next launch reopens the picker. This includes
  non-ROM failures (e.g. GLES unavailable), so an environmental error costs
  one ROM re-pick; the parked file is overwritten on the next parking.
- **First run:** the native `SDL_main` stays alive while the SAF picker is
  open (screen stays black behind it); picking a file starts the game in
  the same activity. Cancel still leaves the overlay ROM button usable.
- **Replace-ROM flow** (overlay ROM button while playing) recreates the
  activity; SDL2 2.30.9's Java glue may briefly finish the old instance —
  reopening the app resumes with the newly selected ROM.
- **Picker accepts every file type.** `ACTION_OPEN_DOCUMENT` is launched
  with a bare `*/*` type and deliberately without `EXTRA_MIME_TYPES`:
  setting that extra replaces the wildcard filter, and because ROM
  extensions (`.sfc`/`.smc`/`.fig`) map to device/OEM-dependent MIME types,
  ROMs rendered greyed-out (unselectable) on some devices. Any openable
  file can now be picked; wrong picks are still rejected by the ≥4 MB Java
  gate and the SHA-256 gate in the native runtime (self-healing
  `.rejected` parking), so the picker reopens with a clear toast.
- **Signing:** CI release APKs are signed with the project keystore stored
  in the `DKC2_RELEASE_*` Actions secrets (created once at setup). The
  keystore and its password live outside Git; keep a private backup, APK
  updates must reuse the same key.
