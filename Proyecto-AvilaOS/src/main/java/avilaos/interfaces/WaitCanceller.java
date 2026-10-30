package avilaos.interfaces;

import avilaos.model.process.PCB;

/**
 * Algo en cuya cola de espera puede estar bloqueado un proceso (por ejemplo, un semaforo).
 * El Kernel la usa para sacar al proceso de esa cola cuando lo termina por deadline,
 * sin conocer la clase concreta del semaforo.
 */
public interface WaitCanceller {

    /**
     * Saca al proceso de la cola de espera y deshace su semWait pendiente.
     * @param pcb proceso que deja de esperar
     * @return true si estaba esperando aqui y se le saco
     */
    boolean cancelWait(PCB pcb);
}
