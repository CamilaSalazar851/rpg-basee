package com.rpg.persistencia;

import com.rpg.entities.EstadoEfecto;
import com.rpg.entities.Gremio;
import com.rpg.entities.Guerrero;
import com.rpg.entities.Mago;
import com.rpg.entities.PerfilJugador;
import com.rpg.entities.Personaje;
import com.rpg.entities.enums.TipoEfecto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Map;

/**
 * Pruebas de serializacion (ejecutar este main). Usa memoria en vez de partidas/ para no pisar tu partida.
 * 1) transient: los efectos activos no se guardan y vuelven vacios al cargar.
 * 2) El ciclo Gremio - Personaje se serializa bien y sigue relacionado.
 * 3) NotSerializableException por un objeto anidado que no es Serializable, y como se resuelve.
 */
public class DemoSerializacion {

    /** Contenedor con un campo que NO es Serializable (Object): provoca NotSerializableException. */
    static class ConRecursoRoto implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Object recurso = new Object();
    }

    /** Solucion: marcar el campo como transient, asi no se intenta guardar. */
    static class ConRecursoCorregido implements Serializable {
        private static final long serialVersionUID = 1L;
        private final transient Object recurso = new Object();
    }

    public static void main(String[] args) throws Exception {
        Gremio gremio = new Gremio("Los Demo");
        Mago mago = new Mago("Merlina");
        mago.unirseAGremio(gremio);
        new Guerrero("Aquiles").unirseAGremio(gremio);
        mago.aplicarEfecto(new EstadoEfecto("Quemadura", TipoEfecto.DANIO_CONTINUO, 4, 3));
        System.out.println("Antes de guardar: efectos de Merlina = " + mago.getEfectos().size());

        PerfilJugador perfil = new PerfilJugador();
        perfil.registrarVictoria();
        perfil.desbloquearLogro("Primera Victoria");
        EstadoPartida cargado = copiar(new EstadoPartida(perfil, gremio, Map.of("Goblin", 2)));
        mago.cancelarEfectos();   // detiene el hilo del efecto de la prueba

        Personaje merlina = cargado.getGremio().getMiembros().get(0);
        System.out.println("Despues de cargar: efectos de Merlina = " + merlina.getEfectos().size()
                + " (transient: vuelve vacio)");
        System.out.println("Victorias cargadas: " + cargado.getPerfil().getVictorias()
                + " | Goblins derrotados: " + cargado.getDerrotados().get("Goblin"));
        System.out.println("Merlina sigue en su gremio (ciclo bien serializado): "
                + (merlina.getGremio() == cargado.getGremio()));

        try {
            copiar(new ConRecursoRoto());
        } catch (NotSerializableException e) {
            System.out.println("NotSerializableException provocada por: " + e.getMessage());
        }
        copiar(new ConRecursoCorregido());
        System.out.println("Con transient en el campo, ya se guarda sin error.");
    }

    @SuppressWarnings("unchecked")
    private static <T> T copiar(T objeto) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream salida = new ObjectOutputStream(bytes)) {
            salida.writeObject(objeto);
        }
        try (ObjectInputStream entrada = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) entrada.readObject();
        }
    }
}
