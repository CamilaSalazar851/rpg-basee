package com.rpg.concurrency;

import com.rpg.entities.Guerrero;
import com.rpg.entities.Personaje;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Demostracion de la condicion de carrera en la vida (ejecutar este main).
 * Dos hilos restan 1 de vida 50.000 veces cada uno: el resultado correcto es vidaInicial - 100.000.
 */
public class DemoCondicionCarrera {

    private static final int VIDA_INICIAL = 200_000;
    private static final int GOLPES_POR_HILO = 50_000;

    /** Version SIN proteccion: leer, calcular y escribir son pasos separados, asi que se pierden actualizaciones. */
    static class VidaSinProteccion {
        private int vida = VIDA_INICIAL;

        void recibirDanio(int cantidad) {
            int leida = vida;
            Thread.yield();   // ensancha la ventana entre leer y escribir para que la carrera se vea siempre
            vida = Math.max(0, leida - cantidad);
        }
    }

    /**
     * Investigacion: recibirDanio con ReentrantLock. Preferiria el lock sobre synchronized cuando necesito
     * tryLock() (no quedarme esperando), tiempo maximo de espera, o poder interrumpir la espera. El unlock()
     * va SIEMPRE en el finally: si no, una excepcion deja el lock tomado para siempre.
     */
    static class VidaConLock {
        private final ReentrantLock lock = new ReentrantLock();
        private int vida = VIDA_INICIAL;

        void recibirDanio(int cantidad) {
            lock.lock();
            try {
                vida = Math.max(0, vida - cantidad);
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        VidaSinProteccion sinProteccion = new VidaSinProteccion();
        correrDosHilos(() -> sinProteccion.recibirDanio(1));
        System.out.printf("Sin proteccion: vida=%d (esperado %d)%n", sinProteccion.vida,
                VIDA_INICIAL - 2 * GOLPES_POR_HILO);

        VidaConLock conLock = new VidaConLock();
        correrDosHilos(() -> conLock.recibirDanio(1));
        System.out.printf("Con ReentrantLock: vida=%d (esperado %d)%n", conLock.vida,
                VIDA_INICIAL - 2 * GOLPES_POR_HILO);

        Personaje personaje = new Guerrero("Demo");
        personaje.setVidaMaxima(VIDA_INICIAL);
        personaje.setVida(VIDA_INICIAL);
        correrDosHilos(() -> personaje.recibirDanio(1));
        System.out.printf("Personaje.recibirDanio (synchronized): vida=%d (esperado %d)%n", personaje.getVida(),
                VIDA_INICIAL - 2 * GOLPES_POR_HILO);
    }

    private static void correrDosHilos(Runnable golpe) throws InterruptedException {
        Runnable tarea = () -> {
            for (int i = 0; i < GOLPES_POR_HILO; i++) {
                golpe.run();
            }
        };
        Thread a = new Thread(tarea, "hilo-A");
        Thread b = new Thread(tarea, "hilo-B");
        a.start();
        b.start();
        a.join();
        b.join();
    }
}
