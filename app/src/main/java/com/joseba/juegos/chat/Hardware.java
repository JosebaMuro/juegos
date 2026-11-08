package com.joseba.juegos.chat;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Describe el hardware del móvil para elegir el motor más eficiente:
 * arquitectura, extensiones de la CPU, núcleos rápidos y memoria.
 */
public class Hardware {
    public final String abi;
    public final Set<String> features = new HashSet<String>();
    public final int cores;
    public final int fastCores;
    public final long ramBytes;
    public final String socName;

    public Hardware(Context c) {
        String[] abis = Build.SUPPORTED_ABIS;
        abi = abis != null && abis.length > 0 ? abis[0] : Build.CPU_ABI;
        cores = Runtime.getRuntime().availableProcessors();
        String soc = Build.HARDWARE;
        try {
            BufferedReader r = new BufferedReader(new FileReader("/proc/cpuinfo"));
            String line;
            while ((line = r.readLine()) != null) {
                String l = line.toLowerCase(Locale.ROOT);
                if (l.startsWith("features") || l.startsWith("flags")) {
                    int i = line.indexOf(':');
                    if (i > 0) for (String f : line.substring(i + 1).trim().split("\\s+")) features.add(f);
                } else if (l.startsWith("hardware") && line.indexOf(':') > 0) {
                    soc = line.substring(line.indexOf(':') + 1).trim();
                }
            }
            r.close();
        } catch (Exception ignored) {}
        socName = soc;
        fastCores = countFastCores(cores);
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        ramBytes = mi.totalMem;
    }

    /**
     * Núcleos que no son "pequeños": los móviles mezclan núcleos rápidos y de bajo consumo,
     * y repartir el trabajo con los lentos hace que todos esperen al más lento.
     */
    static int countFastCores(int cores) {
        List<Long> freqs = new ArrayList<Long>();
        for (int i = 0; i < cores; i++) {
            File f = new File("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
            try {
                BufferedReader r = new BufferedReader(new FileReader(f));
                freqs.add(Long.parseLong(r.readLine().trim()));
                r.close();
            } catch (Exception e) {
                freqs.add(0L);
            }
        }
        long min = Long.MAX_VALUE, max = 0;
        for (long v : freqs) { if (v > 0) { min = Math.min(min, v); max = Math.max(max, v); } }
        if (max == 0 || min == max) return Math.max(1, Math.min(cores, 8));
        int n = 0;
        for (long v : freqs) if (v > min) n++;
        return Math.max(1, Math.min(n, 8));
    }

    public boolean isArm64() { return abi.startsWith("arm64"); }
    public boolean isX86_64() { return abi.startsWith("x86_64"); }
    public boolean hasDotProd() { return features.contains("asimddp"); }
    public boolean hasI8mm() { return features.contains("i8mm"); }
    public double ramGb() { return ramBytes / (1024.0 * 1024 * 1024); }

    /** Variantes del motor de la más rápida a la más compatible. */
    public List<String> engineCandidates() {
        List<String> out = new ArrayList<String>();
        if (isArm64()) {
            if (hasI8mm() && hasDotProd()) out.add("i8mm");
            if (hasDotProd()) out.add("dotprod");
            out.add("armv8");
        } else if (isX86_64()) {
            if (features.contains("avx2") && features.contains("fma")) out.add("avx2");
            out.add("x86");
        }
        return out;
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(socName).append(" · ").append(abi).append(" · ")
                .append(cores).append(" núcleos (").append(fastCores).append(" rápidos) · ")
                .append(String.format(Locale.ROOT, "%.1f", ramGb())).append(" GB de RAM");
        if (isArm64()) {
            sb.append(" · ");
            sb.append(hasI8mm() ? "i8mm+dotprod" : hasDotProd() ? "dotprod" : "NEON básico");
        }
        return sb.toString();
    }
}
