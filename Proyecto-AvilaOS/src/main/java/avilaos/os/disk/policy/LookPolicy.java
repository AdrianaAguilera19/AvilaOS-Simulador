/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.disk.policy;

import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.HeadDirection;

/**
 * LOOK: politica extra que demuestra que el planificador se extiende sin modificarlo.
 * @author Antonella Ruiz
 */
public class LookPolicy extends ScanPolicy {

    @Override
    public DiskSchedulingPolicy getType() {
        return DiskSchedulingPolicy.LOOK;
    }

    /** El cabezal invierte en la ultima solicitud, no en el extremo: la distancia es la directa. */
    @Override
    public int travelDistance(int headPosition, HeadDirection direction, int targetBlock, int totalBlocks) {
        return Math.abs(targetBlock - headPosition);
    }
}