package com.rpg.core;

import com.rpg.concurrency.AutoGuardadoDaemon;
import com.rpg.concurrency.GestorTurno;
import com.rpg.entities.Enemigo;
import com.rpg.entities.EstadoEfecto;
import com.rpg.entities.Gremio;
import com.rpg.entities.Guerrero;
import com.rpg.entities.Mago;
import com.rpg.entities.PerfilJugador;
import com.rpg.persistencia.EstadoPartida;
import com.rpg.persistencia.GestorArchivos;
import com.rpg.persistencia.LogCombateArchivo;
import com.rpg.persistencia.PartidaRepositorio;
import com.rpg.entities.Personaje;
import com.rpg.entities.enums.RaridadItem;
import com.rpg.entities.enums.TipoEfecto;
import com.rpg.exceptions.CapacidadExcedidaException;
import com.rpg.items.Inventario;
import com.rpg.items.Pocion;

import java.util.List;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** Coordina el flujo del juego: crear heroe, combatir, torneo, bestiario y guardado. */
public class MotorJuego {

    private static final String NOMBRE_GREMIO = "Guardianes del Alba";
    private static final int CURACION_POCION_VIDA = 30;
    private static final int TIEMPO_TURNO_SEGUNDOS = 15;
    private static final long INTERVALO_AUTOGUARDADO_MS = 10_000;

    private final Consola consola;
    private final RegistroCombate registro = new RegistroCombate();
    private final Arena arena = new Arena(registro, new Random());
    private final Bestiario bestiario = new Bestiario();
    private volatile PerfilJugador perfil = new PerfilJugador();   // se reemplazan al cargar una partida
    private volatile Gremio gremio = new Gremio(NOMBRE_GREMIO);
    private final PartidaRepositorio repositorio = new PartidaRepositorio();
    private final LogCombateArchivo logArchivo = new LogCombateArchivo();
    // El auto-guardado solo escribe a disco si ya hubo progreso: asi no pisa una partida guardada antes de cargarla.
    private final AtomicBoolean partidaModificada = new AtomicBoolean(false);
    private final TorneoManager torneo = new TorneoManager(arena, registro);
    // TALLER ARCHIVOS: ultimoGuardado es solo el estado de la partida en memoria. Aqui (o en un metodo
    // nuevo como cargarProgreso) iria la carga de una partida guardada en disco; llamala desde Main.
    private volatile String ultimoGuardado = "";   // lo escribe el hilo de auto-guardado y lo lee el principal
    private final GestorTurno gestorTurno = new GestorTurno();
    private final AtomicBoolean tiempoAgotado = new AtomicBoolean(false);
    private volatile Personaje jugadorActivo;
    private volatile Enemigo enemigoActivo;

    public MotorJuego(Consola consola) {
        this.consola = consola;
    }

    // ---- opciones del menu principal

    public void combateRapido() {
        registro.limpiar();
        Personaje jugador = crearJugador();
        Enemigo enemigo = elegirEnemigo();
        registro.agregar("%n=== %s vs %s ===", jugador.getNombre(), enemigo.getNombre());
        jugadorActivo = jugador;
        enemigoActivo = enemigo;

        boolean huyo = combatir(jugador, enemigo);

        // Fin del combate: se interrumpen los hilos de efectos que sigan vivos.
        jugador.cancelarEfectos();
        enemigo.cancelarEfectos();
        cerrarCombate(jugador, enemigo, huyo);
        guardarProgreso();
    }

    public void jugarTorneo() {
        registro.limpiar();
        Personaje campeon = torneo.jugarTorneo();
        registro.agregar("%nCAMPEON DEL TORNEO: %s", campeon.getNombre());
        perfil.desbloquearLogro("Campeon del Torneo");
        guardarProgreso();
    }

    public void mostrarBestiario() {
        System.out.println("\n=== BESTIARIO ===");
        System.out.print(bestiario.describir());
        System.out.printf("Victorias: %d | Derrotas: %d | Logros: %s%n",
                perfil.getVictorias(), perfil.getDerrotas(), perfil.getLogros());
    }

    // ---- creacion de personajes

    private Personaje crearJugador() {
        int clase = consola.leerEntero("Clase (1. Guerrero, 2. Mago): ", 1, 2);
        String nombre = consola.leerNombre("Nombre del heroe: ");
        Personaje jugador = switch (clase) {
            case 1 -> new Guerrero(nombre);
            default -> new Mago(nombre);
        };
        entregarKitInicial(jugador);
        jugador.unirseAGremio(gremio);
        System.out.printf("%s se une al gremio %s (%d miembros).%n",
                jugador.getNombre(), gremio.getNombre(), gremio.getMiembros().size());
        return jugador;
    }

