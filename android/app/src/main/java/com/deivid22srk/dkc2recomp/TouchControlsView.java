package com.deivid22srk.dkc2recomp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

/**
 * On-screen SNES controls for the DKC2 Android host.
 *
 * The view draws a translucent D-pad (8-way), the four face buttons in the
 * SNES arrangement (B jump bottom-left of the cluster, Y run/grab center,
 * A team throw, X top), the L/R shoulders, Start/Select, and a small button
 * to re-open the ROM picker. Multi-touch maps every active pointer to its
 * control and routes presses to the native SDL virtual gamepad, so the
 * gameplay host sees identical input from touch and physical gamepads.
 */
public class TouchControlsView extends View {

    /** Logical button indexes: mirror runner/android_virtualpad.h. */
    private static final int PAD_A = 0;
    private static final int PAD_B = 1;
    private static final int PAD_X = 2;
    private static final int PAD_Y = 3;
    private static final int PAD_BACK = 4;
    private static final int PAD_START = 6;
    private static final int PAD_L = 9;
    private static final int PAD_R = 10;
    private static final int PAD_UP = 11;
    private static final int PAD_DOWN = 12;
    private static final int PAD_LEFT = 13;
    private static final int PAD_RIGHT = 14;

    /** Listener receives (logicalButtonIndex, pressed). */
    interface ButtonListener {
        void onButton(int button, boolean pressed);
    }

    private static final String TAG = "TouchControls";

    private final ButtonListener listener;
    private final Runnable romPickerAction;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pressedFill = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float[] dpad = new float[2];
    private float dpadRadius;
    private final float[] face = new float[2];
    private float faceRadius;
    private final float[] btnA = new float[2];
    private final float[] btnB = new float[2];
    private final float[] btnX = new float[2];
    private final float[] btnY = new float[2];
    private final RectF btnL = new RectF();
    private final RectF btnR = new RectF();
    private final RectF btnStart = new RectF();
    private final RectF btnSelect = new RectF();
    private final RectF btnRom = new RectF();

    private final android.util.SparseArray<int[]> pointerDpad =
            new android.util.SparseArray<>();
    private final android.util.SparseArray<Integer> pointerButton =
            new android.util.SparseArray<>();

