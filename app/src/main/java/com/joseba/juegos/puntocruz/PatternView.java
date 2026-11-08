package com.joseba.juegos.puntocruz;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Lienzo del patrón. Un dedo colorea (toque o arrastre); dos dedos mueven y
 * hacen zoom. Solo se colorean las celdas del color seleccionado.
 */
public class PatternView extends View {

    public interface Listener {
        void onStitchesFilled(int color, int count);
        void onCellInspected(int color);
    }

    private static final int FABRIC = 0xFFFAF6EC;
    private static final int HIGHLIGHT = 0xFFB3E5FC;
    private static final int BACKDROP = 0xFF5D5A55;

    private Pattern pattern;
    private int selected = 0;
    private boolean preview = false;
    private Listener listener;

    // Transformación: pantalla = celda * scale + trans
    private float scale = 1f, transX = 0f, transY = 0f;
    private boolean fitted = false;

    private final Paint fill = new Paint();
    private final Paint stitch = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint();
    private final Paint gridBold = new Paint();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint();
    private final RectF rect = new RectF();
    private final Rect srcRect = new Rect();
    private final Paint.FontMetrics fm = new Paint.FontMetrics();

    // Miniatura de 1 píxel por celda para cuando el zoom está muy alejado.
    private Bitmap overview;
    private boolean overviewDirty = true;

    private final ScaleGestureDetector scaleDetector;
    private final float touchSlop;
    private boolean multiTouch = false;
    private boolean painting = false;
    private float downX, downY, lastFocusX, lastFocusY;
    private int lastCellX = -1, lastCellY = -1;

