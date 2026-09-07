cmake_minimum_required(VERSION 3.22)
project(dkc2recomp_android LANGUAGES C CXX)

set(DKC2_ANDROID_ROOT "${CMAKE_CURRENT_LIST_DIR}/..")
set(DKC2_RELEASE_VERSION "0.0.5-android" CACHE STRING "" FORCE)

# ---- SDL2 (pinned upstream revision, linked statically into libdkc2.so) ----
include(FetchContent)
set(SDL2_DISABLE_INSTALL ON CACHE BOOL "" FORCE)
set(SDL_SHARED OFF CACHE BOOL "" FORCE)
set(SDL_STATIC ON CACHE BOOL "" FORCE)
set(SDL_TEST OFF CACHE BOOL "" FORCE)
FetchContent_Declare(SDL2
    GIT_REPOSITORY https://github.com/libsdl-org/SDL.git
    GIT_TAG release-2.30.9
    GIT_SHALLOW TRUE)
FetchContent_MakeAvailable(SDL2)

# ---- snesrecomp runtime sources (same contract as the desktop hosts) ----
set(SNESRECOMP_ROOT "${DKC2_ANDROID_ROOT}/snesrecomp")
if(NOT EXISTS "${SNESRECOMP_ROOT}/runner/runner.cmake")
    message(FATAL_ERROR
        "snesrecomp is not initialized; run git submodule update --init --recursive")
endif()
include("${SNESRECOMP_ROOT}/runner/runner.cmake")
list(REMOVE_ITEM SNESRECOMP_RUNNER_SOURCES
    "${SNESRECOMP_ROOT}/runner/src/keybinds.c"
    "${SNESRECOMP_ROOT}/runner/src/host_report.c")

# ---- generated private recompilation sources (built from the private ROM) --
set(DKC2_SNESRECOMP_GEN_DIR "${DKC2_ANDROID_ROOT}/generated/snesrecomp" CACHE PATH
    "Directory containing generated snesrecomp C sources")
file(GLOB DKC2_SNESRECOMP_GEN_SOURCES CONFIGURE_DEPENDS
    "${DKC2_SNESRECOMP_GEN_DIR}/*.c")
if(NOT DKC2_SNESRECOMP_GEN_SOURCES)
    message(FATAL_ERROR
        "No generated snesrecomp sources found under "
        "${DKC2_SNESRECOMP_GEN_DIR}; run scripts/generate_snesrecomp.py "
        "with a verified ROM first (see docs/PORT_ANDROID.md)")
endif()

# ---- shared recomp-ui launcher/overlay plumbing (Android-aware: it links
# GLESv2/EGL/log and sets IMGUI_IMPL_OPENGL_ES2 when ANDROID is set) --------
# runner.cmake caches SDL3 as its default backend; this host wires SDL2 the
# same way the upstream desktop targets do (plain variable shadowing).
set(SNESRECOMP_SDL_BACKEND SDL2)
include("${DKC2_ANDROID_ROOT}/recomp-ui/recomp_ui.cmake")

add_library(dkc2 SHARED
    ${SNESRECOMP_RUNNER_SOURCES}
    ${DKC2_SNESRECOMP_GEN_SOURCES}
    "${DKC2_ANDROID_ROOT}/runner/dkc2_game.c"
    "${DKC2_ANDROID_ROOT}/runner/dkc2_video.c"
    "${DKC2_ANDROID_ROOT}/runner/dkc2_hdma.c"
    "${DKC2_ANDROID_ROOT}/runner/diagnostics.c"
    "${DKC2_ANDROID_ROOT}/runner/headless_host.c"
    "${DKC2_ANDROID_ROOT}/runner/verified_rom.c"
    "${DKC2_ANDROID_ROOT}/runner/desktop_filter.c"
    "${DKC2_ANDROID_ROOT}/runner/desktop_fps.c"
    "${DKC2_ANDROID_ROOT}/runner/desktop_input.c"
    "${DKC2_ANDROID_ROOT}/runner/input_recording.c"
    "${DKC2_ANDROID_ROOT}/runner/desktop_launcher.c"
    "${DKC2_ANDROID_ROOT}/runner/desktop_overlay.cpp"
    "${DKC2_ANDROID_ROOT}/runner/desktop_overlay_model.c"
    "${DKC2_ANDROID_ROOT}/runner/desktop_present_sdl.c"
    "${DKC2_ANDROID_ROOT}/runner/desktop_vsync.c"
    "${DKC2_ANDROID_ROOT}/runner/desktop_rewind.c"
    "${DKC2_ANDROID_ROOT}/runner/desktop_viewport.c"
    "${DKC2_ANDROID_ROOT}/runner/desktop_pacer.c"
    "${DKC2_ANDROID_ROOT}/runner/desktop_audio_rate.c"
    # The SDL host's gameplay loop (RunGame) lives in sdl_main.c; its main()
    # stays unused here — Android enters through SDL_main in android_main.c.
    "${DKC2_ANDROID_ROOT}/runner/sdl_main.c"
    "${DKC2_ANDROID_ROOT}/runner/android_main.c"
    "${DKC2_ANDROID_ROOT}/runner/android_virtualpad.c"
    "${SNESRECOMP_ROOT}/runner/src/host_report.c")

target_include_directories(dkc2 PRIVATE
    "${DKC2_ANDROID_ROOT}/runner"
    "${DKC2_ANDROID_ROOT}/recomp"
    ${SNESRECOMP_RUNNER_INCLUDE_DIRS})

target_compile_definitions(dkc2 PRIVATE
    DKC2_RELEASE_VERSION="${DKC2_RELEASE_VERSION}"
    SNESRECOMP_TRACE=0
    SNESRECOMP_REVERSE_DEBUG=0
    SNESRECOMP_EXTERNAL_RAM_ROUTINE_GUARDS=1
    SYSTEM_VOLUME_MIXER_AVAILABLE=0
    SDL_MAIN_HANDLED
    DKC2_REAL_HOST_REPORT=1)

target_link_libraries(dkc2 PRIVATE
    SDL2::SDL2-static
    m
    android
    log
    EGL
    GLESv2
    OpenSLES
    aaudio)

# Console-agnostic launcher/overlay wiring: defines RECOMP_LAUNCHER, compiles
# the vendored ImGui with IMGUI_IMPL_OPENGL_ES2 on Android, and links the
# GLES2/EGL/log libraries the overlay needs.
recomp_target_launcher_ui(dkc2 CONSOLE snes)
