package com.joseba.juegos.chat;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Descarga (una sola vez, con reanudación) el modelo y su proyector de visión
 * desde Hugging Face y los guarda en el almacenamiento privado de la app.
 */
public class ModelStore {
    public interface Progress {
        void onProgress(String what, long done, long total);
        boolean cancelled();
    }

    private final File dir;

    public ModelStore(Context c) {
        File ext = c.getExternalFilesDir("modelos");
        dir = ext != null ? ext : new File(c.getFilesDir(), "modelos");
        dir.mkdirs();
    }

    public File modelDir(ModelCatalog.Model m) { return new File(dir, m.id); }
    public File modelFile(ModelCatalog.Model m) { return new File(modelDir(m), "modelo.gguf"); }
    public File mmprojFile(ModelCatalog.Model m) { return new File(modelDir(m), "vision.gguf"); }

    public boolean isReady(ModelCatalog.Model m) {
        return modelFile(m).isFile() && mmprojFile(m).isFile();
    }

    public void delete(ModelCatalog.Model m) {
        File[] fs = modelDir(m).listFiles();
        if (fs != null) for (File f : fs) f.delete();
        modelDir(m).delete();
    }

    /** Busca en el repositorio el GGUF con la cuantización elegida y el mmproj, y los descarga. */
    /**
     * preferQ40: en Snapdragon se prefiere la cuantización Q4_0, que es la que tienen optimizada
     * los motores de GPU Adreno y NPU Hexagon de llama.cpp.
     */
    public void download(ModelCatalog.Model m, boolean preferQ40, Progress p) throws Exception {
        modelDir(m).mkdirs();
        JSONObject info = new JSONObject(get("https://huggingface.co/api/models/" + m.repo));
        JSONArray sib = info.getJSONArray("siblings");
        String model = null, modelQ40 = null, mmproj = null, mmprojAny = null;
        String q = m.quant.toLowerCase(Locale.ROOT);
        for (int i = 0; i < sib.length(); i++) {
            String name = sib.getJSONObject(i).getString("rfilename");
            String low = name.toLowerCase(Locale.ROOT);
            if (!low.endsWith(".gguf")) continue;
            if (low.contains("mmproj")) {
                if (mmprojAny == null) mmprojAny = name;
                if (low.contains("q8_0") || low.contains("f16")) {
                    if (mmproj == null || low.contains("q8_0")) mmproj = name;
                }
            } else if (low.contains(q) && model == null) {
                model = name;
            } else if (low.contains("q4_0") && modelQ40 == null) {
                modelQ40 = name;
            }
        }
        if (preferQ40 && modelQ40 != null) model = modelQ40;
        if (mmproj == null) mmproj = mmprojAny;
        if (model == null || mmproj == null) throw new Exception("No encuentro los archivos del modelo en " + m.repo);
        fetch(m.repo, mmproj, mmprojFile(m), "Visión", p);
        fetch(m.repo, model, modelFile(m), "Modelo", p);
    }

    private void fetch(String repo, String name, File out, String label, Progress p) throws Exception {
        if (out.isFile()) return;
        File part = new File(out.getPath() + ".part");
        long have = part.isFile() ? part.length() : 0;
        String url = "https://huggingface.co/" + repo + "/resolve/main/" + name.replace(" ", "%20");
        HttpURLConnection c = open(url, have);
        int code = c.getResponseCode();
        if (code == 200) have = 0;          // el servidor no reanuda: empezar de cero
        else if (code != 206) throw new Exception("Descarga fallida (" + code + ") en " + name);
        long len = c.getContentLength() >= 0 ? c.getContentLength() : -1;
        String cl = c.getHeaderField("Content-Length");
        if (cl != null) try { len = Long.parseLong(cl); } catch (Exception ignored) {}
        long total = len >= 0 ? have + len : -1;
        RandomAccessFile raf = new RandomAccessFile(part, "rw");
        raf.setLength(have);
        raf.seek(have);
        InputStream in = c.getInputStream();
        byte[] buf = new byte[256 * 1024];
        long done = have, lastReport = 0;
        try {
            int n;
            while ((n = in.read(buf)) > 0) {
                if (p.cancelled()) throw new Exception("Descarga cancelada");
                raf.write(buf, 0, n);
                done += n;
                if (done - lastReport > 2 * 1024 * 1024) { p.onProgress(label, done, total); lastReport = done; }
            }
        } finally {
            raf.close();
            in.close();
            c.disconnect();
        }
        if (total > 0 && done < total) throw new Exception("La descarga se cortó; vuelve a intentarlo para continuar");
        if (!part.renameTo(out)) throw new Exception("No se pudo guardar " + out.getName());
    }

    private static HttpURLConnection open(String url, long from) throws Exception {
        for (int hops = 0; hops < 6; hops++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(20000);
            c.setReadTimeout(60000);
            c.setRequestProperty("User-Agent", "JuegosReunidos/3");
            if (from > 0) c.setRequestProperty("Range", "bytes=" + from + "-");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                url = new URL(new URL(url), loc).toString();
                continue;
            }
            return c;
        }
        throw new Exception("Demasiadas redirecciones");
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = open(url, 0);
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("Hugging Face respondió " + code + " para " + url);
        InputStream in = c.getInputStream();
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }
}
