/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.disk;

import avilaos.enums.HeadDirection;
import avilaos.interfaces.DiskPolicy;
import avilaos.interfaces.ProcessWaker;

/**
 * Planificador de disco
 * @author Antonella Ruiz
 */
public class DiskScheduler {
    private final DiskQueue queue;
    private final ProcessWaker waker;
    private volatile DiskPolicy policy;

    public DiskScheduler(DiskQueue queue, DiskPolicy policy, ProcessWaker waker) {
        if (queue == null || waker == null) {
            throw new IllegalArgumentException("El planificador necesita la cola y quien despierta procesos");
        }
        this.queue = queue;
        this.waker = waker;
        setPolicy(policy);
    }

    public DiskQueue getQueue() { return queue; }

    public DiskPolicy getPolicy() { return policy; }

    /** Cambia la politica activa (tambien en caliente: se usa desde la proxima eleccion). */
    public void setPolicy(DiskPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("Falta la politica de disco");
        }
        this.policy = policy;
    }

    /**
     * Si el disco esta libre y hay solicitudes, elige una con la politica activa y la pone en servicio.
     * @return la solicitud elegida, o null si el disco esta ocupado o no hay nada pendiente
     */
    public DiskRequest startNext(int headPosition, HeadDirection direction) {
        if (queue.isBusy() || queue.isEmpty()) {
            return null;
        }
        DiskPolicy active = policy;
        int index = active.selectNext(queue.pendingSnapshot(), headPosition, direction);
        if (index < 0 || index >= queue.size()) {
            throw new IllegalStateException("La politica " + active.getType() + " eligio una posicion invalida: " + index);
        }
        DiskRequest request = queue.startService(index);
        request.markServed(active.getType());
        return request;
    }

    /** Termina la solicitud en servicio y despierta al proceso que la pidio. */
    public DiskRequest completeCurrent() {
        DiskRequest done = queue.finishService();
        waker.wake(done.getRequester());
        return done;
    }
}