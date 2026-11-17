/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.disk;

import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.HeadDirection;
import avilaos.interfaces.DiskPolicy;
import avilaos.interfaces.ProcessWaker;
import avilaos.os.disk.policy.DiskPolicyFactory;
import avilaos.os.disk.policy.PolicyRun;
import avilaos.os.disk.policy.PolicySimulator;

/**
 * Planificador de disco: toma solicitudes de la cola segun la politica activa y, cuando el
 * disco termina una, despierta al proceso que la pidio (pasa a Listo).
 * Solo conoce la interfaz DiskPolicy, no ninguna politica concreta.
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
     * Cambio de politica en caliente, con solicitudes pendientes. La cola y el cabezal no se tocan:
     * ninguna solicitud se pierde ni se duplica, y la proxima eleccion ya usa la nueva politica
     * con la direccion que lleve el cabezal en ese momento. La que esta en servicio termina con
     * la politica con la que se eligio.
     * @return la politica que estaba activa
     */
    public DiskPolicy changePolicy(DiskPolicy newPolicy) {
        DiskPolicy old = policy;
        setPolicy(newPolicy);
        return old;
    }

    /** Igual que changePolicy, pero crea la politica desde el valor del enum (selector de la GUI). */
    public DiskPolicy changePolicy(DiskSchedulingPolicy type) {
        return changePolicy(DiskPolicyFactory.create(type));
    }

    /**
     * Como quedaria la cola pendiente con la politica activa: orden de atencion y desplazamiento.
     * No modifica la cola ni el disco; la GUI la usa para mostrar el reordenamiento tras un cambio.
     */
    public PolicyRun plan(int headPosition, HeadDirection direction, int totalBlocks) {
        return PolicySimulator.run(policy, queue.pendingSnapshot(), headPosition, direction, totalBlocks);
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