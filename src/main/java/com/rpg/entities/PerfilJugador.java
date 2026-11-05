package com.rpg.entities;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

// TALLER ARCHIVOS: candidata a Serializable (victorias, derrotas y logros son parte de la partida).
public class PerfilJugador implements Serializable {
    private static final long serialVersionUID = 1L;


    private int victorias;
    private int derrotas;
    private final Set<String> logros = new TreeSet<>();

    public synchronized void registrarVictoria() {
        victorias++;
    }

    public synchronized void registrarDerrota() {
        derrotas++;
    }

    /** @return true si el logro era nuevo; false si ya estaba desbloqueado */
    public synchronized boolean desbloquearLogro(String logro) {
        return logros.add(logro);
    }

    public synchronized int getVictorias() {
        return victorias;
    }

    public synchronized int getDerrotas() {
        return derrotas;
    }

    /** Los logros salen ordenados alfabeticamente porque se guardan en un TreeSet. */
    public synchronized Set<String> getLogros() {
        return Collections.unmodifiableSet(new TreeSet<>(logros));
    }

    private synchronized void writeObject(ObjectOutputStream salida) throws IOException {
        salida.defaultWriteObject();
    }
}
