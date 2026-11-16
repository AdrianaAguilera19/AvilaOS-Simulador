/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.disk;

import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.IOOperationType;

/**
 * Registro de una solicitud de disco ya atendida: llegada, inicio, fin, bloque, politica activa
 * y desplazamiento del cabezal. Es inmutable: las metricas se calculan solo a partir de estos registros.
 * @author Antonella Ruiz
 */
public final class DiskRecord {
    private final int number;
    private final String processName;
    private final IOOperationType operation;
    private final int targetBlock;
    private final DiskSchedulingPolicy policy;
    private final long arrivalCycle;
    private final long startCycle;
    private final long endCycle;
    private final int headBefore;

    DiskRecord(int number, String processName, IOOperationType operation, int targetBlock,
            DiskSchedulingPolicy policy, long arrivalCycle, long startCycle, long endCycle, int headBefore) {
        this.number = number;
        this.processName = processName;
        this.operation = operation;
        this.targetBlock = targetBlock;
        this.policy = policy;
        this.arrivalCycle = arrivalCycle;
        this.startCycle = startCycle;
        this.endCycle = endCycle;
        this.headBefore = headBefore;
    }

    /** Numero de orden de atencion (1 = la primera que termino). */
    public int getNumber() { return number; }

    public String getProcessName() { return processName; }

    public IOOperationType getOperation() { return operation; }

    public int getTargetBlock() { return targetBlock; }

    /** Politica activa cuando se atendio esta solicitud. */
    public DiskSchedulingPolicy getPolicy() { return policy; }

    public long getArrivalCycle() { return arrivalCycle; }

    public long getStartCycle() { return startCycle; }

    public long getEndCycle() { return endCycle; }

    /** Posicion del cabezal justo antes de empezar a atenderla. */
    public int getHeadBefore() { return headBefore; }

    /** Bloques que recorrio el cabezal para llegar: |bloque destino - posicion anterior|. */
    public int getDisplacement() { return Math.abs(targetBlock - headBefore); }

    /** Ciclos que espero en la cola: inicio - llegada. */
    public long getWaitCycles() { return startCycle - arrivalCycle; }

    /** Ciclos que el disco tardo en atenderla: fin - inicio. */
    public long getServiceCycles() { return endCycle - startCycle; }

    @Override
    public String toString() {
        return "#" + number + " " + processName + " " + operation + " bloque " + targetBlock + " (" + policy
                + ", llegada " + arrivalCycle + ", inicio " + startCycle + ", fin " + endCycle
                + ", desplazamiento " + getDisplacement() + ")";
    }
}