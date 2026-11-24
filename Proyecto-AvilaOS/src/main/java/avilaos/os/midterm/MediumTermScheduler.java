/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.midterm;

import avilaos.interfaces.Clockable;
import avilaos.model.hardware.Computer;

/**
 * S04: planificador de mediano plazo de un computador. Es un componente que reacciona al reloj
 * global: el computador lo invoca una vez por ciclo, justo despues de la admision de procesos
 * nuevos, con el stateLock del computador tomado (nadie mas modifica las colas mientras corre).
 *
 * Por ahora solo recibe el ciclo y lo cuenta. Las decisiones se agregan en las tareas siguientes:
 * cuando suspender (S05), a quien (S06), la escritura al swap como solicitud de disco (S07) y la
 * reactivacion cuando se libera memoria (S09).
 */
public class MediumTermScheduler implements Clockable {

    private final Computer computer;
    private volatile boolean enabled = true;
    private volatile long ticksSeen = 0;
    private volatile long lastCycle = -1;

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
        // S05, S06, S07 y S09 se enchufan aqui
    }

    /** @return el computador que administra */
    public Computer getComputer() { return computer; }

    /** @return true si el planificador esta activo (por defecto, si) */
    public boolean isEnabled() { return enabled; }

    /** Activa o desactiva el planificador; desactivado, el computador funciona sin suspender nada. */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** @return cuantos ciclos ha atendido estando activo */
    public long getTicksSeen() { return ticksSeen; }

    /** @return el ultimo ciclo que atendio, o -1 si todavia no atendio ninguno */
    public long getLastCycle() { return lastCycle; }
}