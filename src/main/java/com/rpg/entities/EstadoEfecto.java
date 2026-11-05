package com.rpg.entities;

import java.io.Serializable;

import com.rpg.entities.enums.TipoEfecto;

/** Efecto periodico (DoT o HoT): cambia la vida un valor fijo en cada tick durante cierta duracion. */
// TALLER ARCHIVOS: candidata a Serializable (los efectos activos de Personaje y de Pocion lo referencian).
public class EstadoEfecto implements Serializable {
    private static final long serialVersionUID = 1L;


    private final String nombre;
    private final TipoEfecto tipo;
    private final int valorPorTick;
    private int turnosRestantes;

    public EstadoEfecto(String nombre, TipoEfecto tipo, int valorPorTick, int duracion) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.valorPorTick = valorPorTick;
        this.turnosRestantes = duracion;
    }

    public synchronized EstadoEfecto copia() {
        return new EstadoEfecto(nombre, tipo, valorPorTick, turnosRestantes);
    }

    public synchronized void consumirTurno() {
        turnosRestantes--;
    }

    public synchronized boolean haTerminado() {
        return turnosRestantes <= 0;
    }

    public boolean esDanio() {
        return tipo == TipoEfecto.DANIO_CONTINUO;
    }

    public String getNombre() {
        return nombre;
    }

    public int getValorPorTick() {
        return valorPorTick;
    }

    public synchronized int getTurnosRestantes() {
        return turnosRestantes;
    }
}
