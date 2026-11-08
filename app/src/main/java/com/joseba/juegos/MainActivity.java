package com.joseba.juegos;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Menú principal: muestra una tarjeta por cada juego registrado. */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFFF5F1EA);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        list.setPadding(pad, pad, pad, pad);
        scroll.addView(list);

        TextView header = new TextView(this);
        header.setText("Elige un juego");
        header.setTextSize(22);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setTextColor(0xFF3E2723);
        header.setPadding(0, 0, 0, pad);
        list.addView(header);

        for (final Game game : GameRegistry.all()) {
            list.addView(buildCard(game));
        }
        setContentView(scroll);
    }

    private View buildCard(final Game game) {
        int pad = Ui.dp(this, 16);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(Ui.rounded(Color.WHITE, Ui.dp(this, 14)));
        card.setElevation(Ui.dp(this, 3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 12);
        card.setLayoutParams(lp);

        TextView badge = new TextView(this);
        badge.setText(game.getBadge());
        badge.setTextSize(26);
        badge.setGravity(Gravity.CENTER);
        badge.setTextColor(Color.WHITE);
        badge.setBackground(Ui.rounded(game.getAccentColor(), Ui.dp(this, 12)));
        int s = Ui.dp(this, 60);
        card.addView(badge, new LinearLayout.LayoutParams(s, s));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(pad, 0, 0, 0);
        TextView title = new TextView(this);
        title.setText(game.getTitle());
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF212121);
        TextView desc = new TextView(this);
        desc.setText(game.getDescription());
        desc.setTextSize(14);
        desc.setTextColor(0xFF616161);
        texts.addView(title);
        texts.addView(desc);
        card.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, game.getEntryActivity()));
            }
        });
        return card;
    }
}
