/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.model.hardware;

import avilaos.enums.ExecutionMode;
import avilaos.enums.ProcessState;
import avilaos.model.process.PCB;

/**
 * La CPU de un computador. Solo ejecuta lo que le manden: tiene como maximo un
 * proceso a la vez y no decide cual (eso lo hace la politica de planificacion).
 * @author Antonella Ruiz
 */
public class CPU {

    // volatile: el hilo del reloj los cambia y la GUI los lee desde otro hilo
    private volatile PCB running;                                  // proceso en la CPU (null si esta libre)
    private volatile ExecutionMode mode = ExecutionMode.USUARIO;   // usuario o sistema operativo

    // Ciclos seguidos que lleva el proceso actual en la CPU (lo usa Round Robin para el quantum)
    private int cyclesInCpu;

    // Para la utilizacion del procesador: ciclos ocupada / ciclos totales
    private volatile long busyCycles = 0;
    private volatile long totalCycles = 0;

    /** Se llama una vez al empezar cada ciclo: cuenta el ciclo y vuelve al modo usuario. */
    public void startCycle() {
        totalCycles++;
        mode = ExecutionMode.USUARIO;
    }

    /**
     * Pone un proceso en la CPU. La CPU tiene que estar libre.
     * @param process proceso elegido por la politica
     * @param cycle ciclo global actual
     */
    public void load(PCB process, long cycle) {
        if (running != null) {
            throw new IllegalStateException("La CPU ya esta ocupada");
        }
        running = process;
        running.setState(ProcessState.RUNNING);
        running.markFirstExecution(cycle);   // guarda el primer ciclo en que uso la CPU
        cyclesInCpu = 0;
    }

    /**
     * Saca el proceso de la CPU y la deja libre.
     * @return el proceso que estaba ejecutando (null si estaba libre)
     */
    public PCB unload() {
        PCB p = running;
        running = null;
        cyclesInCpu = 0;
        return p;
    }

    /** Ejecuta una instruccion del programa de usuario (avanza PC, MAR y resta tiempo restante). */
    public void executeUserInstruction() {
        if (running == null) {
            throw new IllegalStateException("No hay proceso en la CPU");
        }
        mode = ExecutionMode.USUARIO;
        running.executeInstruction();
        cyclesInCpu++;
        busyCycles++;
    }

    /**
     * Ejecuta una instruccion del sistema operativo (semWait, semSignal, insertar o
     * extraer de un buffer). Gasta un ciclo pero NO avanza el programa del proceso.
     */
    public void executeSystemInstruction() {
        if (running == null) {
            throw new IllegalStateException("No hay proceso en la CPU");
        }
        mode = ExecutionMode.S_O;
        busyCycles++;
    }

    public boolean isFree() { return running == null; }
    public PCB getRunning() { return running; }
    public ExecutionMode getMode() { return mode; }
    public int getCyclesInCpu() { return cyclesInCpu; }
    public long getBusyCycles() { return busyCycles; }
    public long getTotalCycles() { return totalCycles; }

    /** @return fraccion de ciclos en que la CPU estuvo ocupada (0.0 a 1.0). */
    public double getUtilization() {
        long total = totalCycles;
        return total == 0 ? 0.0 : (double) busyCycles / total;
    }
}