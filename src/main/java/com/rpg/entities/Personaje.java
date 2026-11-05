package com.rpg.entities;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import com.rpg.concurrency.EfectoContinuoTask;
import com.rpg.entities.enums.EstadoPersonaje;
import com.rpg.entities.enums.TipoPersonaje;
import com.rpg.exceptions.SinManaException;
import com.rpg.items.Inventario;
import com.rpg.items.Pocion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

// TALLER ARCHIVOS: para guardar la partida con ObjectOutputStream, Personaje debe volverse Serializable
// (con serialVersionUID). Piensa que campos son transient y que clases anidadas (Inventario, Pocion, Arma,
// Gremio, EstadoEfecto, enums) tambien deben ser Serializable, o tendras NotSerializableException.
// Ojo: Gremio y Personaje se referencian entre si (la serializacion maneja el ciclo).
public abstract class Personaje implements ICombatiente, Serializable {
    private static final long serialVersionUID = 1L;


    private static final int VIDA_BASE = 100;
    private static final int ATAQUE_BASE = 10;
    private static final int DEFENSA_BASE = 5;
    private static final int CAPACIDAD_INVENTARIO = 3;
    private static final int DANIO_MINIMO = 1;
    private static final double PORCENTAJE_TOTAL = 100.0;
    private static final long INTERVALO_EFECTO_MS = 1000;

    private String nombre;
    private final TipoPersonaje tipo;
    private int vidaMaxima;
    private int vida;   // vida, estado y efectos se tocan siempre dentro de metodos synchronized
    private final int ataqueBase;
    private final int defensaBase;
    private EstadoPersonaje estado = EstadoPersonaje.VIVO;

    private Arma arma;                  // asociacion 0..1
    private Gremio gremio;              // asociacion bidireccional 0..1
    private final Inventario<Pocion> inventario = new Inventario<>(CAPACIDAD_INVENTARIO); // composicion
    private final Set<String> habilidades = new HashSet<>();
    // transient: los efectos viven en hilos que no se pueden guardar en disco. Al cargar la partida estos campos
    // vuelven a su valor por defecto (vacios) y readObject los recrea.
    private transient Map<String, EstadoEfecto> efectos = new LinkedHashMap<>();
    private transient Map<String, Thread> hilosEfectos = new HashMap<>();   // un hilo por efecto activo
    private transient volatile Consumer<String> observadorEfectos = System.out::println;

    protected Personaje(String nombre, TipoPersonaje tipo) {
        this(nombre, tipo, VIDA_BASE, ATAQUE_BASE, DEFENSA_BASE);
    }

    protected Personaje(String nombre, TipoPersonaje tipo, int vidaMaxima, int ataqueBase, int defensaBase) {
        validarNombre(nombre);
        if (vidaMaxima <= 0 || ataqueBase < 0 || defensaBase < 0) {
            throw new IllegalArgumentException("Estadisticas invalidas para " + nombre);
        }
        this.nombre = nombre;
        this.tipo = tipo;
        this.vidaMaxima = vidaMaxima;
        this.vida = vidaMaxima;
        this.ataqueBase = ataqueBase;
        this.defensaBase = defensaBase;
    }

