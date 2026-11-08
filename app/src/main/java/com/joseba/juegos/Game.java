package com.joseba.juegos;

import android.app.Activity;

/**
 * Describe un juego de la colección. Para añadir un juego nuevo basta con
 * implementar esta interfaz y registrarlo en {@link GameRegistry}.
 */
public interface Game {
    String getTitle();
    String getDescription();
    /** Color de acento (ARGB) de la tarjeta del juego en el menú. */
    int getAccentColor();
    /** Texto corto o emoji que se muestra como icono de la tarjeta. */
    String getBadge();
    Class<? extends Activity> getEntryActivity();
}
