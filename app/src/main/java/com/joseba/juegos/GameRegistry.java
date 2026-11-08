package com.joseba.juegos;

import com.joseba.juegos.bloques.BloquesGame;
import com.joseba.juegos.chat.ChatGame;
import com.joseba.juegos.dos.DosGame;
import com.joseba.juegos.puntocruz.PuntoCruzGame;
import com.joseba.juegos.terminal.TerminalGame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Lista de juegos disponibles en el menú principal. */
public final class GameRegistry {
    private GameRegistry() {}

    public static List<Game> all() {
        List<Game> games = new ArrayList<Game>();
        games.add(new PuntoCruzGame());
        games.add(new BloquesGame());
        games.add(new DosGame());
        games.add(new TerminalGame());
        games.add(new ChatGame());
        // games.add(new OtroJuego());  <- añade aquí los próximos juegos
        return Collections.unmodifiableList(games);
    }
}
