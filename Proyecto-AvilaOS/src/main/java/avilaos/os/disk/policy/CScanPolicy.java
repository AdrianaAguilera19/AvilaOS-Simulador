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
 * C-SCAN (ascensor circular)
 * @author Antonella Ruiz
 */
public class CScanPolicy implements DiskPolicy {

    @Override
    public DiskSchedulingPolicy getType() {
        return DiskSchedulingPolicy.C_SCAN;
    }

    /**
     * La de bloque mas cercano hacia arriba (incluye el bloque del cabezal). Si ya no queda nada
     * arriba, "salta al 0" y toma la de bloque mas bajo de todas. Empate: la que llego primero.
     */
    @Override
    public int selectNext(DynamicArray<DiskRequest> pending, int headPosition, HeadDirection direction) {
        if (pending == null) {
            throw new IllegalArgumentException("Falta la lista de solicitudes pendientes");
        }
        int ahead = -1;
        int aheadBlock = Integer.MAX_VALUE;
        int lowest = -1;
        int lowestBlock = Integer.MAX_VALUE;
        for (int i = 0; i < pending.size(); i++) {
            int target = pending.get(i).getTargetBlock();
            if (target >= headPosition && target < aheadBlock) {
                aheadBlock = target;
                ahead = i;
            }
            if (target < lowestBlock) {
                lowestBlock = target;
                lowest = i;
            }
        }
        return ahead >= 0 ? ahead : lowest;
    }

    /**
     * Si el destino esta arriba, la distancia directa. Si no, hasta el ultimo bloque, salto al 0
     * (no cuenta) y de ahi hasta el destino: (ultimo - cabezal) + destino.
     */
    @Override
    public int travelDistance(int headPosition, HeadDirection direction, int targetBlock, int totalBlocks) {
        if (targetBlock >= headPosition) {
            return targetBlock - headPosition;
        }
        return (totalBlocks - 1 - headPosition) + targetBlock;
    }
}