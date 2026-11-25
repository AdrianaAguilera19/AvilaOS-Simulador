/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.midterm;

import avilaos.datastructures.BlockChain;
import avilaos.datastructures.CustomQueue;
import avilaos.enums.IOOperationType;
import avilaos.interfaces.Clockable;
import avilaos.interfaces.VictimPolicy;
import avilaos.model.hardware.Computer;
import avilaos.model.process.PCB;
import avilaos.os.disk.DiskQueue;
import avilaos.os.disk.DiskRequest;
import avilaos.os.disk.SwapArea;
import java.util.concurrent.Semaphore;

/**
 * S04: planificador de mediano plazo de un computador. Es un componente que reacciona al reloj
 * global: el computador lo invoca una vez por ciclo, justo despues de la admision de procesos
 * nuevos, con el stateLock del computador tomado (nadie mas modifica las colas mientras corre).
 *
  * S05: en cada ciclo evalua si hay que suspender (ver SuspensionTrigger). Mientras la condicion se
 * mantenga, el disparo queda pendiente; se cuenta una vez por cada vez que se activa. Las decisiones
 * que faltan se agregan en las tareas siguientes: a quien suspender (S06), la escritura al swap como
 * solicitud de disco (S07) y la reactivacion cuando se libera memoria (S09).
 */
public class MediumTermScheduler implements Clockable {

    private final Computer computer;
    private volatile boolean enabled = true;
    private volatile long ticksSeen = 0;
    private volatile long lastCycle = -1;
        private volatile boolean triggerPending = false;   // S05: hay que suspender ahora mismo
    private volatile PCB triggerProcess = null;        // S05: el proceso de Nuevos que no cabe
    private volatile int triggerCount = 0;             // S05: cuantas veces se activo el disparo
        private volatile PCB victim = null;                // S06: a quien se suspenderia ahora
    private volatile VictimPolicy victimPolicy = new VictimSelector();   // S06: criterio intercambiable4
    
    // S07: suspension como operacion de E/S de disco
    private volatile SwapArea swap = null;             // area de swap (null: no hay, no se suspende)
    private volatile DiskQueue diskQueue = null;       // cola unica de E/S del disco
    private PCB inFlightVictim = null;                 // victima cuya escritura al swap esta en curso
    private final CustomQueue<DiskRequest> completedSwap = new CustomQueue<>();   // escrituras terminadas
    private final Semaphore completedLock = new Semaphore(1);   // el disco y el computador la tocan
    private volatile int suspensionCount = 0;          // suspensiones completadas
    private volatile int abortedSuspensions = 0;       // escrituras cuyo proceso ya no estaba suspendible
    private volatile int swapFullCount = 0;            // veces que no se pudo suspender por falta de swap
    private boolean swapFullWarned = false;            // para avisar una sola vez por cada falta de swap
        private PCB reactivating = null;                   // S09: proceso cuya lectura del swap esta en curso
    private volatile int reactivationCount = 0;        // S09: reactivaciones completadas
    private volatile int abortedReactivations = 0;     // S09: lecturas cuyo proceso ya no estaba suspendido

    /**
     * @param computer computador cuyos procesos administra este planificador
     */
    public MediumTermScheduler(Computer computer) {
        if (computer == null) {
            throw new IllegalArgumentException("El planificador necesita un computador");
        }
        this.computer = computer;
    }

    /**
     * Se invoca en cada ciclo del reloj. Si esta desactivado no hace nada.
     * @param cycle numero de ciclo global actual
     */
    @Override
    public void onTick(long cycle) {
                processCompletedSwap();   // S07: las escrituras al swap que el disco ya termino se cierran siempre
        if (!enabled) {
            return;
        }
        lastCycle = cycle;
        ticksSeen++;
                evaluateTrigger();
                startSuspensionIfNeeded();   // S07
                startReactivationIfPossible();   // S09
    }
        /**
     * S05: mira al primer proceso de Nuevos (el que la admision no pudo meter) y decide si hay que
     * suspender. Solo cuenta cuando el disparo pasa de apagado a encendido, y lo deja en el log.
     */
    private void evaluateTrigger() {
        PCB waiting = computer.peekNewProcess();
        boolean must = SuspensionTrigger.mustSuspend(waiting, computer.getRam(),
                computer.blockedCountInRam(), computer.blockedMemoryInRam());
                if (!must) {
            triggerPending = false;
            triggerProcess = null;
            victim = null;
            swapFullWarned = false;
            return;
        }
        if (!triggerPending) {
            triggerPending = true;
            triggerCount++;
            computer.logMediumTerm(waiting.getName() + " (" + waiting.getMemorySize() + ") no cabe en la RAM ("
                    + computer.getRam().getFreeSize() + " libres): hay que suspender un proceso bloqueado");
        }
                triggerProcess = waiting;
        PCB chosen = victimPolicy.choose(computer.blockedQueueCopy());   // S06
        if (chosen != null && chosen != victim) {
            computer.logMediumTerm("Victima de la suspension: " + chosen.getName() + " (prioridad "
                    + chosen.getPriority() + ", " + chosen.getMemorySize() + " de memoria)");
        }
        victim = chosen;
    }
    
