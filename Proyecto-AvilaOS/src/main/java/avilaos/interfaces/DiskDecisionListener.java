/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package avilaos.interfaces;

import avilaos.enums.DiskSchedulingPolicy;
import avilaos.os.disk.DiskRequest;

/**
 * Quien quiere enterarse de las decisiones del planificador de disco: a que solicitud atiende y con
 * que politica, cuando se cambia de politica y cuando C-SCAN salta del final al inicio del disco.
 * El registro de eventos (log) lo implementa; la GUI u otras metricas podrian hacerlo tambien.
 * @author Antonella Ruiz
 */
public interface DiskDecisionListener {

    /**
     * El planificador eligio una solicitud y el disco empieza a atenderla.
     * @param cycle ciclo de reloj de la decision
     * @param request la solicitud elegida
     * @param headPosition posicion del cabezal en ese momento
     * @param policy politica con la que se eligio
     */
    void onAttend(long cycle, DiskRequest request, int headPosition, DiskSchedulingPolicy policy);

    /**
     * Se cambio la politica activa.
     * @param pending solicitudes pendientes en ese momento (se reordenan con la nueva politica)
     */
    void onPolicyChange(long cycle, DiskSchedulingPolicy oldPolicy, DiskSchedulingPolicy newPolicy, int pending);

    /**
     * C-SCAN llego al ultimo bloque y vuelve al bloque 0 para seguir barriendo hacia arriba.
     * @param fromBlock bloque donde estaba el cabezal
     * @param toBlock bloque de la solicitud que se atiende despues del salto
     */
    void onWrapAround(long cycle, int fromBlock, int toBlock);
}