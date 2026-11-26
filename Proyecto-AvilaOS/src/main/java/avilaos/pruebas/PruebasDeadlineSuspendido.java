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
import avilaos.enums.IOOperationType;
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
 * S13 (DEC-08): el deadline sigue corriendo mientras el proceso esta suspendido. Si un suspendido
 * vence, termina (cuenta como deadline incumplido) y sus bloques de swap se liberan: no quedan bloques
 * huerfanos. Si el vencimiento ocurre justo mientras el disco lee su imagen, el swap se libera al
 * terminar la lectura y la RAM reservada se devuelve.
 * Se ejecuta como programa (main); termina con "TODAS LAS PRUEBAS PASARON" o con codigo 1.
 */
public class PruebasDeadlineSuspendido {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        elDeadlineCorreSuspendido();
        venceBloqueadoSuspendido();
        venceListoSuspendidoEsperando();
        venceMientrasSeLee();
        venceUnoYElOtroConservaSuSwap();
        liberarSwapDestrabaUnaSuspension();

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

        int bloquesEscritos = -1;   // tamano de la ultima solicitud SUSPEND vista en la cola
        int bloquesLeidos = -1;     // tamano de la ultima solicitud REACTIVATE vista en la cola

        void tick() {
            ciclo++;
            pc.onTick(ciclo);
            DiskRequest r = pedido();   // se mira justo despues de que el planificador la manda, antes de que el disco la atienda
            if (r != null && r.getOperation() == IOOperationType.SUSPEND) {
                bloquesEscritos = r.getBlocksToTransfer();
            }
            if (r != null && r.getOperation() == IOOperationType.REACTIVATE) {
                bloquesLeidos = r.getBlocksToTransfer();
            }
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
            PCB p = ProcessFactory.createIoBound(nombre, 5, memoria, 500, 1, 5000, 5000, pc.getId(), 0);
            pc.admit(p);
            return p;
        }

        /** Proceso en Listo/Suspendido con su imagen en el swap. */
        PCB listoSuspendido(String nombre, int memoria, int deadline) {
            PCB p = bloqueadoSuspendido(nombre, memoria, deadline);
            pc.moveToReadySuspended(p);
            return p;
        }

        PCB cpu(String nombre, int memoria, int instrucciones) {
            PCB p = ProcessFactory.createCpuBound(nombre, 1, memoria, instrucciones, 5000, pc.getId(), ciclo);
            pc.admit(p);
            return p;
        }

        DiskRequest pedido() {
            return cola.size() > 0 ? cola.get(0) : cola.getInService();
        }

