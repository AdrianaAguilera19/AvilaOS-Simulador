/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.disk.policy;

import avilaos.datastructures.DynamicArray;
import avilaos.enums.HeadDirection;
import avilaos.interfaces.DiskPolicy;
import avilaos.os.disk.DiskRequest;

/**
 * Simula una politica sobre una cola de solicitudes sin tocar el disco ni la cola real: pregunta a la
 * politica cual sigue, mueve un cabezal imaginario y suma el desplazamiento. Sirve para comprobar
 * las politicas con los casos del enunciado, para mostrar en la GUI como quedaria la cola y para
 * comparar politicas.
 * @author Antonella Ruiz
 */
public final class PolicySimulator {

    private PolicySimulator() {
    }

    /**
     * @param policy politica a probar
     * @param pending solicitudes en orden de llegada (no se modifica)
     * @param headPosition bloque donde empieza el cabezal
     * @param direction direccion inicial del cabezal
     * @param totalBlocks tamano del disco (los bloques van de 0 a totalBlocks - 1)
     */
    public static PolicyRun run(DiskPolicy policy, DynamicArray<DiskRequest> pending,
            int headPosition, HeadDirection direction, int totalBlocks) {
        if (policy == null || pending == null || direction == null) {
            throw new IllegalArgumentException("Falta la politica, la cola o la direccion del cabezal");
        }
        if (totalBlocks < 1) {
            throw new IllegalArgumentException("El disco debe tener al menos 1 bloque");
        }
        if (headPosition < 0 || headPosition >= totalBlocks) {
            throw new IllegalArgumentException("El cabezal debe estar entre 0 y " + (totalBlocks - 1));
        }
        DynamicArray<DiskRequest> rest = pending.copy();
        DiskRequest[] order = new DiskRequest[rest.size()];
        long travel = 0;
        int head = headPosition;
        HeadDirection dir = direction;
        for (int n = 0; n < order.length; n++) {
            int index = policy.selectNext(rest, head, dir);
            if (index < 0 || index >= rest.size()) {
                throw new IllegalStateException("La politica " + policy.getType() + " eligio una posicion invalida: " + index);
            }
            DiskRequest request = rest.removeAt(index);
            int target = request.getTargetBlock();
            if (target >= totalBlocks) {
                throw new IllegalArgumentException("El bloque " + target + " esta fuera del disco de " + totalBlocks + " bloques");
            }
            travel += policy.travelDistance(head, dir, target, totalBlocks);
            if (target > head) {
                dir = HeadDirection.UP;
            } else if (target < head) {
                dir = HeadDirection.DOWN;
            }
            head = target;
            order[n] = request;
        }
        return new PolicyRun(order, travel, head, dir);
    }
}