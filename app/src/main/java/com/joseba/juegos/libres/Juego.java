package com.joseba.juegos.libres;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Un juego del catálogo (assets/libres/catalogo.json, generado por libres/preparar.py). */
public class Juego {
    public String id, tipo, titulo, descripcion, controles, autor, licencia, web, repo, commit;
    /** DOS: programa que se ejecuta. Web: página de inicio relativa a assets/web/&lt;id&gt;/. */
    public String exe, inicio;
    /** Web: la página está pensada para escritorio y se muestra ajustada al ancho. */
    public boolean ancho;

    public boolean esDos() { return "dos".equals(tipo); }

    public static List<Juego> cargar(Context c) throws Exception {
        JSONArray arr = new JSONObject(leer(c, "libres/catalogo.json")).getJSONArray("juegos");
        List<Juego> out = new ArrayList<Juego>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Juego j = new Juego();
            j.id = o.getString("id");
            j.tipo = o.getString("tipo");
            j.titulo = o.getString("titulo");
            j.descripcion = o.optString("descripcion");
            j.controles = o.optString("controles");
            j.autor = o.optString("autor");
            j.licencia = o.optString("licencia");
            j.web = o.optString("web");
            j.repo = o.optString("repo");
            j.commit = o.optString("commit");
            j.exe = o.optString("exe", null);
            j.inicio = o.optString("inicio", "index.html");
            j.ancho = o.optBoolean("ancho", false);
            out.add(j);
        }
        return out;
    }

    public static String leer(Context c, String asset) throws Exception {
        InputStream in = c.getAssets().open(asset);
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[16 * 1024];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            return bo.toString("UTF-8");
        } finally {
            in.close();
        }
    }
}
