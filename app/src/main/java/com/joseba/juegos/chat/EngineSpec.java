package com.joseba.juegos.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** Una forma concreta de ejecutar llama.cpp: qué binario, en qué procesador y con qué opciones. */
public class EngineSpec {
    public final String id;        // se guarda en preferencias
    public final String binary;    // nombre del .so en nativeLibraryDir
    public final String label;     // para mostrar
    public final List<String> args;
    public final Map<String, String> env = new HashMap<String, String>();
    public final boolean accelerator;

    EngineSpec(String id, String binary, String label, boolean accelerator, String... args) {
        this.id = id;
        this.binary = binary;
        this.label = label;
        this.accelerator = accelerator;
        List<String> a = new ArrayList<String>();
        Collections.addAll(a, args);
        this.args = a;
    }

    EngineSpec env(String k, String v) { env.put(k, v); return this; }

    @Override public String toString() { return label; }
}