    public TouchControlsView(Context context, ButtonListener listener,
                             Runnable romPickerAction) {
        super(context);
        this.listener = listener;
        this.romPickerAction = romPickerAction;
        fill.setColor(Color.argb(46, 255, 255, 255));
        stroke.setColor(Color.argb(110, 255, 255, 255));
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2.5f);
        label.setColor(Color.argb(190, 255, 255, 255));
        label.setTextAlign(Paint.Align.CENTER);
        pressedFill.setColor(Color.argb(120, 255, 255, 255));
    }

    private void layoutControls(int w, int h) {
        float unit = Math.min(w, h);
        dpad[0] = w * 0.17f;
        dpad[1] = h * 0.68f;
        dpadRadius = unit * 0.19f;
        face[0] = w * 0.85f;
        face[1] = h * 0.66f;
        faceRadius = unit * 0.30f;
        btnB[0] = face[0] - faceRadius * 0.62f;
        btnB[1] = face[1] + faceRadius * 0.34f;
        btnY[0] = face[0] - faceRadius * 0.30f;
        btnY[1] = face[1] - faceRadius * 0.42f;
        btnA[0] = face[0] + faceRadius * 0.30f;
        btnA[1] = face[1] - faceRadius * 0.42f;
        btnX[0] = face[0] + faceRadius * 0.62f;
        btnX[1] = face[1] + faceRadius * 0.34f;
        float faceButtonRadius = unit * 0.085f;
        btnL.set(w * 0.06f, h * 0.03f, w * 0.20f, h * 0.13f);
        btnR.set(w * 0.80f, h * 0.03f, w * 0.94f, h * 0.13f);
        btnStart.set(w * 0.53f, h * 0.88f, w * 0.63f, h * 0.97f);
        btnSelect.set(w * 0.37f, h * 0.88f, w * 0.47f, h * 0.97f);
        btnRom.set(w * 0.46f, h * 0.02f, w * 0.54f, h * 0.10f);
        label.setTextSize(unit * 0.05f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutControls(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // D-pad
        canvas.drawCircle(dpad[0], dpad[1], dpadRadius, fill);
        canvas.drawCircle(dpad[0], dpad[1], dpadRadius, stroke);
        float arrow = dpadRadius * 0.55f;
        canvas.drawCircle(dpad[0], dpad[1] - arrow, dpadRadius * 0.30f, fill);
        canvas.drawCircle(dpad[0], dpad[1] + arrow, dpadRadius * 0.30f, fill);
        canvas.drawCircle(dpad[0] - arrow, dpad[1], dpadRadius * 0.30f, fill);
        canvas.drawCircle(dpad[0] + arrow, dpad[1], dpadRadius * 0.30f, fill);
        // Face cluster
        canvas.drawCircle(face[0], face[1], faceRadius, fill);
        drawFaceButton(canvas, btnB, "B", faceRadius * 0.44f);
        drawFaceButton(canvas, btnY, "Y", faceRadius * 0.44f);
        drawFaceButton(canvas, btnA, "A", faceRadius * 0.44f);
        drawFaceButton(canvas, btnX, "X", faceRadius * 0.44f);
        drawRectButton(canvas, btnL, "L");
        drawRectButton(canvas, btnR, "R");
        drawRectButton(canvas, btnStart, "ST");
        drawRectButton(canvas, btnSelect, "SEL");
        drawRectButton(canvas, btnRom, "ROM");
    }

    private void drawFaceButton(Canvas canvas, float[] center, String text,
                                float radius) {
        canvas.drawCircle(center[0], center[1], radius, fill);
        canvas.drawCircle(center[0], center[1], radius, stroke);
        canvas.drawText(text, center[0],
                center[1] - (label.ascent() + label.descent()) / 2f, label);
    }

    private void drawRectButton(Canvas canvas, RectF rect, String text) {
        canvas.drawRoundRect(rect, rect.height() / 3f, rect.height() / 3f,
                fill);
        canvas.drawRoundRect(rect, rect.height() / 3f, rect.height() / 3f,
                stroke);
        canvas.drawText(text, rect.centerX(),
                rect.centerY() - (label.ascent() + label.descent()) / 2f,
                label);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = event.getActionIndex();
                int id = event.getPointerId(index);
                handlePointerDown(id, event.getX(index), event.getY(index));
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < event.getPointerCount(); ++i) {
                    int id = event.getPointerId(i);
                    handlePointerMove(id, event.getX(i), event.getY(i));
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                int index = event.getActionIndex();
                int id = event.getActionMasked() == MotionEvent.ACTION_CANCEL
                        ? -1
                        : event.getPointerId(index);
                if (id >= 0) handlePointerUp(id);
                if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    releaseAll();
                }
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    private void handlePointerDown(int id, float x, float y) {
        if (btnRom.contains(x, y)) {
            romPickerAction.run();
            return;
        }
        Integer simple = hitSimple(x, y);
        if (simple != null) {
            pointerButton.put(id, simple);
            listener.onButton(simple, true);
            return;
        }
        float dx = x - dpad[0];
        float dy = y - dpad[1];
        if (dx * dx + dy * dy <= dpadRadius * dpadRadius) {
            int[] dirs = dpadDirections(dx, dy);
            pointerDpad.put(id, dirs);
            for (int dir : dirs) listener.onButton(dir, true);
        }
    }

    private void handlePointerMove(int id, float x, float y) {
        int[] dirs = pointerDpad.get(id);
        if (dirs == null) return; // face buttons are press-and-hold only
        float dx = x - dpad[0];
        float dy = y - dpad[1];
        if (dx * dx + dy * dy > dpadRadius * dpadRadius * 1.45f) return;
        int[] next = dpadDirections(dx, dy);
        if (sameDirs(dirs, next)) return;
        for (int dir : dirs) {
            if (!contains(next, dir)) listener.onButton(dir, false);
        }
        for (int dir : next) {
            if (!contains(dirs, dir)) listener.onButton(dir, true);
        }
        pointerDpad.put(id, next);
    }

    private void handlePointerUp(int id) {
        Integer button = pointerButton.get(id, null);
        if (button != null) {
            listener.onButton(button, false);
            pointerButton.delete(id);
            return;
        }
        int[] dirs = pointerDpad.get(id);
        if (dirs != null) {
            for (int dir : dirs) listener.onButton(dir, false);
            pointerDpad.delete(id);
        }
    }

    private void releaseAll() {
        for (int i = 0; i < pointerButton.size(); ++i) {
            listener.onButton(pointerButton.valueAt(i), false);
        }
        pointerButton.clear();
        for (int i = 0; i < pointerDpad.size(); ++i) {
            int[] dirs = pointerDpad.valueAt(i);
            for (int dir : dirs) listener.onButton(dir, false);
        }
        pointerDpad.clear();
    }

    private Integer hitSimple(float x, float y) {
        if (inside(btnB[0], btnB[1], x, y)) return PAD_B;
        if (inside(btnY[0], btnY[1], x, y)) return PAD_Y;
        if (inside(btnA[0], btnA[1], x, y)) return PAD_A;
        if (inside(btnX[0], btnX[1], x, y)) return PAD_X;
        if (btnL.contains(x, y)) return PAD_L;
        if (btnR.contains(x, y)) return PAD_R;
        if (btnStart.contains(x, y)) return PAD_START;
        if (btnSelect.contains(x, y)) return PAD_BACK;
        return null;
    }

    private boolean inside(float cx, float cy, float x, float y) {
        float r = faceRadius * 0.44f;
        float dx = x - cx;
        float dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    private static int[] dpadDirections(float dx, float dy) {
        double angle = Math.toDegrees(Math.atan2(-dy, dx)); // y-down screen
        int dirs = (int) Math.round(angle / 45.0);
        switch (((dirs % 8) + 8) % 8) {
            case 0: return new int[] { PAD_RIGHT };
            case 1: return new int[] { PAD_RIGHT, PAD_UP };
            case 2: return new int[] { PAD_UP };
            case 3: return new int[] { PAD_LEFT, PAD_UP };
            case 4: return new int[] { PAD_LEFT };
            case 5: return new int[] { PAD_LEFT, PAD_DOWN };
            case 6: return new int[] { PAD_DOWN };
            default: return new int[] { PAD_RIGHT, PAD_DOWN };
        }
    }

    private static boolean sameDirs(int[] a, int[] b) {
        if (a.length != b.length) return false;
        for (int value : a) if (!contains(b, value)) return false;
        return true;
    }

    private static boolean contains(int[] array, int value) {
        for (int item : array) if (item == value) return true;
        return false;
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseAll();
        super.onDetachedFromWindow();
    }
}
