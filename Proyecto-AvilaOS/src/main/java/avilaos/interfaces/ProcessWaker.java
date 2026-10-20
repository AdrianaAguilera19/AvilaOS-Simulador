/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package avilaos.interfaces;

import avilaos.model.process.PCB;

/**
 * Quien sabe despertar a un proceso bloqueado, esté en el computador que esté.
 * Lo implementa el Cluster; los computadores solo conocen esta interfaz.
 */
public interface ProcessWaker {

    /** Avisa al computador dueño del proceso que debe pasarlo a la cola de listos. */
    void wake(PCB pcb);
}
