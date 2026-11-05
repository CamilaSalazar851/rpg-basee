package com.rpg.persistencia;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Rutas de los archivos del juego y operaciones con la clase File.
 * Ruta relativa: se resuelve desde la carpeta donde se ejecuta el programa (cambia segun donde lo lances).
 * Ruta absoluta: empieza en la raiz del disco y siempre apunta al mismo sitio (getAbsolutePath()).
 */
public final class GestorArchivos {

    /** Candado comun: el hilo de auto-guardado y el hilo principal nunca tocan los archivos a la vez. */
    public static final Object LOCK = new Object();

    public static final File CARPETA = new File("partidas");
    public static final File PARTIDA = new File(CARPETA, "partida.dat");
    public static final File LOG = new File(CARPETA, "log_combate.txt");

    private GestorArchivos() {
    }

    /** Crea la carpeta partidas/ si no existe (mkdirs) y no falla si ya existe. */
    public static boolean asegurarCarpeta() {
        if (CARPETA.isDirectory() || CARPETA.mkdirs()) {
            return true;
        }
        System.out.println("No se pudo crear la carpeta " + CARPETA.getPath());
        return false;
    }

    /** @return true si lo creo; false si ya existia o no se pudo crear */
    public static boolean crearSiNoExiste(File archivo) {
        try {
            return archivo.createNewFile();
        } catch (IOException e) {
            System.out.println("No se pudo crear " + archivo.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /** Informacion de un archivo: nombre, ruta relativa y absoluta, si existe y cuanto pesa. */
    public static String describir(File archivo) {
        return String.format("%s | ruta relativa: %s | ruta absoluta: %s | existe: %s | tamano: %d bytes",
                archivo.getName(), archivo.getPath(), archivo.getAbsolutePath(),
                archivo.exists() ? "si" : "no", archivo.length());
    }

    /**
     * File.delete(): devuelve false tanto si el archivo no existia como si no se pudo borrar, y nunca explica
     * por que. Hay que revisar el boolean.
     */
    public static boolean borrarConFile(File archivo) {
        boolean borrado = archivo.delete();
        if (!borrado) {
            System.out.println("No se borro " + archivo.getName() + " (no existe o no se pudo borrar).");
        }
        return borrado;
    }

    /**
     * Files.deleteIfExists(Path): devuelve false SOLO si no existia, y si existe pero falla lanza IOException
     * con el motivo. Por eso distingue mejor los dos casos que File.delete().
     */
    public static boolean borrarConNio(File archivo) {
        try {
            boolean borrado = Files.deleteIfExists(archivo.toPath());
            if (!borrado) {
                System.out.println(archivo.getName() + " no existia, no hay nada que borrar.");
            }
            return borrado;
        } catch (IOException e) {
            System.out.println("No se pudo borrar " + archivo.getName() + ": " + e.getMessage());
            return false;
        }
    }
}
