package com.joseba.juegos.libres;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.joseba.juegos.Ui;
import com.joseba.juegos.dos.DosActivity;

import java.util.List;

/** Catálogo de juegos de licencia libre: toca para jugar, ⓘ para ver autor, controles y licencia. */
public class CatalogoActivity extends Activity {
    private static final int VERDE = 0xFF2E7D32;
    private static final int AZUL_DOS = 0xFF0D47A1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Juegos libres");

        List<Juego> juegos;
        try {
            juegos = Juego.cargar(this);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo leer el catálogo: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFFF5F1EA);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        list.setPadding(pad, pad, pad, pad);
        scroll.addView(list);

        TextView intro = new TextView(this);
        intro.setText(juegos.size() + " juegos de software libre, incluidos en la app y sin Internet. "
                + "Toca uno para jugar; ⓘ muestra sus controles, autores y licencia.");
        intro.setTextSize(14);
        intro.setTextColor(0xFF5D4037);
        intro.setPadding(0, 0, 0, pad);
        list.addView(intro);

        list.addView(seccion("Para jugar con el dedo"));
        for (Juego j : juegos) if (!j.esDos()) list.addView(tarjeta(j));
        list.addView(seccion("Clásicos de DOS"));
        TextView nota = new TextView(this);
        nota.setText("Se abren en el emulador DOS con un mando en pantalla (⌨ cambia al teclado completo). "
                + "Para el ratón, desliza el dedo sobre la pantalla del juego y toca para hacer clic; "
                + "mantén pulsado antes de deslizar para arrastrar. Las partidas se guardan.");
        nota.setTextSize(13);
        nota.setTextColor(0xFF6D4C41);
        nota.setPadding(0, 0, 0, Ui.dp(this, 10));
        list.addView(nota);
        for (Juego j : juegos) if (j.esDos()) list.addView(tarjeta(j));

        setContentView(scroll);
    }

    private TextView seccion(String texto) {
        TextView t = new TextView(this);
        t.setText(texto);
        t.setTextSize(19);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFF3E2723);
        t.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        return t;
    }

    private View tarjeta(final Juego j) {
        int pad = Ui.dp(this, 14);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(pad, pad, Ui.dp(this, 6), pad);
        card.setBackground(Ui.rounded(Color.WHITE, Ui.dp(this, 12)));
        card.setElevation(Ui.dp(this, 2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 10);
        card.setLayoutParams(lp);

        TextView badge = new TextView(this);
        badge.setText(inicial(j.titulo));
        badge.setTextSize(20);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setTextColor(Color.WHITE);
        badge.setBackground(Ui.rounded(j.esDos() ? AZUL_DOS : VERDE, Ui.dp(this, 10)));
        int s = Ui.dp(this, 46);
        card.addView(badge, new LinearLayout.LayoutParams(s, s));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(pad, 0, 0, 0);
        TextView title = new TextView(this);
        title.setText(j.titulo);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF212121);
        TextView desc = new TextView(this);
        desc.setText(j.descripcion);
        desc.setTextSize(13);
        desc.setTextColor(0xFF616161);
        TextView lic = new TextView(this);
        lic.setText(j.licencia + " · " + j.autor);
        lic.setTextSize(11);
        lic.setTextColor(0xFF8D6E63);
        lic.setSingleLine(true);
        lic.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(title);
        texts.addView(desc);
        texts.addView(lic);
        card.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView info = new TextView(this);
        info.setText("ⓘ");
        info.setTextSize(24);
        info.setTextColor(0xFF8D6E63);
        info.setGravity(Gravity.CENTER);
        info.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { detalles(j); }
        });
        int b = Ui.dp(this, 44);
        card.addView(info, new LinearLayout.LayoutParams(b, b));

        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { jugar(j); }
        });
        return card;
    }

    private static String inicial(String t) {
        String limpio = t.replaceAll("[^\\p{L}\\p{N}]", "");
        if (limpio.isEmpty()) return "?";
        return limpio.length() >= 2 && Character.isDigit(limpio.charAt(0))
                ? limpio.substring(0, Math.min(4, limpio.length()))
                : limpio.substring(0, 1).toUpperCase();
    }

    private void jugar(Juego j) {
        Intent i;
        if (j.esDos()) {
            i = new Intent(this, DosActivity.class);
            i.putExtra(DosActivity.EXTRA_JUEGO, j.id);
            i.putExtra(DosActivity.EXTRA_EXE, j.exe);
            i.putExtra(DosActivity.EXTRA_TITULO, j.titulo);
        } else {
            i = new Intent(this, WebJuegoActivity.class);
            i.putExtra(WebJuegoActivity.EXTRA_ID, j.id);
            i.putExtra(WebJuegoActivity.EXTRA_INICIO, j.inicio);
            i.putExtra(WebJuegoActivity.EXTRA_ANCHO, j.ancho);
        }
        startActivity(i);
    }

    private void detalles(final Juego j) {
        StringBuilder sb = new StringBuilder();
        sb.append(j.descripcion).append("\n\n");
        if (j.controles.length() > 0) sb.append("Controles: ").append(j.controles).append("\n\n");
        sb.append("Autor: ").append(j.autor).append('\n');
        sb.append("Licencia: ").append(j.licencia).append('\n');
        sb.append("Web: ").append(j.web).append('\n');
        sb.append("Código fuente: ").append(j.repo).append(" (commit ").append(j.commit, 0, Math.min(10, j.commit.length())).append(")");
        if (j.esDos()) sb.append("\n\nVersión empaquetada por el proyecto FreeDOS; el código fuente completo está en ese repositorio.");
        new AlertDialog.Builder(this)
                .setTitle(j.titulo)
                .setView(envolver(textoConEnlaces(sb.toString(), 15)))
                .setPositiveButton("Jugar", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) { jugar(j); }
                })
                .setNeutralButton("Licencia", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) { licencia(j); }
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void licencia(Juego j) {
        String texto;
        try {
            texto = Juego.leer(this, "libres/licencias/" + j.id + ".txt");
        } catch (Exception e) {
            texto = "No se encontró el texto de la licencia.";
        }
        TextView t = textoConEnlaces(texto, 12);
        t.setTypeface(Typeface.MONOSPACE);
        new AlertDialog.Builder(this)
                .setTitle("Licencia de " + j.titulo)
                .setView(envolver(t))
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private ScrollView envolver(TextView t) {
        ScrollView sv = new ScrollView(this);
        sv.addView(t);
        return sv;
    }

    private TextView textoConEnlaces(String texto, int sp) {
        TextView t = new TextView(this);
        t.setText(texto);
        t.setTextSize(sp);
        t.setTextColor(0xFF212121);
        t.setTextIsSelectable(true);
        int p = Ui.dp(this, 20);
        t.setPadding(p, Ui.dp(this, 10), p, 0);
        Linkify.addLinks(t, Linkify.WEB_URLS);
        t.setMovementMethod(LinkMovementMethod.getInstance());
        return t;
    }
}
