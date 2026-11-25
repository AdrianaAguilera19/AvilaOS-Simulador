/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.enums.BlockReason;
import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.HeadDirection;
import avilaos.enums.Permission;
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
import avilaos.os.filesystem.ActiveUser;
import avilaos.os.filesystem.ChainAllocator;
import avilaos.os.filesystem.DiskIoCoordinator;
import avilaos.os.filesystem.FileSystem;
import avilaos.os.filesystem.FileTree;
import avilaos.os.filesystem.PermissionMatrix;
import avilaos.os.filesystem.SecuredFileSystem;
import avilaos.os.midterm.MediumTermScheduler;
import avilaos.scheduler.PolicyFactory;

/**
 * S08: un proceso suspendido (Bloqueado/Suspendido) cuyo evento ocurre pasa a Listo/Suspendido, sin
 * volver a la RAM. Se prueba con los tres tipos de espera: E/S simulada (ciclos), operacion de disco
 * que ya estaba en la cola cuando se suspendio, y el caso en que el evento ocurre mientras todavia se
 * escribia al swap. Tambien comprueba que un proceso que espera al disco puede ser victima.
 * Se ejecuta como programa (main); termina con "TODAS LAS PRUEBAS PASARON" o con codigo 1.
 */
public class PruebasEventoSuspendido {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        eventoDeEsSimulada();
        solicitudDeDiscoPendiente();
        eventoMientrasSeEscribe();

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

        Entorno(DiskSchedulingPolicy politica, int cabezalInicial) {
            BlockMap mapa = new BlockMap(300);
            FileTree arbol = new FileTree("admin");
            ChainAllocator asignador = new ChainAllocator(mapa, 200);
            FileSystem fs = new FileSystem(arbol, asignador, mapa);
            fs.createDirectory("/usuarios", "admin", Permission.PUBLIC);
            swap = new SwapArea(mapa, 200, 100);
            Cluster c = new Cluster(100, 0);
            pc = c.addComputer("PC1", PolicyFactory.create(SchedulingAlgorithm.FCFS, 2), 30);
            Disk disco = new Disk(mapa, new DiskHead(300, cabezalInicial, 50, HeadDirection.UP));
            cola = new DiskQueue();
            DiskScheduler planificador = new DiskScheduler(cola, DiskPolicyFactory.create(politica), c);
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

        boolean hasta(PCB p, ProcessState s, int max) {
            for (int i = 0; i < max && p.getState() != s; i++) {
                tick();
            }
            return p.getState() == s;
        }

        PCB cpu(String nombre, int memoria) {
            PCB p = ProcessFactory.createCpuBound(nombre, 1, memoria, 500, 5000, pc.getId(), ciclo);
            pc.admit(p);
            return p;
        }
    }

    private static void eventoDeEsSimulada() {
        titulo("El evento ocurre cuando el proceso ya esta suspendido (E/S simulada)");
        Entorno e = new Entorno(DiskSchedulingPolicy.FIFO, 0);
        PCB a = ProcessFactory.createIoBound("A", 5, 20, 500, 1, 120, 5000, e.pc.getId(), 0);
        e.pc.admit(a);
        check(e.hasta(a, ProcessState.BLOCKED, 30), "A se bloquea por E/S (120 ciclos)");
        PCB b = e.cpu("B", 20);
        check(e.hasta(a, ProcessState.BLOCKED_SUSPENDED, 100), "A se suspende para que entre B");
        check(b.getState() != ProcessState.NEW, "B entra con la RAM liberada");
        check(e.hasta(a, ProcessState.READY_SUSPENDED, 300), "al cumplirse su E/S, A pasa a Listo/Suspendido (no a Listo)");
        check(e.pc.getReadySuspendedSnapshot().size() == 1 && e.pc.getReadySuspendedSnapshot().get(0) == a,
                "A esta en la cola de Listo/Suspendido");
        check(e.pc.getBlockedSuspendedSnapshot().isEmpty(), "ya no esta en Bloqueado/Suspendido");
        check(a.getBlockReason() == BlockReason.NONE, "ya no tiene motivo de bloqueo");
        check(e.pc.getRam().getUsedSize() == 20, "A no ocupa RAM (solo B, 20)");
        check(e.swap.blocksOf(String.valueOf(a.getId())) == 20, "su imagen sigue en el swap hasta que se reactive");
        check(!e.pc.getReadySnapshot().contains(a), "no esta en la cola de Listos de la RAM");
    }

