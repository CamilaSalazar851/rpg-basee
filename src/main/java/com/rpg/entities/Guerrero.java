package com.rpg.entities;

import com.rpg.entities.enums.RaridadItem;
import com.rpg.entities.enums.TipoPersonaje;
import com.rpg.exceptions.VidaInsuficienteException;

public class Guerrero extends Personaje {
    private static final long serialVersionUID = 1L;


    private static final int VIDA = 120;
    private static final int ATAQUE = 18;
    private static final int DEFENSA = 10;
    private static final int FUERZA_ESCUDO_INICIAL = 8;
    private static final int COSTO_VIDA_ESPECIAL = 10;
    private static final int MULTIPLICADOR_ESPECIAL = 2;
    private static final String NOMBRE_ESPECIAL = "Golpe Brutal";

    private final int fuerzaEscudo;

    public Guerrero(String nombre) {
        this(nombre, FUERZA_ESCUDO_INICIAL);
    }

    public Guerrero(String nombre, int fuerzaEscudo) {
        super(nombre, TipoPersonaje.GUERRERO, VIDA, ATAQUE, DEFENSA);
        this.fuerzaEscudo = fuerzaEscudo;
        aprenderHabilidad(NOMBRE_ESPECIAL);
        equiparArma(new Arma("Espada de Hierro", 6, RaridadItem.COMUN));
    }

    @Override
    public int getDefensa() {
        return super.getDefensa() + fuerzaEscudo;
    }

    /** Golpe Brutal: pega el doble a cambio de sacrificar vida propia. */
    @Override
    public String realizarAtaqueEspecial(Personaje objetivo) {
        if (getVida() <= COSTO_VIDA_ESPECIAL) {
            throw new VidaInsuficienteException(getNombre(), COSTO_VIDA_ESPECIAL, getVida());
        }
        recibirDanio(COSTO_VIDA_ESPECIAL);
        int vidaAntes = objetivo.getVida();
        objetivo.defender(calcularDanio() * MULTIPLICADOR_ESPECIAL);
        return String.format("%s usa %s (-%d de vida propia) y causa %d de dano a %s",
                getNombre(), NOMBRE_ESPECIAL, COSTO_VIDA_ESPECIAL, vidaAntes - objetivo.getVida(), objetivo.getNombre());
    }
}
