package com.rpg.concurrency;

import com.rpg.core.TurnoCallback;

/** Cuenta regresiva de un turno. Si llega a cero avisa al callback; si lo interrumpen, termina sin avisar. */
public class TemporizadorTurnoTask implements Runnable {

    private static final long UN_SEGUNDO_MS = 1000;

    private final int segundos;
    private final TurnoCallback callback;

    public TemporizadorTurnoTask(int segundos, TurnoCallback callback) {
        if (segundos <= 0) {
            throw new IllegalArgumentException("La duracion debe ser positiva");
        }
        this.segundos = segundos;
        this.callback = callback;
    }

    @Override
    public void run() {
        for (int restantes = segundos; restantes > 0; restantes--) {
            try {
                Thread.sleep(UN_SEGUNDO_MS);
            } catch (InterruptedException e) {
                // El jugador respondio a tiempo: se cancela el temporizador sin avisar al callback.
                Thread.currentThread().interrupt();
                return;
            }
        }
        callback.onTiempoAgotado();
    }
}
