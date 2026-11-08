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

    /** Fabricante del SoC: "QTI" (Snapdragon), "Google" (Tensor), "Mediatek", "Samsung"... */
    public String socManufacturer() {
        if (Build.VERSION.SDK_INT >= 31) {
            try { return (String) Build.class.getField("SOC_MANUFACTURER").get(null); } catch (Throwable ignored) {}
        }
        return "";
    }

    public String socModel() {
        if (Build.VERSION.SDK_INT >= 31) {
            try { return (String) Build.class.getField("SOC_MODEL").get(null); } catch (Throwable ignored) {}
        }
        return "";
    }

    public boolean isSnapdragon() {
        String m = socManufacturer().toLowerCase(Locale.ROOT);
        String h = (Build.HARDWARE + " " + Build.BOARD + " " + socName).toLowerCase(Locale.ROOT);
        return m.contains("qti") || m.contains("qualcomm") || h.contains("qcom") || h.contains("qualcomm")
                || h.contains("snapdragon") || h.matches(".*\\b(sm|sdm|msm)\\d+.*");
    }

    public boolean hasOpenCl() {
        return new File("/vendor/lib64/libOpenCL.so").exists() || new File("/system/vendor/lib64/libOpenCL.so").exists();
    }

    public boolean hasVulkan() {
        return Build.VERSION.SDK_INT >= 28 && new File("/system/lib64/libvulkan.so").exists();
    }

    /** Variante de CPU más rápida que admite este procesador, seguida de las más compatibles. */
    public List<String> cpuVariants() {
        List<String> out = new ArrayList<String>();
        if (!isArm64()) return out;
        if (hasI8mm() && hasDotProd()) out.add("cpu_i8mm");
        if (hasDotProd()) out.add("cpu_dotprod");
        out.add("cpu_armv8");
        return out;
    }

    /**
     * Todas las formas de ejecutar el modelo en este móvil: NPU Hexagon y GPU Adreno en
     * Snapdragon, GPU por Vulkan (Mali en Tensor/Exynos/MediaTek, también Adreno) y la CPU.
     * La app mide cuál es más rápida con el modelo elegido y se queda con esa.
     */
    public List<EngineSpec> engineCandidates(String libDir) {
        List<EngineSpec> out = new ArrayList<EngineSpec>();
        String sys = "/vendor/lib64:/system/lib64";
        if (isArm64() && isSnapdragon() && hasOpenCl() && Build.VERSION.SDK_INT >= 31) {
            String dsp = libDir + ";/vendor/dsp/cdsp;/vendor/lib/rfsa/adsp;/system/lib/rfsa/adsp;/dsp";
            out.add(new EngineSpec("npu_hexagon", "snapdragon", "NPU Hexagon (Snapdragon)", true,
                    "--device", "HTP0", "-ngl", "99", "--no-mmap")
                    .env("ADSP_LIBRARY_PATH", dsp).env("LD_LIBRARY_PATH", sys));
            out.add(new EngineSpec("gpu_adreno", "snapdragon", "GPU Adreno (OpenCL)", true,
                    "--device", "GPUOpenCL", "-ngl", "99")
                    .env("LD_LIBRARY_PATH", sys));
        }
        if (isArm64() && hasVulkan()) {
            out.add(new EngineSpec("gpu_vulkan", "vulkan", "GPU por Vulkan", true, "-ngl", "99"));
        }
        for (String v : cpuVariants()) {
            String label = v.equals("cpu_i8mm") ? "CPU ARMv8.6 con i8mm" : v.equals("cpu_dotprod") ? "CPU ARMv8.2 con dotprod" : "CPU ARMv8 básica";
            out.add(new EngineSpec(v, v, label, false, "-ngl", "0"));
        }
        return out;
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        String soc = (socManufacturer() + " " + socModel()).trim();
        sb.append(soc.length() > 0 ? soc : socName).append(" · ").append(abi).append(" · ")
                .append(cores).append(" núcleos (").append(fastCores).append(" rápidos) · ")
                .append(String.format(Locale.ROOT, "%.1f", ramGb())).append(" GB de RAM");
        if (isArm64()) {
            sb.append(" · ");
            sb.append(hasI8mm() ? "i8mm+dotprod" : hasDotProd() ? "dotprod" : "NEON básico");
        }
        if (hasVulkan()) sb.append(" · Vulkan");
        if (isSnapdragon() && hasOpenCl()) sb.append(" · OpenCL Adreno");
        return sb.toString();
    }
}
