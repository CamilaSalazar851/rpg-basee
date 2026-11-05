package com.rpg.concurrency;

import com.rpg.entities.EstadoEfecto;
import com.rpg.entities.Personaje;

/**
 * Un efecto DoT o HoT corriendo en su propio hilo: cada intervalo aplica un tick sobre el personaje.
 * Se cancela con {@code interrupt()} (antidoto, purificacion, fin del combate), sin banderas booleanas.
 */
public class EfectoContinuoTask implements Runnable {

    private final Personaje personaje;
    private final EstadoEfecto efecto;
    private final long intervaloMs;

    public EfectoContinuoTask(Personaje personaje, EstadoEfecto efecto, long intervaloMs) {
        this.personaje = personaje;
        this.efecto = efecto;
        this.intervaloMs = intervaloMs;
    }

    public String getNombre() {
        return efecto.getNombre();
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(intervaloMs);
                } catch (InterruptedException e) {
                    personaje.limpiarEfecto(efecto);
                    Thread.currentThread().interrupt();
                    break;
                }
                // El tick devuelve false cuando el efecto termino, fue reemplazado o el personaje cayo.
                if (!personaje.aplicarTickEfecto(efecto)) {
                    break;
                }
            }
        } finally {
            personaje.liberarHiloEfecto(efecto.getNombre());
        }
    }
}
