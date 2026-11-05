package com.rpg.persistencia;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Guarda y lee el log de combate en texto.
 * FileWriter vs Files.writeString: FileWriter (con BufferedWriter) es un flujo con buffer que abro y cierro yo
 * y sirve para ir escribiendo linea a linea o agregar al final; Files.writeString es una sola llamada de NIO que
 * escribe todo el texto de una vez. Usaria FileWriter para logs que crecen y writeString para archivos pequenos
 * que se escriben completos.
 */
public class LogCombateArchivo {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * @param agregar true para agregar al final (FileWriter(file, true)); false sobrescribe lo anterior
     * @return true si se escribio bien
     */
    public boolean guardar(String texto, boolean agregar) {
        synchronized (GestorArchivos.LOCK) {
            if (!GestorArchivos.asegurarCarpeta()) {
                return false;
            }
            try (BufferedWriter escritor = new BufferedWriter(new FileWriter(GestorArchivos.LOG, agregar))) {
                escritor.write("=== Combate guardado el " + LocalDateTime.now().format(FORMATO_FECHA) + " ===");
                escritor.newLine();
                for (String linea : texto.split("\\R")) {
                    escritor.write(linea);
                    escritor.newLine();
                }
                return true;   // al salir del try, el escritor se cierra y hace flush solo
            } catch (IOException e) {
                System.out.println("No se pudo escribir el historial: " + e.getMessage());
                return false;
            }
        }
    }

    /** @return las lineas del historial; lista vacia si aun no existe o no se pudo leer */
    public List<String> leer() {
        synchronized (GestorArchivos.LOCK) {
            List<String> lineas = new ArrayList<>();
            try (BufferedReader lector = new BufferedReader(new FileReader(GestorArchivos.LOG))) {
                String linea;
                while ((linea = lector.readLine()) != null) {
                    lineas.add(linea);
                }
            } catch (FileNotFoundException e) {
                System.out.println("Aun no hay historial guardado.");
            } catch (IOException e) {
                System.out.println("No se pudo leer el historial: " + e.getMessage());
            }
            return lineas;
        }
    }
}
