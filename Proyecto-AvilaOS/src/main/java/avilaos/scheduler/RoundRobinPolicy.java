package avilaos.scheduler;

import avilaos.datastructures.CustomQueue;
import avilaos.enums.SchedulingAlgorithm;
import avilaos.interfaces.SchedulingPolicy;
import avilaos.model.process.PCB;

public class RoundRobinPolicy implements SchedulingPolicy {
    
    // volatile: la GUI puede cambiarlo mientras el reloj corre
    private volatile int quantum;

    // Constructor que permite configurar el quantum al crear la política
    public RoundRobinPolicy(int quantum) {
        if (quantum <= 0) {
            throw new IllegalArgumentException("El quantum debe ser mayor a 0");
        }
        this.quantum = quantum;
    }

    public int getQuantum() { return quantum; }

    /** Cambia el quantum en caliente; aplica desde el siguiente ciclo. */
    public void setQuantum(int quantum) {
        if (quantum <= 0) {
            throw new IllegalArgumentException("El quantum debe ser mayor a 0");
        }
        this.quantum = quantum;
    }

    @Override
    public SchedulingAlgorithm getAlgorithm() {
        return SchedulingAlgorithm.ROUND_ROBIN; // Identificador de la política
    }

    @Override
    public void addToReady(CustomQueue<PCB> readyQueue, PCB process) {
        // Entra al final de la cola (igual que FCFS)
        readyQueue.enqueue(process);
    }

    @Override
    public PCB selectNext(CustomQueue<PCB> readyQueue) {
        // Sale el que esté de primero en la cola (igual que FCFS)
        return readyQueue.dequeue();
    }

    @Override
    public boolean shouldPreempt(PCB running, CustomQueue<PCB> readyQueue, int cyclesInCpu) {
        // Si el proceso lleva en la CPU la misma cantidad de ciclos que el quantum,
        // retorna 'true' para decirle al sistema que debe sacarlo (desalojarlo).
        return cyclesInCpu >= quantum;
    }
}