package com.rpg.entities;

import com.rpg.entities.enums.RaridadItem;
import com.rpg.entities.enums.TipoEfecto;
import com.rpg.entities.enums.TipoPersonaje;
import com.rpg.exceptions.SinManaException;

public class Mago extends Personaje {
    private static final long serialVersionUID = 1L;


    private static final int VIDA = 80;
    private static final int ATAQUE = 14;
    private static final int DEFENSA = 3;
    private static final int MANA_MAXIMO = 60;
    private static final int COSTO_MANA_ESPECIAL = 20;
    private static final int REGENERACION_MANA = 5;
    private static final double MULTIPLICADOR_ESPECIAL = 1.5;
    private static final String NOMBRE_ESPECIAL = "Bola de Fuego";
    private static final EstadoEfecto QUEMADURA = new EstadoEfecto("Quemadura", TipoEfecto.DANIO_CONTINUO, 4, 3);

    private int mana;

    public Mago(String nombre) {
        this(nombre, MANA_MAXIMO);
    }

    public Mago(String nombre, int mana) {
        super(nombre, TipoPersonaje.MAGO, VIDA, ATAQUE, DEFENSA);
        this.mana = mana;
        aprenderHabilidad(NOMBRE_ESPECIAL);
        equiparArma(new Arma("Baston de Roble", 4, RaridadItem.RARO));
    }

    @Override
    public void alIniciarTurno() {
        mana = Math.min(MANA_MAXIMO, mana + REGENERACION_MANA);
    }

    /** Bola de Fuego: ignora la defensa del objetivo y lo deja ardiendo (DoT). */
    @Override
    public String realizarAtaqueEspecial(Personaje objetivo) throws SinManaException {
        if (mana < COSTO_MANA_ESPECIAL) {
            throw new SinManaException(COSTO_MANA_ESPECIAL, mana);
        }
        mana -= COSTO_MANA_ESPECIAL;
        int danio = (int) Math.round(calcularDanio() * MULTIPLICADOR_ESPECIAL);
        objetivo.recibirDanio(danio);
        objetivo.aplicarEfecto(QUEMADURA);
        return String.format("%s lanza %s (-%d de mana) y causa %d de dano a %s, que queda en llamas",
                getNombre(), NOMBRE_ESPECIAL, COSTO_MANA_ESPECIAL, danio, objetivo.getNombre());
    }

    public int getMana() {
        return mana;
    }
}
