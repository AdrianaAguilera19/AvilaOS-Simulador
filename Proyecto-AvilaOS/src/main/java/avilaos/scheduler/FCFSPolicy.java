package avilaos.scheduler;

import avilaos.datastructures.CustomQueue;
import avilaos.enums.SchedulingAlgorithm;
import avilaos.interfaces.SchedulingPolicy;
import avilaos.model.process.PCB;

public class FCFSPolicy implements SchedulingPolicy {

    @Override
    public SchedulingAlgorithm getAlgorithm() {
        // Le dice al simulador qué política es esta
        return SchedulingAlgorithm.FCFS;
    }

    @Override
    public void addToReady(CustomQueue<PCB> readyQueue, PCB process) {
        // FCFS: El primero que llega, se mete al final de la fila
        readyQueue.enqueue(process);
    }

    @Override
    public PCB selectNext(CustomQueue<PCB> readyQueue) {
        // Saca al proceso que está de primero en la cola
        return readyQueue.dequeue();
    }

    @Override
    public boolean shouldPreempt(PCB running, CustomQueue<PCB> readyQueue, int cyclesInCpu) {
        // FCFS no desaloja a nadie nunca.
        return false;
    }
}
