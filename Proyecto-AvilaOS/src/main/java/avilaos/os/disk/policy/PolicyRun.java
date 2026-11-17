/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.disk.policy;

import avilaos.enums.HeadDirection;
import avilaos.os.disk.DiskRequest;

/**
 * Resultado de simular una politica sobre una cola: el orden en que atenderia las solicitudes,
 * el desplazamiento total del cabezal y como quedaria el cabezal al final.
 * @author Antonella Ruiz
 */
public final class PolicyRun {
    private final DiskRequest[] order;
    private final long totalTravel;
    private final int finalHead;
    private final HeadDirection finalDirection;

    PolicyRun(DiskRequest[] order, long totalTravel, int finalHead, HeadDirection finalDirection) {
        this.order = order;
        this.totalTravel = totalTravel;
        this.finalHead = finalHead;
        this.finalDirection = finalDirection;
    }

    /** Cuantas solicitudes se atenderian. */
    public int size() { return order.length; }

    /** La solicitud que se atenderia en esa posicion (0 = la primera). */
    public DiskRequest get(int index) { return order[index]; }

    /** Los bloques destino en el orden de atencion. */
    public int[] blocks() {
        int[] blocks = new int[order.length];
        for (int i = 0; i < order.length; i++) {
            blocks[i] = order[i].getTargetBlock();
        }
        return blocks;
    }

    /** Bloques que recorreria el cabezal en total (incluye el tramo hasta el extremo en SCAN y C-SCAN). */
    public long getTotalTravel() { return totalTravel; }

    public int getFinalHead() { return finalHead; }

    public HeadDirection getFinalDirection() { return finalDirection; }
}