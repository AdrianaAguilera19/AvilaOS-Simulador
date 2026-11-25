/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.HeadDirection;
import avilaos.enums.IOOperationType;
import avilaos.enums.ProcessState;
import avilaos.enums.SchedulingAlgorithm;
import avilaos.model.hardware.BlockMap;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.hardware.Disk;
import avilaos.model.hardware.DiskHead;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;
import avilaos.os.disk.DiskQueue;
import avilaos.os.disk.DiskRecordLog;
import avilaos.os.disk.DiskRequest;
import avilaos.os.disk.DiskScheduler;
import avilaos.os.disk.SwapArea;
import avilaos.os.disk.policy.DiskPolicyFactory;
import avilaos.os.filesystem.ChainAllocator;
import avilaos.os.filesystem.DiskIoCoordinator;
import avilaos.os.filesystem.FileSystem;
import avilaos.os.filesystem.FileTree;
import avilaos.os.filesystem.PermissionMatrix;
import avilaos.os.filesystem.SecuredFileSystem;
import avilaos.os.midterm.MediumTermScheduler;
import avilaos.scheduler.PolicyFactory;

/**
 * S09: la reactivacion. Un proceso en Listo/Suspendido vuelve a la RAM cuando hay memoria: se reserva
 * su lugar, se pide una lectura REACTIVATE del swap por la cola del disco y, cuando el disco termina,
 * el proceso pasa a Listo y se liberan sus bloques de swap. Se prueban los casos: cabe, no cabe,
 * de a uno y en orden, primero el que cabe, vence mientras se lee, no compite con una suspension
 * pendiente, y el ciclo completo suspender -> evento -> reactivar.
 * Se ejecuta como programa (main); termina con "TODAS LAS PRUEBAS PASARON" o con codigo 1.
 */
