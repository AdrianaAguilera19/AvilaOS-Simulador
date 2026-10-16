package avilaos.scheduler;

import avilaos.datastructures.CustomQueue;
import avilaos.enums.SchedulingAlgorithm;
import avilaos.interfaces.SchedulingPolicy;
import avilaos.model.process.PCB;
import java.util.Comparator;

public class PriorityPolicy implements SchedulingPolicy {

    @Override
    public SchedulingAlgorithm getAlgorithm() {
        // Identificador de la política (asegúrate de que en el enum se llame así, 
        // a veces le ponen PRIORITIES o PRIORIDAD)
        return SchedulingAlgorithm.PRIORITY; 
    }

    @Override
    public void addToReady(CustomQueue<PCB> readyQueue, PCB process) {
        // Ordenamos asumiendo que un número menor = mayor prioridad (ej: 1 le gana a 3)
        Comparator<PCB> comparadorPrioridad = new Comparator<PCB>() {
            @Override
            public int compare(PCB p1, PCB p2) {
                return Integer.compare(p1.getPriority(), p2.getPriority());
            }
        };
        
        readyQueue.insertSorted(process, comparadorPrioridad);
    }

    @Override
    public PCB selectNext(CustomQueue<PCB> readyQueue) {
        return readyQueue.dequeue();
    }

    @Override
    public boolean shouldPreempt(PCB running, CustomQueue<PCB> readyQueue, int cyclesInCpu) {
        // Si la cola está vacía, no hay nadie a quien cederle la CPU
        if (readyQueue.isEmpty()) {
            return false;
        }
        
        // Miramos quién es el primero en la fila (el de mejor prioridad esperando).
        // Nota: Revisa si tu clase CustomQueue usa el método peek(), front() o getFirst() 
        // para mirar sin sacar al proceso. Asumiré peek() que es el más estándar.
        PCB siguienteEnCola = readyQueue.peek(); 
        
        // Si el que está esperando tiene mejor prioridad (número menor) que el que está en la CPU, lo expulsa.
        return siguienteEnCola.getPriority() < running.getPriority();
    }
}
