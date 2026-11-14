/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package avilaos.interfaces;

import avilaos.datastructures.CustomQueue;
import avilaos.model.hardware.RAM;
import avilaos.model.process.PCB;

/**
 * Decide que procesos nuevos entran a RAM y pasan a listos.
 * El computador solo conoce esta interfaz.
 */
public interface MemoryAdmission {

    /** Pasa de nuevos a listos los procesos a los que les alcance la RAM. */
    void admit(CustomQueue<PCB> newQueue, CustomQueue<PCB> readyQueue, RAM ram, SchedulingPolicy policy);
}
