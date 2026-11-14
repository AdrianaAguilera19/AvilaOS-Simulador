/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.midterm;

import avilaos.datastructures.CustomQueue;
import avilaos.enums.ProcessState;
import avilaos.interfaces.MemoryAdmission;
import avilaos.interfaces.SchedulingPolicy;
import avilaos.model.hardware.RAM;
import avilaos.model.process.PCB;

/**
 * Admision por orden de llegada: se detiene en el primer proceso que no cabe en la RAM.
 */
public class FifoMemoryAdmission implements MemoryAdmission {
     /**
     * Pasa de nuevos a listos los procesos que quepan en la RAM, en orden de llegada.
     * Si el primero no cabe, se detiene y espera: asi un proceso grande no se queda
     * esperando para siempre porque otros mas chicos le pasan por delante.
     */
    @Override
    public void admit(CustomQueue<PCB> newQueue, CustomQueue<PCB> readyQueue, RAM ram, SchedulingPolicy policy) {
        PCB p;
        while ((p = newQueue.peek()) != null && ram.allocate(p.getMemorySize())) {
            newQueue.dequeue();
            p.setState(ProcessState.READY);
            policy.addToReady(readyQueue, p);
        }
    }
}