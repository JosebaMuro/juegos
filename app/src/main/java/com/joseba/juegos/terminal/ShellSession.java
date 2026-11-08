package com.joseba.juegos.terminal;

import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * Proceso de shell interactiva (/system/bin/sh, mksh en Android) conectada por tuberías.
 * El prompt se sustituye por marcadores para saber cuándo termina cada orden,
 * con qué código y en qué directorio estamos.
 */
public class ShellSession {
    public interface Listener {
        void onOutput(String text);
        void onPrompt(int exitCode, String cwd);
        void onExit(int code);
    }

    static final char MARK = '\u001e';
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final Listener listener;
    private Process process;
    private OutputStream stdin;
    private int pid = -1;
    private volatile boolean closed;

    public ShellSession(Listener l) { listener = l; }

    public void start(File home) throws Exception {
        String sh = new File("/system/bin/sh").exists() ? "/system/bin/sh" : "sh";
        ProcessBuilder pb = new ProcessBuilder(sh, "-i");
        pb.redirectErrorStream(true);
        pb.directory(home);
        Map<String, String> env = pb.environment();
        env.put("HOME", home.getAbsolutePath());
        env.put("TMPDIR", home.getAbsolutePath());
        env.put("TERM", "dumb");
        env.put("PS1", MARK + "$?" + MARK + "$PWD" + MARK);
        process = pb.start();
        stdin = process.getOutputStream();
        pid = readPid(process);
        // Fijar el prompt por si el mkshrc del sistema lo cambia.
        write("PS1='" + MARK + "$?" + MARK + "$PWD" + MARK + "'; cd \"$HOME\"\n");
        Thread reader = new Thread(new Runnable() {
            @Override public void run() { readLoop(); }
        }, "shell-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private static int readPid(Process p) {
        for (String name : new String[]{"pid", "id"}) {
            try {
                Field f = p.getClass().getDeclaredField(name);
                f.setAccessible(true);
                return f.getInt(p);
            } catch (Throwable ignored) {}
        }
        return -1;
    }

    public void write(String s) {
        try {
            stdin.write(s.getBytes(UTF8));
            stdin.flush();
        } catch (Exception e) {
            if (listener != null && !closed) listener.onOutput("\n[No se pudo escribir en la shell: " + e.getMessage() + "]\n");
        }
    }

    /** Envía SIGINT a los procesos hijos de la shell (equivale a Ctrl+C). */
    public void interrupt() {
        if (pid <= 0) return;
        try {
            Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c",
                    "pkill -INT -P " + pid + " 2>/dev/null || kill -INT $(ps -o pid= --ppid " + pid + ") 2>/dev/null"});
        } catch (Exception ignored) {}
    }

    public void close() {
        closed = true;
        if (process != null) process.destroy();
    }

    private void readLoop() {
        StringBuilder out = new StringBuilder();
        StringBuilder mark = null;   // contenido entre marcadores del prompt
        int marks = 0;
        boolean ready = false;       // ignorar lo anterior al primer prompt propio
        char[] buf = new char[4096];
        try {
            Reader r = new InputStreamReader(process.getInputStream(), UTF8);
            int n;
            while ((n = r.read(buf)) > 0) {
                for (int i = 0; i < n; i++) {
                    char c = buf[i];
                    if (mark != null) {
                        if (c == MARK) {
                            marks++;
                            if (marks == 3) {
                                String[] parts = mark.toString().split(String.valueOf(MARK), -1);
                                int code = 0;
                                try { code = Integer.parseInt(parts[0].trim()); } catch (Exception ignored) {}
                                String cwd = parts.length > 1 ? parts[1] : "";
                                if (ready) flush(out); else out.setLength(0);
                                ready = true;
                                listener.onPrompt(code, cwd);
                                mark = null;
                                continue;
                            }
                            mark.append(c);
                        } else if (mark.length() > 4096) {
                            out.append(MARK).append(mark).append(c);
                            mark = null;
                        } else {
                            mark.append(c);
                        }
                    } else if (c == MARK) {
                        mark = new StringBuilder();
                        marks = 1;
                    } else if (ready) {
                        out.append(c);
                    }
                }
                if (mark == null && ready) flush(out);
            }
        } catch (Exception ignored) {
        }
        int code = -1;
        try { code = process.waitFor(); } catch (Exception ignored) {}
        if (!closed) listener.onExit(code);
    }

    private void flush(StringBuilder out) {
        if (out.length() == 0) return;
        String s = clean(out.toString());
        out.setLength(0);
        if (s.length() > 0) listener.onOutput(s);
    }

    /** Quita avisos de control de trabajos y secuencias de escape de color. */
    static String clean(String s) {
        StringBuilder sb = new StringBuilder();
        for (String line : s.split("(?<=\n)")) {
            if (line.contains("can't find tty fd") || line.contains("won't have full job control")) continue;
            sb.append(line);
        }
        return sb.toString().replaceAll("\u001b\\[[0-9;?]*[A-Za-z]", "").replace("\r", "");
    }
}
