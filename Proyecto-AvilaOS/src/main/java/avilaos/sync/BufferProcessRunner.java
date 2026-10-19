/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.sync;

import avilaos.enums.ProcessType;
import avilaos.enums.StepKind;
import avilaos.model.process.PCB;

/**
 * Ejecuta, paso a paso, el algoritmo del productor o del consumidor
 * (Stallings, fig. 5.13) para un proceso. Cada llamada a step() es UN ciclo.
 * No llama al Kernel: devuelve un StepOutcome y el Kernel reacciona.
 *
 * Productor: producir -> semWait(espacios) -> semWait(mutex) -> insertar
 *            -> semSignal(mutex) -> semSignal(elementos)
 * Consumidor: semWait(elementos) -> semWait(mutex) -> extraer
 *            -> semSignal(mutex) -> semSignal(espacios) -> consumir
 *
 * Si un semWait bloquea al proceso, el paso cuenta como hecho: el valor del
 * semáforo ya bajó y, al despertarlo un semSignal, continúa con el paso siguiente.
 */
public class BufferProcessRunner {

    private enum Phase { WORK, WAIT_FIRST, WAIT_MUTEX, ACCESS, SIGNAL_MUTEX, SIGNAL_OTHER }

    private final PCB pcb;
    private final BoundedBuffer buffer;
    private final boolean producer;

    private Phase phase;
    private int workDone = 0;    // instrucciones de usuario del elemento actual
    private int itemsDone = 0;   // elementos completados

    public BufferProcessRunner(PCB pcb, BoundedBuffer buffer) {
        if (pcb == null || buffer == null) {
            throw new IllegalArgumentException("Se necesita un proceso y un buffer");
        }
        if (pcb.getType() != ProcessType.PRODUCER && pcb.getType() != ProcessType.CONSUMER) {
            throw new IllegalArgumentException("Solo un productor o consumidor usa un buffer");
        }
        if (pcb.getAssociatedBufferId() != buffer.getId()) {
            throw new IllegalArgumentException("El proceso " + pcb.getName()
                    + " trabaja con otro buffer");
        }
        this.pcb = pcb;
        this.buffer = buffer;
        this.producer = pcb.getType() == ProcessType.PRODUCER;
        this.phase = producer ? Phase.WORK : Phase.WAIT_FIRST;
    }

    /** Ejecuta un paso (un ciclo). */
    public StepOutcome step() {
        if (isFinished()) {
            return new StepOutcome(StepKind.FINISHED, null, pcb.getName() + " terminó");
        }
        switch (phase) {
            case WORK:
                return doWork();
            case WAIT_FIRST:
                phase = Phase.WAIT_MUTEX;
                return doWait(producer ? buffer.getSpaces() : buffer.getItems(),
                              producer ? "espacios" : "elementos");
            case WAIT_MUTEX:
                phase = Phase.ACCESS;
                return doWait(buffer.getMutex(), "mutex");
            case ACCESS:
                return doAccess();
            case SIGNAL_MUTEX:
                phase = Phase.SIGNAL_OTHER;
                return doSignal(buffer.getMutex(), "mutex", false);
            case SIGNAL_OTHER:
                return doSignalOther();
            default:
                throw new IllegalStateException("Fase desconocida");
        }
    }

    private StepOutcome doWork() {
        workDone++;
        if (workDone >= pcb.getCyclesPerItem()) {
            workDone = 0;
            if (producer) {
                phase = Phase.WAIT_FIRST;      // ya produjo: ahora lo mete al buffer
            } else {
                finishItem();                  // ya consumió: elemento completo
            }
        }
        return new StepOutcome(StepKind.USER_INSTRUCTION, null,
                pcb.getName() + (producer ? " produce" : " consume") + " un elemento");
    }

    private StepOutcome doWait(SemaforoAvila sem, String name) {
        boolean blocked = sem.semWait(pcb);
        String text = pcb.getName() + " semWait(" + name + ") del buffer B" + buffer.getId();
        if (blocked) {
            return new StepOutcome(StepKind.BLOCKED, null,
                    text + ": se bloquea (" + pcb.getBlockReason() + ")");
        }
        return new StepOutcome(StepKind.SYSTEM_INSTRUCTION, null, text);
    }

    private StepOutcome doAccess() {
        phase = Phase.SIGNAL_MUTEX;
        if (producer) {
            buffer.insert(itemsDone + 1);
            return new StepOutcome(StepKind.SYSTEM_INSTRUCTION, null,
                    pcb.getName() + " inserta en el buffer B" + buffer.getId());
        }
        int item = buffer.extract();
        return new StepOutcome(StepKind.SYSTEM_INSTRUCTION, null,
                pcb.getName() + " extrae " + item + " del buffer B" + buffer.getId());
    }

    private StepOutcome doSignal(SemaforoAvila sem, String name, boolean unused) {
        PCB woken = sem.semSignal();
        String text = pcb.getName() + " semSignal(" + name + ") del buffer B" + buffer.getId();
        if (woken != null) {
            text += ": despierta a " + woken.getName();
        }
        return new StepOutcome(StepKind.SYSTEM_INSTRUCTION, woken, text);
    }

    private StepOutcome doSignalOther() {
        SemaforoAvila sem = producer ? buffer.getItems() : buffer.getSpaces();
        StepOutcome outcome = doSignal(sem, producer ? "elementos" : "espacios", true);
        if (producer) {
            finishItem();          // el productor termina el elemento al hacer el último signal
        } else {
            phase = Phase.WORK;    // el consumidor ahora consume lo extraído
        }
        return outcome;
    }

    private void finishItem() {
        itemsDone++;
        phase = producer ? Phase.WORK : Phase.WAIT_FIRST;
    }

    public boolean isFinished() { return itemsDone >= pcb.getItemsRequired(); }
    public int getItemsDone() { return itemsDone; }
    public PCB getPcb() { return pcb; }
    public BoundedBuffer getBuffer() { return buffer; }
}