public class PruebasReactivacion {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        cabeYSeReactiva();
        noCabeYEspera();
        deAUnoYEnOrden();
        eligeElPrimeroQueCabe();
        venceMientrasSeLee();
        noCompiteConUnaSuspension();
        cicloCompleto();

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
        } else {
            System.out.println("HAY " + fallas + " FALLAS de " + verificaciones + " verificaciones");
            System.exit(1);
        }
    }

    private static class Entorno {
        final Computer pc;
        final DiskQueue cola;
        final SwapArea swap;
        final DiskIoCoordinator coordinador;
        final MediumTermScheduler mts;
        long ciclo = 0;

        Entorno(int ram) {
            BlockMap mapa = new BlockMap(300);
            FileTree arbol = new FileTree("admin");
            ChainAllocator asignador = new ChainAllocator(mapa, 200);
            FileSystem fs = new FileSystem(arbol, asignador, mapa);
            swap = new SwapArea(mapa, 200, 100);
            Cluster c = new Cluster(100, 0);
            pc = c.addComputer("PC1", PolicyFactory.create(SchedulingAlgorithm.FCFS, 2), ram);
            Disk disco = new Disk(mapa, new DiskHead(300, 0, 50, HeadDirection.UP));
            cola = new DiskQueue();
            DiskScheduler planificador = new DiskScheduler(cola, DiskPolicyFactory.create(DiskSchedulingPolicy.FIFO), c);
            SecuredFileSystem seguro = new SecuredFileSystem(fs, new PermissionMatrix(arbol));
            coordinador = new DiskIoCoordinator(pc, disco, planificador, seguro, new DiskRecordLog(), c.getLogger());
            mts = pc.getMediumTermScheduler();
            mts.attachSwap(swap, cola);
        }

        void tick() {
            ciclo++;
            pc.onTick(ciclo);
            coordinador.onTick(ciclo);
        }

        void ticks(int n) {
            for (int i = 0; i < n; i++) {
                tick();
            }
        }

        /** Proceso que ya esta en Listo/Suspendido con su imagen en el swap (sin pasar por la suspension). */
        PCB suspendido(String nombre, int memoria, int deadline) {
            PCB p = ProcessFactory.createCpuBound(nombre, 1, memoria, 500, deadline, pc.getId(), 0);
            p.setState(ProcessState.READY);
            p.setState(ProcessState.RUNNING);
            p.setState(ProcessState.BLOCKED);
            pc.moveToBlockedSuspended(p);
            pc.moveToReadySuspended(p);
            swap.reserve(String.valueOf(p.getId()), memoria);
            return p;
        }

        PCB cpu(String nombre, int memoria, int instrucciones) {
            PCB p = ProcessFactory.createCpuBound(nombre, 1, memoria, instrucciones, 5000, pc.getId(), ciclo);
            pc.admit(p);
            return p;
        }

        int enDisco() {
            return cola.size() + (cola.getInService() != null ? 1 : 0);
        }

        boolean esReactivacion(DiskRequest r) {
            return r != null && r.getOperation() == IOOperationType.REACTIVATE;
        }

        DiskRequest pedido() {
            return cola.size() > 0 ? cola.get(0) : cola.getInService();
        }
    }

    private static void cabeYSeReactiva() {
        titulo("Cabe: se pide la lectura, vuelve a Listo y libera el swap");
        Entorno e = new Entorno(30);
        PCB a = e.suspendido("A", 20, 5000);
        check(e.swap.usedBlocks() == 20 && e.pc.getRam().getUsedSize() == 0, "A esta en el swap (20 bloques) y no usa RAM");
        e.tick();
        DiskRequest r = e.pedido();
        check(e.esReactivacion(r) && r.getRequester() == a, "hay una solicitud REACTIVATE de A en la cola del disco");
        check(r != null && r.getBlocksToTransfer() == 20 && r.getTargetBlock() == e.swap.firstBlockOf(String.valueOf(a.getId())),
                "lee 20 bloques, empezando por el primero de A en el swap");
        check(e.pc.getRam().getUsedSize() == 20, "su lugar en la RAM se reserva desde que se pide la lectura");
        check(a.getState() == ProcessState.READY_SUSPENDED, "mientras el disco lee, A sigue Listo/Suspendido");
        for (int i = 0; i < 100 && e.mts.getReactivationCount() == 0; i++) {
            e.tick();
        }
        check(e.mts.getReactivationCount() == 1, "la reactivacion se completo");
        check(a.getState() != ProcessState.READY_SUSPENDED, "A salio de Listo/Suspendido (Listo o ya en CPU)");
        check(e.pc.getReadySuspendedSnapshot().isEmpty(), "la cola de Listo/Suspendido quedo vacia");
        check(e.swap.usedBlocks() == 0, "se liberaron sus bloques de swap");
        check(e.pc.getRam().getUsedSize() == 20, "A ocupa 20 de RAM");
        int enRam = e.pc.getReadySnapshot().size() + (e.pc.getRunning() != null ? 1 : 0);
        check(enRam == 1, "A esta una sola vez en las colas de la RAM (el disco no lo despierta ademas)");
    }

    private static void noCabeYEspera() {
        titulo("No cabe: espera hasta que se libere memoria");
        Entorno e = new Entorno(30);
        PCB z = e.cpu("Z", 25, 12);
        PCB a = e.suspendido("A", 20, 5000);
        e.ticks(5);
        check(z.getState() != ProcessState.TERMINATED, "Z (25) sigue ocupando la RAM");
        check(e.cola.isEmpty() && e.cola.getInService() == null, "no se pide ninguna lectura (A necesita 20 y hay 5 libres)");
        check(a.getState() == ProcessState.READY_SUSPENDED && e.pc.getRam().getUsedSize() == 25, "A espera en el swap");
        for (int i = 0; i < 200 && e.mts.getReactivationCount() == 0; i++) {
            e.tick();
        }
        check(z.getState() == ProcessState.TERMINATED, "Z termino y libero su memoria");
        check(e.mts.getReactivationCount() == 1 && a.getState() != ProcessState.READY_SUSPENDED,
                "entonces A se reactivo");
    }

    private static void deAUnoYEnOrden() {
        titulo("De a uno y en orden de llegada");
        Entorno e = new Entorno(30);
        PCB a = e.suspendido("A", 10, 5000);
        PCB c = e.suspendido("C", 10, 5000);
        int maxJuntas = 0;
        PCB primero = null;
        for (int i = 0; i < 200 && e.mts.getReactivationCount() < 2; i++) {
            e.tick();
            maxJuntas = Math.max(maxJuntas, e.enDisco());
            if (primero == null && e.pedido() != null && e.esReactivacion(e.pedido())) {
                primero = e.pedido().getRequester();
            }
        }
        check(e.mts.getReactivationCount() == 2, "se reactivaron los dos");
        check(maxJuntas <= 1, "nunca hubo dos lecturas a la vez (maximo " + maxJuntas + ")");
        check(primero == a, "primero se reactivo A, que llego antes");
        check(e.swap.usedBlocks() == 0 && e.pc.getRam().getUsedSize() == 20, "swap libre y 20 de RAM ocupados");
        check(c.getState() != ProcessState.READY_SUSPENDED, "C tambien volvio");
    }

    private static void eligeElPrimeroQueCabe() {
        titulo("Se reactiva al primero que quepa");
        Entorno e = new Entorno(30);
        e.cpu("Z", 10, 5000);
        PCB grande = e.suspendido("Grande", 25, 5000);
        PCB chico = e.suspendido("Chico", 15, 5000);
        e.tick();
        check(e.esReactivacion(e.pedido()) && e.pedido().getRequester() == chico,
                "Grande (25) no cabe en los 20 libres: se reactiva a Chico (15)");
        for (int i = 0; i < 100 && e.mts.getReactivationCount() == 0; i++) {
            e.tick();
        }
        check(chico.getState() != ProcessState.READY_SUSPENDED && grande.getState() == ProcessState.READY_SUSPENDED,
                "Chico volvio y Grande sigue esperando en el swap");
    }

    private static void venceMientrasSeLee() {
        titulo("Vence mientras se lee: se devuelve la RAM reservada y el swap");
        Entorno e = new Entorno(30);
        PCB a = e.suspendido("A", 20, 5);
        e.tick();
        check(e.esReactivacion(e.pedido()) && e.pc.getRam().getUsedSize() == 20, "se pidio la lectura y se reservo la RAM");
        for (int i = 0; i < 100 && e.mts.getAbortedReactivations() == 0; i++) {
            e.tick();
        }
        check(a.getState() == ProcessState.TERMINATED && a.isDeadlineMissed(), "A vencio por deadline mientras se leia");
        check(e.mts.getAbortedReactivations() == 1 && e.mts.getReactivationCount() == 0, "la reactivacion se cancelo");
        check(e.pc.getRam().getUsedSize() == 0, "la RAM reservada se devolvio");
        check(e.swap.usedBlocks() == 0, "el swap se devolvio");
        check(e.mts.getReactivating() == null, "no queda reactivacion en curso");
    }

    private static void noCompiteConUnaSuspension() {
        titulo("No se reactiva mientras hay que suspender para admitir a un nuevo");
        Entorno e = new Entorno(30);
        PCB v = ProcessFactory.createIoBound("V", 5, 20, 500, 1, 5000, 5000, e.pc.getId(), 0);
        e.pc.admit(v);
        for (int i = 0; i < 30 && v.getState() != ProcessState.BLOCKED; i++) {
            e.tick();
        }
        PCB a = e.suspendido("A", 10, 5000);   // cabria en los 10 libres
        e.cpu("N", 20, 500);                    // pero N no cabe y obliga a suspender a V
        e.tick();
        check(e.mts.isTriggerPending(), "N no cabe: hay que suspender a V");
        boolean hayLectura = e.esReactivacion(e.pedido());
        check(!hayLectura, "no se pide la lectura de A mientras el disparo de suspension esta activo");
        check(a.getState() == ProcessState.READY_SUSPENDED, "A sigue en el swap");
    }

    private static void cicloCompleto() {
        titulo("Ciclo completo: suspender -> ocurre su evento -> reactivar");
        Entorno e = new Entorno(30);
        PCB a = ProcessFactory.createIoBound("A", 5, 20, 500, 1, 60, 5000, e.pc.getId(), 0);
        e.pc.admit(a);
        for (int i = 0; i < 30 && a.getState() != ProcessState.BLOCKED; i++) {
            e.tick();
        }
        PCB b = e.cpu("B", 20, 60);
        boolean suspendido = false;
        boolean listoSuspendido = false;
        for (int i = 0; i < 400 && e.mts.getReactivationCount() == 0; i++) {
            e.tick();
            suspendido = suspendido || a.getState() == ProcessState.BLOCKED_SUSPENDED;
            listoSuspendido = listoSuspendido || a.getState() == ProcessState.READY_SUSPENDED;
        }
        check(suspendido, "A se suspendio (Bloqueado/Suspendido) para que entrara B");
        check(listoSuspendido, "al cumplirse su E/S paso a Listo/Suspendido");
        check(b.getState() == ProcessState.TERMINATED, "B termino y libero la RAM");
        check(e.mts.getSuspensionCount() == 1 && e.mts.getReactivationCount() == 1, "1 suspension y 1 reactivacion");
        check(a.getState() != ProcessState.READY_SUSPENDED && a.getState() != ProcessState.BLOCKED_SUSPENDED,
                "A volvio a la RAM");
        check(e.swap.usedBlocks() == 0, "el swap quedo libre");
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
