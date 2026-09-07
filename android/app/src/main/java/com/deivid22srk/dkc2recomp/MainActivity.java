package com.deivid22srk.dkc2recomp;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.libsdl.app.SDLActivity;

/**
 * Android host for the DKC2 recompilation.
 *
 * Responsibilities kept deliberately small:
 *  - load exactly one native library (SDL2 is linked statically into
 *    libdkc2.so, so {@link #getLibraries()} overrides the SDL2 default);
 *  - hand the ROM path chosen through the system file picker to SDL_main
 *    (which skips its ImGui launcher when a ROM argument is present);
 *  - attach the on-screen touch controls, which drive an SDL virtual
 *    gamepad inside the native library.
 */
public class MainActivity extends SDLActivity {

    private static final int ROM_PICK_REQUEST = 4711;
    private static final long MIN_ROM_BYTES = 4_000_000L;

    private File romFile;
    /** Set by getArguments when no ROM exists yet: the native SDL_main is
     * alive waiting for the picker's copy, so finishing the pick must NOT
     * recreate the activity (that would race the waiting native loop). */
    private volatile boolean waitingForRom = false;

    @Override
    protected String[] getLibraries() {
        // SDL2 is statically linked into libdkc2.so; SDLActivity dlopens the
        // last entry to locate the SDL_main entry point.
        return new String[] { "dkc2" };
    }

    @Override
    protected String[] getArguments() {
        if (romFile != null && romFile.isFile() && romFile.length() > 0) {
            return new String[] { romFile.getAbsolutePath() };
        }
        waitingForRom = true;
        return new String[0];
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        romFile = new File(getFilesDir(), "rom.sfc");
        if (!romFile.isFile() || romFile.length() == 0) {
            requestRomPick();
        }
        attachTouchOverlay();
        // SDLActivity.onCreate unconditionally queues setWindowStyle(false)
        // on this looper, which clears the theme's FLAG_FULLSCREEN after our
        // onCreate returns. Posting keeps FIFO order so this runs AFTER that
        // command and wins.
        getWindow().getDecorView().post(this::applyImmersiveMode);
    }

    /**
     * True fullscreen: hide the status bar AND the navigation bar,
     * sticky-immersive (a swipe shows them transiently as an overlay without
     * resizing the game surface). Re-applied on focus gain and resume; the
     * SDL window also carries SDL_WINDOW_FULLSCREEN_DESKTOP (see
     * runner/android_main.c) so the vendored SDLActivity style path and this
     * one agree instead of fighting.
     */
    private void applyImmersiveMode() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= 30 /* Android 11 */) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars()
                        | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController
                                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            // API 26-29: legacy immersive-sticky flags.
            View decor = window.getDecorView();
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.clearFlags(
                    WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        }
        if (Build.VERSION.SDK_INT >= 28 /* Android 9 */) {
            // Draw into the camera-cutout area in landscape (short edges);
            // the game letterboxes inside, the touch overlay stays usable.
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams
                    .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Canonical re-apply point: covers the SAF picker return,
            // Recents, Home and notification shade. Never touch the bars
            // while focus is lost (would fight the system picker UI).
            applyImmersiveMode();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveMode();
    }

    private void attachTouchOverlay() {
        ViewGroup layout = (ViewGroup) SDLActivity.getContentView();
        if (layout == null) return;
        TouchControlsView overlay =
                new TouchControlsView(this, (button, pressed) ->
                        nativeVirtualPadButton(button, pressed),
                        this::requestRomPick);
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        layout.addView(overlay, params);
    }

    private void requestRomPick() {
        // NOTE: deliberately NO EXTRA_MIME_TYPES. When that extra is set it
        // REPLACES the "*/*" filter, and only files whose documents provider
        // reports exactly one of the listed MIME types stay clickable — ROM
        // extensions (.sfc/.smc/.fig) map to device/OEM-dependent MIME types,
        // so on several devices the ROM rendered greyed-out and unselectable.
        // With the bare "*/*" every openable file can be picked; wrong picks
        // are rejected afterwards by the size gate here and the SHA-256 gate
        // in the native runtime (self-healing parked .rejected copy).
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, ROM_PICK_REQUEST);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_file_picker, Toast.LENGTH_LONG)
                    .show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != ROM_PICK_REQUEST) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (romFile == null || !romFile.isFile() || romFile.length() == 0) {
                Toast.makeText(this, R.string.rom_required, Toast.LENGTH_LONG)
                        .show();
            }
            return;
        }
        Uri uri = data.getData();
        File staging = new File(getFilesDir(), "rom.sfc.tmp");
        if (copyRom(uri, staging) && staging.length() >= MIN_ROM_BYTES) {
            if (romFile.exists() && !romFile.delete()) {
                Toast.makeText(this, R.string.rom_replace_failed,
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (!staging.renameTo(romFile)) {
                Toast.makeText(this, R.string.rom_replace_failed,
                        Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, R.string.rom_ready, Toast.LENGTH_SHORT).show();
            if (waitingForRom) {
                // First run: the native SDL_main loop picks the file up and
                // starts the game in this same activity instance.
            } else {
                recreate(); // replace-ROM flow: restart SDL with the new path
            }
        } else {
            staging.delete();
            Toast.makeText(this, R.string.rom_invalid, Toast.LENGTH_LONG)
                    .show();
        }
    }

    private boolean copyRom(Uri uri, File target) {
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(target)) {
            if (in == null) return false;
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return true;
        } catch (IOException | SecurityException e) {
            return false;
        }
    }

    /** JNI: logical button indexes match runner/android_virtualpad.h. */
    public static native void nativeVirtualPadButton(int button,
                                                     boolean pressed);
}
