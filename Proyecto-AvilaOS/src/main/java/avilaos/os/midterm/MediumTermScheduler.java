/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.midterm;

import avilaos.interfaces.Clockable;
import avilaos.interfaces.VictimPolicy;
import avilaos.model.hardware.Computer;
import avilaos.model.process.PCB;

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
    private volatile VictimPolicy victimPolicy = new VictimSelector();   // S06: criterio intercambiable

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
        if (!enabled) {
            return;
        }
        lastCycle = cycle;
        ticksSeen++;
                evaluateTrigger();
        // S06, S07 y S09 se enchufan aqui
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
}