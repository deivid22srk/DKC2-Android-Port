/* android_virtualpad.c — SDL2 virtual gamepad for the Android touch overlay.
 *
 * The gameplay host (desktop_input.c) reads an abstract Dkc2GamepadState
 * sourced from the SDL_GameController standard layout. Backing the on-screen
 * controls with an SDL2 virtual gamepad therefore reuses the exact same code
 * path as a physical Bluetooth/USB controller, including hotplug handling,
 * deadzones, and the launcher's keybind-independent binding table.
 */
#include "android_virtualpad.h"

#include <SDL.h>

#include <jni.h>

#include <math.h>
#include <stdio.h>
#include <string.h>

static SDL_Joystick *s_virtual_joystick;
static int s_device_index = -1;
static float s_left_x;
static float s_left_y;

void Dkc2AndroidVirtualPadInit(void) {
  if (s_virtual_joystick) return;
  if (!SDL_WasInit(SDL_INIT_GAMECONTROLLER)) return;

  SDL_VirtualJoystickDesc desc;
  SDL_zero(desc);
  desc.version = SDL_VIRTUAL_JOYSTICK_DESC_VERSION;
  desc.type = SDL_JOYSTICK_TYPE_GAMECONTROLLER;
  desc.naxes = 6; /* left stick, right stick, two triggers */
  desc.nbuttons = 16;
  desc.button_mask = 0xFFFFu;
  desc.axis_mask = 0x3Fu;
  /* SDL2's descriptor stores a borrowed name pointer (SDL3 uses a char
   * array); the string must therefore outlive the attach call. */
  static const char kVirtualPadName[] = "DKC2 Touch Pad";
  desc.name = kVirtualPadName;

  int device_index = SDL_JoystickAttachVirtualEx(&desc);
  if (device_index < 0) {
    SDL_Log("virtual gamepad attach failed: %s", SDL_GetError());
    return;
  }
  s_device_index = device_index;
  s_virtual_joystick = SDL_JoystickOpen(device_index);
  if (!s_virtual_joystick) {
    SDL_Log("virtual gamepad open failed: %s", SDL_GetError());
    s_device_index = -1;
    return;
  }
  /* Neutral axes so the standard deadzone logic sees a resting pad. */
  for (int axis = 0; axis < 6; ++axis) {
    SDL_JoystickSetVirtualAxis(s_virtual_joystick, axis, 0);
  }
  SDL_Log("virtual gamepad attached at device index %d", device_index);
}

void Dkc2AndroidVirtualPadButton(int button, int pressed) {
  if (!s_virtual_joystick || button < 0 || button > 15) return;
  SDL_JoystickSetVirtualButton(s_virtual_joystick, button,
                               pressed ? 1 : 0);
}

void Dkc2AndroidVirtualPadQuit(void) {
  /* SDL_Quit destroys every joystick handle. Drop our reference and reset
   * the statics so a later host run in this process re-attaches instead of
   * writing through a dangling pointer. */
  if (s_virtual_joystick) {
    SDL_JoystickClose(s_virtual_joystick);
  }
  s_virtual_joystick = NULL;
  s_device_index = -1;
}

void Dkc2AndroidVirtualPadLeftStick(float x, float y) {
  if (!s_virtual_joystick) return;
  s_left_x = x < -1.0f ? -1.0f : (x > 1.0f ? 1.0f : x);
  s_left_y = y < -1.0f ? -1.0f : (y > 1.0f ? 1.0f : y);
  SDL_JoystickSetVirtualAxis(s_virtual_joystick, 0,
                             (Sint16)lroundf(s_left_x * 32767.0f));
  SDL_JoystickSetVirtualAxis(s_virtual_joystick, 1,
                             (Sint16)lroundf(-s_left_y * 32767.0f));
}

/* JNI bridge: org.libsdl.app loads libdkc2.so, so these symbols resolve once
 * the SDLActivity stack has run its library bootstrap. */
JNIEXPORT void JNICALL
Java_com_deivid22srk_dkc2recomp_MainActivity_nativeVirtualPadButton(
    JNIEnv *env, jclass clazz, jint button, jboolean pressed) {
  (void)env;
  (void)clazz;
  Dkc2AndroidVirtualPadButton((int)button, pressed == JNI_TRUE ? 1 : 0);
}
