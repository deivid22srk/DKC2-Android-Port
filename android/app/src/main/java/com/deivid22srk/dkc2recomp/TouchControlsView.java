package com.deivid22srk.dkc2recomp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * On-screen SNES controls for the DKC2 Android host.
 *
 * Layout notes (AAA pass, see docs/PORT_ANDROID.md): the face cluster is a
 * true SNES diamond (X top, Y left, A right, B bottom) with non-overlapping
 * hit circles, the D-pad renders a plus-shaped 8-way pad with arrow
 * triangles and a dead-zone hub, L/R sit in the upper side band clear of
 * the status bar, START/SELECT sit above the gesture bar, and the ROM
 * button is small and tap-confirmed. A portrait branch keeps every control
 * reachable if the pad is ever rotated. Every state change posts an
 * invalidate so presses light up immediately.
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

    private final ButtonListener listener;
    private final Runnable romPickerAction;
    private final int touchSlop;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pressedFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint romFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint romStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clusterRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint romLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tintB = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tintY = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tintA = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tintX = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float[] dpad = new float[2];
    private float dpadRadius;
    private final Path dpadArrows = new Path();
    private final RectF dpadArmV = new RectF();
    private final RectF dpadArmH = new RectF();

    private final float[] face = new float[2];
    private float faceHaloRadius;
    private final float[] btnA = new float[2];
    private final float[] btnB = new float[2];
    private final float[] btnX = new float[2];
    private final float[] btnY = new float[2];
    private float faceDrawRadius;
    private float faceHitRadius;

    private final RectF btnL = new RectF();
    private final RectF btnR = new RectF();
    private final RectF btnStart = new RectF();
    private final RectF btnSelect = new RectF();
    private final RectF btnRom = new RectF();

    private final android.util.SparseArray<int[]> pointerDpad =
            new android.util.SparseArray<>();
    private final android.util.SparseArray<Integer> pointerButton =
            new android.util.SparseArray<>();
    /** Origin of the face-button press, for slide-release hysteresis. */
    private final android.util.SparseArray<float[]> pointerFaceOrigin =
            new android.util.SparseArray<>();
    /** Armed ROM taps: pointer id -> down x,y. */
    private final android.util.SparseArray<float[]> pointerRomArm =
            new android.util.SparseArray<>();

    public TouchControlsView(Context context, ButtonListener listener,
                             Runnable romPickerAction) {
        super(context);
        this.listener = listener;
        this.romPickerAction = romPickerAction;
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        fill.setColor(Color.argb(44, 255, 255, 255));
        stroke.setColor(Color.argb(120, 255, 255, 255));
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(3.5f);
        pressedFill.setColor(Color.argb(120, 255, 255, 255));
        romFill.setColor(Color.argb(32, 255, 255, 255));
        romStroke.setColor(Color.argb(80, 255, 255, 255));
        romStroke.setStyle(Paint.Style.STROKE);
        romStroke.setStrokeWidth(2f);
        hub.setColor(Color.argb(70, 255, 255, 255));
        arrow.setColor(Color.argb(150, 255, 255, 255));
        clusterRing.setColor(Color.argb(28, 255, 255, 255));
        clusterRing.setStyle(Paint.Style.STROKE);
        clusterRing.setStrokeWidth(2f);
        label.setColor(Color.argb(210, 255, 255, 255));
        label.setTextAlign(Paint.Align.CENTER);
        label.setFakeBoldText(true);
        label.setShadowLayer(4f, 0f, 2f, Color.argb(120, 0, 0, 0));
        romLabel.setColor(Color.argb(160, 255, 255, 255));
        romLabel.setTextAlign(Paint.Align.CENTER);
        romLabel.setFakeBoldText(true);
        tintB.setColor(Color.argb(90, 255, 210, 63));
        tintY.setColor(Color.argb(90, 61, 220, 132));
        tintA.setColor(Color.argb(90, 255, 82, 82));
        tintX.setColor(Color.argb(90, 79, 195, 247));
        for (Paint tint : new Paint[] {tintB, tintY, tintA, tintX}) {
            tint.setStyle(Paint.Style.STROKE);
            tint.setStrokeWidth(6f);
        }
    }

    private void layoutControls(int w, int h) {
        if (w > h) {
            layoutLandscape(w, h);
        } else {
            layoutPortrait(w, h);
        }
    }

    /* Landscape reference: 1439x678. Radii are h-based so ultrawide
     * panels do not inflate buttons; edge margins keep ~40px sides and
     * clear the status bar (top) and gesture bar (bottom). */
    private void layoutLandscape(int w, int h) {
        dpad[0] = w * 0.160f;
        dpad[1] = h * 0.710f;
        dpadRadius = h * 0.190f;
        face[0] = w * 0.835f;
        face[1] = h * 0.675f;
        float d = h * 0.175f;
        faceDrawRadius = h * 0.095f;
        faceHitRadius = h * 0.110f;
        layoutFaceCluster(d, h);
        btnL.set(w * 0.045f, h * 0.145f, w * 0.185f, h * 0.265f);
        btnR.set(w * 0.815f, h * 0.145f, w * 0.955f, h * 0.265f);
        btnSelect.set(w * 0.395f, h * 0.855f, w * 0.475f, h * 0.945f);
        btnStart.set(w * 0.525f, h * 0.855f, w * 0.605f, h * 0.945f);
        btnRom.set(w * 0.465f, h * 0.030f, w * 0.535f, h * 0.085f);
        label.setTextSize(h * 0.052f);
        romLabel.setTextSize(h * 0.038f);
    }

    /* Portrait fallback (logged once on a device rotation): everything
     * stacks in the lower half above the gesture bar. */
    private void layoutPortrait(int w, int h) {
        float unit = Math.min(w, h);
        dpad[0] = w * 0.240f;
        dpad[1] = h * 0.850f;
        dpadRadius = unit * 0.180f;
        face[0] = w * 0.720f;
        face[1] = h * 0.850f;
        float d = unit * 0.139f;
        faceDrawRadius = unit * 0.0667f;
        faceHitRadius = unit * 0.0806f;
        layoutFaceCluster(d, unit);
        btnL.set(w * 0.060f, h * 0.615f, w * 0.200f, h * 0.665f);
        btnR.set(w * 0.800f, h * 0.615f, w * 0.940f, h * 0.665f);
        btnSelect.set(w * 0.345f, h * 0.700f, w * 0.465f, h * 0.750f);
        btnStart.set(w * 0.535f, h * 0.700f, w * 0.655f, h * 0.750f);
        btnRom.set(w * 0.425f, h * 0.025f, w * 0.575f, h * 0.075f);
        label.setTextSize(unit * 0.052f);
        romLabel.setTextSize(unit * 0.038f);
    }

    /** SNES diamond: X top, Y left, A right, B bottom. Neighbor center
     * distance is d*sqrt(2); with hit radii rHit the gap is
     * d*sqrt(2) - 2*rHit (>= 18px in the landscape reference). */
    private void layoutFaceCluster(float d, float unit) {
        btnX[0] = face[0];
        btnX[1] = face[1] - d;
        btnY[0] = face[0] - d;
        btnY[1] = face[1];
        btnA[0] = face[0] + d;
        btnA[1] = face[1];
        btnB[0] = face[0];
        btnB[1] = face[1] + d;
        faceHaloRadius = d + faceDrawRadius + 10f * unit / 678f;

        dpadArmV.set(dpad[0] - dpadRadius * 0.31f, dpad[1] - dpadRadius,
                dpad[0] + dpadRadius * 0.31f, dpad[1] + dpadRadius);
        dpadArmH.set(dpad[0] - dpadRadius, dpad[1] - dpadRadius * 0.31f,
                dpad[0] + dpadRadius, dpad[1] + dpadRadius * 0.31f);
        dpadArrows.reset();
        for (int i = 0; i < 4; i++) {
            android.graphics.Matrix rotation = new android.graphics.Matrix();
            rotation.postRotate(90f * i, dpad[0], dpad[1]);
            Path arm = new Path();
            arm.moveTo(dpad[0], dpad[1] - dpadRadius * 0.78f);
            arm.lineTo(dpad[0] - dpadRadius * 0.17f, dpad[1] - dpadRadius * 0.52f);
            arm.lineTo(dpad[0] + dpadRadius * 0.17f, dpad[1] - dpadRadius * 0.52f);
            arm.close();
            arm.transform(rotation);
            dpadArrows.addPath(arm);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        releaseAll(); // no held control may survive a geometry change
        layoutControls(w, h);
        invalidate();
    }

    /** Pressed flags derived from live pointer maps (single source of
     * truth, so multi-pointer edges cannot desync the drawing). */
    private boolean isPressed(int button) {
        for (int i = 0; i < pointerButton.size(); ++i) {
            if (pointerButton.valueAt(i) == button) return true;
        }
        return false;
    }

    private boolean isDirPressed(int dir) {
        for (int i = 0; i < pointerDpad.size(); ++i) {
            for (int value : pointerDpad.valueAt(i)) {
                if (value == dir) return true;
            }
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // D-pad: plus-shaped body, 4 arrow triangles, dead-zone hub.
        canvas.drawRoundRect(dpadArmV, dpadRadius * 0.22f,
                dpadRadius * 0.22f, fill);
        canvas.drawRoundRect(dpadArmH, dpadRadius * 0.22f,
                dpadRadius * 0.22f, fill);
        canvas.drawRoundRect(dpadArmV, dpadRadius * 0.22f,
                dpadRadius * 0.22f, stroke);
        canvas.drawRoundRect(dpadArmH, dpadRadius * 0.22f,
                dpadRadius * 0.22f, stroke);
        boolean up = isDirPressed(PAD_UP);
        boolean down = isDirPressed(PAD_DOWN);
        boolean left = isDirPressed(PAD_LEFT);
        boolean right = isDirPressed(PAD_RIGHT);
        Paint pressedArm = null;
        if (up || down || left || right) {
            pressedArm = pressedFill;
        }
        if (up) canvas.drawRect(dpadArmV.left, dpad[1],
                dpadArmV.right, dpadArmV.top, pressedArm);
        if (down) canvas.drawRect(dpadArmV.left, dpadArmV.bottom,
                dpadArmV.right, dpad[1], pressedArm);
        if (left) canvas.drawRect(dpadArmH.left, dpadArmV.top,
                dpad[0], dpadArmV.bottom, pressedArm);
        if (right) canvas.drawRect(dpad[0], dpadArmV.top,
                dpadArmH.right, dpadArmV.bottom, pressedArm);
        canvas.drawPath(dpadArrows, arrow);
        canvas.drawCircle(dpad[0], dpad[1], dpadRadius * 0.16f, hub);

        // Face cluster: thin halo ring keeps the affordance without the
        // old stacked-alpha blob.
        canvas.drawCircle(face[0], face[1], faceHaloRadius, clusterRing);
        drawFaceButton(canvas, btnX, "X", tintX, isPressed(PAD_X));
        drawFaceButton(canvas, btnY, "Y", tintY, isPressed(PAD_Y));
        drawFaceButton(canvas, btnA, "A", tintA, isPressed(PAD_A));
        drawFaceButton(canvas, btnB, "B", tintB, isPressed(PAD_B));
        drawRectButton(canvas, btnL, "L", isPressed(PAD_L));
        drawRectButton(canvas, btnR, "R", isPressed(PAD_R));
        drawRectButton(canvas, btnStart, "ST", isPressed(PAD_START));
        drawRectButton(canvas, btnSelect, "SEL", isPressed(PAD_BACK));
        drawRectButton(canvas, btnRom, "ROM", false);
    }

    private void drawFaceButton(Canvas canvas, float[] center, String text,
                                Paint tint, boolean pressed) {
        Paint body = pressed ? pressedFill : fill;
        canvas.drawCircle(center[0], center[1], faceDrawRadius, body);
        canvas.drawCircle(center[0], center[1], faceDrawRadius, stroke);
        canvas.drawArc(center[0] - faceDrawRadius + 6f,
                center[1] - faceDrawRadius + 6f,
                center[0] + faceDrawRadius - 6f,
                center[1] + faceDrawRadius - 6f, -90f, 360f, false, tint);
        label.setColor(Color.argb(pressed ? 255 : 210, 255, 255, 255));
        canvas.drawText(text, center[0],
                center[1] - (label.ascent() + label.descent()) / 2f, label);
    }

    private void drawRectButton(Canvas canvas, RectF rect, String text,
                                boolean pressed) {
        boolean isRom = text.equals("ROM");
        Paint body = pressed ? pressedFill : (isRom ? romFill : fill);
        Paint edge = isRom ? romStroke : stroke;
        float radius = isRom ? rect.height() / 2f : rect.height() / 3f;
        canvas.drawRoundRect(rect, radius, radius, body);
        canvas.drawRoundRect(rect, radius, radius, edge);
        Paint paint = isRom ? romLabel : label;
        canvas.drawText(text, rect.centerX(),
                rect.centerY() - (paint.ascent() + paint.descent()) / 2f,
                paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = event.getActionIndex();
                int id = event.getPointerId(index);
                handlePointerDown(id, event.getX(index), event.getY(index));
                postInvalidateOnAnimation();
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
                if (id >= 0) handlePointerUp(id, event.getX(index),
                        event.getY(index));
                if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    releaseAll();
                }
                postInvalidateOnAnimation();
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    private void handlePointerDown(int id, float x, float y) {
        Integer simple = hitNearestFace(x, y);
        if (simple == null) simple = hitRect(x, y);
        if (simple != null) {
            Log.d("TouchControls", "press btn=" + simple);
            pointerButton.put(id, simple);
            if (isFaceButton(simple)) {
                pointerFaceOrigin.put(id, new float[] {x, y, simple});
            }
            listener.onButton(simple, true);
            return;
        }
        if (btnRom.contains(x, y)) {
            // Arm only; a confirmed tap on UP fires the picker (a stray
            // stretch mid-game must not eject to the file picker).
            pointerRomArm.put(id, new float[] {x, y});
            return;
        }
        float dx = x - dpad[0];
        float dy = y - dpad[1];
        float distSq = dx * dx + dy * dy;
        if (distSq <= dpadRadius * dpadRadius) {
            int[] dirs = distSq <= dpadRadius * dpadRadius * 0.18f * 0.18f
                    ? new int[0] // dead zone: tracked, no direction yet
                    : dpadDirections(dx, dy);
            pointerDpad.put(id, dirs);
            for (int dir : dirs) listener.onButton(dir, true);
        }
    }

    private void handlePointerMove(int id, float x, float y) {
        int[] dirs = pointerDpad.get(id);
        if (dirs != null) {
            float dx = x - dpad[0];
            float dy = y - dpad[1];
            float distSq = dx * dx + dy * dy;
            // Glide out at ~1.20R, re-engage only inside ~1.15R: no
            // direction flicker on the rim.
            float exitSq = dpadRadius * dpadRadius * 1.45f;
            float enterSq = dpadRadius * dpadRadius * 1.3225f;
            if (distSq > exitSq) return;
            if (distSq > enterSq && dirs.length == 0) return;
            int[] next = distSq <= dpadRadius * dpadRadius * 0.18f * 0.18f
                    ? new int[0]
                    : dpadDirections(dx, dy);
            if (sameDirs(dirs, next)) return;
            for (int dir : dirs) {
                if (!contains(next, dir)) listener.onButton(dir, false);
            }
            for (int dir : next) {
                if (!contains(dirs, dir)) listener.onButton(dir, true);
            }
            pointerDpad.put(id, next);
            postInvalidateOnAnimation();
            return;
        }
        Integer held = pointerButton.get(id);
        if (held == null || !isFaceButton(held)) return;
        // Face slide-release with hysteresis: move to a neighboring button
        // (gaps are clean now), but do not jitter at the rim.
        float[] origin = pointerFaceOrigin.get(id);
        if (origin == null) return;
        float odx = x - origin[0];
        float ody = y - origin[1];
        if (odx * odx + ody * ody > faceHitRadius * faceHitRadius * 1.44f) {
            listener.onButton(held, false);
            pointerButton.delete(id);
            pointerFaceOrigin.delete(id);
            postInvalidateOnAnimation();
            return;
        }
        Integer next = hitNearestFace(x, y);
        if (next == null || next == held) return;
        listener.onButton(held, false);
        pointerButton.put(id, next);
        origin[0] = x;
        origin[1] = y;
        origin[2] = next;
        listener.onButton(next, true);
        postInvalidateOnAnimation();
    }

    private void handlePointerUp(int id, float x, float y) {
        float[] romArm = pointerRomArm.get(id);
        if (romArm != null) {
            pointerRomArm.delete(id);
            float dx = x - romArm[0];
            float dy = y - romArm[1];
            RectF generous = new RectF(btnRom);
            generous.inset(-16f, -16f);
            if (dx * dx + dy * dy <= (float) touchSlop * touchSlop
                    && generous.contains(x, y)) {
                romPickerAction.run();
            }
            return;
        }
        Integer button = pointerButton.get(id, null);
        if (button != null) {
            listener.onButton(button, false);
            pointerButton.delete(id);
            pointerFaceOrigin.delete(id);
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
        pointerFaceOrigin.clear();
        for (int i = 0; i < pointerDpad.size(); ++i) {
            int[] dirs = pointerDpad.valueAt(i);
            for (int dir : dirs) listener.onButton(dir, false);
        }
        pointerDpad.clear();
        pointerRomArm.clear();
    }

    /** Nearest-center wins among the face buttons; rect buttons follow.
     * The diamond geometry guarantees non-overlapping hit circles, so the
     * nearest rule only matters for future aspect tweaks. */
    private Integer hitNearestFace(float x, float y) {
        Integer best = null;
        float bestDistSq = faceHitRadius * faceHitRadius;
        for (int[] def : new int[][] {{PAD_B}, {PAD_Y}, {PAD_A}, {PAD_X}}) {
            float[] center = def[0] == PAD_B ? btnB
                    : def[0] == PAD_Y ? btnY
                    : def[0] == PAD_A ? btnA : btnX;
            float dx = x - center[0];
            float dy = y - center[1];
            float distSq = dx * dx + dy * dy;
            if (distSq <= bestDistSq) {
                bestDistSq = distSq;
                best = def[0];
            }
        }
        return best;
    }

    private Integer hitRect(float x, float y) {
        if (btnL.contains(x, y)) return PAD_L;
        if (btnR.contains(x, y)) return PAD_R;
        if (btnStart.contains(x, y)) return PAD_START;
        if (btnSelect.contains(x, y)) return PAD_BACK;
        return null;
    }

    private static boolean isFaceButton(int button) {
        return button == PAD_A || button == PAD_B
                || button == PAD_X || button == PAD_Y;
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
