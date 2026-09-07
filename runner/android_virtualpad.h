#ifndef DKC2_ANDROID_VIRTUALPAD_H
#define DKC2_ANDROID_VIRTUALPAD_H

/* android_virtualpad.c — SDL2 virtual gamepad backing the Android on-screen
 * touch controls. The virtual device uses the SDL_GameController standard
 * layout order, so the gameplay host sees it exactly like a Bluetooth or USB
 * gamepad and no input path changes are required. */

/* Logical button indexes, matching desktop_input.c's kStandardPad* order:
 * 0 A, 1 B, 2 X, 3 Y, 4 Back, 5 Guide, 6 Start, 7 LeftStick, 8 RightStick,
 * 9 LeftShoulder, 10 RightShoulder, 11 DpadUp, 12 DpadDown, 13 DpadLeft,
 * 14 DpadRight. */
enum {
  kDkc2VirtualPadA = 0,
  kDkc2VirtualPadB = 1,
  kDkc2VirtualPadX = 2,
  kDkc2VirtualPadY = 3,
  kDkc2VirtualPadBack = 4,
  kDkc2VirtualPadGuide = 5,
  kDkc2VirtualPadStart = 6,
  kDkc2VirtualPadLeftStick = 7,
  kDkc2VirtualPadRightStick = 8,
  kDkc2VirtualPadLeftShoulder = 9,
  kDkc2VirtualPadRightShoulder = 10,
  kDkc2VirtualPadDpadUp = 11,
  kDkc2VirtualPadDpadDown = 12,
  kDkc2VirtualPadDpadLeft = 13,
  kDkc2VirtualPadDpadRight = 14,
};

/* Must be called after SDL_Init(SDL_INIT_GAMECONTROLLER) so the virtual
 * device is discovered by the normal hotplug/scan path. Idempotent. */
void Dkc2AndroidVirtualPadInit(void);

/* Release the virtual device before SDL_Quit destroys it; resets internal
 * state so Init can attach again in a later host run. */
void Dkc2AndroidVirtualPadQuit(void);

/* Device index of the attached virtual pad, or -1 when absent. The host
 * input scan uses it to open the pad first. */
int Dkc2AndroidVirtualPadDeviceIndex(void);

/* Set one logical button's pressed state. Ignored before Init. */
void Dkc2AndroidVirtualPadButton(int button, int pressed);

/* Left stick axes in [-1, 1]; optional, defaults to 0. */
void Dkc2AndroidVirtualPadLeftStick(float x, float y);

#endif /* DKC2_ANDROID_VIRTUALPAD_H */
