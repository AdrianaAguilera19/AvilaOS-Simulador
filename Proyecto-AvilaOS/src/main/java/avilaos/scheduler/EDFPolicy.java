package avilaos.scheduler;

import avilaos.datastructures.CustomQueue;
import avilaos.enums.SchedulingAlgorithm;
import avilaos.interfaces.SchedulingPolicy;
import avilaos.model.process.PCB;
import java.util.Comparator; // Importante para poder comparar los deadlines

public class EDFPolicy implements SchedulingPolicy {

    @Override
    public SchedulingAlgorithm getAlgorithm() {
        // Identificamos que esta es la política EDF
        return SchedulingAlgorithm.EDF;
    }

    @Override
    public void addToReady(CustomQueue<PCB> readyQueue, PCB process) {
        // EDF ordena a los procesos por su deadline (de menor a mayor).
        // Creamos una "regla" (Comparator) que le enseña a Java cómo comparar dos PCBs.
        Comparator<PCB> comparadorDeadline = new Comparator<PCB>() {
            @Override
            public int compare(PCB p1, PCB p2) {
                // Integer.compare devuelve un número negativo si p1 es menor, 
                // positivo si p1 es mayor, o 0 si son iguales.
                return Integer.compare(p1.getDeadline(), p2.getDeadline());
            }
        };
        
        // Usamos el método especial de tu compañera para meter el proceso en la cola
        // pero ordenándolo automáticamente usando la regla que acabamos de crear.
        readyQueue.insertSorted(process, comparadorDeadline);
    }

    @Override
    public PCB selectNext(CustomQueue<PCB> readyQueue) {
        // Como la cola ya se encargó de ordenarlos al insertarlos, 
        // el proceso con el menor deadline siempre quedará de primero.
        return readyQueue.dequeue();
    }

    @Override
    public boolean shouldPreempt(PCB running, CustomQueue<PCB> readyQueue, int cyclesInCpu) {
        // El enunciado dice específicamente que la política apropiativa (que desaloja)
        // es la de Prioridades. Por lo tanto, EDF no desaloja.
        return false;
    }
}