    private void entregarKitInicial(Personaje jugador) {
        List<Pocion> kit = List.of(
                new Pocion("Pocion de Vida", CURACION_POCION_VIDA, RaridadItem.COMUN),
                new Pocion("Elixir de Regeneracion", 0, RaridadItem.RARO,
                        new EstadoEfecto("Regeneracion", TipoEfecto.CURACION_CONTINUA, 6, 3), false),
                new Pocion("Antidoto", 0, RaridadItem.COMUN, null, true));
        try {
            for (Pocion pocion : kit) {
                jugador.getInventario().agregar(pocion);
            }
        } catch (CapacidadExcedidaException e) {
            System.out.println("No se pudo entregar todo el kit: " + e.getMessage());
        }
    }

    private Enemigo elegirEnemigo() {
        List<String> nombres = bestiario.nombres();
        System.out.println("Rivales disponibles:");
        for (int i = 0; i < nombres.size(); i++) {
            System.out.printf("  %d. %s%n", i + 1, nombres.get(i));
        }
        while (true) {
            String texto = consola.leerLinea("Rival (numero o nombre): ");
            Enemigo enemigo = resolverRival(texto, nombres);
            if (enemigo != null) {
                return enemigo;
            }
            System.out.println("No existe ese rival.");
        }
    }

    private Enemigo resolverRival(String texto, List<String> nombres) {
        try {
            int numero = Integer.parseInt(texto);
            boolean valido = numero >= 1 && numero <= nombres.size();
            return valido ? bestiario.crearEnemigo(nombres.get(numero - 1)) : null;
        } catch (NumberFormatException e) {
            return bestiario.crearEnemigo(texto);
        }
    }

    // ---- bucle de combate

    /** @return true si el jugador huyo */
    private boolean combatir(Personaje jugador, Enemigo enemigo) {
        int ronda = 1;
        while (jugador.estaVivo() && enemigo.estaVivo()) {
            registro.agregar("%n-- Ronda %d --", ronda);
            if (arena.iniciarTurno(jugador) && turnoJugador(jugador, enemigo)) {
                return true;
            }
            arena.turnoAutomatico(enemigo, jugador);
            ronda++;
        }
        return false;
    }

    /**
     * Turno del jugador. Las acciones que no gastan turno (ver estado, cancelar) vuelven a pedir.
     * @return true si el jugador decidio huir
     */
    private boolean turnoJugador(Personaje jugador, Enemigo enemigo) {
        boolean turnoGastado;
        do {
            registro.agregar(FormatoCombate.resumen(jugador));
            registro.agregar(FormatoCombate.resumen(enemigo));
            Accion accion = pedirAccion();
            if (accion == Accion.HUIR) {
                return true;
            }
            turnoGastado = switch (accion) {
                case ATACAR -> {
                    arena.atacar(jugador, enemigo);
                    yield true;
                }
                case ESPECIAL -> arena.intentarEspecial(jugador, enemigo);
                case POCION -> usarPocion(jugador);
                case TIEMPO_AGOTADO -> {
                    registro.agregar("%s se quedo sin tiempo y pierde el turno", jugador.getNombre());
                    yield true;
                }
                default -> {
                    mostrarEstado(jugador);
                    yield false;
                }
            };
        } while (!turnoGastado && enemigo.estaVivo());
        return false;
    }

    /**
     * Pide la accion al jugador con un temporizador de turno. Si responde a tiempo se interrumpe el
     * temporizador; si no, el callback avisa y el turno se pierde. Ojo: Scanner no se puede abortar mientras
     * espera el teclado, asi que tras el aviso hay que pulsar Enter para que el juego continue.
     */
    private Accion pedirAccion() {
        tiempoAgotado.set(false);
        System.out.printf("Tienes %d segundos para elegir.%n", TIEMPO_TURNO_SEGUNDOS);
        System.out.println("1. Atacar  2. Habilidad especial  3. Usar pocion  4. Ver estado  0. Huir");
        gestorTurno.iniciar(TIEMPO_TURNO_SEGUNDOS, this::alAgotarseElTiempo);
        int opcion;
        try {
            opcion = consola.leerEntero("Accion: ", 0, 4);
        } finally {
            gestorTurno.cancelar();
        }
        if (tiempoAgotado.get()) {
            return Accion.TIEMPO_AGOTADO;
        }
        return switch (opcion) {
            case 1 -> Accion.ATACAR;
            case 2 -> Accion.ESPECIAL;
            case 3 -> Accion.POCION;
            case 4 -> Accion.ESTADO;
            default -> Accion.HUIR;
        };
    }

