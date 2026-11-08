package com.joseba.juegos.dos;

import android.app.Activity;

import com.joseba.juegos.Game;

public class DosGame implements Game {
    @Override public String getTitle() { return "DOS"; }
    @Override public String getDescription() { return "Ordenador DOS emulado (DOSBox) sin conexión. Importa tus programas y juegos de DOS."; }
    @Override public int getAccentColor() { return 0xFF0D47A1; }
    @Override public String getBadge() { return "C:\\"; }
    @Override public Class<? extends Activity> getEntryActivity() { return DosActivity.class; }
}
