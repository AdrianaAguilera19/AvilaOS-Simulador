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
 * S15: log de decisiones del planificador de mediano plazo. Cada decision queda escrita con su ciclo y
 * con el nombre de los procesos: que el proceso nuevo no cabe, a quien se elige como victima, a quien se
 * suspende y para admitir a quien, cuando queda suspendido, cuando su evento lo pasa a Listo/Suspendido,
 * cuando se reactiva y cuando la reactivacion termina. Tambien quedan las esperas: swap lleno y Nuevo
 * que espera a los suspendidos. Se prueba el orden de los mensajes en un ciclo completo.
 * Se ejecuta como programa (main); termina con "TODAS LAS PRUEBAS PASARON" o con codigo 1.
 */
public class PruebasLogMedianoPlazo {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        cicloCompletoEnElLog();
        esperaPorSwapLleno();
        nuevoEsperaAlSuspendido();
        cancelacionesEnElLog();

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

        /** Proceso Bloqueado/Suspendido con su imagen en el swap (ocupa swap, no RAM). */
        PCB bloqueadoSuspendido(String nombre, int memoria) {
            return bloqueadoSuspendido(nombre, memoria, 5000);
        }

        PCB bloqueadoSuspendido(String nombre, int memoria, int deadline) {
            PCB p = ProcessFactory.createCpuBound(nombre, 1, memoria, 500, deadline, pc.getId(), 0);
            p.setState(ProcessState.READY);
            p.setState(ProcessState.RUNNING);
            p.setState(ProcessState.BLOCKED);
            pc.moveToBlockedSuspended(p);
            swap.reserve(String.valueOf(p.getId()), memoria);
            return p;
        }

        /** Proceso con E/S larga: queda Bloqueado en RAM y es candidato a victima. */
        PCB bloqueado(String nombre, int memoria) {
            PCB p = ProcesoBloqueante.crear(pc, nombre, 5, memoria, 500, 1, 5000, 5000, 0);
            pc.admit(p);
            return p;
        }

        /** Proceso en Listo/Suspendido con su imagen en el swap. */
        PCB listoSuspendido(String nombre, int memoria, int deadline) {
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

    private static void cicloCompletoEnElLog() {
        titulo("Un ciclo completo deja cada decision en el log, en orden");
        Entorno e = new Entorno(30);
        PCB v = ProcesoBloqueante.crear(e.pc, "P7", 5, 20, 500, 1, 60, 5000, 0);
        e.pc.admit(v);
        for (int i = 0; i < 30 && v.getState() != ProcessState.BLOCKED; i++) {
            e.tick();
        }
        e.cpu("P9", 25, 30);
        for (int i = 0; i < 600 && e.mts.getReactivationCount() == 0; i++) {
            e.tick();
        }
        String log = e.log();
        String[] pasos = {
            "P9 (25) no cabe en la RAM",
            "Victima de la suspension: P7",
            "Se suspende al proceso P7 para admitir a P9",
            "P7 queda suspendido (Bloqueado/Suspendido) y libera 20 de memoria",
            "P7 (suspendido) termina su espera (NETWORK_LATENCY) y pasa a Listo/Suspendido",
            "Se reactiva al proceso P7",
            "P7 vuelve del swap a la RAM (Listo/Suspendido -> Listo)",
            "Reactivacion de P7 completa"
        };
        int desde = 0;
        for (int i = 0; i < pasos.length; i++) {
            int pos = log.indexOf(pasos[i], desde);
            check(pos >= 0, "aparece, y en este orden: " + pasos[i]);
            if (pos >= 0) {
                desde = pos;
            }
        }
        check(log.contains("[Ciclo 4] Computador 1: Se suspende al proceso P7 para admitir a P9"),
                "el mensaje lleva el ciclo y el computador");
    }

    private static void esperaPorSwapLleno() {
        titulo("La espera por swap lleno queda en el log");
        Entorno e = new Entorno(100);
        e.bloqueadoSuspendido("S1", 100);
        PCB v = ProcesoBloqueante.crear(e.pc, "P7", 5, 60, 500, 1, 5000, 5000, 0);
        e.pc.admit(v);
        for (int i = 0; i < 30 && v.getState() != ProcessState.BLOCKED; i++) {
            e.tick();
        }
        e.cpu("P9", 60, 100);
        e.ticks(10);
        String log = e.log();
        check(log.contains("No se suspende a P7 (swap lleno"), "dice que no se suspende a P7 por swap lleno");
        check(log.contains("P9 espera en Nuevos"), "dice que P9 espera en Nuevos");
    }

    private static void nuevoEsperaAlSuspendido() {
        titulo("El Nuevo que espera a un Listo/Suspendido queda en el log");
        Entorno e = new Entorno(30);
        e.cpu("Z", 25, 5000);
        e.tick();
        e.listoSuspendido("A", 20, 5000);
        e.cpu("N", 5, 50);
        e.ticks(5);
        check(e.log().contains("N espera en Nuevos: primero se reactivan los procesos Listo/Suspendido"),
                "dice que N espera a que se reactiven los suspendidos");
    }

    private static void cancelacionesEnElLog() {
        titulo("Las cancelaciones y el swap que se libera quedan en el log");
        Entorno e = new Entorno(30);
        PCB v = ProcesoBloqueante.crear(e.pc, "P7", 5, 20, 500, 1, 5000, 14, 0);
        e.pc.admit(v);
        for (int i = 0; i < 30 && v.getState() != ProcessState.BLOCKED; i++) {
            e.tick();
        }
        e.cpu("P9", 25, 30);
        e.ticks(60);
        check(e.log().contains("Se cancela la suspension de P7"), "dice que se cancela la suspension de P7 (vencio mientras se escribia)");

        Entorno f = new Entorno(100);
        f.bloqueadoSuspendido("S1", 30, 5);
        f.ticks(10);
        check(f.log().contains("Se liberan 30 bloques de swap de S1"), "dice que se liberan los 30 bloques de swap de S1 al vencer");
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
