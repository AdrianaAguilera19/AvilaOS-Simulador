/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.datastructures.CustomQueue;
import avilaos.enums.ProcessState;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.hardware.ComputerSnapshot;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;
import avilaos.scheduler.FCFSPolicy;

/**
 * S03: las colas de procesos suspendidos (Listo/Suspendido y Bloqueado/Suspendido) y sus fotos.
 * Comprueba que empiezan vacias, que los procesos se mueven entre colas respetando la tabla de
 * transiciones (S01), que las fotos son copias y que un suspendido tambien vence por deadline.
 * Se ejecuta como programa (main); termina con "TODAS LAS PRUEBAS PASARON" o con codigo 1.
 */
public class PruebasColasSuspendidos {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        colasVacias();
        movimientoEntreColas();
        transicionesInvalidas();
        fotosSonCopias();
        deadlineEnSuspendidos();

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
        } else {
            System.out.println("HAY " + fallas + " FALLAS de " + verificaciones + " verificaciones");
            System.exit(1);
        }
    }

    private static Computer computador(Cluster c) {
        c.addComputer("PC1", new FCFSPolicy(), 100);
        return c.getComputer(1);
    }

    /** Proceso que llega a Bloqueado por un camino legal (sin pasar por el computador). */
    private static PCB bloqueado(String nombre, int deadline) {
        PCB p = ProcessFactory.createCpuBound(nombre, 1, 10, 50, deadline, 1, 0);
        p.setState(ProcessState.READY);
        p.setState(ProcessState.RUNNING);
        p.setState(ProcessState.BLOCKED);
        return p;
    }

    private static void colasVacias() {
        titulo("Colas vacias al inicio");
        Computer pc = computador(new Cluster(10, 0));
        check(pc.getReadySuspendedSnapshot().isEmpty(), "Listo/Suspendido empieza vacia");
        check(pc.getBlockedSuspendedSnapshot().isEmpty(), "Bloqueado/Suspendido empieza vacia");
        ComputerSnapshot s = pc.takeSnapshot();
        check(s.getReadySuspendedQueue().isEmpty() && s.getBlockedSuspendedQueue().isEmpty(),
                "la foto del computador las incluye vacias");
        check(s.totalProcesses() == 0, "totalProcesses() es 0");
    }

    private static void movimientoEntreColas() {
        titulo("Mover entre colas");
        Computer pc = computador(new Cluster(10, 0));
        PCB a = bloqueado("A", 500);
        PCB b = bloqueado("B", 500);
        check(pc.moveToBlockedSuspended(a) && a.getState() == ProcessState.BLOCKED_SUSPENDED,
                "A pasa a Bloqueado/Suspendido");
        check(pc.moveToBlockedSuspended(b), "B tambien");
        check(pc.getBlockedSuspendedSnapshot().size() == 2, "la cola tiene 2 procesos");
        check(pc.getBlockedSuspendedSnapshot().get(0) == a && pc.getBlockedSuspendedSnapshot().get(1) == b,
                "se conserva el orden de llegada (A, B)");
        check(pc.moveToReadySuspended(a) && a.getState() == ProcessState.READY_SUSPENDED,
                "al ocurrir su evento, A pasa a Listo/Suspendido");
        check(pc.getBlockedSuspendedSnapshot().size() == 1 && pc.getBlockedSuspendedSnapshot().get(0) == b,
                "A salio de Bloqueado/Suspendido; queda solo B");
        check(pc.getReadySuspendedSnapshot().size() == 1 && pc.getReadySuspendedSnapshot().get(0) == a,
                "A esta en Listo/Suspendido");
        check(pc.takeSnapshot().totalProcesses() == 2, "la foto cuenta los dos suspendidos");
    }

    private static void transicionesInvalidas() {
        titulo("Transiciones invalidas se rechazan");
        Computer pc = computador(new Cluster(10, 0));
        PCB listo = ProcessFactory.createCpuBound("L", 1, 10, 50, 500, 1, 0);
        listo.setState(ProcessState.READY);
        check(!pc.moveToBlockedSuspended(listo) && listo.getState() == ProcessState.READY,
                "un proceso Listo no se suspende como Bloqueado");
        check(pc.getBlockedSuspendedSnapshot().isEmpty(), "no entra a la cola");
        PCB bloq = bloqueado("B", 500);
        check(!pc.moveToReadySuspended(bloq) && bloq.getState() == ProcessState.BLOCKED,
                "un Bloqueado en RAM no salta a Listo/Suspendido");
        check(pc.getReadySuspendedSnapshot().isEmpty(), "Listo/Suspendido sigue vacia");
        check(!pc.moveToBlockedSuspended(null) && !pc.moveToReadySuspended(null), "null se rechaza");
    }

    private static void fotosSonCopias() {
        titulo("Las fotos son copias");
        Computer pc = computador(new Cluster(10, 0));
        PCB a = bloqueado("A", 500);
        pc.moveToBlockedSuspended(a);
        CustomQueue<PCB> foto = pc.getBlockedSuspendedSnapshot();
        foto.dequeue();
        check(foto.isEmpty() && pc.getBlockedSuspendedSnapshot().size() == 1,
                "vaciar la foto no afecta a la cola real");
    }

    private static void deadlineEnSuspendidos() {
        titulo("Un suspendido tambien vence por deadline");
        Cluster c = new Cluster(10, 0);
        Computer pc = computador(c);
        PCB a = bloqueado("A", 3);
        PCB b = bloqueado("B", 500);
        PCB d = bloqueado("D", 3);
        pc.moveToBlockedSuspended(a);
        pc.moveToBlockedSuspended(b);
        pc.moveToBlockedSuspended(d);
        pc.moveToReadySuspended(d);
        for (int i = 0; i < 10; i++) {
            c.tick();
        }
        check(a.getState() == ProcessState.TERMINATED && a.isDeadlineMissed(),
                "A vence estando en Bloqueado/Suspendido");
        check(d.getState() == ProcessState.TERMINATED && d.isDeadlineMissed(),
                "D vence estando en Listo/Suspendido");
        check(b.getState() == ProcessState.BLOCKED_SUSPENDED, "B (deadline largo) sigue suspendido");
        check(pc.getBlockedSuspendedSnapshot().size() == 1 && pc.getReadySuspendedSnapshot().isEmpty(),
                "A y D salieron de las colas de suspendidos");
        check(pc.getFinishedSnapshot().size() == 2, "A y D estan en terminados");
        check(pc.getRam().getUsedSize() == 0, "no se libero RAM que no tenian (sigue en 0)");
    }

    private static void titulo(String texto) {
        System.out.println("--- " + texto + " ---");
    }

    private static void check(boolean ok, String texto) {
        verificaciones++;
        System.out.println((ok ? "  OK    " : "  FALLA ") + texto);
        if (!ok) {
            fallas++;
        }
    }
}