        /**
     * S07: si el disparo esta activo, hay victima y no hay otra suspension en curso, pide la
     * escritura de la victima al swap. Antes comprueba que el swap tenga lugar (S05/S11): si no, no
     * suspende y el proceso nuevo sigue esperando. La RAM de la victima NO se libera aqui: se libera
     * cuando el disco termina la escritura (ver processCompletedSwap).
     */
    private void startSuspensionIfNeeded() {
        if (!triggerPending || victim == null || inFlightVictim != null) {
            return;
        }
        int blocks = victim.getMemorySize();
        SwapArea area = swap;
        DiskQueue queue = diskQueue;
        if (area == null || queue == null) {
            warnSwapFull("no hay area de swap configurada");
            return;
        }
        if (area.freeBlocks() < blocks) {
            warnSwapFull("necesita " + blocks + " bloques y el swap tiene " + area.freeBlocks() + " libres");
            return;
        }
        BlockChain chain = area.reserve(String.valueOf(victim.getId()), blocks);
        if (chain == null) {
            warnSwapFull("no hay " + blocks + " bloques de swap disponibles");
            return;
        }
        DiskRequest request = new DiskRequest(chain.first(), IOOperationType.SUSPEND, victim, lastCycle, blocks);
        queue.add(request);
        inFlightVictim = victim;
        swapFullWarned = false;
        computer.logMediumTerm("Se suspende al proceso " + victim.getName() + " para admitir a "
                + triggerProcess.getName() + ": escribe " + blocks + " bloques en el swap (desde el bloque "
                + chain.first() + ")");
    }

    /** Avisa (una sola vez por cada falta) que no se pudo suspender por falta de swap. */
    private void warnSwapFull(String reason) {
        if (swapFullWarned) {
            return;
        }
        swapFullWarned = true;
        swapFullCount++;
        computer.logMediumTerm("No se suspende a " + victim.getName() + " (swap lleno: " + reason + "): "
                + triggerProcess.getName() + " espera en Nuevos");
    }

    /**
     * Lo llama el coordinador de E/S cuando el disco termina una solicitud SUSPEND. Solo deja el
     * aviso: el cambio de colas lo hace el computador en su proximo ciclo, igual que los despertares.
     * @param request solicitud de swap que el disco acaba de atender
     */
    public void onSwapServiceFinished(DiskRequest request) {
        if (request == null) {
            return;
        }
        completedLock.acquireUninterruptibly();
        try {
            completedSwap.enqueue(request);
        } finally {
            completedLock.release();
        }
    }

    private DiskRequest pollCompletedSwap() {
        completedLock.acquireUninterruptibly();
        try {
            return completedSwap.dequeue();
        } finally {
            completedLock.release();
        }
    }
    
        /**
     * S09: si hay procesos en Listo/Suspendido, ninguna operacion de swap en curso, no hay un
     * proceso nuevo esperando que obligue a suspender, y a alguno le alcanza la memoria libre, pide
     * leerlo del swap. Se elige al primero de la cola (orden de llegada) que quepa. Reserva su lugar
     * en la RAM desde ya y manda una solicitud REACTIVATE al disco; el proceso vuelve a Listo cuando
     * el disco termina de leer (ver finishReactivation).
     */
    private void startReactivationIfPossible() {
        SwapArea area = swap;
        DiskQueue queue = diskQueue;
        if (area == null || queue == null || reactivating != null || inFlightVictim != null || triggerPending) {
            return;
        }
        avilaos.datastructures.CustomQueue<PCB> candidates = computer.readySuspendedQueueCopy();
        for (int i = 0; i < candidates.size(); i++) {
            PCB p = candidates.get(i);
            String key = String.valueOf(p.getId());
            int first = area.firstBlockOf(key);
            if (first < 0 || p.getMemorySize() > computer.freeRam()) {
                continue;
            }
            if (!computer.reserveRamForReactivation(p)) {
                continue;
            }
            queue.add(new DiskRequest(first, IOOperationType.REACTIVATE, p, lastCycle, p.getMemorySize()));
            reactivating = p;
            computer.logMediumTerm("Se reactiva al proceso " + p.getName() + ": lee " + p.getMemorySize()
                    + " bloques del swap (desde el bloque " + first + ")");
            return;
        }
    }

