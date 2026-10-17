package avilaos.scheduler;

import avilaos.enums.SchedulingAlgorithm;
import avilaos.interfaces.SchedulingPolicy;

/**
 * Crea la politica de planificacion que corresponde a cada valor del enum.
 * Es el unico lugar que conoce las clases concretas: la GUI y Computer solo
 * trabajan con SchedulingAlgorithm. Para agregar una politica nueva basta con
 * un caso mas aqui.
 */
public final class PolicyFactory {

    private PolicyFactory() { }

    /**
     * @param algorithm algoritmo elegido (no puede ser null)
     * @param quantum   quantum para Round Robin (las demas politicas lo ignoran)
     * @return una politica nueva lista para usar
     */
    public static SchedulingPolicy create(SchedulingAlgorithm algorithm, int quantum) {
        if (algorithm == null) {
            throw new IllegalArgumentException("El algoritmo no puede ser nulo");
        }
        switch (algorithm) {
            case FCFS:        return new FCFSPolicy();
            case EDF:         return new EDFPolicy();
            case PRIORITY:    return new PriorityPolicy();
            case ROUND_ROBIN: return new RoundRobinPolicy(quantum);
            default:
                throw new IllegalArgumentException("Algoritmo no soportado: " + algorithm);
        }
    }
}
