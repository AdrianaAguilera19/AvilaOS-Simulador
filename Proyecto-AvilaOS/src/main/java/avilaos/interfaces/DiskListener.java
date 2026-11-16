/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package avilaos.interfaces;

import avilaos.os.disk.DiskRequest;

/**
 * Quien quiere enterarse de que el disco termino de atender una solicitud
 * (el planificador para despertar al proceso, el registro de solicitudes, el log).
 * @author Antonella Ruiz
 */
public interface DiskListener {

    /**
     * El disco termino la solicitud.
     * @param request la solicitud atendida
     * @param startCycle ciclo en que el disco empezo a atenderla
     * @param endCycle ciclo en que termino
     * @param headBefore posicion del cabezal justo antes de empezar
     */
    void onServiceFinished(DiskRequest request, long startCycle, long endCycle, int headBefore);
}