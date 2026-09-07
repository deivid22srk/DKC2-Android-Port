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
#include "desktop_launcher.h"
#include "diagnostics.h"
#include "launcher.h"

#include <errno.h>
#include <stdio.h>
#include <string.h>
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

  Dkc2DiagnosticsInit("android", DKC2_RELEASE_VERSION);

  RecompLauncherCSettings settings;
  Dkc2LauncherSettingsDefault(&settings);
  Dkc2LauncherSettingsLoad(&settings);

  if (!rom_path[0]) {
    (void)Dkc2LauncherReadRomCache(rom_path, sizeof rom_path);
  }
  if (!rom_path[0]) {
    fprintf(stderr,
            "No ROM was provided. Select a game file in the app first.\n");
    return 0;
  }
  (void)Dkc2LauncherWriteRomCache(rom_path);

  int result = Dkc2RunGameHost(rom_path, &settings);
  (void)Dkc2LauncherSettingsSave(&settings);
  return result;
}
