/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.enums.StepKind;
import avilaos.interfaces.ProcessRunner;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;
import avilaos.model.process.StepOutcome;

/**
 * Ayuda SOLO para las pruebas (E11). El simulador ya no tiene E/S simulada: la unica E/S es la del
 * disco. Pero muchas pruebas de planificacion, memoria y suspension necesitan un proceso que se bloquee
 * un tiempo conocido sin depender del disco. Este ejecutor hace eso con un mecanismo que si existe en
 * el simulador (la espera de red del P1): el proceso corre instrucciones de usuario y, tras cada
 * 'cadaCuantas' instrucciones, queda Bloqueado 'espera' ciclos (motivo NETWORK_LATENCY) y despues
 * vuelve a Listo. Es un proceso CPU_BOUND normal: nada de esto esta en el codigo del simulador.
 */
public final class ProcesoBloqueante implements ProcessRunner {

    private final PCB pcb;
    private final int cadaCuantas;
    private final int espera;
    private int ultimoBloqueoEn = 0;   // numero de instrucciones ejecutadas cuando se bloqueo la ultima vez

    private ProcesoBloqueante(PCB pcb, int cadaCuantas, int espera) {
        this.pcb = pcb;
        this.cadaCuantas = cadaCuantas;
        this.espera = espera;
    }

    /** Crea el proceso y su ejecutor, y deja el ejecutor registrado en el computador. */
    public static PCB crear(Computer pc, String nombre, int prioridad, int memoria, int instrucciones,
            int cadaCuantas, int espera, int deadline, long llegada) {
        PCB p = ProcessFactory.createCpuBound(nombre, prioridad, memoria, instrucciones, deadline, pc.getId(), llegada);
        pc.registerRunner(new ProcesoBloqueante(p, cadaCuantas, espera));
        return p;
    }

    /** Igual que crear(Computer, ...) pero eligiendo el computador del cluster por su id. */
    public static PCB crear(Cluster cl, int computerId, String nombre, int prioridad, int memoria, int instrucciones,
            int cadaCuantas, int espera, int deadline, long llegada) {
        return crear(cl.getComputer(computerId), nombre, prioridad, memoria, instrucciones, cadaCuantas, espera,
                deadline, llegada);
    }

    @Override
    public StepOutcome step() {
        int hechas = pcb.getTotalInstructions() - pcb.getRemainingTime();
        if (hechas > 0 && hechas % cadaCuantas == 0 && hechas != ultimoBloqueoEn && !pcb.isFinished()) {
            ultimoBloqueoEn = hechas;
            return new StepOutcome(StepKind.NETWORK_WAIT, null,
                    pcb.getName() + " queda bloqueado " + espera + " ciclos", espera);
        }
        return new StepOutcome(StepKind.USER_INSTRUCTION, null, pcb.getName() + " ejecuta una instruccion");
    }

    @Override
    public boolean isFinished() {
        return pcb.isFinished();
    }

    @Override
    public PCB getPcb() {
        return pcb;
    }
}
