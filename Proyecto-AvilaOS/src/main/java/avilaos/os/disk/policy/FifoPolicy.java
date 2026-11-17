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
 * FIFO: atiende las solicitudes en el orden en que llegaron, sin mirar donde esta el cabezal.
 * @author Antonella Ruiz
 */
public class FifoPolicy implements DiskPolicy {

    @Override
    public DiskSchedulingPolicy getType() {
        return DiskSchedulingPolicy.FIFO;
    }

    /** La primera de la lista (la que llego antes), o -1 si no hay ninguna. */
    @Override
    public int selectNext(DynamicArray<DiskRequest> pending, int headPosition, HeadDirection direction) {
        if (pending == null) {
            throw new IllegalArgumentException("Falta la lista de solicitudes pendientes");
        }
        return pending.isEmpty() ? -1 : 0;
    }
}