package com.joseba.juegos.puntocruz;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.joseba.juegos.Ui;

/** Pantalla de juego: colorear el patrón eligiendo colores de la paleta. */
public class PuntoCruzActivity extends Activity implements PatternView.Listener {
    private static final int MENU_PREVIEW = 1, MENU_RESET = 2, MENU_FIT = 3;

    private Pattern pattern;
    private PatternView view;
    private LinearLayout paletteRow;
    private HorizontalScrollView paletteScroll;
    private Swatch[] swatches;
    private TextView status;
    private int selected = 0;
    private boolean celebrated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pattern = PatternStore.load(this);
        if (pattern == null || pattern.colorCount() == 0) {
            Toast.makeText(this, "No hay ningún patrón guardado", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        celebrated = pattern.isComplete();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF5F1EA);

        status = new TextView(this);
        status.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        status.setTextColor(0xFF3E2723);
        status.setTextSize(14);
        root.addView(status);

        view = new PatternView(this);
        view.setPattern(pattern);
        view.setListener(this);
        root.addView(view, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), 0);
        tools.addView(tool("－", new View.OnClickListener() {
            @Override public void onClick(View v) { view.zoomBy(1 / 1.5f); }
        }));
        tools.addView(tool("＋", new View.OnClickListener() {
            @Override public void onClick(View v) { view.zoomBy(1.5f); }
        }));
        tools.addView(tool("Buscar punto", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!view.focusNextRemaining()) {
                    Toast.makeText(PuntoCruzActivity.this, "Este color ya está completo", Toast.LENGTH_SHORT).show();
                }
            }
        }), new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1f));
        root.addView(tools);

        paletteScroll = new HorizontalScrollView(this);
        paletteScroll.setHorizontalScrollBarEnabled(false);
        paletteRow = new LinearLayout(this);
        paletteRow.setOrientation(LinearLayout.HORIZONTAL);
        paletteRow.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 10));
        paletteScroll.addView(paletteRow);
        root.addView(paletteScroll);

        swatches = new Swatch[pattern.colorCount()];
        for (int i = 0; i < swatches.length; i++) {
            final int color = i;
            Swatch s = new Swatch(this, pattern, i);
            s.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { select(color); }
            });
            int size = Ui.dp(this, 58);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, Ui.dp(this, 74));
            lp.leftMargin = lp.rightMargin = Ui.dp(this, 3);
            paletteRow.addView(s, lp);
            swatches[i] = s;
        }
        // Empezar por el primer color con puntos pendientes.
        int first = 0;
        for (int i = 0; i < pattern.colorCount(); i++) {
            if (pattern.remaining(i) > 0) { first = i; break; }
        }
        select(first);
        updateStatus();
        setContentView(root);
    }

    private Button tool(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(0xFF3E2723);
        b.setBackground(Ui.rounded(0xFFE0DCD3, Ui.dp(this, 8)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 40));
        lp.rightMargin = Ui.dp(this, 6);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private void select(int color) {
        selected = color;
        view.setSelectedColor(color);
        for (int i = 0; i < swatches.length; i++) swatches[i].setSelectedState(i == color);
        final View s = swatches[color];
        paletteScroll.post(new Runnable() {
            @Override public void run() {
                int target = s.getLeft() - (paletteScroll.getWidth() - s.getWidth()) / 2;
                paletteScroll.smoothScrollTo(Math.max(0, target), 0);
            }
        });
    }

    private void updateStatus() {
        int done = pattern.doneStitches(), total = pattern.totalStitches();
        int pct = total == 0 ? 100 : (int) (100L * done / total);
        status.setText("Progreso: " + done + " / " + total + " puntos (" + pct + "%)"
                + "   ·   1 dedo colorea, 2 dedos mueven y hacen zoom");
    }

    @Override
    public void onStitchesFilled(int color, int count) {
        swatches[color].invalidate();
        updateStatus();
        if (pattern.remaining(color) == 0) {
            // Pasar automáticamente al siguiente color pendiente.
            for (int k = 1; k <= pattern.colorCount(); k++) {
                int next = (color + k) % pattern.colorCount();
                if (pattern.remaining(next) > 0) {
                    Toast.makeText(this, "¡Color " + pattern.symbols[color] + " completado!", Toast.LENGTH_SHORT).show();
                    select(next);
                    break;
                }
            }
        }
        if (!celebrated && pattern.isComplete()) {
            celebrated = true;
            PatternStore.save(this, pattern);
            new AlertDialog.Builder(this)
                    .setTitle("¡Terminado! 🎉")
                    .setMessage("Has completado las " + pattern.totalStitches() + " puntadas del patrón.")
                    .setPositiveButton("Ver mi obra", null)
                    .show();
        }
    }

    @Override
    public void onCellInspected(int color) {
        Toast.makeText(this, "Ese punto es del color " + pattern.symbols[color]
                + ". Tócalo en la paleta para usarlo.", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (pattern != null) PatternStore.save(this, pattern);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_FIT, 0, "Ver entero");
        menu.add(0, MENU_PREVIEW, 0, "Ver resultado final");
        menu.add(0, MENU_RESET, 0, "Reiniciar progreso");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_FIT:
                view.setPattern(pattern);
                return true;
            case MENU_PREVIEW:
                view.setPreview(!view.isPreview());
                item.setTitle(view.isPreview() ? "Volver a colorear" : "Ver resultado final");
                return true;
            case MENU_RESET:
                new AlertDialog.Builder(this)
                        .setTitle("¿Reiniciar progreso?")
                        .setMessage("Se borrarán todos los puntos coloreados.")
                        .setPositiveButton("Reiniciar", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                pattern.resetProgress();
                                celebrated = false;
                                PatternStore.save(PuntoCruzActivity.this, pattern);
                                for (Swatch s : swatches) s.invalidate();
                                view.refresh();
                                select(0);
                                updateStatus();
                            }
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Muestra de color de la paleta: círculo con símbolo y puntos pendientes. */
    private static class Swatch extends View {
        private final Pattern p;
        private final int color;
        private boolean sel;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;

        Swatch(Context c, Pattern p, int color) {
            super(c);
            this.p = p;
            this.color = color;
            density = c.getResources().getDisplayMetrics().density;
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            setClickable(true);
        }

        void setSelectedState(boolean s) {
            sel = s;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), cx = w / 2f;
            float r = Math.min(w, getHeight() - 18 * density) / 2f - 4 * density;
            float cy = r + 5 * density;
            int col = p.palette[color];
            if (sel) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF263238);
                canvas.drawCircle(cx, cy, r + 4 * density, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(col);
            canvas.drawCircle(cx, cy, r, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(density);
            paint.setColor(0x55000000);
            canvas.drawCircle(cx, cy, r, paint);

            int remaining = p.remaining(color);
            int lum = (Color.red(col) * 299 + Color.green(col) * 587 + Color.blue(col) * 114) / 1000;
            text.setColor(lum > 140 ? Color.BLACK : Color.WHITE);
            text.setTextSize(r * 0.95f);
            String sym = remaining == 0 ? "✓" : p.symbols[color];
            Paint.FontMetrics fm = text.getFontMetrics();
            canvas.drawText(sym, cx, cy - (fm.ascent + fm.descent) / 2f, text);

            text.setColor(0xFF4E342E);
            text.setTextSize(11 * density);
            canvas.drawText(remaining == 0 ? "hecho" : String.valueOf(remaining), cx, getHeight() - 3 * density, text);
        }
    }
}
