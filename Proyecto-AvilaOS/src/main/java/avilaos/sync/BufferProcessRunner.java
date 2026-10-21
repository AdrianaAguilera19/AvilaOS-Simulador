/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.sync;

import avilaos.enums.AccessType;
import avilaos.enums.ProcessType;
import avilaos.enums.StepKind;
import avilaos.interfaces.ProcessRunner;
import avilaos.model.process.PCB;
import avilaos.model.process.StepOutcome;
import java.util.function.IntSupplier;

/**
 * Ejecuta, paso a paso, el algoritmo del productor o del consumidor
 * (Stallings, fig. 5.13) para un proceso. Cada llamada a step() es UN ciclo.
 * No llama al Kernel: devuelve un StepOutcome y el Kernel reacciona.
 *
 * Productor: producir -> [red] -> semWait(espacios) -> semWait(mutex) -> insertar
 *            -> semSignal(mutex) -> semSignal(elementos)
 * Consumidor: [red] -> semWait(elementos) -> semWait(mutex) -> extraer
 *            -> semSignal(mutex) -> semSignal(espacios) -> consumir
 *
 * [red] solo existe si el buffer está en otro computador: el proceso espera la
 * latencia de red una vez por elemento, antes de empezar el protocolo.
 *
 * Si un semWait bloquea al proceso, el paso cuenta como hecho: el valor del
 * semáforo ya bajó y, al despertarlo un semSignal, continúa con el paso siguiente.
 */
public class BufferProcessRunner implements ProcessRunner {

    private enum Phase { WORK, NETWORK, WAIT_FIRST, WAIT_MUTEX, ACCESS, SIGNAL_MUTEX, SIGNAL_OTHER }

    private final PCB pcb;
    private final BoundedBuffer buffer;
    private final boolean producer;
    private final IntSupplier networkLatency;   // latencia de red actual del cluster

    private Phase phase;
    private int workDone = 0;    // instrucciones de usuario del elemento actual
    private int itemsDone = 0;   // elementos completados

    /** Sin latencia de red (pruebas). */
    public BufferProcessRunner(PCB pcb, BoundedBuffer buffer) {
        this(pcb, buffer, () -> 0);
    }

    public BufferProcessRunner(PCB pcb, BoundedBuffer buffer, IntSupplier networkLatency) {
        if (pcb == null || buffer == null || networkLatency == null) {
            throw new IllegalArgumentException("Se necesita un proceso, un buffer y la latencia");
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
        this.networkLatency = networkLatency;
        this.producer = pcb.getType() == ProcessType.PRODUCER;
        this.phase = producer ? Phase.WORK : accessStartPhase();
    }

    /** Ejecuta un paso (un ciclo). */
    @Override
    public StepOutcome step() {
        if (isFinished()) {
            return new StepOutcome(StepKind.FINISHED, null, pcb.getName() + " terminó");
        }
        switch (phase) {
            case WORK:
                return doWork();
            case NETWORK:
                return doNetwork();
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
                return doSignal(buffer.getMutex(), "mutex");
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
                phase = accessStartPhase();    // ya produjo: ahora accede al buffer
            } else {
                finishItem();                  // ya consumió: elemento completo
            }
        }
        return new StepOutcome(StepKind.USER_INSTRUCTION, null,
                pcb.getName() + (producer ? " produce" : " consume") + " un elemento");
    }

    /** Antes de tocar el buffer, un acceso remoto paga primero la latencia de red. */
    private Phase accessStartPhase() {
        return buffer.accessTypeFor(pcb) == AccessType.REMOTE ? Phase.NETWORK : Phase.WAIT_FIRST;
    }

    private StepOutcome doNetwork() {
        phase = Phase.WAIT_FIRST;
        int cycles = networkLatency.getAsInt();
        if (cycles <= 0) {
            return step();   // sin latencia: sigue con el semWait en este mismo ciclo
        }
        return new StepOutcome(StepKind.NETWORK_WAIT, null,
                pcb.getName() + " accede al buffer B" + buffer.getId() + " de forma remota ("
                + cycles + " ciclos de red)", cycles);
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

    private StepOutcome doSignal(SemaforoAvila sem, String name) {
        PCB woken = sem.semSignal();
        String text = pcb.getName() + " semSignal(" + name + ") del buffer B" + buffer.getId();
        if (woken != null) {
            text += ": despierta a " + woken.getName();
        }
        return new StepOutcome(StepKind.SYSTEM_INSTRUCTION, woken, text);
    }

    private StepOutcome doSignalOther() {
        SemaforoAvila sem = producer ? buffer.getItems() : buffer.getSpaces();
        StepOutcome outcome = doSignal(sem, producer ? "elementos" : "espacios");
        if (producer) {
            finishItem();          // el productor termina el elemento al hacer el último signal
        } else {
            phase = Phase.WORK;    // el consumidor ahora consume lo extraído
        }
        return outcome;
    }

    private void finishItem() {
        itemsDone++;
        phase = producer ? Phase.WORK : accessStartPhase();
    }

    @Override
    public boolean isFinished() { return itemsDone >= pcb.getItemsRequired(); }

    public int getItemsDone() { return itemsDone; }

    @Override
    public PCB getPcb() { return pcb; }

    public BoundedBuffer getBuffer() { return buffer; }

    public AccessType getAccessType() { return buffer.accessTypeFor(pcb); }
}