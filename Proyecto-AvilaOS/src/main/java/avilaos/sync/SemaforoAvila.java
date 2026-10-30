/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.sync;

import avilaos.datastructures.CustomQueue;
import avilaos.enums.BlockReason;
import avilaos.enums.ProcessState;
import avilaos.interfaces.WaitCanceller;
import avilaos.model.process.PCB;
import java.util.concurrent.Semaphore;

/**
 * Semáforo de conteo del sistema operativo simulado.
 * No llama al Kernel: semWait devuelve si el proceso se bloqueó
 * y semSignal devuelve el proceso despertado.
 */
public class SemaforoAvila implements WaitCanceller {

    private int value;
    private final BlockReason reason;
    private final CustomQueue<PCB> waiting = new CustomQueue<>();
    private final Semaphore lock = new Semaphore(1); // protege value y waiting

    // S10: esperas ya terminadas (el proceso se despertó) y suma de sus ciclos bloqueado
    private long completedWaits = 0;
    private long totalWaitedCycles = 0;
    public SemaforoAvila(int initialValue, BlockReason reason) {
        if (initialValue < 0) {
            throw new IllegalArgumentException("El valor inicial no puede ser negativo");
        }
        this.value = initialValue;
        this.reason = reason;
    }

    /** @return true si el proceso quedó bloqueado */
    public boolean semWait(PCB pcb) {
        lock.acquireUninterruptibly();
        try {
            value--;
            if (value < 0) {
                pcb.setState(ProcessState.BLOCKED);
                pcb.setBlockReason(reason);
                                pcb.setWaitingOn(this);
                waiting.enqueue(pcb);
                return true;
            }
            return false;
        } finally {
            lock.release();
        }
    }

    /** @return el proceso despertado, o null si nadie esperaba */
    public PCB semSignal() {
        lock.acquireUninterruptibly();
        try {
            value++;
                        PCB woken = (value <= 0) ? waiting.dequeue() : null;
            if (woken != null) {
                woken.setWaitingOn(null);
            }
            return woken;
        } finally {
            lock.release();
        }
    }
        /**
     * Saca de la cola de espera a un proceso que murio bloqueado (por deadline) y deshace su
     * semWait, para que un semSignal posterior no "despierte" a un proceso ya terminado.
     * @return true si el proceso estaba esperando en este semaforo
     */
    @Override
    public boolean cancelWait(PCB pcb) {
        lock.acquireUninterruptibly();
        try {
            if (!waiting.remove(pcb)) {
                return false;
            }
            value++;
            pcb.setWaitingOn(null);
            return true;
        } finally {
            lock.release();
        }
    }

    public int getValue() {
        lock.acquireUninterruptibly();
        try {
            return value;
        } finally {
            lock.release();
        }
    }
        /**
     * Anota que un proceso estuvo bloqueado en este semáforo durante cierta cantidad de ciclos
     * (S10). Lo llama el Kernel cuando despierta al proceso; el semáforo no conoce el reloj.
     * @param cycles ciclos que estuvo bloqueado (no puede ser negativo)
     */
    public void recordWait(long cycles) {
        if (cycles < 0) {
            throw new IllegalArgumentException("Los ciclos de espera no pueden ser negativos");
        }
        lock.acquireUninterruptibly();
        try {
            completedWaits++;
            totalWaitedCycles += cycles;
        } finally {
            lock.release();
        }
    }

    /** @return {esperas terminadas, suma de ciclos bloqueado}, leídas a la vez */
    public long[] getWaitStats() {
        lock.acquireUninterruptibly();
        try {
            return new long[] { completedWaits, totalWaitedCycles };
        } finally {
            lock.release();
        }
    }

    /** Promedio de ciclos bloqueado en este semáforo; 0 si todavía no hubo esperas. */
    public double getAverageWait() {
        long[] stats = getWaitStats();
        return stats[0] == 0 ? 0.0 : (double) stats[1] / stats[0];
    }

    public BlockReason getReason() {
        return reason;
    }

    /** Copia de la cola de espera para que la pantalla la dibuje. */
    public CustomQueue<PCB> getWaitingSnapshot() {
        lock.acquireUninterruptibly();
        try {
            return waiting.copy();
        } finally {
            lock.release();
        }
    }
}