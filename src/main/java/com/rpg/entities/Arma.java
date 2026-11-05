package com.rpg.entities;

import java.io.Serializable;

import com.rpg.entities.enums.RaridadItem;

// TALLER ARCHIVOS: candidata a Serializable (la referencia desde Personaje.arma la necesita).
public class Arma implements Serializable {
    private static final long serialVersionUID = 1L;


    private final String nombre;
    private final int danioBase;
    private final RaridadItem raridad;

    public Arma(String nombre, int danioBase, RaridadItem raridad) {
        if (danioBase < 0) {
            throw new IllegalArgumentException("El danio del arma no puede ser negativo");
        }
        this.nombre = nombre;
        this.danioBase = danioBase;
        this.raridad = raridad;
    }

    public double getDanioEfectivo() {
        return danioBase * raridad.getMultiplicadorStat();
    }

    public String getNombre() {
        return nombre;
    }
}
