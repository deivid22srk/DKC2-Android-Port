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
| Target Android | 14 (API 34, compileSdk 35) |
| ROM | DKC2 USA v1.0, 4 MiB headerless, SHA-256 `35421a9a…` |
| NDK | r27 (27.2.12479018) |
| CMake | 3.31.6 (SDK), Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, JDK 21 |

## Repository layout added by the port

```
android/                    Gradle project (app module + wrapper)
android/CMakeLists.txt      native build: runner + generated AOT + SDL2 + recomp-ui
android/app/src/main/java/org/libsdl/app/   SDL2 2.30.9 Java glue (vendored)
android/app/src/main/java/com/deivid22srk/dkc2recomp/
    MainActivity.java       SDL host: SAF picker (fallback), JNI bridge, wiring
    TouchControlsView.java  on-screen SNES controls (virtual gamepad client)
android/app/src/main/java/com/deivid22srk/dkc2recomp/launcher/
    LauncherActivity.kt     home screen (Port Screen Template, Compose):
                            immersive front-end + navigation + game hand-off
    config/PortBrandingConfig.kt   single branding edit point (DKC2 identity)
    data/RomStager.kt       copy + SHA-256 gate mirroring verified_rom.c
    data/GameDataScanner.kt folder scan (exact names → extensions)
    viewmodel/…, settings/…, ui/…   phases, persisted preferences, AAA scene
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
8. **Home screen (launcher).** A Compose front-end adapted from the Port
   Screen Template is the app entry (`launcher.LauncherActivity`); the SDL
   host (`MainActivity`) is no longer the launcher and is started only
   with the ROM already staged. The screen is immersive edge-to-edge,
   adapts to portrait and landscape, and offers file picking (bare
   `*/*` — no `EXTRA_MIME_TYPES`, keeping the grey-out fix), folder
   scanning (`.sfc/.smc/.fig/.swc`), a persisted folder grant, a
   dedicated Settings screen (SharedPreferences, ready to wire into the
   engine) and credits with porter links. `RomStager` validates the pick
   with the exact native gate (header skip, 4 MiB, SHA-256) and swaps
   `rom.sfc` atomically; a staged file re-verified at boot short-circuits
   to the "ready" state without touching SAF.

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
- **Touch controls = player 1.** `android_main.c` pins
  `player_src[0]` to the gamepad source before launching the host: the
  shared default routes player 1 to the keyboard, and a keyboard-source
  player never consumes a gamepad, so the virtual pad silently became
  player 2 and the title screen never saw Start. A Bluetooth pad joins as
  player 2.
- **Orientation is re-locked in native code.** SDL overwrites the
  manifest's `sensorLandscape` with `FULL_USER` when the window is
  created (rotatable in-session, EGL surface churn on rotation);
  `SDL_HINT_ORIENTATIONS` restores the landscape lock.
- **GLES2 present path.** The frame quad is a VBO drawn as two explicit
  triangles that tile along a single diagonal (the earlier
  4-vertex-strip / wrong-pair layouts — (BL,BR,TR)+(BR,TR,TL) — cover only
  75% of the quad: the wedge between the two diagonals stays at the clear
  color, which was the on-device black arrowhead); the raster size is
  answered by `eglQuerySurface` on the real EGLSurface (SDL's window
  logical size on Android is fed by asynchronous JNI events and can latch
  mismatched device/surface pairs, which letterboxed the game off-center);
  frames are skipped while the surface has no authoritative size (picker,
  rotation); any drawable-size change forces a full texture redefine; one
  GL error per site is surfaced via `SDL_Log` (logcat:
  `DKC2 GLES2: GL error ...`).
- **True fullscreen.** The window is born
  `SDL_WINDOW_FULLSCREEN_DESKTOP` (`android_main.c`) and
  `MainActivity` enforces immersive mode via `WindowInsetsController`
  (API 30+) or legacy system-UI flags (API 26-29), re-applied on focus
  gain and resume, with `SHORT_EDGES` cutout mode. The status and
  navigation bars only reappear transiently (swipe) or over system UI
  such as the ROM picker.
- **Game recognition.** The manifest carries `android:appCategory="game"`
  and `android:isGame="true"` plus the optional gamepad feature, so
  launchers, OEM game tools (e.g. Moto Game Time) and the Play listing
  treat the app as a game.
- **Audio telemetry.** The obtained audio spec, open failures, the 48 kHz
  fallback, and backgrounding/foreground queue sizes log to logcat
  (`audio: ...`), so field reports can be diagnosed without repro.
- **Signing:** CI release APKs are signed with the project keystore stored
  in the `DKC2_RELEASE_*` Actions secrets (created once at setup). The
  keystore and its password live outside Git; keep a private backup, APK
  updates must reuse the same key.

## Real settings (launcher.cfg)

The home screen's Settings panel exposes only preferences the native host
actually consumes — every control maps 1:1 to a `launcher.cfg` key parsed by
`Dkc2LauncherSettingsLoad` (`runner/desktop_launcher.c`), the same file the
base project's desktop launcher writes. `android_main.c` `chdir`s into the
app's internal files directory before loading, so the Kotlin side
(`LauncherCfg`) writes `filesDir/launcher.cfg` with C-parser parity
(`Key=Value`, no padding; unknown lines are dropped, matching sscanf
behavior). No native code was changed.

Exposed keys, ranges and the consuming site:

| Key | Range | Consumer |
|-----|-------|----------|
| `AspectIndex` | 0 native 4:3 / 1 16:10 / 2 16:9 | `sdl_main.c` → `Dkc2VideoSetAspect` |
| `WidescreenEdge` | 0 reflect / 1 bars / 2 shift / 3 glide | `Dkc2VideoSetEdgePolicy` |
| `ScreenKind` | 0 raw / 1 crt / 2 composite / 3 trinitron | `Dkc2DesktopColorFilterInit` (CPU color-LUT, applied in the shared present loop) |
| `Upscaler` | 0 nearest / 1 bilinear / 2 reconstruct | `Dkc2SdlPresenterSetUpscaler` |
| `TextureFilter` | 0 nearest / 1 bilinear | decides the effective upscaler when `Upscaler != 2` (Android has no `DKC2_UPSCALER` env), so the writer keeps both consistent |
| `ReconstructMode/Strength/Softness/Shading` | 0..4 / 0..100 ×3 | reconstruct upscaler uniforms (labels mirror the desktop overlay) |
| `EnableAudio`, `Volume` | 0..1, 0..100 | audio init gate, `host.audio_volume` |
| `Player1Deadzone`, `Player2Deadzone` | 0..100 | `host.player_deadzone` (virtual pad = P1, Bluetooth pad = P2) |

Deliberately NOT exposed (would be decorative): `AudioFrequency` (the mixer
pins 32040 Hz), `WindowScale`/`Renderer`/`PlayerNSource` (forced or unused on
Android), resolution scale, VSync, FPS limit, frame skip, audio latency,
overlay opacity/visibility and haptics (no native parameter reads them).

The tech chip on the home screen shows engine facts only: `SDL2 · GLES2 ·
<ABI>` — SDL2 2.30.9 static host, the explicit OpenGL ES 2.0 context the
presenter requests (not the device's maximum GLES version), and the runtime
`Build.SUPPORTED_ABIS` value.
