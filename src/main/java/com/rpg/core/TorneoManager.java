package com.rpg.core;

import com.rpg.entities.Escuadron;
import com.rpg.entities.Guerrero;
import com.rpg.entities.Mago;
import com.rpg.entities.Personaje;

import java.util.List;

/** Torneo de 4 gladiadores: dos semifinales y una final. */
public class TorneoManager {

    private final Arena arena;
    private final RegistroCombate registro;

    public TorneoManager(Arena arena, RegistroCombate registro) {
        this.arena = arena;
        this.registro = registro;
    }

    /** @return el campeon del torneo */
    public Personaje jugarTorneo() {
        List<Personaje> gladiadores = crearGladiadores().getMiembros();

        // Las dos semifinales corren en paralelo, cada una en su Thread. Cada hilo escribe su ganador en su
        // propia posicion del arreglo; el hilo principal solo lo lee despues de los join().
        Personaje[] ganadores = new Personaje[2];
        Thread semifinalA = new Thread(() -> ganadores[0] = simularSemifinal(gladiadores.get(0), gladiadores.get(1)),
                "semifinal-A");
        Thread semifinalB = new Thread(() -> ganadores[1] = simularSemifinal(gladiadores.get(2), gladiadores.get(3)),
                "semifinal-B");

        registro.agregar("Estado antes de start(): A=%s B=%s", semifinalA.getState(), semifinalB.getState());
        semifinalA.start();
        semifinalB.start();
        esperarUnPoco();
        registro.agregar("Estado durante la ejecucion: A=%s B=%s", semifinalA.getState(), semifinalB.getState());

        try {
            semifinalA.join();
            semifinalB.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            semifinalA.interrupt();
            semifinalB.interrupt();
            throw new IllegalStateException("El torneo fue interrumpido", e);
        }
        registro.agregar("Estado despues de join(): A=%s B=%s", semifinalA.getState(), semifinalB.getState());

        if (ganadores[0] == null || ganadores[1] == null) {
            throw new IllegalStateException("Una semifinal termino con error");
        }
        return simularFinal(ganadores[0], ganadores[1]);
    }

    /** Deja correr un instante a las semifinales para poder ver su estado (RUNNABLE o TIMED_WAITING). */
    private void esperarUnPoco() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public Personaje simularSemifinal(Personaje a, Personaje b) {
        registro.agregar("=== SEMIFINAL: %s vs %s ===", a.getNombre(), b.getNombre());
        Personaje ganador = arena.duelo(a, b);
        registro.agregar("Clasifica a la final: %s", ganador.getNombre());
        return ganador;
    }

    private Personaje simularFinal(Personaje a, Personaje b) {
        a.restaurarVida();
        b.restaurarVida();
        registro.agregar("=== FINAL: %s vs %s ===", a.getNombre(), b.getNombre());
        return arena.duelo(a, b);
    }

    private Escuadron crearGladiadores() {
        Escuadron escuadron = new Escuadron("Gladiadores");
        escuadron.agregar(new Guerrero("Maximo"));
        escuadron.agregar(new Mago("Circe"));
        escuadron.agregar(new Guerrero("Aquiles"));
        escuadron.agregar(new Mago("Merlina"));
        return escuadron;
    }
}
