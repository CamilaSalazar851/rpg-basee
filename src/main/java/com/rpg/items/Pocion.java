package com.rpg.items;

import java.io.Serializable;

import com.rpg.entities.EstadoEfecto;
import com.rpg.entities.enums.RaridadItem;

// TALLER ARCHIVOS: candidata a Serializable (guarda un EstadoEfecto, que tambien debe serlo).
public class Pocion implements Serializable {
    private static final long serialVersionUID = 1L;


    private final String nombre;
    private final int curacion;
    private final RaridadItem raridad;
    private final EstadoEfecto efecto;
    private final boolean purifica;

    public Pocion(String nombre, int curacion, RaridadItem raridad) {
        this(nombre, curacion, raridad, null, false);
    }

    /**
     * @param efecto   efecto que se aplica al usarla (por ejemplo un HoT), o null
     * @param purifica si es true, elimina los efectos de dano continuo (antidoto)
     */
    public Pocion(String nombre, int curacion, RaridadItem raridad, EstadoEfecto efecto, boolean purifica) {
        this.nombre = nombre;
        this.curacion = curacion;
        this.raridad = raridad;
        this.efecto = efecto;
        this.purifica = purifica;
    }

    public String getNombre() {
        return nombre;
    }

    public int getCuracionReal() {
        return (int) (curacion * raridad.getMultiplicadorStat());
    }

    public EstadoEfecto getEfecto() {
        return efecto;
    }

    public boolean isPurifica() {
        return purifica;
    }
}