    /** Callback del temporizador: corre en el hilo del temporizador, por eso usa un AtomicBoolean. */
    private void alAgotarseElTiempo() {
        tiempoAgotado.set(true);
        System.out.println("\nSe acabo el tiempo! Pulsa Enter para continuar.");
    }

    private boolean usarPocion(Personaje jugador) {
        Inventario<Pocion> inventario = jugador.getInventario();
        if (inventario.estaVacio()) {
            System.out.println("No tienes pociones.");
            return false;
        }
        int numero = 1;
        for (Pocion pocion : inventario) {
            System.out.printf("  %d. %s%n", numero++, pocion.getNombre());
        }
        int eleccion = consola.leerEntero("Pocion (0 = cancelar): ", 0, inventario.tamanio());
        if (eleccion == 0) {
            return false;
        }
        registro.agregar(jugador.usarPocion(inventario.quitar(eleccion - 1)));
        return true;
    }

    private void mostrarEstado(Personaje jugador) {
        System.out.printf("Habilidades: %s | Arma: %s | Defensa: %d | Pociones: %d%n",
                jugador.getHabilidades(),
                jugador.getArma() != null ? jugador.getArma().getNombre() : "ninguna",
                jugador.getDefensa(), jugador.getInventario().tamanio());
        for (EstadoEfecto efecto : jugador.getEfectos()) {
            System.out.printf("Efecto activo: %s (%d turnos)%n", efecto.getNombre(), efecto.getTurnosRestantes());
        }
    }

    // ---- resultado del combate

    private void cerrarCombate(Personaje jugador, Enemigo enemigo, boolean huyo) {
        if (huyo) {
            registro.agregar("%s huye del combate.", jugador.getNombre());
        } else if (jugador.estaVivo()) {
            registro.agregar("%nVictoria de %s!", jugador.getNombre());
            perfil.registrarVictoria();
            bestiario.registrarEnemigoDerrotado(enemigo.getNombre());
            desbloquearLogros(jugador, enemigo);
            repartirBotin(jugador);
        } else {
            registro.agregar("%n%s ha caido ante %s.", jugador.getNombre(), enemigo.getNombre());
            perfil.registrarDerrota();
        }
    }

    private void desbloquearLogros(Personaje jugador, Enemigo enemigo) {
        boolean intacto = jugador.getVida() == jugador.getVidaMaxima();
        List<String> candidatos = List.of("Primera Victoria", "Cazador de " + enemigo.getNombre(),
                intacto ? "Intocable" : "");
        for (String logro : candidatos) {
            if (!logro.isEmpty() && perfil.desbloquearLogro(logro)) {
                registro.agregar("Logro desbloqueado: %s", logro);
            }
        }
    }

    private void repartirBotin(Personaje jugador) {
        try {
            jugador.getInventario().agregar(new Pocion("Pocion de Vida", CURACION_POCION_VIDA, RaridadItem.COMUN));
            registro.agregar("Botin: Pocion de Vida.");
        } catch (CapacidadExcedidaException e) {
            registro.agregar("Botin perdido: %s", e.getMessage());
        }
    }

    // ---- guardado

    /**
     * Guarda las estadisticas en memoria (el texto queda en {@code ultimoGuardado}); aun no se escribe ningun
     * archivo. Hoy solo se invoca al terminar una pelea.
     */
    public void guardarProgreso() {
        // TALLER ARCHIVOS: ademas de dejar el texto en memoria, escribe la partida en disco (por ejemplo un
        // .txt con BufferedWriter, y/o un .dat serializando el estado con ObjectOutputStream). Usa try-with-resources.
        ultimoGuardado = construirInstantanea();
        System.out.println("[Progreso guardado en memoria]");
        partidaModificada.set(true);
        if (repositorio.guardar(crearEstado())) {
            System.out.println("[Partida guardada en disco: " + GestorArchivos.PARTIDA.getPath() + "]");
        }
        logArchivo.guardar(registro.getTexto(), true);
    }

