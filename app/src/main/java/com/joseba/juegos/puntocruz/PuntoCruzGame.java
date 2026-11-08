package com.joseba.juegos.puntocruz;

import android.app.Activity;

import com.joseba.juegos.Game;

public class PuntoCruzGame implements Game {
    @Override public String getTitle() { return "Punto de cruz"; }
    @Override public String getDescription() {
        return "Convierte una foto en un patrón de punto de cruz y coloréalo punto a punto.";
    }
    @Override public int getAccentColor() { return 0xFFE91E63; }
    @Override public String getBadge() { return "✖"; }
    @Override public Class<? extends Activity> getEntryActivity() { return PuntoCruzSetupActivity.class; }
}
