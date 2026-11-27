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
 * S12: un proceso suspendido ocupa en el swap tantos bloques como unidades de memoria tiene asignadas.
 * Un proceso de memoria 16 usa 16 bloques: la escritura al suspenderlo y la lectura al reactivarlo
 * mueven esa cantidad, y los 16 bloques se liberan al reactivarlo. Se prueba con varios tamanos y con
 * dos procesos suspendidos a la vez (los bloques se suman y no se pisan).
 * Se ejecuta como programa (main); termina con "TODAS LAS PRUEBAS PASARON" o con codigo 1.
 */
public class PruebasBloquesSwap {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        int[] tamanos = {1, 16, 37};
        for (int i = 0; i < tamanos.length; i++) {
            cicloConMemoria(tamanos[i]);
        }
        dosSuspendidosASuma();

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

        DiskRequest pedido() {
            return cola.size() > 0 ? cola.get(0) : cola.getInService();
        }

        String log() {
            return cluster.getLogger().getLastAsText(1000);
        }
    }

    private static void cicloConMemoria(int m) {
        titulo("Proceso de memoria " + m + ": usa " + m + " bloques de swap");
        Entorno e = new Entorno(m + 10);
        PCB v = ProcesoBloqueante.crear(e.pc, "V", 5, m, 500, 1, 60, 5000, 0);
        e.pc.admit(v);
        for (int i = 0; i < 30 && v.getState() != ProcessState.BLOCKED; i++) {
            e.tick();
        }
        PCB n = e.cpu("N", m + 10, 30);  // ocupa toda la RAM: obliga a suspender a V
        int maxSwap = 0;
        boolean vioSuspendido = false;
        for (int i = 0; i < 600 && e.mts.getReactivationCount() == 0; i++) {
            e.tick();
            maxSwap = Math.max(maxSwap, e.swap.usedBlocks());
            if (v.getState() == ProcessState.BLOCKED_SUSPENDED || v.getState() == ProcessState.READY_SUSPENDED) {
                vioSuspendido = true;
                check(e.swap.blocksOf(String.valueOf(v.getId())) == m,
                        "V suspendido ocupa exactamente " + m + " bloques de swap (" + e.swap.blocksOf(String.valueOf(v.getId())) + ")");
                break;
            }
        }
        check(vioSuspendido, "V llego a estar suspendido");
        for (int i = 0; i < 600 && e.mts.getReactivationCount() == 0; i++) {
            e.tick();
            maxSwap = Math.max(maxSwap, e.swap.usedBlocks());
        }
        check(e.bloquesEscritos == m, "la escritura SUSPEND mueve " + m + " bloques (" + e.bloquesEscritos + ")");
        check(e.mts.getReactivationCount() == 1, "V se reactivo");
        check(e.bloquesLeidos == m, "la lectura REACTIVATE mueve " + m + " bloques (" + e.bloquesLeidos + ")");
        check(maxSwap == m, "el swap nunca uso mas de " + m + " bloques (maximo " + maxSwap + ")");
        check(e.swap.blocksOf(String.valueOf(v.getId())) == 0 && e.swap.usedBlocks() == 0, "al reactivarlo se liberan sus bloques");
        check(e.swap.freeBlocks() == e.swap.size(), "el swap queda entero libre");
    }

    private static void dosSuspendidosASuma() {
        titulo("Dos suspendidos a la vez: los bloques se suman y no se pisan");
        Entorno e = new Entorno(100);
        PCB a = e.bloqueadoSuspendido("A", 16);
        PCB b = e.bloqueadoSuspendido("B", 24);
        check(e.swap.blocksOf(String.valueOf(a.getId())) == 16, "A ocupa 16");
        check(e.swap.blocksOf(String.valueOf(b.getId())) == 24, "B ocupa 24");
        check(e.swap.usedBlocks() == 40 && e.swap.freeBlocks() == e.swap.size() - 40, "el swap usa 40 y el resto esta libre");
        int primero = e.swap.firstBlockOf(String.valueOf(a.getId()));
        int segundo = e.swap.firstBlockOf(String.valueOf(b.getId()));
        check(primero >= 0 && segundo >= 0 && primero != segundo, "cada uno empieza en un bloque distinto");
        e.swap.release(String.valueOf(a.getId()));
        check(e.swap.blocksOf(String.valueOf(a.getId())) == 0 && e.swap.blocksOf(String.valueOf(b.getId())) == 24,
                "liberar a A no toca los 24 bloques de B");
        check(e.swap.usedBlocks() == 24, "queda usando solo los 24 de B");
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
