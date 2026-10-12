/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package avilaos.interfaces;

import avilaos.datastructures.CustomQueue;
import avilaos.enums.SchedulingAlgorithm;
import avilaos.model.process.PCB;

/**
 *Contrato que cumple con toda politica de planificacion de avilaOS
 * @author Antonella Ruiz
 */
public interface SchedulingPolicy {

    /**
     * 
     * @return EL algortimo que implementa esta politica
     */
    SchedulingAlgorithm getAlgorithm();
    
    /**
     * Mete un proceso en la cola de listos respetando el orden de la politica
     * @param readyQueue cola de listos del comp
     * @param process proceso que pasa a estado LISTO
     */
    void addToReady(CustomQueue<PCB> readyQueue, PCB process);
    
    /**
     * Elige el proceso que usara la CPU, lo saca de la cola y lo devuelve
     * @param readyQueue cola de listos del computador
     * @return el proceso elegido (null si esta vacia la cola)
     */
    PCB selectNext(CustomQueue<PCB> readyQueue);
    
    /**
     * Pregunta si el proceso qe esta en la CPU debe ser desalojado
     * @param running proceso en ejecucion
     * @param readyQueue cola de listos del computador
     * @param cyclesInCpu ciclos seguidos que lleva el proceso en la CPU
     * @return true si hay que sacarlo de la CPU y devolverlo a la cola de listos
     */
    boolean shouldPreempt(PCB running, CustomQueue<PCB> readyQueue,int cyclesInCpu);
}
