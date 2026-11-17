package com.joseba.juegos.libres;

import android.app.Activity;

import com.joseba.juegos.Game;

/** Tarjeta del menú que abre el catálogo de juegos de licencia libre. */
public class LibresGame implements Game {
    @Override public String getTitle() { return "Juegos libres"; }
    @Override public String getDescription() { return "Una colección de juegos de licencia libre: NetHack, Liquid War, 2048, Hextris y muchos más. Sin Internet."; }
    @Override public int getAccentColor() { return 0xFF2E7D32; }
    @Override public String getBadge() { return "★"; }
    @Override public Class<? extends Activity> getEntryActivity() { return CatalogoActivity.class; }
}