    private static void validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre no puede estar vacio");
        }
    }

    // ---- combate

    /**
     * Ataque especial propio de cada clase.
     * @return descripcion de lo ocurrido, para el registro de combate
     */
    public abstract String realizarAtaqueEspecial(Personaje objetivo) throws SinManaException;

    /** Hook que se ejecuta al comenzar el turno (por ejemplo, el mago regenera mana). */
    public void alIniciarTurno() {
    }

    public int calcularDanio() {
        double bruto = ataqueBase + tipo.getBonificadorAtaque();
        if (arma != null) {
            bruto += arma.getDanioEfectivo();
        }
        return (int) Math.round(bruto);
    }

    @Override
    public void defender(int danoRecibido) {
        // Con enteros, 100 / 123 daria 0: por eso se divide con un double
        double factorMitigacion = PORCENTAJE_TOTAL / (PORCENTAJE_TOTAL + getDefensa());
        int danioFinal = (int) Math.round(danoRecibido * factorMitigacion);
        recibirDanio(Math.max(DANIO_MINIMO, danioFinal));
    }

    // ---- vida

    public synchronized void recibirDanio(int cantidad) {
        if (cantidad < 0) {
            throw new IllegalArgumentException("El danio no puede ser negativo");
        }
        // synchronized: leer la vida, calcular y escribir (con setVida, que tambien cambia el estado) es una sola
        // operacion. AtomicInteger.addAndGet no basta porque setVida acota la vida a 0..vidaMaxima y cambia estado.
        setVida(vida - cantidad);
    }

    /** @return vida realmente recuperada */
    public synchronized int curar(int cantidad) {
        if (cantidad < 0) {
            throw new IllegalArgumentException("La curacion no puede ser negativa");
        }
        if (!estaVivo()) {
            return 0;
        }
        int vidaAntes = vida;
        setVida(vida + cantidad);
        return vida - vidaAntes;
    }

    public synchronized void restaurarVida() {
        cancelarEfectos();
        estado = EstadoPersonaje.VIVO;
        vida = vidaMaxima;
    }

    @Override
    public synchronized boolean estaVivo() {
        return estado != EstadoPersonaje.MUERTO;
    }

    /** Se guarda con el candado tomado para que ningun hilo cambie la vida a mitad de la copia. */
    private synchronized void writeObject(ObjectOutputStream salida) throws IOException {
        salida.defaultWriteObject();
    }

    /** Los campos transient no se restauran solos: se recrean vacios y el estado se normaliza. */
    private void readObject(ObjectInputStream entrada) throws IOException, ClassNotFoundException {
        entrada.defaultReadObject();
        efectos = new LinkedHashMap<>();
        hilosEfectos = new HashMap<>();
        observadorEfectos = System.out::println;
        normalizarEstado();
    }

    // ---- efectos DoT / HoT (cada efecto corre en su propio hilo, ver EfectoContinuoTask)

    /** Quien recibe los mensajes de los efectos (normalmente el registro de combate). */
    public void setObservadorEfectos(Consumer<String> observador) {
        this.observadorEfectos = observador;
    }

    public synchronized void aplicarEfecto(EstadoEfecto efecto) {
        if (!estaVivo()) {
            return;
        }
        EstadoEfecto copia = efecto.copia();
        detenerHilo(copia.getNombre());          // si ya habia uno con ese nombre, se reemplaza
        efectos.put(copia.getNombre(), copia);
        if (copia.esDanio() && estado == EstadoPersonaje.VIVO) {
            estado = EstadoPersonaje.ENVENENADO;
        }
        Thread hilo = new Thread(new EfectoContinuoTask(this, copia, INTERVALO_EFECTO_MS),
                "efecto-" + copia.getNombre() + "-" + nombre);
        hilosEfectos.put(copia.getNombre(), hilo);
        hilo.start();
    }

    /** Quita un efecto por nombre e interrumpe su hilo (antidoto, purificacion). */
    public synchronized boolean quitarEfecto(String nombreEfecto) {
        if (efectos.remove(nombreEfecto) == null) {
            return false;
        }
        detenerHilo(nombreEfecto);
        normalizarEstado();
        return true;
    }

    public synchronized void quitarEfectosDanio() {
        for (String nombreEfecto : new ArrayList<>(efectos.keySet())) {
            if (efectos.get(nombreEfecto).esDanio()) {
                quitarEfecto(nombreEfecto);
            }
        }
    }

    /** Interrumpe todos los hilos de efectos (fin del combate). */
    public synchronized void cancelarEfectos() {
        for (String nombreEfecto : new ArrayList<>(hilosEfectos.keySet())) {
            detenerHilo(nombreEfecto);
        }
        efectos.clear();
        normalizarEstado();
    }

    /**
     * Un tick del efecto, llamado desde su hilo.
     * @return false si el efecto ya no debe seguir (termino, fue quitado o reemplazado, o el personaje cayo)
     */
    public synchronized boolean aplicarTickEfecto(EstadoEfecto efecto) {
        if (efectos.get(efecto.getNombre()) != efecto || !estaVivo()) {
            return false;
        }
        if (efecto.esDanio()) {
            recibirDanio(efecto.getValorPorTick());
            observadorEfectos.accept(String.format("%s sufre %d de dano por %s",
                    nombre, efecto.getValorPorTick(), efecto.getNombre()));
        } else {
            int curado = curar(efecto.getValorPorTick());
            observadorEfectos.accept(String.format("%s recupera %d de vida por %s", nombre, curado, efecto.getNombre()));
        }
        efecto.consumirTurno();
        if (efecto.haTerminado()) {
            efectos.remove(efecto.getNombre());
            observadorEfectos.accept(nombre + ": termina " + efecto.getNombre());
            normalizarEstado();
            return false;
        }
        if (!estaVivo()) {
            cancelarEfectos();
            return false;
        }
        return true;
    }

    /** Limpieza cuando el hilo de un efecto es interrumpido: solo quita ESE efecto (no uno que lo reemplazo). */
    public synchronized void limpiarEfecto(EstadoEfecto efecto) {
        if (efectos.get(efecto.getNombre()) == efecto) {
            efectos.remove(efecto.getNombre());
        }
        normalizarEstado();
    }

    /** Se llama desde el finally del hilo: olvida su referencia si sigue siendo el hilo registrado. */
    public synchronized void liberarHiloEfecto(String nombreEfecto) {
        if (hilosEfectos.get(nombreEfecto) == Thread.currentThread()) {
            hilosEfectos.remove(nombreEfecto);
        }
    }

    private void detenerHilo(String nombreEfecto) {
        Thread hilo = hilosEfectos.remove(nombreEfecto);
        if (hilo != null) {
            hilo.interrupt();
        }
    }

    private boolean tieneEfectoDanio() {
        for (EstadoEfecto efecto : efectos.values()) {
            if (efecto.esDanio()) {
                return true;
            }
        }
        return false;
    }

    /** Si ya no queda ningun DoT, un personaje ENVENENADO vuelve a estar VIVO. */
    private void normalizarEstado() {
        if (estado == EstadoPersonaje.ENVENENADO && !tieneEfectoDanio()) {
            estado = EstadoPersonaje.VIVO;
        }
    }

    // ---- aturdimiento

    public synchronized void aturdir() {
        if (estaVivo()) {
            estado = EstadoPersonaje.ATURDIDO;
        }
    }

    /** @return true si estaba aturdido (y por tanto pierde este turno) */
    public synchronized boolean consumirAturdimiento() {
        boolean estabaAturdido = estado == EstadoPersonaje.ATURDIDO;
        if (estabaAturdido) {
            estado = tieneEfectoDanio() ? EstadoPersonaje.ENVENENADO : EstadoPersonaje.VIVO;
        }
        return estabaAturdido;
    }

    // ---- inventario, arma, gremio, habilidades

    public String usarPocion(Pocion pocion) {
        int curado = curar(pocion.getCuracionReal());
        if (pocion.getEfecto() != null) {
            aplicarEfecto(pocion.getEfecto());
        }
        if (pocion.isPurifica()) {
            quitarEfectosDanio();
        }
        return String.format("%s usa %s (+%d de vida)", nombre, pocion.getNombre(), curado);
    }

    public void equiparArma(Arma nuevaArma) {
        this.arma = nuevaArma;
    }

    public void desequiparArma() {
        this.arma = null;
    }

    public void unirseAGremio(Gremio nuevoGremio) {
        if (nuevoGremio == null || nuevoGremio == gremio) {
            return;
        }
        if (gremio != null) {
            gremio.removerMiembro(this);
        }
        gremio = nuevoGremio;
        nuevoGremio.agregarMiembro(this);
    }

    public void abandonarGremio() {
        Gremio anterior = gremio;
        if (anterior == null) {
            return;
        }
        gremio = null;
        anterior.removerMiembro(this);
    }

    /** @return false si la habilidad ya estaba aprendida (el Set rechaza duplicados) */
    public boolean aprenderHabilidad(String habilidad) {
        return habilidades.add(habilidad);
    }

    // ---- getters y setters validados

    public synchronized void setVida(int nuevaVida) {
        vida = Math.max(0, Math.min(nuevaVida, vidaMaxima));
        if (vida == 0) {
            estado = EstadoPersonaje.MUERTO;
        }
    }

    public void setNombre(String nombre) {
        validarNombre(nombre);
        this.nombre = nombre;
    }

    public synchronized void setVidaMaxima(int vidaMaxima) {
        if (vidaMaxima <= 0) {
            throw new IllegalArgumentException("La vida maxima debe ser positiva");
        }
        this.vidaMaxima = vidaMaxima;
        this.vida = Math.min(vida, vidaMaxima);
    }

    public int getDefensa() {
        return defensaBase + tipo.getBonificadorDefensa();
    }

    public String getNombre() {
        return nombre;
    }

    public TipoPersonaje getTipo() {
        return tipo;
    }

    public synchronized int getVida() {
        return vida;
    }

    public synchronized int getVidaMaxima() {
        return vidaMaxima;
    }

    public synchronized EstadoPersonaje getEstado() {
        return estado;
    }

    public Arma getArma() {
        return arma;
    }

    public Gremio getGremio() {
        return gremio;
    }

    public Inventario<Pocion> getInventario() {
        return inventario;
    }

    public Set<String> getHabilidades() {
        return Collections.unmodifiableSet(habilidades);
    }

    /** Copia de los efectos activos: asi nadie recorre el mapa mientras un hilo de efecto lo modifica. */
    public synchronized Collection<EstadoEfecto> getEfectos() {
        return Collections.unmodifiableList(new ArrayList<>(efectos.values()));
    }
}
