package com.joseba.juegos.terminal;

import android.app.Activity;

import com.joseba.juegos.Game;

public class TerminalGame implements Game {
    @Override public String getTitle() { return "Terminal Linux"; }
    @Override public String getDescription() { return "La shell real del sistema Linux de tu móvil, con sus comandos (ls, cat, grep, ps…)."; }
    @Override public int getAccentColor() { return 0xFF263238; }
    @Override public String getBadge() { return ">_"; }
    @Override public Class<? extends Activity> getEntryActivity() { return TerminalActivity.class; }
}
