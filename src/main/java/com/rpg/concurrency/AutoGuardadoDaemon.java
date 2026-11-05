package com.rpg.concurrency;

/**
 * Guarda la partida cada cierto tiempo en un hilo daemon.
 * Hilo de usuario: la JVM no termina mientras alguno siga vivo. Hilo daemon: la JVM lo ignora al decidir
 * si cierra, asi que no impide que el juego termine al salir.
 */
public class AutoGuardadoDaemon implements Runnable {

    private final long intervaloMs;
    private final Runnable guardado;

    public AutoGuardadoDaemon(long intervaloMs, Runnable guardado) {
        this.intervaloMs = intervaloMs;
        this.guardado = guardado;
    }

    /** Crea el hilo, lo marca como daemon ANTES de start() (despues lanzaria IllegalThreadStateException). */
    public Thread iniciar() {
        Thread hilo = new Thread(this, "auto-guardado");
        hilo.setDaemon(true);
        hilo.start();
        return hilo;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(intervaloMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            guardado.run();
        }
    }
}
