/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.HeadDirection;
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
 * S10 (DEC-07): regla entre reactivar suspendidos y admitir nuevos cuando se libera memoria.
 * Regla: mientras haya procesos en Listo/Suspendido, la memoria libre es para reactivarlos; los
 * Nuevos esperan (y tampoco provocan suspensiones). Cuando la cola de Listo/Suspendido queda vacia,
 * los Nuevos se admiten como siempre. Se prueban: sin suspendidos nada cambia, el Nuevo que cabria
 * igual espera, al liberarse memoria el suspendido vuelve antes que el Nuevo, el aviso del log sale
 * una sola vez, y si el suspendido vence los Nuevos pasan.
 * Se ejecuta como programa (main); termina con "TODAS LAS PRUEBAS PASARON" o con codigo 1.
 */
public class PruebasReglaAdmision {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        sinSuspendidosNadaCambia();
        nuevoQueCabriaEsperaAlSuspendido();
        alLiberarseMemoriaVaPrimeroElSuspendido();
        elAvisoDelLogSaleUnaSolaVez();
        siElSuspendidoVenceLosNuevosPasan();

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
        } else {
            System.out.println("HAY " + fallas + " FALLAS de " + verificaciones + " verificaciones");
            System.exit(1);
        }
    }

    private static class Entorno {
        final Cluster cluster;
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
            cluster = new Cluster(100, 0);
            pc = cluster.addComputer("PC1", PolicyFactory.create(SchedulingAlgorithm.FCFS, 2), ram);
            Disk disco = new Disk(mapa, new DiskHead(300, 0, 50, HeadDirection.UP));
            cola = new DiskQueue();
            DiskScheduler planificador = new DiskScheduler(cola, DiskPolicyFactory.create(DiskSchedulingPolicy.FIFO), cluster);
            SecuredFileSystem seguro = new SecuredFileSystem(fs, new PermissionMatrix(arbol));
            coordinador = new DiskIoCoordinator(pc, disco, planificador, seguro, new DiskRecordLog(), cluster.getLogger());
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

        String log() {
            return cluster.getLogger().getLastAsText(1000);
        }
    }

    private static void sinSuspendidosNadaCambia() {
        titulo("Sin Listo/Suspendido, el Nuevo se admite como siempre");
        Entorno e = new Entorno(30);
        PCB n = e.cpu("N", 10, 50);
        e.tick();
        check(n.getState() != ProcessState.NEW, "N salio de Nuevos en el primer ciclo");
        check(e.pc.getRam().getUsedSize() == 10, "N ocupa 10 de RAM");
        check(!e.log().contains("espera en Nuevos"), "el log no dice que espere a nadie");
    }

    private static void nuevoQueCabriaEsperaAlSuspendido() {
        titulo("Un Nuevo que cabria igual espera si hay un Listo/Suspendido");
        Entorno e = new Entorno(30);
        PCB z = e.cpu("Z", 25, 5000);
        e.tick();   // Z entra a la RAM: quedan 5 libres
        PCB a = e.suspendido("A", 20, 5000);   // A no cabe (necesita 20)
        PCB n = e.cpu("N", 5, 50);              // N si cabria en los 5 libres
        e.ticks(5);
        check(z.getState() != ProcessState.NEW && z.getState() != ProcessState.TERMINATED, "Z sigue en la RAM");
        check(a.getState() == ProcessState.READY_SUSPENDED, "A espera en el swap (no le alcanza la memoria)");
        check(n.getState() == ProcessState.NEW, "N espera en Nuevos aunque cabria: A va primero");
        check(e.pc.getRam().getUsedSize() == 25, "la RAM no cambio (solo Z)");
        check(!e.mts.isTriggerPending(), "el Nuevo en espera no activa ninguna suspension");
    }

    private static void alLiberarseMemoriaVaPrimeroElSuspendido() {
        titulo("Al liberarse memoria el suspendido vuelve antes que el Nuevo");
        Entorno e = new Entorno(30);
        PCB z = e.cpu("Z", 25, 12);
        e.tick();
        PCB a = e.suspendido("A", 10, 5000);   // con Z fuera cabrian A (10) y N (10) a la vez
        PCB n = e.cpu("N", 10, 50);
        boolean nAntesQueA = false;
        for (int i = 0; i < 300 && e.mts.getReactivationCount() == 0; i++) {
            e.tick();
            // si N saliera de Nuevos mientras A sigue en Listo/Suspendido se estaria violando la regla
            if (n.getState() != ProcessState.NEW && a.getState() == ProcessState.READY_SUSPENDED) {
                nAntesQueA = true;
            }
        }
        check(z.getState() == ProcessState.TERMINATED, "Z termino y libero su memoria");
        check(e.mts.getReactivationCount() == 1, "A se reactivo");
        check(!nAntesQueA, "N nunca salio de Nuevos antes que A");
        e.ticks(2);
        check(n.getState() != ProcessState.NEW, "con la cola de suspendidos vacia, N ya fue admitido");
        check(e.swap.usedBlocks() == 0, "el swap quedo libre");
    }

    private static void elAvisoDelLogSaleUnaSolaVez() {
        titulo("El aviso en el log sale una sola vez por Nuevo");
        Entorno e = new Entorno(30);
        e.cpu("Z", 25, 5000);
        e.tick();
        e.suspendido("A", 20, 5000);
        e.cpu("N", 5, 50);
        e.ticks(10);
        String log = e.log();
        int veces = 0;
        int desde = 0;
        while ((desde = log.indexOf("N espera en Nuevos", desde)) >= 0) {
            veces++;
            desde++;
        }
        check(veces == 1, "el log dice una sola vez que N espera en Nuevos (salio " + veces + ")");
    }

    private static void siElSuspendidoVenceLosNuevosPasan() {
        titulo("Si el suspendido vence, el Nuevo ya no espera");
        Entorno e = new Entorno(30);
        e.cpu("Z", 25, 5000);
        e.tick();
        PCB a = e.suspendido("A", 20, 4);   // vence a los pocos ciclos
        PCB n = e.cpu("N", 5, 50);
        e.ticks(2);
        check(n.getState() == ProcessState.NEW, "mientras A vive en Listo/Suspendido, N espera");
        e.ticks(6);
        check(a.getState() == ProcessState.TERMINATED && a.isDeadlineMissed(), "A vencio por deadline");
        check(n.getState() != ProcessState.NEW, "sin suspendidos, N fue admitido");
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