        String log() {
            return cluster.getLogger().getLastAsText(1000);
        }
    }

    private static void elDeadlineCorreSuspendido() {
        titulo("El deadline sigue corriendo mientras esta suspendido");
        Entorno e = new Entorno(100);
        PCB a = e.bloqueadoSuspendido("A", 20, 50);
        int antes = a.getDeadline();
        e.ticks(10);
        check(antes - a.getDeadline() == 10, "en 10 ciclos suspendido perdio 10 de deadline (" + (antes - a.getDeadline()) + ")");
        check(a.getState() == ProcessState.BLOCKED_SUSPENDED && e.swap.blocksOf(String.valueOf(a.getId())) == 20,
                "sigue suspendido y con su swap");
    }

    private static void venceBloqueadoSuspendido() {
        titulo("Vence un Bloqueado/Suspendido: termina y libera su swap");
        Entorno e = new Entorno(100);
        PCB a = e.bloqueadoSuspendido("A", 20, 5);
        check(e.swap.usedBlocks() == 20, "A tiene 20 bloques en el swap");
        e.ticks(10);
        check(a.getState() == ProcessState.TERMINATED && a.isDeadlineMissed(), "A termino por deadline");
        check(e.pc.getBlockedSuspendedSnapshot().isEmpty(), "ya no esta en Bloqueado/Suspendido");
        check(e.pc.getFinishedSnapshot().size() == 1, "esta en terminados");
        check(e.swap.blocksOf(String.valueOf(a.getId())) == 0 && e.swap.usedBlocks() == 0, "sus bloques de swap se liberaron");
        check(e.pc.getRam().getUsedSize() == 0, "no toco la RAM");
        check(e.log().contains("bloques de swap de A"), "el log dice que se liberaron sus bloques de swap");
    }

    private static void venceListoSuspendidoEsperando() {
        titulo("Vence un Listo/Suspendido que esperaba memoria");
        Entorno e = new Entorno(30);
        PCB z = e.cpu("Z", 25, 5000);
        e.tick();                                   // Z entra a la RAM y ocupa 25: A (20) no cabe
        check(z.getState() != ProcessState.NEW && e.pc.getRam().getUsedSize() == 25, "Z ocupa 25 de RAM");
        PCB a = e.listoSuspendido("A", 20, 8);
        e.ticks(15);
        check(a.getState() == ProcessState.TERMINATED && a.isDeadlineMissed(), "A termino por deadline");
        check(e.pc.getReadySuspendedSnapshot().isEmpty(), "ya no esta en Listo/Suspendido");
        check(e.swap.usedBlocks() == 0, "sus bloques de swap se liberaron");
        check(e.mts.getReactivationCount() == 0 && e.mts.getReactivating() == null, "nunca se intento reactivarlo");
        check(e.pc.getRam().getUsedSize() == 25, "la RAM sigue igual (solo Z)");
    }

    private static void venceMientrasSeLee() {
        titulo("Vence mientras el disco lee su imagen");
        Entorno e = new Entorno(30);
        PCB a = e.listoSuspendido("A", 20, 5);
        e.tick();
        check(e.mts.getReactivating() == a, "se pidio la lectura de A");
        for (int i = 0; i < 100 && a.getState() != ProcessState.TERMINATED; i++) {
            e.tick();
        }
        check(a.getState() == ProcessState.TERMINATED && a.isDeadlineMissed(), "A vencio mientras se leia");
        check(e.mts.getReactivating() == a && e.swap.blocksOf(String.valueOf(a.getId())) == 20,
                "el swap sigue ocupado mientras el disco termina de leer (no se sueltan bloques en uso)");
        for (int i = 0; i < 100 && e.mts.getReactivating() != null; i++) {
            e.tick();
        }
        check(e.mts.getReactivating() == null && e.mts.getReactivationCount() == 0, "la reactivacion se cancelo");
        check(e.swap.usedBlocks() == 0 && e.swap.blocksOf(String.valueOf(a.getId())) == 0, "el swap termino libre");
        check(e.pc.getRam().getUsedSize() == 0, "la RAM reservada se devolvio");
    }

    private static void venceUnoYElOtroConservaSuSwap() {
        titulo("Si vence uno, el otro suspendido conserva su swap");
        Entorno e = new Entorno(100);
        PCB a = e.bloqueadoSuspendido("A", 16, 5);
        PCB b = e.bloqueadoSuspendido("B", 24, 5000);
        e.ticks(10);
        check(a.getState() == ProcessState.TERMINATED, "A vencio");
        check(b.getState() == ProcessState.BLOCKED_SUSPENDED, "B sigue suspendido");
        check(e.swap.blocksOf(String.valueOf(a.getId())) == 0, "A no tiene swap");
        check(e.swap.blocksOf(String.valueOf(b.getId())) == 24 && e.swap.usedBlocks() == 24, "B conserva sus 24 bloques");
    }

    private static void liberarSwapDestrabaUnaSuspension() {
        titulo("El swap liberado por un vencido destraba la suspension que esperaba");
        Entorno e = new Entorno(100);
        PCB s1 = e.bloqueadoSuspendido("S1", 100, 40);   // llena el swap y vence a los 40 ciclos
        PCB v = e.bloqueado("V", 60);
        for (int i = 0; i < 30 && v.getState() != ProcessState.BLOCKED; i++) {
            e.tick();
        }
        PCB n = e.cpu("N", 60, 100);
        e.ticks(5);
        check(n.getState() == ProcessState.NEW && e.mts.getSuspensionCount() == 0, "con el swap lleno N espera");
        for (int i = 0; i < 200 && e.mts.getSuspensionCount() == 0; i++) {
            e.tick();
        }
        check(s1.getState() == ProcessState.TERMINATED && s1.isDeadlineMissed(), "S1 vencio");
        check(e.mts.getSuspensionCount() == 1 && v.getState() == ProcessState.BLOCKED_SUSPENDED, "entonces V pudo suspenderse");
        e.ticks(5);
        check(n.getState() != ProcessState.NEW, "y N fue admitido");
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