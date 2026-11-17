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
 * SCAN (ascensor): el cabezal avanza en su direccion atendiendo lo que encuentra, llega hasta el
 * ULTIMO BLOQUE DEL DISCO (no solo hasta la ultima solicitud, eso seria LOOK) y regresa.
 * Por eso al invertir el sentido el recorrido incluye el tramo hasta el extremo.
 * @author Antonella Ruiz
 */
public class ScanPolicy implements DiskPolicy {

    @Override
    public DiskSchedulingPolicy getType() {
        return DiskSchedulingPolicy.SCAN;
    }

    /**
     * La mas cercana en la direccion actual (incluye el bloque donde ya esta el cabezal).
     * Si ya no queda nada adelante, invierte el sentido y toma la mas cercana en el otro.
     */
    @Override
    public int selectNext(DynamicArray<DiskRequest> pending, int headPosition, HeadDirection direction) {
        if (pending == null || direction == null) {
            throw new IllegalArgumentException("Falta la lista de solicitudes o la direccion del cabezal");
        }
        int ahead = nearest(pending, headPosition, direction);
        return ahead >= 0 ? ahead : nearest(pending, headPosition, direction.opposite());
    }

    /**
     * Si el destino esta en la direccion actual, la distancia directa. Si no, el cabezal sigue hasta
     * el extremo del disco, invierte el sentido y vuelve: (extremo - cabezal) + (extremo - destino).
     */
    @Override
    public int travelDistance(int headPosition, HeadDirection direction, int targetBlock, int totalBlocks) {
        if (isAhead(targetBlock, headPosition, direction)) {
            return Math.abs(targetBlock - headPosition);
        }
        int last = totalBlocks - 1;
        return direction == HeadDirection.UP
                ? (last - headPosition) + (last - targetBlock)
                : headPosition + targetBlock;
    }

    private static boolean isAhead(int target, int head, HeadDirection direction) {
        return direction == HeadDirection.UP ? target >= head : target <= head;
    }

    private static int nearest(DynamicArray<DiskRequest> pending, int head, HeadDirection direction) {
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < pending.size(); i++) {
            int target = pending.get(i).getTargetBlock();
            if (!isAhead(target, head, direction)) {
                continue;
            }
            int distance = Math.abs(target - head);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }
}