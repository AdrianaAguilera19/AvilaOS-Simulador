package avilaos.pruebas;

import avilaos.enums.BlockReason;
import avilaos.enums.ProcessState;
import avilaos.model.hardware.Cluster;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;
import avilaos.scheduler.FCFSPolicy;
import avilaos.sync.BoundedBuffer;

/**
 * Prueba del arreglo "proceso que muere por deadline mientras espera en un semaforo".
 * Antes del arreglo el proceso muerto seguia en la cola del semaforo: el siguiente semSignal lo
 * "despertaba" y el elemento quedaba inalcanzable, con lo que el siguiente consumidor se
 * quedaba bloqueado para siempre. Se ejecuta como programa (main), con ticks manuales.
 */
public class PruebasDeadlineSemaforo {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        Cluster c = new Cluster(10, 0);
        c.addComputer("PC1", new FCFSPolicy(), 200);
        c.addComputer("PC2", new FCFSPolicy(), 200);
        BoundedBuffer b = c.getBufferManager().createBuffer(2, 1);

        System.out.println("--- consumidor que vence su deadline esperando en el semaforo ---");
        PCB c1 = entregar(c, ProcessFactory.createConsumer("C1", 1, 5, b.getId(), 1, 1, 6, 1, c.getCurrentCycle()));
        for (int i = 0; i < 40 && c1.getState() != ProcessState.TERMINATED; i++) {
            c.tick();
        }
        check(c1.getState() == ProcessState.TERMINATED && c1.isDeadlineMissed(),
                "C1 termino por deadline mientras esperaba");
        check(b.getItems().getValue() == 0, "el semaforo de elementos volvio a 0 (valor " + b.getItems().getValue() + ")");
        check(b.getItems().getWaitingSnapshot().size() == 0, "C1 ya no esta en la cola del semaforo");
        check(c1.getWaitingOn() == null, "C1 ya no apunta a ningun semaforo");

        System.out.println("--- un productor y un consumidor nuevo siguen funcionando ---");
        PCB prod = entregar(c, ProcessFactory.createProducer("P1", 1, 5, b.getId(), 1, 1, 5000, 1, c.getCurrentCycle()));
        PCB c2 = entregar(c, ProcessFactory.createConsumer("C2", 1, 5, b.getId(), 1, 1, 5000, 2, c.getCurrentCycle()));
        for (int i = 0; i < 200 && !(prod.getState() == ProcessState.TERMINATED
                && c2.getState() == ProcessState.TERMINATED); i++) {
            c.tick();
        }
        check(prod.getState() == ProcessState.TERMINATED && !prod.isDeadlineMissed(), "P1 termino bien");
        check(c2.getState() == ProcessState.TERMINATED && !c2.isDeadlineMissed(),
                "C2 consumio el elemento y termino (no quedo bloqueado)");
        check(b.size() == 0 && b.getItems().getValue() == 0 && b.getSpaces().getValue() == 2
                && b.getMutex().getValue() == 1, "buffer y semaforos en reposo");

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
        } else {
            System.out.println("HAY " + fallas + " FALLAS de " + verificaciones + " verificaciones");
            System.exit(1);
        }
    }

    /** Igual que ConfigLoader: primero el ejecutor, despues el proceso. */
    private static PCB entregar(Cluster c, PCB p) {
        c.getComputer(p.getComputerId()).registerRunner(c.getBufferManager().createRunner(p));
        c.submit(p);
        return p;
    }

    private static void check(boolean ok, String texto) {
        verificaciones++;
        System.out.println((ok ? "  OK    " : "  FALLA ") + texto);
        if (!ok) {
            fallas++;
        }
    }
}
