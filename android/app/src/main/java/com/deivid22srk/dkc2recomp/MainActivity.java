package com.deivid22srk.dkc2recomp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
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
