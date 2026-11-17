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
 * El planificador solo conoce esta interfaz: agregar una politica nueva es crear una clase
 * que la implemente y una constante en el enum, sin tocar el planificador.
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

    /**
     * Bloques que recorre el cabezal para llegar al destino con esta politica. Por defecto es la
     * distancia directa; SCAN y C-SCAN la alargan cuando deben ir hasta el extremo del disco antes
     * de invertir el sentido o de saltar al 0.
     * @param totalBlocks tamano del disco (el ultimo bloque es totalBlocks - 1)
     */
    default int travelDistance(int headPosition, HeadDirection direction, int targetBlock, int totalBlocks) {
        return Math.abs(targetBlock - headPosition);
    }
}