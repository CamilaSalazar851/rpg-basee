package com.rpg.persistencia;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Guarda y carga la partida con serializacion (partidas/partida.dat).
 * Seguridad de Serializable: nunca se debe llamar readObject sobre datos no confiables, porque un archivo
 * manipulado puede ejecutar codigo o gastar memoria al reconstruir objetos. Alternativas: JSON o un formato de
 * texto propio, donde uno decide que campos lee. Aqui solo se lee el archivo que el propio juego escribio.
 */
public class PartidaRepositorio {

    private static final String SUFIJO_TEMPORAL = ".tmp";

    /**
     * Escribe primero en un archivo temporal y despues lo mueve sobre el definitivo: si el juego se cierra a
     * mitad de la escritura, partida.dat anterior queda intacto y nunca a medio escribir.
     * @return true si se guardo
     */
    public boolean guardar(EstadoPartida estado) {
        synchronized (GestorArchivos.LOCK) {
            if (!GestorArchivos.asegurarCarpeta()) {
                return false;
            }
            java.io.File temporal = new java.io.File(GestorArchivos.PARTIDA.getPath() + SUFIJO_TEMPORAL);
            try (ObjectOutputStream salida = new ObjectOutputStream(new FileOutputStream(temporal))) {
                salida.writeObject(estado);
            } catch (NotSerializableException e) {
                System.out.println("No se pudo guardar: hay una clase que no es Serializable (" + e.getMessage() + ").");
                return false;
            } catch (IOException e) {
                System.out.println("No se pudo guardar la partida: " + e.getMessage());
                return false;
            }
            try {
                Files.move(temporal.toPath(), GestorArchivos.PARTIDA.toPath(), StandardCopyOption.ATOMIC_MOVE);
                return true;
            } catch (IOException e) {
                System.out.println("No se pudo terminar de guardar la partida: " + e.getMessage());
                return false;
            }
        }
    }

    /** @return la partida guardada, o vacio si no existe, esta corrupta o es de otra version */
    public Optional<EstadoPartida> cargar() {
        synchronized (GestorArchivos.LOCK) {
            try (ObjectInputStream entrada = new ObjectInputStream(new FileInputStream(GestorArchivos.PARTIDA))) {
                return Optional.of((EstadoPartida) entrada.readObject());
            } catch (FileNotFoundException e) {
                System.out.println("No hay ninguna partida guardada todavia.");
            } catch (InvalidClassException e) {
                System.out.println("La partida guardada es de otra version del juego y no se puede cargar.");
            } catch (ClassNotFoundException | ClassCastException e) {
                System.out.println("El archivo de partida no tiene el contenido esperado: " + e.getMessage());
            } catch (IOException e) {
                System.out.println("El archivo de partida esta danado o no se pudo leer: " + e.getMessage());
            }
            return Optional.empty();
        }
    }
}
