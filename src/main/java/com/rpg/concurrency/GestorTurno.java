package com.rpg.concurrency;

import com.rpg.core.TurnoCallback;

/** Crea y cancela el hilo del temporizador de cada turno (un Thread nuevo por turno, nunca se reutiliza). */
public class GestorTurno {

    private Thread hiloTemporizador;

    public void iniciar(int segundos, TurnoCallback callback) {
        cancelar();
        hiloTemporizador = new Thread(new TemporizadorTurnoTask(segundos, callback), "temporizador-turno");
        hiloTemporizador.start();
    }

    /** Interrumpe el temporizador si sigue corriendo. */
    public void cancelar() {
        if (hiloTemporizador != null) {
            hiloTemporizador.interrupt();
            hiloTemporizador = null;
        }
    }
}
