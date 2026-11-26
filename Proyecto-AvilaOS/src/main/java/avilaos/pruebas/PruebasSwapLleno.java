
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
 * S11: swap lleno. Si la victima no cabe en el swap, no se suspende: el proceso nuevo espera en Nuevos,
 * el aviso queda en el log una sola vez, el mapa de bloques del swap no se toca y no se pierde ningun
 * proceso. Cuando se libera swap, la suspension sigue sola. Tambien se prueba que una reserva que no
 * cabe deja el swap exactamente como estaba (sin bloques a medias).
 * Se ejecuta como programa (main); termina con "TODAS LAS PRUEBAS PASARON" o con codigo 1.
 */
public class PruebasSwapLleno {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        reservaQueNoCabeNoDejaRastro();
        swapLlenoNoSuspendeNiCorrompe();
        hayAlgoDeSwapPeroNoAlcanza();
        alLiberarseSwapLaSuspensionSigue();

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
            PCB p = ProcessFactory.createIoBound(nombre, 5, memoria, 500, 1, 5000, 5000, pc.getId(), 0);
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

    private static void reservaQueNoCabeNoDejaRastro() {
        titulo("Una reserva que no cabe deja el swap como estaba");
        Entorno e = new Entorno(100);
        e.bloqueadoSuspendido("S1", 70);
        int usados = e.swap.usedBlocks();
        check(usados == 70 && e.swap.freeBlocks() == 30, "el swap tiene 70 usados y 30 libres");
        check(e.swap.reserve("X", 40) == null, "pedir 40 bloques con 30 libres devuelve null");
        check(e.swap.usedBlocks() == 70 && e.swap.freeBlocks() == 30, "el swap sigue con 70 usados y 30 libres");
        check(e.swap.blocksOf("X") == 0, "no quedaron bloques a medias a nombre del que pidio");
        check(e.swap.reserve("Y", 30) != null && e.swap.freeBlocks() == 0, "justo lo que queda (30) si se puede reservar");
        check(e.swap.reserve("Z", 1) == null && e.swap.usedBlocks() == 100, "con el swap en 0 libres no se reserva ni 1");
    }

    private static void swapLlenoNoSuspendeNiCorrompe() {
        titulo("Swap agotado: no se suspende, el nuevo espera y no se pierde nadie");
        Entorno e = new Entorno(100);
        PCB s1 = e.bloqueadoSuspendido("S1", 50);
        PCB s2 = e.bloqueadoSuspendido("S2", 50);
        PCB v = e.bloqueado("V", 60);
        for (int i = 0; i < 30 && v.getState() != ProcessState.BLOCKED; i++) {
            e.tick();
        }
        check(v.getState() == ProcessState.BLOCKED && e.swap.freeBlocks() == 0, "V esta Bloqueado en RAM y el swap esta lleno");
        PCB n = e.cpu("N", 60, 100);
        e.ticks(20);
        check(e.mts.isTriggerPending(), "N (60) no cabe en los 40 libres: hay que suspender");
        check(n.getState() == ProcessState.NEW, "N espera en Nuevos");
        check(v.getState() == ProcessState.BLOCKED, "V sigue Bloqueado en la RAM (no se suspendio)");
        check(e.mts.getSuspensionCount() == 0, "no hubo ninguna suspension");
        check(e.cola.isEmpty() && e.cola.getInService() == null, "no se mando ninguna escritura al disco");
        check(e.swap.usedBlocks() == 100 && e.swap.blocksOf(String.valueOf(s1.getId())) == 50
                && e.swap.blocksOf(String.valueOf(s2.getId())) == 50 && e.swap.blocksOf(String.valueOf(v.getId())) == 0,
                "el swap no cambio: 50 de S1, 50 de S2 y nada de V");
        check(e.mts.getSwapFullCount() == 1, "se registro una sola falta de swap (" + e.mts.getSwapFullCount() + ")");
        int avisos = contar(e.log(), "swap lleno");
        check(avisos == 1, "el log dice una sola vez 'swap lleno' (salio " + avisos + ")");
        check(e.pc.getRam().getUsedSize() == 60, "la RAM solo tiene a V (60)");
        check(s1.getState() == ProcessState.BLOCKED_SUSPENDED && s2.getState() == ProcessState.BLOCKED_SUSPENDED,
                "S1 y S2 siguen Bloqueado/Suspendido");
        check(e.pc.getBlockedSuspendedSnapshot().size() == 2, "las dos estan en la cola de Bloqueado/Suspendido");
    }

    private static void hayAlgoDeSwapPeroNoAlcanza() {
        titulo("Queda algo de swap pero menos de lo que necesita la victima");
        Entorno e = new Entorno(100);
        e.bloqueadoSuspendido("S1", 70);   // quedan 30 libres
        PCB v = e.bloqueado("V", 60);       // necesita 60
        for (int i = 0; i < 30 && v.getState() != ProcessState.BLOCKED; i++) {
            e.tick();
        }
        PCB n = e.cpu("N", 60, 100);
        e.ticks(20);
        check(n.getState() == ProcessState.NEW && v.getState() == ProcessState.BLOCKED, "N espera y V no se suspende");
        check(e.swap.usedBlocks() == 70 && e.swap.freeBlocks() == 30, "el swap sigue con 70 usados y 30 libres (nada a medias)");
        check(e.swap.blocksOf(String.valueOf(v.getId())) == 0, "V no tiene bloques de swap");
        check(contar(e.log(), "swap lleno") == 1, "un solo aviso en el log");
    }

    private static void alLiberarseSwapLaSuspensionSigue() {
        titulo("Cuando se libera swap, la suspension sigue y entra el nuevo");
        Entorno e = new Entorno(100);
        PCB s1 = e.bloqueadoSuspendido("S1", 100);
        PCB v = e.bloqueado("V", 60);
        for (int i = 0; i < 30 && v.getState() != ProcessState.BLOCKED; i++) {
            e.tick();
        }
        PCB n = e.cpu("N", 60, 100);
        e.ticks(10);
        check(n.getState() == ProcessState.NEW && e.mts.getSuspensionCount() == 0, "con el swap lleno N espera");
        int liberados = e.swap.release(String.valueOf(s1.getId()));
        check(liberados == 100 && e.swap.freeBlocks() == 100, "se liberan los 100 bloques de swap");
        for (int i = 0; i < 300 && e.mts.getSuspensionCount() == 0; i++) {
            e.tick();
        }
        check(e.mts.getSuspensionCount() == 1 && v.getState() == ProcessState.BLOCKED_SUSPENDED, "ahora V si se suspende");
        check(e.swap.blocksOf(String.valueOf(v.getId())) == 60, "V ocupa 60 bloques de swap");
        e.ticks(5);
        check(n.getState() != ProcessState.NEW, "N salio de Nuevos y fue admitido");
        check(e.mts.getSwapFullCount() == 1, "la falta de swap se conto una sola vez");
    }

    private static int contar(String texto, String buscado) {
        int veces = 0;
        int desde = 0;
        while ((desde = texto.indexOf(buscado, desde)) >= 0) {
            veces++;
            desde += buscado.length();
        }
        return veces;
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