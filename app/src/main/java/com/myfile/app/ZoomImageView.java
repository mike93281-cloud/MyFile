package com.myfile.app;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

/** Imagen con zoom (pellizcar / doble toque), arrastre y deslizar para cambiar de foto. */
public class ZoomImageView extends ImageView {

    public interface Listener {
        void onSwipe(int direction);
        void onTap();
    }

    private final Matrix matrix = new Matrix();
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private float minScale = 1f;
    private float scale = 1f;
    private Listener listener;

    public ZoomImageView(Context context) {
        this(context, null);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float target = clamp(scale * d.getScaleFactor(), minScale, minScale * 8f);
                float f = target / scale;
                scale = target;
                matrix.postScale(f, f, d.getFocusX(), d.getFocusY());
                fixTranslation();
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (isZoomed()) {
                    matrix.postTranslate(-dx, -dy);
                    fixTranslation();
                }
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (!isZoomed() && listener != null && Math.abs(vx) > Math.abs(vy) && Math.abs(vx) > 600) {
                    listener.onSwipe(vx < 0 ? 1 : -1);
                    return true;
                }
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (isZoomed()) {
                    reset();
                } else {
                    float f = 2.5f;
                    scale *= f;
                    matrix.postScale(f, f, e.getX(), e.getY());
                    fixTranslation();
                }
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (listener != null) listener.onTap();
                return true;
            }
        });
    }

    public void setListener(Listener l) {
        listener = l;
    }

    private boolean isZoomed() {
        return scale > minScale * 1.02f;
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        reset();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        reset();
    }

    public void reset() {
        Drawable d = getDrawable();
        if (d == null || getWidth() == 0 || getHeight() == 0) return;
        float dw = d.getIntrinsicWidth(), dh = d.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;
        float s = Math.min(getWidth() / dw, getHeight() / dh);
        minScale = s;
        scale = s;
        matrix.reset();
        matrix.postScale(s, s);
        matrix.postTranslate((getWidth() - dw * s) / 2f, (getHeight() - dh * s) / 2f);
        setImageMatrix(matrix);
    }

    private void fixTranslation() {
        Drawable d = getDrawable();
        if (d == null) return;
        RectF r = new RectF(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        matrix.mapRect(r);
        float dx = 0, dy = 0;
        if (r.width() <= getWidth()) dx = (getWidth() - r.width()) / 2f - r.left;
        else if (r.left > 0) dx = -r.left;
        else if (r.right < getWidth()) dx = getWidth() - r.right;
        if (r.height() <= getHeight()) dy = (getHeight() - r.height()) / 2f - r.top;
        else if (r.top > 0) dy = -r.top;
        else if (r.bottom < getHeight()) dy = getHeight() - r.bottom;
        matrix.postTranslate(dx, dy);
        setImageMatrix(matrix);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    @SuppressWarnings("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        if (!scaleDetector.isInProgress()) gestureDetector.onTouchEvent(event);
        return true;
    }
}