    private EstadoPartida crearEstado() {
        return new EstadoPartida(perfil, gremio, bestiario.copiaDerrotados());
    }

    /** Al salir: si hubo progreso, deja la partida en disco (el candado evita chocar con el auto-guardado). */
    public void guardarAlSalir() {
        if (partidaModificada.get()) {
            repositorio.guardar(crearEstado());
        }
    }

    public void cargarPartida() {
        Optional<EstadoPartida> cargada = repositorio.cargar();
        if (cargada.isEmpty()) {
            return;
        }
        EstadoPartida estado = cargada.get();
        perfil = estado.getPerfil();
        gremio = estado.getGremio();
        bestiario.cargarDerrotados(estado.getDerrotados());
        partidaModificada.set(true);
        System.out.printf("Partida cargada: %d victorias, %d derrotas, gremio %s con %d miembros.%n",
                perfil.getVictorias(), perfil.getDerrotas(), gremio.getNombre(), gremio.getMiembros().size());
    }

    public void mostrarHistorial() {
        List<String> lineas = logArchivo.leer();
        if (lineas.isEmpty()) {
            System.out.println("El historial esta vacio.");
            return;
        }
        System.out.println("\n=== HISTORIAL DE COMBATES ===");
        for (String linea : lineas) {
            System.out.println(linea);
        }
    }

    public void mostrarInfoArchivos() {
        System.out.println("\n=== ARCHIVOS DEL JUEGO ===");
        System.out.println(GestorArchivos.describir(GestorArchivos.PARTIDA));
        System.out.println(GestorArchivos.describir(GestorArchivos.LOG));
    }

    /** Borra la partida o el historial, pidiendo confirmacion antes. */
    public void borrarArchivos() {
        System.out.println("1. Borrar partida guardada  2. Borrar historial  0. Cancelar");
        int opcion = consola.leerEntero("Opcion: ", 0, 2);
        if (opcion == 0) {
            return;
        }
        String respuesta = consola.leerLinea("Seguro que quieres borrarlo? (s/n): ").trim();
        if (!respuesta.equalsIgnoreCase("s")) {
            System.out.println("No se borro nada.");
            return;
        }
        boolean borrado = opcion == 1
                ? GestorArchivos.borrarConNio(GestorArchivos.PARTIDA)
                : GestorArchivos.borrarConFile(GestorArchivos.LOG);
        if (borrado) {
            System.out.println("Archivo borrado.");
        }
    }

    /** Arranca el hilo daemon que guarda la partida en memoria cada cierto tiempo, sin molestar al jugador. */
    public void iniciarAutoGuardado() {
        new AutoGuardadoDaemon(INTERVALO_AUTOGUARDADO_MS, this::autoGuardar).iniciar();
    }

    /** Corre en el hilo daemon: copia en memoria y, si hay progreso, tambien a disco. */
    private void autoGuardar() {
        ultimoGuardado = construirInstantanea();
        if (partidaModificada.get()) {
            repositorio.guardar(crearEstado());
        }
    }

    private String construirInstantanea() {
        StringBuilder contenido = new StringBuilder();
        contenido.append("=== PARTIDA RPG ===").append(System.lineSeparator());
        contenido.append("Victorias: ").append(perfil.getVictorias()).append(System.lineSeparator());
        contenido.append("Derrotas: ").append(perfil.getDerrotas()).append(System.lineSeparator());
        contenido.append("Logros: ").append(String.join(", ", perfil.getLogros())).append(System.lineSeparator());
        contenido.append("Gremio ").append(gremio.getNombre()).append(": ")
                .append(gremio.getMiembros().size()).append(" miembros").append(System.lineSeparator());
        contenido.append("Enemigos derrotados: ").append(bestiario.resumenDerrotados()).append(System.lineSeparator());
        Personaje jugador = jugadorActivo;
        Enemigo enemigo = enemigoActivo;
        if (jugador != null && enemigo != null) {
            contenido.append(System.lineSeparator()).append("--- Personajes ---").append(System.lineSeparator());
            contenido.append(FormatoCombate.resumen(jugador)).append(System.lineSeparator());
            contenido.append(FormatoCombate.resumen(enemigo)).append(System.lineSeparator());
        }
        contenido.append(System.lineSeparator()).append("--- Ultimo combate ---").append(System.lineSeparator());
        contenido.append(registro.getTexto());
        return contenido.toString();
    }

    public String getUltimoGuardado() {
        return ultimoGuardado;
    }
}
