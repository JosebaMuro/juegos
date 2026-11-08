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
 * Motor local: llama.cpp (llama-server) compilado para cada nivel de CPU ARM64 e incluido
 * en la app como bibliotecas nativas. Se arranca la variante más rápida que admite la CPU
 * (i8mm → dotprod → ARMv8 básico); si una falla, se prueba la siguiente.
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
    private String variant;
    private final LinkedList<String> log = new LinkedList<String>();

    public LocalEngine(Context c, Hardware hw) {
        ctx = c.getApplicationContext();
        this.hw = hw;
    }

    public String variant() { return variant; }
    public boolean isRunning() { return process != null; }

    public synchronized String recentLog() {
        StringBuilder sb = new StringBuilder();
        for (String l : log) sb.append(l).append('\n');
        return sb.toString();
    }

    private File binary(String v) {
        return new File(ctx.getApplicationInfo().nativeLibraryDir, "libllama_" + v + ".so");
    }

    /** Arranca el motor con el modelo indicado. Bloquea hasta que está listo (llamar fuera del hilo principal). */
    public void start(File model, File mmproj, Listener l) throws Exception {
        stop();
        List<String> tried = new ArrayList<String>();
        for (String v : hw.engineCandidates()) {
            File bin = binary(v);
            if (!bin.exists()) continue;
            tried.add(v);
            l.onStatus("Arrancando el motor (" + describe(v) + ", " + hw.fastCores + " hilos)…");
            if (tryStart(bin, model, mmproj, l)) {
                variant = v;
                return;
            }
        }
        throw new Exception(tried.isEmpty()
                ? "Este móvil (" + hw.abi + ") no tiene un motor compatible en la app."
                : "El motor no pudo arrancar. Últimas líneas:\n" + tail(12));
    }

    public static String describe(String v) {
        if ("i8mm".equals(v)) return "CPU ARMv8.6 con i8mm";
        if ("dotprod".equals(v)) return "CPU ARMv8.2 con dotprod";
        if ("armv8".equals(v)) return "CPU ARMv8 básica";
        return v;
    }

    private boolean tryStart(File bin, File model, File mmproj, Listener l) throws Exception {
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
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.environment().put("HOME", ctx.getFilesDir().getAbsolutePath());
        pb.environment().put("TMPDIR", ctx.getCacheDir().getAbsolutePath());
        synchronized (this) { log.clear(); }
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
                            if (log.size() > 200) log.removeFirst();
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
                l.onStatus("Cargando el modelo en memoria…");
            } catch (Exception ignored) {}
            Thread.sleep(500);
        }
        stop();
        return false;
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
