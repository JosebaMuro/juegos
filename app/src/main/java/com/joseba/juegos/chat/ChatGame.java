package com.joseba.juegos.chat;

import android.app.Activity;

import com.joseba.juegos.Game;

public class ChatGame implements Game {
    @Override public String getTitle() { return "Chat con IA local"; }
    @Override public String getDescription() { return "IA con visión 100 % local: funciona sin Internet y se adapta al procesador de tu móvil."; }
    @Override public int getAccentColor() { return 0xFFD97757; }
    @Override public String getBadge() { return "✦"; }
    @Override public Class<? extends Activity> getEntryActivity() { return ChatActivity.class; }
}
