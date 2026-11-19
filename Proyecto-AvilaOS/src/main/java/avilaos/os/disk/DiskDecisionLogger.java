/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.disk;

import avilaos.enums.DiskSchedulingPolicy;
import avilaos.interfaces.DiskDecisionListener;
import avilaos.log.EventLogger;
import avilaos.log.LogEvent;
import avilaos.enums.LogCategory;
/**
 * Escribe en el log de eventos las decisiones del disco, con frases como
 * «El disco atiende la solicitud de P4 en el bloque 87 (SSTF)». Cada atencion, cada cambio de
 * politica y cada salto de C-SCAN deja una linea. Es global (computador SYSTEM) porque el disco
 * es uno solo para todo el sistema.
 * @author Antonella Ruiz
 */
public class DiskDecisionLogger implements DiskDecisionListener {
    private final EventLogger logger;
    private final int lastBlock;

    /**
     * @param logger registro global de eventos
     * @param totalBlocks tamano del disco, para decir a que bloque llega C-SCAN antes de saltar
     */
    public DiskDecisionLogger(EventLogger logger, int totalBlocks) {
        if (logger == null) {
            throw new IllegalArgumentException("Falta el registro de eventos");
        }
        if (totalBlocks < 1) {
            throw new IllegalArgumentException("El disco debe tener al menos un bloque");
        }
        this.logger = logger;
        this.lastBlock = totalBlocks - 1;
    }

    @Override
    public void onAttend(long cycle, DiskRequest request, int headPosition, DiskSchedulingPolicy policy) {
        logger.log(cycle, LogEvent.SYSTEM,  LogCategory.DISCO,"El disco atiende la solicitud de " + request.getRequester().getName()
                + " en el bloque " + request.getTargetBlock() + " (" + policy + ")");
    }

    @Override
    public void onPolicyChange(long cycle, DiskSchedulingPolicy oldPolicy, DiskSchedulingPolicy newPolicy, int pending) {
        logger.log(cycle, LogEvent.SYSTEM, LogCategory.DISCO, "El disco cambia de politica: " + oldPolicy + " -> " + newPolicy
                + " (" + pending + (pending == 1 ? " solicitud pendiente se reordena)" : " solicitudes pendientes se reordenan)"));
    }

    @Override
    public void onWrapAround(long cycle, int fromBlock, int toBlock) {
        logger.log(cycle, LogEvent.SYSTEM, LogCategory.DISCO, "C-SCAN: el cabezal llega al bloque " + lastBlock
                + " y salta al bloque 0 (venia del " + fromBlock + ", sigue con el " + toBlock + ")");
    }
}