    private static PCB procesoDeDisco(Entorno e, String nombre, int memoria) {
        return e.coordinador.submitFileOperationProcess(nombre, 5, memoria, 3, 5000, e.ciclo,
                "CREATE", "/usuarios/a.txt", 2, null, Permission.PUBLIC, ActiveUser.admin());
    }

    private static void solicitudDeDiscoPendiente() {
        titulo("Suspendido mientras esperaba el disco: al terminar su solicitud pasa a Listo/Suspendido");
        // SSTF con el cabezal cerca del swap: el disco atiende primero la escritura al swap y despues la del archivo
        Entorno e = new Entorno(DiskSchedulingPolicy.SSTF, 190);
        e.coordinador.setHoldDiskDispatch(true);
        PCB a = procesoDeDisco(e, "A", 20);
        check(e.hasta(a, ProcessState.BLOCKED, 30) && a.getBlockReason() == BlockReason.DISK_IO,
                "A queda Bloqueado esperando al disco (DISK_IO)");
        check(e.cola.size() == 1, "su solicitud CREATE esta en la cola del disco");
        PCB b = e.cpu("B", 20);
        for (int i = 0; i < 10 && e.mts.getInFlightVictim() == null; i++) {
            e.tick();
        }
        check(e.mts.getInFlightVictim() == a, "A, que espera al disco, es elegida como victima");
        check(e.cola.size() == 2, "en la cola hay dos solicitudes: la CREATE de A y la SUSPEND");
        e.coordinador.setHoldDiskDispatch(false);
        check(e.hasta(a, ProcessState.BLOCKED_SUSPENDED, 100), "primero se escribe A al swap: queda Bloqueado/Suspendido");
        check(e.cola.size() + (e.cola.getInService() != null ? 1 : 0) == 1, "su CREATE sigue pendiente en el disco");
        check(b.getState() != ProcessState.NEW, "B entra con la RAM liberada");
        check(e.hasta(a, ProcessState.READY_SUSPENDED, 100), "al terminar su CREATE, A pasa a Listo/Suspendido");
        check(e.cola.isEmpty() && e.cola.getInService() == null, "el disco quedo libre");
        check(e.pc.getReadySuspendedSnapshot().size() == 1, "A esta en la cola de Listo/Suspendido");
        check(a.getState() != ProcessState.READY, "el disco no lo mando a Listo en la RAM");
        check(e.pc.getRam().getUsedSize() == 20, "A no ocupa RAM (solo B, 20)");
    }

    private static void eventoMientrasSeEscribe() {
        titulo("El evento ocurre mientras todavia se escribe al swap: la suspension se cancela");
        // FIFO: la CREATE de A (que llego antes) termina primero; la escritura al swap queda detras
        Entorno e = new Entorno(DiskSchedulingPolicy.FIFO, 0);
        e.coordinador.setHoldDiskDispatch(true);
        PCB a = procesoDeDisco(e, "A", 20);
        e.hasta(a, ProcessState.BLOCKED, 30);
        e.cpu("B", 20);
        for (int i = 0; i < 10 && e.mts.getInFlightVictim() == null; i++) {
            e.tick();
        }
        check(e.mts.getInFlightVictim() == a, "se pide la escritura de A");
        e.coordinador.setHoldDiskDispatch(false);
        for (int i = 0; i < 200 && e.mts.getAbortedSuspensions() == 0; i++) {
            e.tick();
        }
        check(e.mts.getAbortedSuspensions() == 1 && e.mts.getSuspensionCount() == 0,
                "como A desperto antes, la suspension se cancela");
        check(e.swap.usedBlocks() == 0, "el swap se devuelve");
        check(a.getState() != ProcessState.BLOCKED_SUSPENDED && a.getState() != ProcessState.READY_SUSPENDED,
                "A nunca llego a quedar suspendido");
        check(e.pc.getReadySuspendedSnapshot().isEmpty() && e.pc.getBlockedSuspendedSnapshot().isEmpty(),
                "las colas de suspendidos estan vacias");
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