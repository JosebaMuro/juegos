package com.joseba.juegos.bloques;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.joseba.juegos.Ui;

public class BloquesActivity extends Activity implements BloquesView.Listener {
    private BloquesView board;
    private TextView stats;
    private NextView nextView;
    private SharedPreferences prefs;
    private int best;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("bloques", MODE_PRIVATE);
        best = prefs.getInt("best", 0);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF12131C);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        stats = new TextView(this);
        stats.setTextColor(0xFFE0E0E0);
        stats.setTextSize(15);
        top.addView(stats, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        nextView = new NextView(this);
        top.addView(nextView, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 44)));
        root.addView(top);

        board = new BloquesView(this);
        board.setListener(this);
        root.addView(board, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout pad = new LinearLayout(this);
        pad.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 10));
        pad.addView(btn("◀", new Runnable() { public void run() { board.left(); } }, true));
        pad.addView(btn("⟳", new Runnable() { public void run() { board.rotate(); } }, false));
        pad.addView(btn("▼", new Runnable() { public void run() { board.down(); } }, true));
        pad.addView(btn("⤓", new Runnable() { public void run() { board.drop(); } }, false));
        pad.addView(btn("▶", new Runnable() { public void run() { board.right(); } }, true));
        pad.addView(btn("❚❚", new Runnable() { public void run() { board.setPaused(!board.isPaused()); } }, false));
        root.addView(pad);

        setContentView(root);
        board.restart();
    }

    /** Botón que repite la acción mientras se mantiene pulsado (si repeat es true). */
    private View btn(String label, final Runnable action, final boolean repeat) {
        final Button b = new Button(this);
        b.setText(label);
        b.setTextSize(20);
        b.setTextColor(0xFFFFFFFF);
        b.setBackground(Ui.rounded(0xFF2C3047, Ui.dp(this, 10)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 56), 1f);
        lp.leftMargin = lp.rightMargin = Ui.dp(this, 3);
        b.setLayoutParams(lp);
        final Runnable[] rep = new Runnable[1];
        rep[0] = new Runnable() { public void run() { action.run(); b.postDelayed(rep[0], 60); } };
        b.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        action.run();
                        if (repeat) b.postDelayed(rep[0], 220);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        b.removeCallbacks(rep[0]);
                        return true;
                }
                return true;
            }
        });
        return b;
    }

    @Override
    public void onChanged() {
        BloquesEngine g = board.engine;
        stats.setText("Puntos " + g.score + "   Líneas " + g.lines + "   Nivel " + g.level + "\nRécord " + Math.max(best, g.score));
        nextView.setType(g.next);
    }

    @Override
    public void onGameOver() {
        if (board.engine.score > best) {
            best = board.engine.score;
            prefs.edit().putInt("best", best).apply();
        }
        onChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        board.setPaused(true);
    }

    /** Muestra la siguiente pieza. */
    private static class NextView extends View {
        private int type = -1;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        NextView(Context c) { super(c); }
        void setType(int t) { if (t != type) { type = t; invalidate(); } }
        @Override protected void onDraw(Canvas c) {
            if (type < 0) return;
            int[][] cells = BloquesEngine.cells(type, 0);
            float s = Math.min(getWidth() / 4.5f, getHeight() / 2.5f);
            int minX = 9, maxX = 0, minY = 9, maxY = 0;
            for (int[] k : cells) { minX = Math.min(minX, k[0]); maxX = Math.max(maxX, k[0]); minY = Math.min(minY, k[1]); maxY = Math.max(maxY, k[1]); }
            float ox = (getWidth() - (maxX - minX + 1) * s) / 2f - minX * s;
            float oy = (getHeight() - (maxY - minY + 1) * s) / 2f - minY * s;
            p.setColor(BloquesView.COLORS[type]);
            for (int[] k : cells) c.drawRoundRect(ox + k[0] * s + 1, oy + k[1] * s + 1, ox + (k[0] + 1) * s - 1, oy + (k[1] + 1) * s - 1, s * 0.2f, s * 0.2f, p);
        }
    }
}
