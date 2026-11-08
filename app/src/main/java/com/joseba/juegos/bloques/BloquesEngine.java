package com.joseba.juegos.bloques;

import java.util.Random;

/** Lógica del juego de bloques, sin dependencias de Android. */
public class BloquesEngine {
    public static final int COLS = 10, ROWS = 20;

    // Siete piezas de cuatro casillas; cada una como lista de (x,y) en una caja de 4x4.
    static final int[][][] SHAPES = {
            {{0, 1}, {1, 1}, {2, 1}, {3, 1}},  // barra
            {{0, 0}, {0, 1}, {1, 1}, {2, 1}},  // J
            {{2, 0}, {0, 1}, {1, 1}, {2, 1}},  // L
            {{1, 0}, {2, 0}, {1, 1}, {2, 1}},  // cuadrado
            {{1, 0}, {2, 0}, {0, 1}, {1, 1}},  // S
            {{1, 0}, {0, 1}, {1, 1}, {2, 1}},  // T
            {{0, 0}, {1, 0}, {1, 1}, {2, 1}},  // Z
    };
    static final int[] BOX = {4, 3, 3, 4, 3, 3, 3};

    public final int[] board = new int[COLS * ROWS]; // 0 vacío, 1..7 tipo de pieza
    public int type, next, rot, px, py;
    public int score, lines, level = 1;
    public boolean over;
    private final Random rnd = new Random();
    private int[] bag = new int[7];
    private int bagPos = 7;

    public BloquesEngine() { reset(); }

    public void reset() {
        java.util.Arrays.fill(board, 0);
        score = lines = 0;
        level = 1;
        over = false;
        bagPos = 7;
        next = draw();
        spawn();
    }

    private int draw() {
        if (bagPos >= 7) {
            for (int i = 0; i < 7; i++) bag[i] = i;
            for (int i = 6; i > 0; i--) { int j = rnd.nextInt(i + 1); int t = bag[i]; bag[i] = bag[j]; bag[j] = t; }
            bagPos = 0;
        }
        return bag[bagPos++];
    }

    private void spawn() {
        type = next;
        next = draw();
        rot = 0;
        px = (COLS - BOX[type]) / 2;
        py = type == 0 ? -1 : 0;
        if (collides(px, py, rot)) over = true;
    }

    /** Casillas de la pieza (tipo t, giro r) relativas a su caja. */
    public static int[][] cells(int t, int r) {
        int n = BOX[t];
        int[][] out = new int[4][2];
        for (int i = 0; i < 4; i++) {
            int x = SHAPES[t][i][0], y = SHAPES[t][i][1];
            for (int k = 0; k < (t == 3 ? 0 : r); k++) { int nx = n - 1 - y; y = x; x = nx; }
            out[i][0] = x;
            out[i][1] = y;
        }
        return out;
    }

    boolean collides(int x, int y, int r) {
        for (int[] c : cells(type, r)) {
            int cx = x + c[0], cy = y + c[1];
            if (cx < 0 || cx >= COLS || cy >= ROWS) return true;
            if (cy >= 0 && board[cy * COLS + cx] != 0) return true;
        }
        return false;
    }

    public boolean move(int dx) {
        if (over || collides(px + dx, py, rot)) return false;
        px += dx;
        return true;
    }

    public boolean rotate() {
        if (over) return false;
        int nr = (rot + 1) % 4;
        int[] kicks = {0, -1, 1, -2, 2};
        for (int k : kicks) {
            if (!collides(px + k, py, nr)) { px += k; rot = nr; return true; }
        }
        if (!collides(px, py - 1, nr)) { py--; rot = nr; return true; }
        return false;
    }

    /** Baja una fila. Devuelve el número de líneas hechas si la pieza se fija, o -1 si sigue cayendo. */
    public int step() {
        if (over) return -1;
        if (!collides(px, py + 1, rot)) { py++; return -1; }
        return lock();
    }

    public int softDrop() {
        int r = step();
        if (r < 0) score += 1;
        return r;
    }

    public int hardDrop() {
        if (over) return 0;
        int d = 0;
        while (!collides(px, py + 1, rot)) { py++; d++; }
        score += 2 * d;
        return lock();
    }

    public int ghostY() {
        int y = py;
        while (!collides(px, y + 1, rot)) y++;
        return y;
    }

    private int lock() {
        for (int[] c : cells(type, rot)) {
            int cx = px + c[0], cy = py + c[1];
            if (cy < 0) { over = true; continue; }
            board[cy * COLS + cx] = type + 1;
        }
        int cleared = 0;
        for (int y = ROWS - 1; y >= 0; y--) {
            boolean full = true;
            for (int x = 0; x < COLS; x++) if (board[y * COLS + x] == 0) { full = false; break; }
            if (full) {
                System.arraycopy(board, 0, board, COLS, y * COLS);
                for (int x = 0; x < COLS; x++) board[x] = 0;
                cleared++;
                y++;
            }
        }
        if (cleared > 0) {
            int[] pts = {0, 100, 300, 500, 800};
            score += pts[cleared] * level;
            lines += cleared;
            level = 1 + lines / 10;
        }
        if (!over) spawn();
        return cleared;
    }

    /** Milisegundos entre caídas según el nivel. */
    public long dropInterval() {
        return Math.max(70, (long) (800 * Math.pow(0.85, level - 1)));
    }
}
