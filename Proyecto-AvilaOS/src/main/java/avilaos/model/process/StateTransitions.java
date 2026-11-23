/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.model.process;

import avilaos.enums.ProcessState;

/**
 * S01: tabla de transiciones validas del modelo de siete estados.
 * Un proceso solo puede pasar de un estado a otro si la tabla lo permite; PCB.setState la consulta
 * y rechaza el resto. Quedarse en el mismo estado se acepta (no cambia nada).
 *
 * Transiciones permitidas:
 *   NEW               -> READY (admision), TERMINATED
 *   READY             -> RUNNING (despacho), TERMINATED
 *   RUNNING           -> READY (expulsion), BLOCKED, TERMINATED
 *   BLOCKED           -> READY (ocurre el evento), BLOCKED_SUSPENDED (suspension), TERMINATED
 *   BLOCKED_SUSPENDED -> READY_SUSPENDED (ocurre el evento en el swap), TERMINATED
 *   READY_SUSPENDED   -> READY (reactivacion), TERMINATED
 *   TERMINATED        -> ninguno
 */
public final class StateTransitions {

    private static final int N = ProcessState.values().length;
    private static final boolean[][] ALLOWED = new boolean[N][N];

    static {
        allow(ProcessState.NEW, ProcessState.READY);
        allow(ProcessState.NEW, ProcessState.TERMINATED);

        allow(ProcessState.READY, ProcessState.RUNNING);
        allow(ProcessState.READY, ProcessState.TERMINATED);

        allow(ProcessState.RUNNING, ProcessState.READY);
        allow(ProcessState.RUNNING, ProcessState.BLOCKED);
        allow(ProcessState.RUNNING, ProcessState.TERMINATED);

        allow(ProcessState.BLOCKED, ProcessState.READY);
        allow(ProcessState.BLOCKED, ProcessState.BLOCKED_SUSPENDED);
        allow(ProcessState.BLOCKED, ProcessState.TERMINATED);

        allow(ProcessState.BLOCKED_SUSPENDED, ProcessState.READY_SUSPENDED);
        allow(ProcessState.BLOCKED_SUSPENDED, ProcessState.TERMINATED);

        allow(ProcessState.READY_SUSPENDED, ProcessState.READY);
        allow(ProcessState.READY_SUSPENDED, ProcessState.TERMINATED);
    }

    private StateTransitions() {
    }

    private static void allow(ProcessState from, ProcessState to) {
        ALLOWED[from.ordinal()][to.ordinal()] = true;
    }

    /**
     * @return true si el proceso puede pasar de from a to (o si se queda en el mismo estado);
     *         false si la transicion no existe o alguno de los dos es null
     */
    public static boolean isAllowed(ProcessState from, ProcessState to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true;
        }
        return ALLOWED[from.ordinal()][to.ordinal()];
    }
}