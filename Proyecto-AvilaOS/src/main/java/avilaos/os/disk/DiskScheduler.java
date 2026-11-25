package avilaos.os.disk;

import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.HeadDirection;
import avilaos.interfaces.DiskDecisionListener;
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
    private volatile DiskDecisionListener decisions;

    public DiskScheduler(DiskQueue queue, DiskPolicy policy, ProcessWaker waker) {
        if (queue == null || waker == null) {
            throw new IllegalArgumentException("El planificador necesita la cola y quien despierta procesos");
        }
        this.queue = queue;
        this.waker = waker;
        setPolicy(policy);
    }

    /** Quien se entera de cada decision (atencion, cambio de politica, salto de C-SCAN); null para ninguno. */
    public void setDecisionListener(DiskDecisionListener listener) {
        this.decisions = listener;
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
        return changePolicy(newPolicy, 0);
    }

    /** Igual que changePolicy, avisando al registro de decisiones con el ciclo en que ocurre el cambio. */
    public DiskPolicy changePolicy(DiskPolicy newPolicy, long cycle) {
        DiskPolicy old = policy;
        setPolicy(newPolicy);
        DiskDecisionListener listener = decisions;
        if (listener != null) {
            listener.onPolicyChange(cycle, old.getType(), newPolicy.getType(), queue.size());
        }
        return old;
    }

    /** Igual que changePolicy, pero crea la politica desde el valor del enum (selector de la GUI). */
    public DiskPolicy changePolicy(DiskSchedulingPolicy type) {
        return changePolicy(DiskPolicyFactory.create(type), 0);
    }

    public DiskPolicy changePolicy(DiskSchedulingPolicy type, long cycle) {
        return changePolicy(DiskPolicyFactory.create(type), cycle);
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
        return startNext(0, headPosition, direction);
    }

    /** Igual que startNext, registrando la decision con el ciclo de reloj en que se toma. */
    public DiskRequest startNext(long cycle, int headPosition, HeadDirection direction) {
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
        DiskDecisionListener listener = decisions;
        if (listener != null) {
            // C-SCAN siempre sube: si la elegida esta por debajo del cabezal, hubo salto del final al 0
            if (active.getType() == DiskSchedulingPolicy.C_SCAN && request.getTargetBlock() < headPosition) {
                listener.onWrapAround(cycle, headPosition, request.getTargetBlock());
            }
            listener.onAttend(cycle, request, headPosition, active.getType());
        }
        return request;
    }

    /** Termina la solicitud en servicio y despierta al proceso que la pidio. */
    public DiskRequest completeCurrent() {
        DiskRequest done = queue.finishService();
                if (!done.getOperation().isSwapOperation()) {
            waker.wake(done.getRequester());   // las de swap las cierra el planificador de mediano plazo
        }
        return done;
    }
}