package com.joseba.juegos.puntocruz;

import java.util.Arrays;
import java.util.Random;

/**
 * Convierte una imagen (array ARGB) en un patrón de punto de cruz:
 * 1) reduce la imagen a una cuadrícula de puntadas promediando bloques,
 * 2) agrupa los colores con k-means en espacio CIELab (perceptual),
 * 3) opcionalmente elimina puntadas sueltas ("confeti"),
 * 4) ordena la paleta por uso y asigna un símbolo a cada color.
 * No depende de Android, así que se puede probar en la JVM.
 */
public final class PatternGenerator {
    private PatternGenerator() {}

    public static final String[] SYMBOLS = {
            "●", "■", "▲", "◆", "★", "○", "□", "△",
            "◇", "☆", "▼", "+", "×", "=", "#", "%", "@", "&", "?", "!",
            "A", "B", "C", "D", "E", "F", "G", "H", "K", "M", "N", "P", "R", "S", "T", "U",
            "V", "W", "X", "Y", "Z", "2", "3", "4", "5", "6", "7", "8", "9"
    };
    public static final int MAX_COLORS = SYMBOLS.length;

    public static Pattern generate(int[] argb, int srcW, int srcH, int gridW, int maxColors, boolean cleanup) {
        gridW = Math.max(4, Math.min(gridW, srcW));
        maxColors = Math.max(2, Math.min(maxColors, MAX_COLORS));
        int gridH = Math.max(1, Math.round(srcH * (float) gridW / srcW));

        // 1) Promediar bloques de la imagen original.
        int n = gridW * gridH;
        int[] rgb = new int[n];
        boolean[] transparent = new boolean[n];
        for (int gy = 0; gy < gridH; gy++) {
            int y0 = gy * srcH / gridH, y1 = Math.max(y0 + 1, (gy + 1) * srcH / gridH);
            for (int gx = 0; gx < gridW; gx++) {
                int x0 = gx * srcW / gridW, x1 = Math.max(x0 + 1, (gx + 1) * srcW / gridW);
                long r = 0, g = 0, b = 0, a = 0, cnt = 0;
                for (int y = y0; y < y1; y++) {
                    int row = y * srcW;
                    for (int x = x0; x < x1; x++) {
                        int p = argb[row + x];
                        int pa = p >>> 24;
                        a += pa;
                        r += ((p >> 16) & 0xFF) * pa;
                        g += ((p >> 8) & 0xFF) * pa;
                        b += (p & 0xFF) * pa;
                        cnt++;
                    }
                }
                int i = gy * gridW + gx;
                if (a < 128 * cnt) {
                    transparent[i] = true;
                } else {
                    rgb[i] = 0xFF000000 | (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
                }
            }
        }

        // 2) k-means en Lab.
        float[][] lab = new float[n][];
        int opaque = 0;
        for (int i = 0; i < n; i++) {
            if (!transparent[i]) {
                lab[i] = toLab(rgb[i]);
                opaque++;
            }
        }
        int[] cells = new int[n];
        Arrays.fill(cells, Pattern.EMPTY);
        if (opaque == 0) {
            return new Pattern(gridW, gridH, cells, new int[0], new String[0], null);
        }
        float[][] pts = new float[opaque][];
        int[] ptIndex = new int[opaque];
        for (int i = 0, k = 0; i < n; i++) {
            if (lab[i] != null) { pts[k] = lab[i]; ptIndex[k] = i; k++; }
        }
        int k = Math.min(maxColors, opaque);
        float[][] centers = kMeansPlusPlusInit(pts, k, new Random(1234));
        int[] assign = new int[opaque];
        for (int iter = 0; iter < 20; iter++) {
            boolean changed = false;
            for (int p = 0; p < opaque; p++) {
                int best = nearest(centers, pts[p]);
                if (best != assign[p] || iter == 0) { changed |= best != assign[p]; assign[p] = best; }
            }
            float[][] sum = new float[k][3];
            int[] cnt = new int[k];
            for (int p = 0; p < opaque; p++) {
                int c = assign[p];
                sum[c][0] += pts[p][0]; sum[c][1] += pts[p][1]; sum[c][2] += pts[p][2];
                cnt[c]++;
            }
            for (int c = 0; c < k; c++) {
                if (cnt[c] > 0) {
                    centers[c] = new float[]{sum[c][0] / cnt[c], sum[c][1] / cnt[c], sum[c][2] / cnt[c]};
                }
            }
            if (!changed && iter > 0) break;
        }
        for (int p = 0; p < opaque; p++) cells[ptIndex[p]] = assign[p];

        // 3) Eliminar puntadas aisladas.
        if (cleanup) removeConfetti(cells, gridW, gridH);

        // 4) Compactar y ordenar la paleta por número de puntadas.
        final int[] counts = new int[k];
        for (int c : cells) if (c >= 0) counts[c]++;
        Integer[] order = new Integer[k];
        for (int i = 0; i < k; i++) order[i] = i;
        Arrays.sort(order, new java.util.Comparator<Integer>() {
            @Override public int compare(Integer a, Integer b) { return counts[b] - counts[a]; }
        });
        int[] remap = new int[k];
        Arrays.fill(remap, -1);
        int used = 0;
        for (Integer o : order) if (counts[o] > 0) remap[o] = used++;
        int[] palette = new int[used];
        String[] symbols = new String[used];
        for (int i = 0; i < k; i++) {
            if (remap[i] >= 0) palette[remap[i]] = fromLab(centers[i]);
        }
        for (int i = 0; i < used; i++) symbols[i] = SYMBOLS[i];
        for (int i = 0; i < n; i++) if (cells[i] >= 0) cells[i] = remap[cells[i]];
        return new Pattern(gridW, gridH, cells, palette, symbols, null);
    }

    private static void removeConfetti(int[] cells, int w, int h) {
        int[] copy = cells.clone();
        int[] tally = new int[64];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int c = copy[i];
                if (c < 0) continue;
                boolean alone = true;
                int[] nb = new int[8];
                int m = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                        int v = copy[ny * w + nx];
                        if (v == c && (dx == 0 || dy == 0)) alone = false;
                        if (v >= 0) nb[m++] = v;
                    }
                }
                if (!alone || m == 0) continue;
                int best = -1, bestCount = 0;
                for (int j = 0; j < m; j++) {
                    int v = nb[j];
                    if (v >= tally.length) continue;
                    tally[v]++;
                    if (tally[v] > bestCount) { bestCount = tally[v]; best = v; }
                }
                for (int j = 0; j < m; j++) if (nb[j] < tally.length) tally[nb[j]] = 0;
                if (best >= 0 && bestCount >= 3) cells[i] = best;
            }
        }
    }

    private static float[][] kMeansPlusPlusInit(float[][] pts, int k, Random rnd) {
        float[][] centers = new float[k][];
        centers[0] = pts[rnd.nextInt(pts.length)].clone();
        double[] d2 = new double[pts.length];
        Arrays.fill(d2, Double.MAX_VALUE);
        for (int c = 1; c < k; c++) {
            double total = 0;
            for (int p = 0; p < pts.length; p++) {
                double d = dist2(pts[p], centers[c - 1]);
                if (d < d2[p]) d2[p] = d;
                total += d2[p];
            }
            if (total <= 0) { centers[c] = pts[rnd.nextInt(pts.length)].clone(); continue; }
            double r = rnd.nextDouble() * total;
            int chosen = pts.length - 1;
            for (int p = 0; p < pts.length; p++) {
                r -= d2[p];
                if (r <= 0) { chosen = p; break; }
            }
            centers[c] = pts[chosen].clone();
        }
        return centers;
    }

    private static int nearest(float[][] centers, float[] p) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int c = 0; c < centers.length; c++) {
            double d = dist2(centers[c], p);
            if (d < bd) { bd = d; best = c; }
        }
        return best;
    }

    private static double dist2(float[] a, float[] b) {
        float d0 = a[0] - b[0], d1 = a[1] - b[1], d2 = a[2] - b[2];
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    // ---- Conversión sRGB <-> CIELab (D65) ----

    static float[] toLab(int rgb) {
        double r = lin(((rgb >> 16) & 0xFF) / 255.0);
        double g = lin(((rgb >> 8) & 0xFF) / 255.0);
        double b = lin((rgb & 0xFF) / 255.0);
        double x = (r * 0.4124 + g * 0.3576 + b * 0.1805) / 0.95047;
        double y = (r * 0.2126 + g * 0.7152 + b * 0.0722);
        double z = (r * 0.0193 + g * 0.1192 + b * 0.9505) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new float[]{(float) (116 * fy - 16), (float) (500 * (fx - fy)), (float) (200 * (fy - fz))};
    }

    static int fromLab(float[] lab) {
        double fy = (lab[0] + 16) / 116.0;
        double fx = fy + lab[1] / 500.0;
        double fz = fy - lab[2] / 200.0;
        double x = finv(fx) * 0.95047, y = finv(fy), z = finv(fz) * 1.08883;
        double r = x * 3.2406 + y * -1.5372 + z * -0.4986;
        double g = x * -0.9689 + y * 1.8758 + z * 0.0415;
        double b = x * 0.0557 + y * -0.2040 + z * 1.0570;
        return 0xFF000000 | ch(r) << 16 | ch(g) << 8 | ch(b);
    }

    private static double lin(double c) { return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }
    private static double f(double t) { return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16 / 116.0; }
    private static double finv(double t) { return t > 0.206893 ? t * t * t : (t - 16 / 116.0) / 7.787; }
    private static int ch(double c) {
        double v = c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
        return (int) Math.max(0, Math.min(255, Math.round(v * 255)));
    }
}
