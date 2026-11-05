package com.rpg.core;

/** Imprime cada evento del combate y lo guarda para incluirlo en el guardado de la partida. */
public class RegistroCombate {

    private final StringBuilder texto = new StringBuilder();

    // synchronized: las semifinales y los efectos escriben desde hilos distintos
    public synchronized void agregar(String linea) {
        System.out.println(linea);
        texto.append(linea).append(System.lineSeparator());
    }

    public void agregar(String formato, Object... argumentos) {
        agregar(String.format(formato, argumentos));
    }

    public synchronized void limpiar() {
        texto.setLength(0);
    }

    // TALLER ARCHIVOS: aqui iria volcar el registro de combate a un archivo .txt (BufferedWriter + FileWriter,
    // con append si quieres conservar combates anteriores) y leerlo de vuelta con BufferedReader.
    public synchronized String getTexto() {
        return texto.toString();
    }
}
