package com.rpg.core;

/** Aviso que recibe el motor cuando el temporizador de turno llega a cero. */
@FunctionalInterface
public interface TurnoCallback {

    void onTiempoAgotado();
}
