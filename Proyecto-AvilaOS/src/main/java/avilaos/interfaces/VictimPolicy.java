/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.interfaces;

import avilaos.datastructures.CustomQueue;
import avilaos.model.process.PCB;

/**
 * S06: criterio para elegir a quien suspender. El planificador de mediano plazo solo conoce esta
 * interfaz, asi que el criterio se puede cambiar sin tocar el planificador (igual que las politicas
 * de CPU y de disco).
 */
public interface VictimPolicy {

    /** @return nombre corto del criterio, para el log y la interfaz */
    String getName();

    /**
     * @param blocked cola de procesos bloqueados en RAM, en orden de bloqueo (no se debe modificar)
     * @return el proceso a suspender, o null si no hay ninguno elegible
     */
    PCB choose(CustomQueue<PCB> blocked);
}