    public PatternView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        fill.setStyle(Paint.Style.FILL);
        stitch.setStyle(Paint.Style.STROKE);
        stitch.setStrokeCap(Paint.Cap.ROUND);
        grid.setColor(0x33000000);
        grid.setStrokeWidth(1f);
        gridBold.setColor(0x88000000);
        gridBold.setStrokeWidth(2f);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        bitmapPaint.setFilterBitmap(false);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector d) {
                lastFocusX = d.getFocusX();
                lastFocusY = d.getFocusY();
                return true;
            }
            @Override public boolean onScale(ScaleGestureDetector d) {
                float fx = d.getFocusX(), fy = d.getFocusY();
                transX += fx - lastFocusX;
                transY += fy - lastFocusY;
                zoomAround(fx, fy, d.getScaleFactor());
                lastFocusX = fx;
                lastFocusY = fy;
                return true;
            }
        });
        // Evita que "doble toque y arrastrar" haga zoom: con un dedo se colorea.
        scaleDetector.setQuickScaleEnabled(false);
    }

    public void setPattern(Pattern p) {
        pattern = p;
        fitted = false;
        overview = null;
        overviewDirty = true;
        invalidate();
    }

    public void setListener(Listener l) { listener = l; }

    public void setSelectedColor(int color) {
        selected = color;
        invalidate();
    }

    public void setPreview(boolean on) {
        preview = on;
        overviewDirty = true;
        invalidate();
    }

    public boolean isPreview() { return preview; }

    public void refresh() {
        overviewDirty = true;
        invalidate();
    }

    public void zoomBy(float factor) {
        zoomAround(getWidth() / 2f, getHeight() / 2f, factor);
    }

    /** Centra la vista en la siguiente puntada pendiente del color seleccionado. */
    public boolean focusNextRemaining() {
        if (pattern == null) return false;
        int cx = (int) ((getWidth() / 2f - transX) / scale);
        int cy = (int) ((getHeight() / 2f - transY) / scale);
        int best = -1;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < pattern.cells.length; i++) {
            if (pattern.cells[i] == selected && !pattern.done[i]) {
                long dx = i % pattern.width - cx, dy = i / pattern.width - cy;
                long d = dx * dx + dy * dy;
                if (d < bestD) { bestD = d; best = i; }
            }
        }
        if (best < 0) return false;
        float minScale = 28 * getResources().getDisplayMetrics().density;
        if (scale < minScale) scale = minScale;
        transX = getWidth() / 2f - (best % pattern.width + 0.5f) * scale;
        transY = getHeight() / 2f - (best / pattern.width + 0.5f) * scale;
        clamp();
        invalidate();
        return true;
    }

    private float fitScale() {
        if (pattern == null || getWidth() == 0) return 1f;
        return Math.min(getWidth() / (float) pattern.width, getHeight() / (float) pattern.height) * 0.95f;
    }

    private void fit() {
        scale = fitScale();
        transX = (getWidth() - pattern.width * scale) / 2f;
        transY = (getHeight() - pattern.height * scale) / 2f;
        fitted = true;
    }

    private void zoomAround(float fx, float fy, float factor) {
        float min = fitScale() * 0.5f;
        float max = 120 * getResources().getDisplayMetrics().density;
        float ns = Math.max(min, Math.min(max, scale * factor));
        float real = ns / scale;
        transX = fx - (fx - transX) * real;
        transY = fy - (fy - transY) * real;
        scale = ns;
        clamp();
        invalidate();
    }

    private void clamp() {
        if (pattern == null) return;
        float pw = pattern.width * scale, ph = pattern.height * scale;
        float marginX = getWidth() * 0.5f, marginY = getHeight() * 0.5f;
        transX = Math.max(Math.min(transX, marginX), getWidth() - marginX - pw);
        transY = Math.max(Math.min(transY, marginY), getHeight() - marginY - ph);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (pattern != null) fit();
    }

    // ------------------------------------------------------------------ dibujo

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(BACKDROP);
        if (pattern == null) return;
        if (!fitted) fit();

        int w = pattern.width, h = pattern.height;
        float cs = scale;
        int x0 = Math.max(0, (int) Math.floor(-transX / cs));
        int y0 = Math.max(0, (int) Math.floor(-transY / cs));
        int x1 = Math.min(w, (int) Math.ceil((getWidth() - transX) / cs));
        int y1 = Math.min(h, (int) Math.ceil((getHeight() - transY) / cs));
        if (x0 >= x1 || y0 >= y1) return;

        if (cs < 7f) {
            drawOverview(canvas);
            return;
        }

        stitch.setStrokeWidth(Math.max(1.5f, cs * 0.22f));
        boolean showSymbols = cs >= 14f;
        text.setTextSize(cs * 0.62f);
        text.getFontMetrics(fm);
        float textOffset = -(fm.ascent + fm.descent) / 2f;
        float inset = cs * 0.2f;

        for (int y = y0; y < y1; y++) {
            float top = transY + y * cs;
            for (int x = x0; x < x1; x++) {
                float left = transX + x * cs;
                int i = y * w + x;
                int c = pattern.cells[i];
                rect.set(left, top, left + cs, top + cs);
                if (c < 0) {
                    fill.setColor(FABRIC);
                    canvas.drawRect(rect, fill);
                    continue;
                }
                if (pattern.done[i] || preview) {
                    fill.setColor(FABRIC);
                    canvas.drawRect(rect, fill);
                    stitch.setColor(pattern.palette[c]);
                    canvas.drawLine(left + inset, top + inset, left + cs - inset, top + cs - inset, stitch);
                    canvas.drawLine(left + cs - inset, top + inset, left + inset, top + cs - inset, stitch);
                } else {
                    boolean sel = c == selected;
                    fill.setColor(sel ? HIGHLIGHT : faded(pattern.palette[c]));
                    canvas.drawRect(rect, fill);
                    if (showSymbols) {
                        text.setColor(sel ? 0xFF01579B : 0xFF6D6D6D);
                        canvas.drawText(pattern.symbols[c], left + cs / 2f, top + cs / 2f + textOffset, text);
                    }
                }
            }
        }

        // Cuadrícula: líneas finas por celda y gruesas cada 10, como en los gráficos de bordado.
        float gl = transX + x0 * cs, gr = transX + x1 * cs;
        float gt = transY + y0 * cs, gb = transY + y1 * cs;
        for (int x = x0; x <= x1; x++) {
            float px = transX + x * cs;
            canvas.drawLine(px, gt, px, gb, x % 10 == 0 ? gridBold : grid);
        }
        for (int y = y0; y <= y1; y++) {
            float py = transY + y * cs;
            canvas.drawLine(gl, py, gr, py, y % 10 == 0 ? gridBold : grid);
        }
    }

    private void drawOverview(Canvas canvas) {
        int w = pattern.width, h = pattern.height;
        if (overview == null) {
            overview = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            overviewDirty = true;
        }
        if (overviewDirty) {
            int[] px = new int[w * h];
            for (int i = 0; i < px.length; i++) {
                int c = pattern.cells[i];
                if (c < 0) px[i] = FABRIC;
                else if (pattern.done[i] || preview) px[i] = pattern.palette[c];
                else if (c == selected) px[i] = HIGHLIGHT;
                else px[i] = faded(pattern.palette[c]);
            }
            overview.setPixels(px, 0, w, 0, 0, w, h);
            overviewDirty = false;
        }
        srcRect.set(0, 0, w, h);
        rect.set(transX, transY, transX + w * scale, transY + h * scale);
        canvas.drawBitmap(overview, srcRect, rect, bitmapPaint);
    }

    /** Versión muy clara y en gris del color, para orientarse sin desvelar el dibujo. */
    private static int faded(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        int lum = (r * 299 + g * 587 + b * 114) / 1000;
        int v = 205 + lum * 45 / 255;
        return Color.rgb(v, v, v);
    }

    // ------------------------------------------------------------------ toque

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (pattern == null) return false;
        scaleDetector.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                multiTouch = false;
                painting = false;
                downX = e.getX();
                downY = e.getY();
                lastCellX = lastCellY = -1;
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                multiTouch = true;
                painting = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (multiTouch || e.getPointerCount() > 1) return true;
                if (!painting && Math.hypot(e.getX() - downX, e.getY() - downY) > touchSlop) {
                    painting = true;
                    paintAt(downX, downY);
                }
                if (painting) {
                    // Interpolar para no saltarse celdas al arrastrar rápido.
                    int n = e.getHistorySize();
                    for (int k = 0; k < n; k++) paintAt(e.getHistoricalX(k), e.getHistoricalY(k));
                    paintAt(e.getX(), e.getY());
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!multiTouch && !painting) {
                    tapAt(e.getX(), e.getY());
                }
                painting = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                painting = false;
                return true;
        }
        return true;
    }

    private void tapAt(float sx, float sy) {
        int x = (int) Math.floor((sx - transX) / scale);
        int y = (int) Math.floor((sy - transY) / scale);
        if (x < 0 || y < 0 || x >= pattern.width || y >= pattern.height) return;
        int c = pattern.cells[pattern.index(x, y)];
        if (c < 0) return;
        if (c == selected) {
            paintAt(sx, sy);
        } else if (listener != null && !pattern.done[pattern.index(x, y)]) {
            listener.onCellInspected(c);
        }
    }

    private void paintAt(float sx, float sy) {
        int x = (int) Math.floor((sx - transX) / scale);
        int y = (int) Math.floor((sy - transY) / scale);
        if (x == lastCellX && y == lastCellY) return;
        int count = 0;
        if (lastCellX >= 0 && painting) {
            // Recorre la línea entre la celda anterior y la actual (Bresenham).
            int dx = Math.abs(x - lastCellX), dy = -Math.abs(y - lastCellY);
            int stepX = lastCellX < x ? 1 : -1, stepY = lastCellY < y ? 1 : -1;
            int err = dx + dy, cx = lastCellX, cy = lastCellY;
            while (true) {
                if (pattern.fill(cx, cy, selected)) count++;
                if (cx == x && cy == y) break;
                int e2 = 2 * err;
                if (e2 >= dy) { err += dy; cx += stepX; }
                if (e2 <= dx) { err += dx; cy += stepY; }
            }
        } else if (pattern.fill(x, y, selected)) {
            count++;
        }
        lastCellX = x;
        lastCellY = y;
        if (count > 0) {
            overviewDirty = true;
            invalidate();
            if (listener != null) listener.onStitchesFilled(selected, count);
        }
    }
}
