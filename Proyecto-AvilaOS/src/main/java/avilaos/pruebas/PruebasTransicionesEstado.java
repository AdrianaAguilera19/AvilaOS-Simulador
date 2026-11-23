/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.enums.ProcessState;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;
import avilaos.model.process.StateTransitions;

/**
 * S01: la tabla de transiciones del modelo de siete estados. Comprueba las 49 combinaciones
 * origen/destino contra la tabla esperada y que PCB.setState rechaza las invalidas.
 * Se ejecuta como programa (main); termina con "TODAS LAS PRUEBAS PASARON" o con codigo 1.
 */
public class PruebasTransicionesEstado {

    private static int fallas = 0;
    private static int verificaciones = 0;

    private static final ProcessState NEW = ProcessState.NEW;
    private static final ProcessState READY = ProcessState.READY;
    private static final ProcessState RUNNING = ProcessState.RUNNING;
    private static final ProcessState BLOCKED = ProcessState.BLOCKED;
    private static final ProcessState READY_S = ProcessState.READY_SUSPENDED;
    private static final ProcessState BLOCKED_S = ProcessState.BLOCKED_SUSPENDED;
    private static final ProcessState TERMINATED = ProcessState.TERMINATED;

    /** Las unicas transiciones validas (sin contar quedarse igual). */
    private static final ProcessState[][] VALIDAS = {
        {NEW, READY}, {NEW, TERMINATED},
        {READY, RUNNING}, {READY, TERMINATED},
        {RUNNING, READY}, {RUNNING, BLOCKED}, {RUNNING, TERMINATED},
        {BLOCKED, READY}, {BLOCKED, BLOCKED_S}, {BLOCKED, TERMINATED},
        {BLOCKED_S, READY_S}, {BLOCKED_S, TERMINATED},
        {READY_S, READY}, {READY_S, TERMINATED}
    };

    public static void main(String[] args) {
        System.out.println("--- tabla completa (49 combinaciones) ---");
        ProcessState[] todos = ProcessState.values();
        for (int i = 0; i < todos.length; i++) {
            for (int j = 0; j < todos.length; j++) {
                boolean esperado = i == j || esValida(todos[i], todos[j]);
                check(StateTransitions.isAllowed(todos[i], todos[j]) == esperado,
                        todos[i] + " -> " + todos[j] + (esperado ? " permitida" : " rechazada"));
            }
        }
        check(!StateTransitions.isAllowed(null, READY), "origen null rechazado");
        check(!StateTransitions.isAllowed(READY, null), "destino null rechazado");

        System.out.println("--- PCB.setState rechaza las invalidas ---");
        PCB p = nuevo();
        check(!p.setState(RUNNING) && p.getState() == NEW, "NEW -> RUNNING rechazada, sigue en NEW");
        check(!p.setState(BLOCKED_S) && p.getState() == NEW, "NEW -> BLOCKED_SUSPENDED rechazada");
        check(p.setState(READY) && p.getState() == READY, "NEW -> READY aceptada");
        check(!p.setState(BLOCKED) && p.getState() == READY, "READY -> BLOCKED rechazada (hay que pasar por RUNNING)");
        check(!p.setState(READY_S) && p.getState() == READY, "READY -> READY_SUSPENDED rechazada (solo se suspende un bloqueado)");
        check(p.setState(RUNNING) && p.getState() == RUNNING, "READY -> RUNNING aceptada");
        check(p.setState(RUNNING) && p.getState() == RUNNING, "quedarse en RUNNING se acepta");
        check(p.setState(BLOCKED) && p.getState() == BLOCKED, "RUNNING -> BLOCKED aceptada");
        check(!p.setState(RUNNING) && p.getState() == BLOCKED, "BLOCKED -> RUNNING rechazada (un bloqueado vuelve por READY)");
        check(!p.setState(READY_S) && p.getState() == BLOCKED, "BLOCKED -> READY_SUSPENDED rechazada (pasa por BLOCKED_SUSPENDED)");

        System.out.println("--- camino de la suspension y la reactivacion ---");
        check(p.setState(BLOCKED_S) && p.getState() == BLOCKED_S, "BLOCKED -> BLOCKED_SUSPENDED");
        check(!p.setState(READY) && p.getState() == BLOCKED_S, "BLOCKED_SUSPENDED -> READY rechazada (primero debe cumplirse el evento)");
        check(p.setState(READY_S) && p.getState() == READY_S, "BLOCKED_SUSPENDED -> READY_SUSPENDED");
        check(!p.setState(BLOCKED_S) && p.getState() == READY_S, "READY_SUSPENDED -> BLOCKED_SUSPENDED rechazada");
        check(!p.setState(RUNNING) && p.getState() == READY_S, "READY_SUSPENDED -> RUNNING rechazada (primero vuelve a RAM)");
        check(p.setState(READY) && p.getState() == READY, "READY_SUSPENDED -> READY (reactivacion)");

        System.out.println("--- terminado es final ---");
        check(p.setState(TERMINATED) && p.getState() == TERMINATED, "READY -> TERMINATED");
        for (int i = 0; i < todos.length; i++) {
            if (todos[i] != TERMINATED) {
                check(!p.setState(todos[i]) && p.getState() == TERMINATED, "TERMINATED -> " + todos[i] + " rechazada");
            }
        }

        System.out.println("--- el deadline termina desde cualquier estado vivo ---");
        for (int i = 0; i < todos.length; i++) {
            if (todos[i] != TERMINATED) {
                check(StateTransitions.isAllowed(todos[i], TERMINATED), todos[i] + " -> TERMINATED permitida");
            }
        }

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
        } else {
            System.out.println("HAY " + fallas + " FALLAS de " + verificaciones + " verificaciones");
            System.exit(1);
        }
    }

    private static PCB nuevo() {
        return ProcessFactory.createCpuBound("p", 1, 10, 5, 100, 1, 0);
    }

    private static boolean esValida(ProcessState de, ProcessState a) {
        for (int i = 0; i < VALIDAS.length; i++) {
            if (VALIDAS[i][0] == de && VALIDAS[i][1] == a) {
                return true;
            }
        }
        return false;
    }

    private static void check(boolean ok, String texto) {
        verificaciones++;
        System.out.println((ok ? "  OK    " : "  FALLA ") + texto);
        if (!ok) {
            fallas++;
        }
    }
}