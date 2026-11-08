package com.joseba.juegos.chat;

import java.util.ArrayList;
import java.util.List;

/**
 * Modelos con visión en formato GGUF (Hugging Face, organización ggml-org).
 * Los nombres exactos de archivo se resuelven al descargar con la API de Hugging Face,
 * eligiendo la cuantización preferida y su proyector de visión (mmproj).
 */
public final class ModelCatalog {
    private ModelCatalog() {}

    public static class Model {
        public final String id, name, repo, quant;
        public final double minRamGb, approxGb;
        Model(String id, String name, String repo, String quant, double minRamGb, double approxGb) {
            this.id = id; this.name = name; this.repo = repo; this.quant = quant;
            this.minRamGb = minRamGb; this.approxGb = approxGb;
        }
    }

    public static final List<Model> ALL = new ArrayList<Model>();
    static {
        ALL.add(new Model("smolvlm2-500m", "SmolVLM2 500M (muy ligero)",
                "ggml-org/SmolVLM2-500M-Video-Instruct-GGUF", "Q8_0", 0, 0.6));
        ALL.add(new Model("smolvlm2-2.2b", "SmolVLM2 2.2B (equilibrado)",
                "ggml-org/SmolVLM2-2.2B-Instruct-GGUF", "Q4_K_M", 4.5, 1.9));
        ALL.add(new Model("gemma3-4b", "Gemma 3 4B (el mejor en español)",
                "ggml-org/gemma-3-4b-it-GGUF", "Q4_K_M", 7.0, 3.3));
    }

    /** El modelo más capaz que cabe holgadamente en la memoria del móvil. */
    public static Model recommended(Hardware hw) {
        Model best = ALL.get(0);
        for (Model m : ALL) if (hw.ramGb() >= m.minRamGb) best = m;
        return best;
    }

    public static Model byId(String id) {
        for (Model m : ALL) if (m.id.equals(id)) return m;
        return null;
    }
}
