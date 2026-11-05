package com.rpg.entities;

import com.rpg.entities.enums.TipoPersonaje;

/** Monstruo del bestiario. Su ataque especial pega fuerte y puede envenenar, quemar o aturdir. */
public class Enemigo extends Personaje {
    private static final long serialVersionUID = 1L;


    private static final double MULTIPLICADOR_ESPECIAL = 1.5;

    private final int ataque;
    private final EstadoEfecto efectoEspecial;
    private final boolean aturde;

    public Enemigo(String nombre, int vida, int ataque, int defensa) {
        this(nombre, vida, ataque, defensa, null, false);
    }

    public Enemigo(String nombre, int vida, int ataque, int defensa, EstadoEfecto efectoEspecial, boolean aturde) {
        super(nombre, TipoPersonaje.MONSTRUO, vida, ataque, defensa);
        this.ataque = ataque;
        this.efectoEspecial = efectoEspecial;
        this.aturde = aturde;
    }

    /** Nueva instancia con la vida llena: el bestiario guarda plantillas y se combate con copias. */
    public Enemigo copia() {
        return new Enemigo(getNombre(), getVidaMaxima(), ataque, getDefensa(), efectoEspecial, aturde);
    }

    @Override
    public String realizarAtaqueEspecial(Personaje objetivo) {
        int vidaAntes = objetivo.getVida();
        objetivo.defender((int) Math.round(calcularDanio() * MULTIPLICADOR_ESPECIAL));
        StringBuilder mensaje = new StringBuilder(String.format("%s ataca con furia a %s y causa %d de dano",
                getNombre(), objetivo.getNombre(), vidaAntes - objetivo.getVida()));
        if (efectoEspecial != null) {
            objetivo.aplicarEfecto(efectoEspecial);
            mensaje.append(", aplicando ").append(efectoEspecial.getNombre());
        }
        if (aturde) {
            objetivo.aturdir();
            mensaje.append(" y deja aturdido a ").append(objetivo.getNombre());
        }
        return mensaje.toString();
    }

    public int getAtaque() {
        return ataque;
    }

    public String getHabilidad() {
        if (efectoEspecial != null) {
            return efectoEspecial.getNombre();
        }
        return aturde ? "Aturdir" : "Ninguna";
    }
}
