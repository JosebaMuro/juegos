package com.joseba.juegos.bloques;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/** Dibuja el tablero y gestiona gestos: tocar gira, deslizar mueve, deslizar abajo deja caer. */
public class BloquesView extends View {
    public interface Listener { void onChanged(); void onGameOver(); }

    static final int[] COLORS = {
            0xFF26C6DA, 0xFF5C6BC0, 0xFFFFA726, 0xFFFFEE58, 0xFF66BB6A, 0xFFAB47BC, 0xFFEF5350
    };

    final BloquesEngine engine = new BloquesEngine();
    private Listener listener;
    private boolean paused = true;
    private long lastDrop;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float cell, ox, oy;
    private float downX, downY, accX;
    private boolean moved;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (paused || engine.over) return;
            long now = SystemClock.uptimeMillis();
            if (now - lastDrop >= engine.dropInterval()) {
                lastDrop = now;
                engine.step();
                changed();
            }
            postDelayed(this, 16);
        }
    };

    public BloquesView(Context c) { super(c); }

    public void setListener(Listener l) { listener = l; }

    public void setPaused(boolean pz) {
        if (paused == pz) return;
        paused = pz;
        removeCallbacks(tick);
        if (!paused) { lastDrop = SystemClock.uptimeMillis(); post(tick); }
        invalidate();
    }

    public boolean isPaused() { return paused; }

    public void restart() {
        engine.reset();
        paused = true;
        setPaused(false);
        changed();
    }

    void changed() {
        invalidate();
        if (listener != null) {
            listener.onChanged();
            if (engine.over) { removeCallbacks(tick); listener.onGameOver(); }
        }
    }

    public void left() { if (!paused && engine.move(-1)) changed(); }
    public void right() { if (!paused && engine.move(1)) changed(); }
    public void rotate() { if (!paused && engine.rotate()) changed(); }
    public void down() { if (!paused) { engine.softDrop(); lastDrop = SystemClock.uptimeMillis(); changed(); } }
    public void drop() { if (!paused) { engine.hardDrop(); lastDrop = SystemClock.uptimeMillis(); changed(); } }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        cell = Math.min(w / (float) BloquesEngine.COLS, h / (float) BloquesEngine.ROWS);
        ox = (w - cell * BloquesEngine.COLS) / 2f;
        oy = (h - cell * BloquesEngine.ROWS) / 2f;
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0xFF12131C);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF1C1E2B);
        c.drawRect(ox, oy, ox + cell * BloquesEngine.COLS, oy + cell * BloquesEngine.ROWS, p);
        p.setColor(0xFF242738);
        p.setStrokeWidth(1);
        for (int x = 1; x < BloquesEngine.COLS; x++) c.drawLine(ox + x * cell, oy, ox + x * cell, oy + cell * BloquesEngine.ROWS, p);
        for (int y = 1; y < BloquesEngine.ROWS; y++) c.drawLine(ox, oy + y * cell, ox + cell * BloquesEngine.COLS, oy + y * cell, p);

        for (int y = 0; y < BloquesEngine.ROWS; y++)
            for (int x = 0; x < BloquesEngine.COLS; x++) {
                int v = engine.board[y * BloquesEngine.COLS + x];
                if (v != 0) block(c, x, y, COLORS[v - 1], 255);
            }
        if (!engine.over) {
            int gy = engine.ghostY();
            for (int[] k : BloquesEngine.cells(engine.type, engine.rot)) {
                if (gy + k[1] >= 0) block(c, engine.px + k[0], gy + k[1], COLORS[engine.type], 60);
            }
            for (int[] k : BloquesEngine.cells(engine.type, engine.rot)) {
                if (engine.py + k[1] >= 0) block(c, engine.px + k[0], engine.py + k[1], COLORS[engine.type], 255);
            }
        }
        if (paused || engine.over) {
            p.setColor(0xAA000000);
            c.drawRect(ox, oy, ox + cell * BloquesEngine.COLS, oy + cell * BloquesEngine.ROWS, p);
            p.setColor(0xFFFFFFFF);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(cell * 1.1f);
            String t = engine.over ? "Fin de la partida" : "Pausa";
            c.drawText(t, getWidth() / 2f, getHeight() / 2f, p);
            p.setTextSize(cell * 0.6f);
            p.setTypeface(Typeface.DEFAULT);
            c.drawText("Toca para " + (engine.over ? "jugar otra vez" : "seguir"), getWidth() / 2f, getHeight() / 2f + cell * 1.2f, p);
        }
    }

    private void block(Canvas c, int x, int y, int color, int alpha) {
        float l = ox + x * cell, t = oy + y * cell, in = cell * 0.06f;
        r.set(l + in, t + in, l + cell - in, t + cell - in);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        p.setAlpha(alpha);
        c.drawRoundRect(r, cell * 0.18f, cell * 0.18f, p);
        if (alpha == 255) {
            p.setColor(0x40FFFFFF);
            r.set(l + in * 2, t + in * 2, l + cell - in * 2, t + cell * 0.4f);
            c.drawRoundRect(r, cell * 0.12f, cell * 0.12f, p);
        }
        p.setAlpha(255);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY(); accX = 0; moved = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (paused) return true;
                float dx = e.getX() - downX - accX;
                while (Math.abs(dx) >= cell) {
                    int dir = dx > 0 ? 1 : -1;
                    if (dir > 0) right(); else left();
                    accX += dir * cell;
                    dx -= dir * cell;
                    moved = true;
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                float dy = e.getY() - downY, dx = e.getX() - downX;
                if (paused || engine.over) {
                    if (Math.abs(dx) < cell && Math.abs(dy) < cell) {
                        if (engine.over) restart(); else setPaused(false);
                    }
                    return true;
                }
                if (!moved && dy > cell * 3 && Math.abs(dy) > Math.abs(dx) * 2) drop();
                else if (!moved && Math.abs(dx) < cell * 0.5f && Math.abs(dy) < cell * 0.5f) rotate();
                return true;
            }
        }
        return true;
    }
}
