package com.joseba.juegos.bloques;

import android.app.Activity;

import com.joseba.juegos.Game;

public class BloquesGame implements Game {
    @Override public String getTitle() { return "Bloques"; }
    @Override public String getDescription() { return "Encaja las piezas que caen y completa líneas antes de llegar arriba."; }
    @Override public int getAccentColor() { return 0xFF3F51B5; }
    @Override public String getBadge() { return "▦"; }
    @Override public Class<? extends Activity> getEntryActivity() { return BloquesActivity.class; }
}
