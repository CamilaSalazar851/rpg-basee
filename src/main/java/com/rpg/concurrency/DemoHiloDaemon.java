package com.rpg.concurrency;

/**
 * Prueba de hilos daemon (ejecutar este main).
 * 1) setDaemon(true) despues de start() lanza IllegalThreadStateException.
 * 2) Al terminar main, la JVM cierra aunque el hilo daemon siga vivo (en bucle infinito).
 * Hilo de usuario: mantiene viva la JVM mientras corra. Hilo daemon: la JVM lo ignora y lo corta al cerrar.
 */
public class DemoHiloDaemon {

    public static void main(String[] args) throws InterruptedException {
        Thread yaIniciado = new Thread(() -> sleepSeguro(300), "ya-iniciado");
        yaIniciado.start();
        try {
            yaIniciado.setDaemon(true);
        } catch (IllegalThreadStateException e) {
            System.out.println("setDaemon despues de start(): IllegalThreadStateException (esperado)");
        }
        yaIniciado.join();

        Thread daemon = new Thread(() -> {
            while (true) {
                sleepSeguro(100);
            }
        }, "daemon-infinito");
        daemon.setDaemon(true);
        daemon.start();
        System.out.println("main termina; el daemon sigue vivo=" + daemon.isAlive() + " y la JVM cierra igual.");
    }

    private static void sleepSeguro(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
