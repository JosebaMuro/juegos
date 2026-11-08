package com.joseba.juegos.chat;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Motor local: llama.cpp (llama-server) incluido en la app como bibliotecas nativas, en
 * varias versiones: NPU Hexagon y GPU Adreno (Snapdragon), GPU por Vulkan y CPU ARM64 con
 * distintas extensiones. Se mide cuál es más rápida en este móvil con el modelo elegido.
 * La app habla con él por HTTP en 127.0.0.1: nada sale del móvil.
 */
public class LocalEngine {
    public interface Listener {
        void onStatus(String text);
    }

    private final Context ctx;
    private final Hardware hw;
    private Process process;
    private int port;
    private EngineSpec current;
    private final LinkedList<String> log = new LinkedList<String>();

    public LocalEngine(Context c, Hardware hw) {
        ctx = c.getApplicationContext();
        this.hw = hw;
    }

    public EngineSpec current() { return current; }
    public boolean isRunning() { return process != null; }

    public String libDir() { return ctx.getApplicationInfo().nativeLibraryDir; }

    public boolean available(EngineSpec s) {
        return new File(libDir(), "libllama_" + s.binary + ".so").exists();
    }

    /** Candidatos que existen en esta compilación de la app y encajan con el hardware. */
    public List<EngineSpec> candidates() {
        List<EngineSpec> out = new ArrayList<EngineSpec>();
        for (EngineSpec s : hw.engineCandidates(libDir())) if (available(s)) out.add(s);
        return out;
    }

    /** Arranca el motor indicado y espera a que el modelo esté cargado. Devuelve false si no funciona. */
    public boolean start(EngineSpec spec, File model, File mmproj, Listener l) throws Exception {
        stop();
        l.onStatus("Arrancando " + spec.label + "…");
        if (tryStart(spec, model, mmproj, l)) {
            current = spec;
            return true;
        }
        return false;
    }

