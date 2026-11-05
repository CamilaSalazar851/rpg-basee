package com.rpg.persistencia;

import com.rpg.entities.Gremio;
import com.rpg.entities.PerfilJugador;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Lo que se guarda de una partida: perfil, gremio (con sus personajes) y enemigos derrotados. */
public class EstadoPartida implements Serializable {

    private static final long serialVersionUID = 1L;

    private final PerfilJugador perfil;
    private final Gremio gremio;
    private final LinkedHashMap<String, Integer> derrotados;

    public EstadoPartida(PerfilJugador perfil, Gremio gremio, Map<String, Integer> derrotados) {
        this.perfil = perfil;
        this.gremio = gremio;
        this.derrotados = new LinkedHashMap<>(derrotados);
    }

    public PerfilJugador getPerfil() {
        return perfil;
    }

    public Gremio getGremio() {
        return gremio;
    }

    public Map<String, Integer> getDerrotados() {
        return derrotados;
    }
}
