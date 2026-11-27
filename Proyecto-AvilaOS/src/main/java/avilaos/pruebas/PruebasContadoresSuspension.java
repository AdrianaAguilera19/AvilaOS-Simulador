/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.HeadDirection;
import avilaos.enums.ProcessState;
import avilaos.enums.SchedulingAlgorithm;
import avilaos.metrics.Metrics;
import avilaos.metrics.MetricsCalculator;
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
 * S14: contadores de suspensiones y de reactivaciones. Cada suspension que el disco termina de escribir
 * suma 1 al contador de suspensiones; cada reactivacion que el disco termina de leer suma 1 al de
 * reactivaciones. Una suspension cancelada (la victima vencio mientras se escribia) no cuenta. Los
 * contadores salen en las metricas del computador y del sistema (MetricsCalculator).
 * Se ejecuta como programa (main); termina con "TODAS LAS PRUEBAS PASARON" o con codigo 1.
 */
public class PruebasContadoresSuspension {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        empiezanEnCero();
        unaSuspensionYUnaReactivacion();
        variasSuspensiones();
        suspensionCanceladaNoCuenta();

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
            PCB p = ProcessFactory.createCpuBound(nombre, 1, memoria, 500, 5000, pc.getId(), 0);
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

        PCB cpu(String nombre, int memoria, int instrucciones) {
            PCB p = ProcessFactory.createCpuBound(nombre, 1, memoria, instrucciones, 5000, pc.getId(), ciclo);
            pc.admit(p);
            return p;
        }

        String log() {
            return cluster.getLogger().getLastAsText(1000);
        }
    }

    private static void empiezanEnCero() {
        titulo("Sin actividad de swap los contadores valen 0");
        Entorno e = new Entorno(30);
        e.cpu("A", 10, 20);
        e.ticks(30);
        Metrics m = MetricsCalculator.forComputer(e.pc);
        check(m.getSuspensions() == 0 && m.getReactivations() == 0, "0 suspensiones y 0 reactivaciones");
        check(MetricsCalculator.forSystem(e.cluster).getSuspensions() == 0, "el sistema tambien marca 0");
    }

    private static void unaSuspensionYUnaReactivacion() {
        titulo("Una suspension y una reactivacion");
        Entorno e = new Entorno(30);
        PCB v = ProcesoBloqueante.crear(e.pc, "V", 5, 20, 500, 1, 60, 5000, 0);
        e.pc.admit(v);
        for (int i = 0; i < 30 && v.getState() != ProcessState.BLOCKED; i++) {
            e.tick();
        }
        e.cpu("N", 25, 30);
        for (int i = 0; i < 400 && e.mts.getSuspensionCount() == 0; i++) {
            e.tick();
        }
        Metrics m = MetricsCalculator.forComputer(e.pc);
        check(m.getSuspensions() == 1 && m.getReactivations() == 0, "tras suspender: 1 suspension, 0 reactivaciones");
        for (int i = 0; i < 600 && e.mts.getReactivationCount() == 0; i++) {
            e.tick();
        }
        m = MetricsCalculator.forComputer(e.pc);
        check(m.getSuspensions() == 1 && m.getReactivations() == 1, "tras reactivar: 1 suspension, 1 reactivacion");
        Metrics s = MetricsCalculator.forSystem(e.cluster);
        check(s.getSuspensions() == 1 && s.getReactivations() == 1, "el sistema suma lo mismo");
        check(m.toString().contains("suspensiones=1") && m.toString().contains("reactivaciones=1"),
                "el texto de las metricas muestra los dos contadores");
    }

    private static void variasSuspensiones() {
        titulo("Dos victimas: dos suspensiones y dos reactivaciones");
        Entorno e = new Entorno(40);
        PCB v1 = ProcesoBloqueante.crear(e.pc, "V1", 5, 15, 500, 1, 60, 5000, 0);
        PCB v2 = ProcesoBloqueante.crear(e.pc, "V2", 5, 15, 500, 1, 60, 5000, 0);
        e.pc.admit(v1);
        e.pc.admit(v2);
        for (int i = 0; i < 40 && !(v1.getState() == ProcessState.BLOCKED && v2.getState() == ProcessState.BLOCKED); i++) {
            e.tick();
        }
        e.cpu("N", 40, 30);   // necesita toda la RAM: hay que suspender a las dos
        for (int i = 0; i < 1500 && e.mts.getReactivationCount() < 2; i++) {
            e.tick();
        }
        Metrics m = MetricsCalculator.forComputer(e.pc);
        check(m.getSuspensions() == 2, "2 suspensiones (" + m.getSuspensions() + ")");
        check(m.getReactivations() == 2, "2 reactivaciones (" + m.getReactivations() + ")");
        check(e.swap.usedBlocks() == 0, "el swap quedo libre");
    }

    private static void suspensionCanceladaNoCuenta() {
        titulo("Una suspension cancelada no suma");
        Entorno e = new Entorno(30);
        PCB v = ProcesoBloqueante.crear(e.pc, "V", 5, 20, 500, 1, 5000, 14, 0);   // vence a los 14 ciclos, con la escritura al swap a medias
        e.pc.admit(v);
        for (int i = 0; i < 30 && v.getState() != ProcessState.BLOCKED; i++) {
            e.tick();
        }
        e.cpu("N", 25, 30);
        for (int i = 0; i < 200 && e.mts.getAbortedSuspensions() == 0 && v.getState() != ProcessState.TERMINATED; i++) {
            e.tick();
        }
        e.ticks(60);
        check(v.getState() == ProcessState.TERMINATED && v.isDeadlineMissed(), "V vencio");
        check(e.mts.getAbortedSuspensions() == 1, "la escritura al swap se pidio y se cancelo (" + e.mts.getAbortedSuspensions() + ")");
        Metrics m = MetricsCalculator.forComputer(e.pc);
        check(m.getSuspensions() == 0 && m.getReactivations() == 0, "0 suspensiones y 0 reactivaciones (" + m.getSuspensions() + "/" + m.getReactivations() + ")");
        check(e.swap.usedBlocks() == 0, "sin bloques de swap huerfanos");
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
