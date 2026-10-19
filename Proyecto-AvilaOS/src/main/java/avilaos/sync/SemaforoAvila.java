/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.sync;

import avilaos.datastructures.CustomQueue;
import avilaos.enums.BlockReason;
import avilaos.enums.ProcessState;
import avilaos.model.process.PCB;
import java.util.concurrent.Semaphore;

/**
 * Semáforo de conteo del sistema operativo simulado.
 * No llama al Kernel: semWait devuelve si el proceso se bloqueó
 * y semSignal devuelve el proceso despertado.
 */
public class SemaforoAvila {

    private int value;
    private final BlockReason reason;
    private final CustomQueue<PCB> waiting = new CustomQueue<>();
    private final Semaphore lock = new Semaphore(1); // protege value y waiting

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
            return (value <= 0) ? waiting.dequeue() : null;
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