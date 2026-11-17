/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.disk.policy;

import avilaos.datastructures.DynamicArray;
import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.HeadDirection;
import avilaos.interfaces.DiskPolicy;
import avilaos.os.disk.DiskRequest;

/**
 * SSTF atiende la solicitud mas cercana al cabezal.
 * Si dos estan a la misma distancia, gana la que llego primero.
 * @author Antonella Ruiz
 */
public class SstfPolicy implements DiskPolicy {

    @Override
    public DiskSchedulingPolicy getType() {
        return DiskSchedulingPolicy.SSTF;
    }

    /** La mas cercana al cabezal (empate: la primera de la lista), o -1 si no hay ninguna. */
    @Override
    public int selectNext(DynamicArray<DiskRequest> pending, int headPosition, HeadDirection direction) {
        if (pending == null) {
            throw new IllegalArgumentException("Falta la lista de solicitudes pendientes");
        }
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < pending.size(); i++) {
            int distance = Math.abs(pending.get(i).getTargetBlock() - headPosition);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }
}