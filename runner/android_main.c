/* android_main.c — Android host entry for DKC2Recomp.
 *
 * SDL2's Android glue (org.libsdl.app.SDLActivity) loads this shared library
 * and invokes the exported SDL_main symbol on its own thread. The desktop SDL
 * host (sdl_main.c) owns the gameplay loop; this entry adapts the platform
 * differences only:
 *   - no executable-relative anchor: user data (settings, rom.cfg, saves)
 *     lives in the app's internal storage directory, reached with chdir;
 *   - the ROM path arrives from Java (SAF picker copy) as argv[1];
 *   - the ImGui pre-boot launcher is not used on Android.
 */
#ifndef SDL_MAIN_HANDLED
#define SDL_MAIN_HANDLED
#endif

#include <SDL.h>

#include "android_virtualpad.h"
#include "desktop_input.h"
#include "desktop_launcher.h"
#include "diagnostics.h"
#include "launcher.h"

#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#ifndef DKC2_RELEASE_VERSION
#define DKC2_RELEASE_VERSION "dev"
#endif

/* Implemented in sdl_main.c; the shared gameplay loop for SDL hosts. */
extern int Dkc2RunGameHost(const char *rom_path,
                           RecompLauncherCSettings *settings);

enum {
  kPathCapacity = 4096,
};

int SDL_main(int argc, char *argv[]) {
  SDL_SetMainReady();

  if (argc > 2) {
    fprintf(stderr, "Usage: DKC2Recomp [ROM.sfc]\n");
    return 2;
  }

  char rom_path[kPathCapacity] = {0};
  if (argc == 2 && argv[1] && argv[1][0]) {
    (void)snprintf(rom_path, sizeof rom_path, "%s", argv[1]);
  }

  /* User data root: internal storage (settings, rom.cfg, saves/).
   * On Android SDL resolves the path through JNI once the activity context
   * is attached, which is guaranteed inside SDL_main. */
  const char *user_dir = SDL_AndroidGetInternalStoragePath();
  if (user_dir && *user_dir) {
    if (chdir(user_dir) != 0)
      fprintf(stderr, "warning: chdir(%s) failed: %s\n", user_dir,
              strerror(errno));
  } else {
    fprintf(stderr, "warning: internal storage path unavailable\n");
  }

  /* EVENTS+TIMER only: the first-run wait loop below must observe SDL quit
   * requests (activity teardown) before RunGame initializes the remaining
   * subsystems. RunGame's own SDL_Init call adds them. */
  if (SDL_Init(SDL_INIT_EVENTS | SDL_INIT_TIMER) != 0) {
    fprintf(stderr, "warning: SDL_Init(events/timer): %s\n", SDL_GetError());
  }

  Dkc2DiagnosticsInit("android", DKC2_RELEASE_VERSION);

  RecompLauncherCSettings settings;
  Dkc2LauncherSettingsDefault(&settings);
  Dkc2LauncherSettingsLoad(&settings);
  /* The window must be born fullscreen on Android. With the flag set,
   * SDLActivity applies its own immersive window style (and arms its
   * re-hide listener); MainActivity additionally enforces modern
   * WindowInsetsController immersive mode. Without it the surface keeps
   * the 1439x678 system-bar-inset geometry and SDL's window size can
   * latch inconsistent pairs during inset animations. There is no ImGui
   * launcher on Android to write launcher.cfg, so the loaded default
   * (0) must be overridden here. */
  settings.fullscreen = 1;
  /* Phones have no hardware keyboard, and the shared default routes
   * player 1 to it (desktop convention). A keyboard-source player never
   * consumes a gamepad, so the virtual pad landed on player 2 and the
   * title screen — which polls joypad 1 — waited for Start forever.
   * Player 1 must read the (virtual) gamepad; player 2 keeps the gamepad
   * slot so a Bluetooth pad can still join as P2. */
  settings.player_src[0] = kDkc2InputSourceGamepad;

  if (!rom_path[0]) {
    (void)Dkc2LauncherReadRomCache(rom_path, sizeof rom_path);
  }
  if (!rom_path[0]) {
    /* First run: SDLActivity finishes the activity as soon as SDL_main
     * returns, so returning here would close the app before the SAF picker
     * can be used. Stay alive and wait for the picker's copy into internal
     * storage instead; a quit request (activity recreate/teardown) ends the
     * wait, and the replacement run receives the path through argv. */
    const char *dir = user_dir && *user_dir ? user_dir : ".";
    char candidate[kPathCapacity];
    for (;;) {
      if (SDL_QuitRequested()) return 0;
      (void)snprintf(candidate, sizeof candidate, "%s/rom.sfc", dir);
      struct stat st;
      if (stat(candidate, &st) == 0 && st.st_size > 0) {
        (void)snprintf(rom_path, sizeof rom_path, "%s", candidate);
        break;
      }
      SDL_Delay(250);
    }
  }
  (void)Dkc2LauncherWriteRomCache(rom_path);

  int result = Dkc2RunGameHost(rom_path, &settings);
  (void)Dkc2LauncherSettingsSave(&settings);

  if (result != 0) {
    /* Self-healing: park the rejected file (wrong SHA-256, truncated copy,
     * ...) so the next launch reopens the picker instead of failing on the
     * same bad ROM forever. */
    char rejected[kPathCapacity];
    (void)snprintf(rejected, sizeof rejected, "%s.rejected", rom_path);
    if (rename(rom_path, rejected) == 0) {
      fprintf(stderr, "DKC2Recomp: ROM parked as %s\n", rejected);
    } else {
      fprintf(stderr, "warning: could not park rejected ROM: %s\n",
              strerror(errno));
    }
  }
  return result;
}
