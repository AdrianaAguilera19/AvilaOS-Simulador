/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.disk;

import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.IOOperationType;
import avilaos.model.process.PCB;

/**
 * Solicitud que un proceso hace al disco
 * @author Antonella Ruiz
 */
public class DiskRequest {
    private final int targetBlock;
    private final IOOperationType operation;
    private final PCB requester;
    private final long arrivalCycle;
    private final int blocksToTransfer;
    private DiskSchedulingPolicy servedWith;

    public DiskRequest(int targetBlock, IOOperationType operation, PCB requester, long arrivalCycle, int blocksToTransfer) {
        if (targetBlock < 0) {
            throw new IllegalArgumentException("El bloque destino no puede ser negativo");
        }
        if (operation == null) {
            throw new IllegalArgumentException("La solicitud necesita una operacion");
        }
        if (requester == null) {
            throw new IllegalArgumentException("La solicitud necesita el proceso que la pide");
        }
        if (arrivalCycle < 0) {
            throw new IllegalArgumentException("El ciclo de llegada no puede ser negativo");
        }
        if (blocksToTransfer < 1) {
            throw new IllegalArgumentException("Hay que transferir al menos 1 bloque");
        }
        this.targetBlock = targetBlock;
        this.operation = operation;
        this.requester = requester;
        this.arrivalCycle = arrivalCycle;
        this.blocksToTransfer = blocksToTransfer;
    }

    public int getTargetBlock() { return targetBlock; }

    public IOOperationType getOperation() { return operation; }

    public PCB getRequester() { return requester; }

    public long getArrivalCycle() { return arrivalCycle; }

    public int getBlocksToTransfer() { return blocksToTransfer; }

    /** Politica con la que se atendio, o null si todavia no se atiende. */
    public DiskSchedulingPolicy getServedWith() { return servedWith; }

    public boolean isServed() { return servedWith != null; }

    /** Anota la politica con la que se atendio. Solo se puede una vez. */
    public void markServed(DiskSchedulingPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("Falta la politica con la que se atendio");
        }
        if (servedWith != null) {
            throw new IllegalStateException("La solicitud ya fue atendida con " + servedWith);
        }
        servedWith = policy;
    }

    @Override
    public String toString() {
        return requester.getName() + " " + operation + " bloque " + targetBlock
                + " (llegada " + arrivalCycle + ", " + blocksToTransfer + " bloques)";
    }
}