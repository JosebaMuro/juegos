package com.joseba.juegos.puntocruz;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Patrón de punto de cruz: una cuadrícula de puntadas donde cada celda
 * apunta a un color de la paleta (o -1 si es fondo sin bordar).
 */
public class Pattern {
    public static final int EMPTY = -1;
    private static final int MAGIC = 0x50435A31; // "PCZ1"

    public final int width;
    public final int height;
    public final int[] cells;      // índice de paleta por celda
    public final int[] palette;    // colores RGB (0xFFRRGGBB)
    public final String[] symbols; // símbolo por color
    public final boolean[] done;   // celdas ya coloreadas

    private final int[] totalPerColor;
    private final int[] donePerColor;

    public Pattern(int width, int height, int[] cells, int[] palette, String[] symbols, boolean[] done) {
        this.width = width;
        this.height = height;
        this.cells = cells;
        this.palette = palette;
        this.symbols = symbols;
        this.done = done != null ? done : new boolean[cells.length];
        totalPerColor = new int[palette.length];
        donePerColor = new int[palette.length];
        for (int i = 0; i < cells.length; i++) {
            int c = cells[i];
            if (c >= 0) {
                totalPerColor[c]++;
                if (this.done[i]) donePerColor[c]++;
            }
        }
    }

    public int colorCount() { return palette.length; }
    public int index(int x, int y) { return y * width + x; }
    public int total(int color) { return totalPerColor[color]; }
    public int remaining(int color) { return totalPerColor[color] - donePerColor[color]; }

    public int totalStitches() {
        int t = 0;
        for (int v : totalPerColor) t += v;
        return t;
    }

    public int doneStitches() {
        int t = 0;
        for (int v : donePerColor) t += v;
        return t;
    }

    public boolean isComplete() { return doneStitches() == totalStitches(); }

    /** Marca la celda como hecha si su color coincide. Devuelve true si cambió. */
    public boolean fill(int x, int y, int color) {
        if (x < 0 || y < 0 || x >= width || y >= height) return false;
        int i = index(x, y);
        if (cells[i] != color || done[i]) return false;
        done[i] = true;
        donePerColor[color]++;
        return true;
    }

    public void resetProgress() {
        for (int i = 0; i < done.length; i++) done[i] = false;
        for (int i = 0; i < donePerColor.length; i++) donePerColor[i] = 0;
    }

    public void write(DataOutputStream out) throws IOException {
        out.writeInt(MAGIC);
        out.writeInt(width);
        out.writeInt(height);
        out.writeInt(palette.length);
        for (int i = 0; i < palette.length; i++) {
            out.writeInt(palette[i]);
            out.writeUTF(symbols[i]);
        }
        for (int c : cells) out.writeShort(c);
        for (boolean d : done) out.writeBoolean(d);
    }

    public static Pattern read(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC) throw new IOException("Formato desconocido");
        int w = in.readInt();
        int h = in.readInt();
        int n = in.readInt();
        int[] pal = new int[n];
        String[] sym = new String[n];
        for (int i = 0; i < n; i++) {
            pal[i] = in.readInt();
            sym[i] = in.readUTF();
        }
        int[] cells = new int[w * h];
        for (int i = 0; i < cells.length; i++) cells[i] = in.readShort();
        boolean[] done = new boolean[w * h];
        for (int i = 0; i < done.length; i++) done[i] = in.readBoolean();
        return new Pattern(w, h, cells, pal, sym, done);
    }
}
