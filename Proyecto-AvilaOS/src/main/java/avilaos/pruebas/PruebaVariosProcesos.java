/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.enums.ProcessState;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;
import avilaos.scheduler.FCFSPolicy;
import avilaos.sync.BoundedBuffer;
import avilaos.sync.BufferManager;

/**
 * Escenario de S06: dos productores y dos consumidores comparten un buffer de
 * capacidad 2 que vive en PC1. Un productor y un consumidor están en PC1 (acceso
 * local) y los otros dos en PC2 (acceso remoto, con latencia de red de 2 ciclos).
 * Cada proceso trabaja con 3 elementos.
 */
public class PruebaVariosProcesos {

    private static final int MAX_CICLOS = 500;

    public static void main(String[] args) {
        Cluster cluster = new Cluster(100, 2);
        Computer pc1 = cluster.addComputer("PC1", new FCFSPolicy(), 100);
        Computer pc2 = cluster.addComputer("PC2", new FCFSPolicy(), 100);
        BufferManager mgr = new BufferManager(cluster);
        BoundedBuffer b = mgr.createBuffer(2, pc1.getId());

        PCB p1 = ProcessFactory.createProducer("P1", 1, 10, b.getId(), 2, 3, 1000, pc1.getId(), 0);
        PCB p2 = ProcessFactory.createProducer("P2", 1, 10, b.getId(), 1, 3, 1000, pc2.getId(), 0);
        PCB c1 = ProcessFactory.createConsumer("C1", 1, 10, b.getId(), 2, 3, 1000, pc1.getId(), 0);
        PCB c2 = ProcessFactory.createConsumer("C2", 1, 10, b.getId(), 1, 3, 1000, pc2.getId(), 0);
        registrar(pc1, mgr, p1);
        registrar(pc2, mgr, p2);
        registrar(pc1, mgr, c1);
        registrar(pc2, mgr, c2);

        int fin = -1;
        int maxElementos = 0;
        for (int cycle = 1; cycle <= MAX_CICLOS && fin < 0; cycle++) {
            pc1.onTick(cycle);
            pc2.onTick(cycle);
            maxElementos = Math.max(maxElementos, b.size());
            if (terminaron(p1, p2, c1, c2)) {
                fin = cycle;
            }
        }

        System.out.println(fin > 0
                ? "Los 4 procesos terminaron en el ciclo " + fin
                : "ERROR: no terminaron en " + MAX_CICLOS + " ciclos (posible interbloqueo)");
        System.out.println("Producidos/consumidos: " + b.getTotalProduced() + "/"
                + b.getTotalConsumed() + " (esperado: 6/6)");
        System.out.println("Elementos que quedan en el buffer: " + b.size() + " (esperado: 0)");
        System.out.println("Máximo de elementos en el buffer: " + maxElementos
                + " (esperado: 2 o menos)");
        System.out.println("Semáforos mutex/elementos/espacios: " + b.getMutex().getValue() + "/"
                + b.getItems().getValue() + "/" + b.getSpaces().getValue() + " (esperado: 1/0/2)");
        System.out.println("Bloqueados PC1/PC2: " + pc1.getBlockedSnapshot().size() + "/"
                + pc2.getBlockedSnapshot().size() + " (esperado: 0/0)");
        System.out.println("Accesos remotos PC1/PC2: " + pc1.getNetworkAccesses() + "/"
                + pc2.getNetworkAccesses() + " (esperado: 0/6)");
        System.out.println("Ciclos de espera de red PC2: " + pc2.getNetworkWaitedCycles()
                + " (esperado: 12)");
    }

    private static void registrar(Computer pc, BufferManager mgr, PCB proceso) {
        pc.registerRunner(mgr.createRunner(proceso));
        pc.admit(proceso);
    }

    private static boolean terminaron(PCB a, PCB b, PCB c, PCB d) {
        return a.getState() == ProcessState.TERMINATED && b.getState() == ProcessState.TERMINATED
                && c.getState() == ProcessState.TERMINATED && d.getState() == ProcessState.TERMINATED;
    }
}