    private boolean tryStart(EngineSpec spec, File model, File mmproj, Listener l) throws Exception {
        File bin = new File(libDir(), "libllama_" + spec.binary + ".so");
        ServerSocket ss = new ServerSocket(0);
        port = ss.getLocalPort();
        ss.close();
        int threads = hw.fastCores;
        List<String> cmd = new ArrayList<String>();
        cmd.add(bin.getAbsolutePath());
        cmd.add("-m"); cmd.add(model.getAbsolutePath());
        if (mmproj != null) { cmd.add("--mmproj"); cmd.add(mmproj.getAbsolutePath()); }
        cmd.add("--host"); cmd.add("127.0.0.1");
        cmd.add("--port"); cmd.add(String.valueOf(port));
        cmd.add("-t"); cmd.add(String.valueOf(threads));
        cmd.add("-tb"); cmd.add(String.valueOf(threads));
        cmd.add("-c"); cmd.add("4096");
        cmd.add("-np"); cmd.add("1");
        cmd.add("-cram"); cmd.add("256");
        cmd.add("--no-webui");
        cmd.add("--no-warmup");
        cmd.addAll(spec.args);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.environment().put("HOME", ctx.getFilesDir().getAbsolutePath());
        pb.environment().put("TMPDIR", ctx.getCacheDir().getAbsolutePath());
        for (java.util.Map.Entry<String, String> e : spec.env.entrySet()) {
            String old = pb.environment().get(e.getKey());
            pb.environment().put(e.getKey(), e.getKey().equals("LD_LIBRARY_PATH") && old != null
                    ? e.getValue() + ":" + old : e.getValue());
        }
        synchronized (this) { log.clear(); log.add("$ " + spec.label + ": " + cmd); }
        final Process p = pb.start();
        process = p;
        Thread reader = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
                    String line;
                    while ((line = r.readLine()) != null) {
                        synchronized (LocalEngine.this) {
                            log.add(line);
                            if (log.size() > 300) log.removeFirst();
                        }
                    }
                } catch (Exception ignored) {}
            }
        }, "llama-log");
        reader.setDaemon(true);
        reader.start();

        long deadline = System.currentTimeMillis() + 5 * 60 * 1000;
        while (System.currentTimeMillis() < deadline) {
            if (exited(p)) { process = null; return false; }
            try {
                HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/health").openConnection();
                c.setConnectTimeout(1000);
                c.setReadTimeout(2000);
                int code = c.getResponseCode();
                c.disconnect();
                if (code == 200) return true;
                l.onStatus("Cargando el modelo en " + spec.label + "…");
            } catch (Exception ignored) {}
            Thread.sleep(500);
        }
        stop();
        return false;
    }

    /**
     * Mide la velocidad de generación (tokens por segundo) del motor que está en marcha
     * con una petición corta de texto. Devuelve -1 si falla.
     */
    public double benchmark() {
        try {
            JSONArray msgs = new JSONArray();
            msgs.put(new JSONObject().put("role", "user").put("content",
                    "Escribe los números del uno al treinta en palabras, separados por comas."));
            JSONObject body = new JSONObject();
            body.put("messages", msgs);
            body.put("max_tokens", 48);
            body.put("temperature", 0);
            long t0 = System.currentTimeMillis();
            HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/v1/chat/completions").openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5 * 60 * 1000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("content-type", "application/json");
            OutputStream os = c.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();
            if (c.getResponseCode() != 200) return -1;
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            long dt = System.currentTimeMillis() - t0;
            JSONObject j = new JSONObject(sb.toString());
            JSONObject timings = j.optJSONObject("timings");
            if (timings != null && timings.optDouble("predicted_per_second", 0) > 0) {
                return timings.getDouble("predicted_per_second");
            }
            JSONObject usage = j.optJSONObject("usage");
            int toks = usage != null ? usage.optInt("completion_tokens", 0) : 0;
            return toks > 0 && dt > 0 ? toks * 1000.0 / dt : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private static boolean exited(Process p) {
        try { p.exitValue(); return true; } catch (IllegalThreadStateException e) { return false; }
    }

    public synchronized String tail(int n) {
        StringBuilder sb = new StringBuilder();
        int skip = Math.max(0, log.size() - n), i = 0;
        for (String s : log) if (i++ >= skip) sb.append(s).append('\n');
        return sb.toString();
    }

    public void stop() {
        if (process != null) {
            process.destroy();
            process = null;
        }
    }

    public interface TokenSink {
        void onToken(String piece);
    }

    /**
     * Envía la conversación (formato OpenAI de llama-server) y va devolviendo el texto
     * a medida que se genera. Devuelve el texto completo.
     */
    public String chat(JSONArray messages, TokenSink sink) throws Exception {
        JSONObject body = new JSONObject();
        body.put("messages", messages);
        body.put("stream", true);
        body.put("max_tokens", 1024);
        body.put("temperature", 0.7);
        HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/v1/chat/completions").openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(10 * 60 * 1000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("content-type", "application/json");
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();
        int status = c.getResponseCode();
        if (status >= 400) {
            InputStream es = c.getErrorStream();
            String err = es == null ? "" : new BufferedReader(new InputStreamReader(es, "UTF-8")).readLine();
            throw new Exception("El motor respondió " + status + ": " + err);
        }
        StringBuilder full = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        String line;
        while ((line = r.readLine()) != null) {
            if (!line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
            if (data.equals("[DONE]")) break;
            try {
                JSONObject j = new JSONObject(data);
                JSONArray ch = j.optJSONArray("choices");
                if (ch == null || ch.length() == 0) continue;
                JSONObject delta = ch.getJSONObject(0).optJSONObject("delta");
                if (delta == null) continue;
                String piece = delta.optString("content", "");
                if (delta.isNull("content")) piece = "";
                if (piece.length() > 0) {
                    full.append(piece);
                    sink.onToken(piece);
                }
            } catch (Exception ignored) {}
        }
        r.close();
        c.disconnect();
        return full.toString();
    }
}
