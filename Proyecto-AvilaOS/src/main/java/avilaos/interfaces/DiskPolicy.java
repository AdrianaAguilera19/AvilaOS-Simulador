/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package avilaos.interfaces;

import avilaos.datastructures.DynamicArray;
import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.HeadDirection;
import avilaos.os.disk.DiskRequest;

/**
 * Contrato de toda politica de planificacion de disco (FIFO, SSTF, SCAN, C-SCAN y las extra).
 * @author Antonella Ruiz
 */
public interface DiskPolicy {

    /** La politica que implementa esta clase (se muestra en la GUI y en el log). */
    DiskSchedulingPolicy getType();

    /**
     * Elige la siguiente solicitud a atender.
     * No modifica la lista: solo dice cual es.
     * @param pending solicitudes pendientes en orden de llegada
     * @param headPosition bloque donde esta el cabezal
     * @param direction direccion actual del cabezal
     * @return posicion (en pending) de la solicitud elegida, o -1 si no hay ninguna
     */
    int selectNext(DynamicArray<DiskRequest> pending, int headPosition, HeadDirection direction);
}