    /** S09: el disco termino de leer: el proceso vuelve a Listo y se liberan sus bloques de swap. */
    private void finishReactivation(PCB p) {
        boolean ok = computer.completeReactivation(p);
        int freed = swap != null ? swap.release(String.valueOf(p.getId())) : 0;
        if (ok) {
            reactivationCount++;
            computer.logMediumTerm("Reactivacion de " + p.getName() + " completa: se liberan " + freed
                    + " bloques de swap");
        } else {
            abortedReactivations++;
            computer.logMediumTerm("Se cancela la reactivacion de " + p.getName() + " (se devuelven " + freed
                    + " bloques de swap)");
        }
        if (p == reactivating) {
            reactivating = null;
        }
    }

    /**
     * S07: cierra las suspensiones cuya escritura termino. Si la victima sigue Bloqueada en RAM,
     * pasa a Bloqueado/Suspendido y se libera su RAM. Si ya no (desperto o vencio su deadline
     * mientras se escribia), se cancela y se devuelven los bloques de swap.
     */
    private void processCompletedSwap() {
        DiskRequest done;
        while ((done = pollCompletedSwap()) != null) {
            PCB p = done.getRequester();
                        if (done.getOperation() == IOOperationType.REACTIVATE) {
                finishReactivation(p);
                continue;
            }
            if (computer.completeSuspension(p)) {
                suspensionCount++;
            } else {
                int freed = swap != null ? swap.release(String.valueOf(p.getId())) : 0;
                abortedSuspensions++;
                computer.logMediumTerm("Se cancela la suspension de " + p.getName()
                        + ": ya no estaba bloqueado en RAM (se devuelven " + freed + " bloques de swap)");
            }
            if (p == inFlightVictim) {
                inFlightVictim = null;
            }
        }
    }

    /**
     * Conecta el planificador con el swap y la cola del disco. Sin esto no se suspende nada.
     * @param swap area de swap (puede ser null si no hay)
     * @param diskQueue cola unica de E/S del disco, por donde pasan las solicitudes de suspension
     */
    public void attachSwap(SwapArea swap, DiskQueue diskQueue) {
        if (swap != null && diskQueue == null) {
            throw new IllegalArgumentException("Con swap hace falta la cola del disco");
        }
        this.swap = swap;
        this.diskQueue = diskQueue;
    }

    /** @return suspensiones completadas */
    public int getSuspensionCount() { return suspensionCount; }

    /** @return escrituras al swap canceladas porque el proceso ya no estaba suspendible */
    public int getAbortedSuspensions() { return abortedSuspensions; }

    /** @return veces que no se pudo suspender por falta de swap */
    public int getSwapFullCount() { return swapFullCount; }

    /** @return proceso cuya escritura al swap esta en curso, o null */
    public PCB getInFlightVictim() { return inFlightVictim; }

    /** @return el computador que administra */
    public Computer getComputer() { return computer; }

    /** @return true si el planificador esta activo (por defecto, si) */
    public boolean isEnabled() { return enabled; }

    /** Activa o desactiva el planificador; desactivado, el computador funciona sin suspender nada. */
        public void setEnabled(boolean enabled) {
        this.enabled = enabled;
                if (!enabled) {
            triggerPending = false;
            triggerProcess = null;
            victim = null;
        }
    }

    /** @return true si ahora mismo hay que suspender un proceso para que entre uno de Nuevos */
    public boolean isTriggerPending() { return triggerPending; }

    /** @return el proceso de Nuevos que motiva la suspension, o null si no hay disparo */
    public PCB getTriggerProcess() { return triggerProcess; }
    
        /** @return el proceso Bloqueado que se suspenderia ahora (S06), o null si no hay disparo o candidato */
    public PCB getVictim() { return victim; }

    /** @return el criterio con que se elige a la victima (por defecto, VictimSelector) */
    public VictimPolicy getVictimPolicy() { return victimPolicy; }

    /**
     * Cambia el criterio de eleccion de la victima; se aplica desde el proximo ciclo.
     * @param policy nuevo criterio (no puede ser null)
     */
    public void setVictimPolicy(VictimPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("El criterio de victima no puede ser nulo");
        }
        this.victimPolicy = policy;
    }

    /** @return cuantas veces se activo el disparo de suspension */
    public int getTriggerCount() { return triggerCount; }

    /** @return cuantos ciclos ha atendido estando activo */
    public long getTicksSeen() { return ticksSeen; }

    /** @return el ultimo ciclo que atendio, o -1 si todavia no atendio ninguno */
    public long getLastCycle() { return lastCycle; }
    
        /** @return reactivaciones completadas (procesos traidos de vuelta del swap) */
    public int getReactivationCount() { return reactivationCount; }

    /** @return lecturas del swap canceladas porque el proceso ya no estaba suspendido (vencio) */
    public int getAbortedReactivations() { return abortedReactivations; }

    /** @return proceso cuya lectura del swap esta en curso, o null */
    public PCB getReactivating() { return reactivating; }
}