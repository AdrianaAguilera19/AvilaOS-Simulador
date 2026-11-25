/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.enums.HeadDirection;
import avilaos.enums.IOOperationType;
import avilaos.enums.DiskSchedulingPolicy;
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
import avilaos.os.filesystem.PermissionMatrix;
import avilaos.os.filesystem.SecuredFileSystem;
import avilaos.os.midterm.MediumTermScheduler;
import avilaos.scheduler.PolicyFactory;

/**
 * S07: la suspension como operacion de E/S. Comprueba que, cuando un proceso nuevo no cabe, la
 * victima se escribe al swap con una solicitud SUSPEND que pasa por la cola del disco; que la RAM
 * se libera recien al terminar la escritura; que el proceso queda Bloqueado/Suspendido y NO se
 * despierta; que sin espacio en el swap no se suspende; y que si la victima vence mientras se
 * escribe, la suspension se cancela y el swap se devuelve.
 * Se ejecuta como programa (main); termina con "TODAS LAS PRUEBAS PASARON" o con codigo 1.
 */
public class PruebasSuspensionSwap {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        flujoCompleto();
        swapLleno();
        sinSwapConfigurado();
        vencimientoDuranteLaEscritura();
        dosSuspensionesSeguidas();

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
        } else {
            System.out.println("HAY " + fallas + " FALLAS de " + verificaciones + " verificaciones");
            System.exit(1);
        }
    }

    /** Un computador de 30 de RAM con disco de 200 bloques de archivos + swap, y todo conectado. */
    private static class Entorno {
        final Computer pc;
        final DiskQueue cola;
        final SwapArea swap;
        final DiskIoCoordinator coordinador;
        final MediumTermScheduler mts;
        long ciclo = 0;

        Entorno(int ram, int swapBloques, boolean conectarSwap) {
            BlockMap mapa = new BlockMap(200 + swapBloques);
            avilaos.os.filesystem.FileTree arbol = new avilaos.os.filesystem.FileTree("admin");
            ChainAllocator asignador = new ChainAllocator(mapa, 200);
            FileSystem fs = new FileSystem(arbol, asignador, mapa);
            swap = new SwapArea(mapa, 200, swapBloques);
            Cluster c = new Cluster(100, 0);
            pc = c.addComputer("PC1", PolicyFactory.create(SchedulingAlgorithm.FCFS, 2), ram);
            Disk disco = new Disk(mapa, new DiskHead(200 + swapBloques, 0, 50, HeadDirection.UP));
            cola = new DiskQueue();
            DiskScheduler planificador = new DiskScheduler(cola, DiskPolicyFactory.create(DiskSchedulingPolicy.FIFO), c);
            SecuredFileSystem seguro = new SecuredFileSystem(fs, new PermissionMatrix(arbol));
            coordinador = new DiskIoCoordinator(pc, disco, planificador, seguro, new DiskRecordLog(), c.getLogger());
            mts = pc.getMediumTermScheduler();
            if (conectarSwap) {
                mts.attachSwap(swap, cola);
            }
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

        PCB conEs(String nombre, int prioridad, int memoria, int deadline) {
            PCB p = ProcessFactory.createIoBound(nombre, prioridad, memoria, 500, 1, 5000, deadline, pc.getId(), ciclo);
            pc.admit(p);
            return p;
        }

        PCB cpu(String nombre, int memoria) {
            PCB p = ProcessFactory.createCpuBound(nombre, 1, memoria, 500, 5000, pc.getId(), ciclo);
            pc.admit(p);
            return p;
        }

        boolean hasta(PCB p, ProcessState s, int max) {
            for (int i = 0; i < max && p.getState() != s; i++) {
                tick();
            }
            return p.getState() == s;
        }
    }

    private static void flujoCompleto() {
        titulo("Flujo completo: SUSPEND por la cola del disco, RAM liberada al terminar");
        Entorno e = new Entorno(30, 100, true);
        PCB a = e.conEs("A", 5, 20, 5000);
        check(e.hasta(a, ProcessState.BLOCKED, 30), "A (20 de 30) se bloquea");
        PCB b = e.cpu("B", 20);
        e.tick();
        check(b.getState() == ProcessState.NEW, "B (20) no cabe en los 10 libres");
        check(e.mts.getInFlightVictim() == a, "el planificador eligio a A y pidio su escritura");
        DiskRequest pedido = e.cola.size() > 0 ? e.cola.get(0) : e.cola.getInService();
        check(pedido != null && pedido.getOperation() == IOOperationType.SUSPEND, "la solicitud es SUSPEND y esta en la cola del disco");
        check(pedido != null && pedido.getRequester() == a, "la pide A");
        check(pedido != null && pedido.getBlocksToTransfer() == 20, "transfiere 20 bloques (la memoria de A)");
        check(pedido != null && e.swap.contains(pedido.getTargetBlock()), "su bloque destino esta dentro del swap");
        check(e.swap.blocksOf(String.valueOf(a.getId())) == 20, "el swap reservo 20 bloques para A");
        check(a.getState() == ProcessState.BLOCKED && e.pc.getRam().getUsedSize() == 20,
                "mientras se escribe, A sigue Bloqueado y la RAM no se libera (20 usados)");
        e.ticks(2);
        check(e.cola.size() + (e.cola.getInService() != null ? 1 : 0) == 1, "nunca hay mas de una solicitud de swap");
        for (int i = 0; i < 100 && e.mts.getSuspensionCount() == 0; i++) {
            e.tick();
        }
        check(e.mts.getSuspensionCount() == 1, "la suspension se completo");
        check(a.getState() == ProcessState.BLOCKED_SUSPENDED, "A queda en Bloqueado/Suspendido");
        check(e.pc.getBlockedSuspendedSnapshot().size() == 1 && e.pc.getBlockedSuspendedSnapshot().get(0) == a,
                "A esta en la cola de Bloqueado/Suspendido");
        check(e.pc.getBlockedSnapshot().isEmpty(), "ya no esta en la cola de Bloqueados");
        check(a.getState() != ProcessState.READY && e.pc.getReadySnapshot().size() <= 1,
                "el disco NO lo desperto (no paso a Listo)");
        check(e.swap.blocksOf(String.valueOf(a.getId())) == 20, "sus 20 bloques siguen reservados en el swap");
        e.ticks(2);
        check(b.getState() != ProcessState.NEW, "con la RAM liberada, B entra");
        check(e.pc.getRam().getUsedSize() == 20, "la RAM refleja solo a B (20)");
        check(e.mts.getInFlightVictim() == null, "ya no hay suspension en curso");
    }

    private static void swapLleno() {
        titulo("Swap lleno: no se suspende y el Nuevo espera");
        Entorno e = new Entorno(30, 10, true);
        PCB a = e.conEs("A", 5, 20, 5000);
        e.hasta(a, ProcessState.BLOCKED, 30);
        PCB b = e.cpu("B", 20);
        e.ticks(10);
        check(e.mts.isTriggerPending() && e.mts.getVictim() == a, "el disparo se activa y la victima seria A");
        check(e.cola.isEmpty() && e.cola.getInService() == null, "pero no se pide nada al disco (A necesita 20 y el swap tiene 10)");
        check(e.mts.getSuspensionCount() == 0 && e.mts.getInFlightVictim() == null, "no hay suspension en curso");
        check(b.getState() == ProcessState.NEW, "B sigue esperando en Nuevos");
        check(a.getState() == ProcessState.BLOCKED && e.pc.getRam().getUsedSize() == 20, "A sigue en RAM");
        check(e.swap.usedBlocks() == 0, "el swap queda intacto");
        check(e.mts.getSwapFullCount() == 1, "se avisa una sola vez, no en cada ciclo (" + e.mts.getSwapFullCount() + ")");
    }

    private static void sinSwapConfigurado() {
        titulo("Sin swap conectado no se suspende");
        Entorno e = new Entorno(30, 100, false);
        PCB a = e.conEs("A", 5, 20, 5000);
        e.hasta(a, ProcessState.BLOCKED, 30);
        PCB b = e.cpu("B", 20);
        e.ticks(10);
        check(e.cola.isEmpty() && b.getState() == ProcessState.NEW && a.getState() == ProcessState.BLOCKED,
                "no se pide nada al disco y todo sigue igual");
        boolean fallo = false;
        try {
            e.mts.attachSwap(e.swap, null);
        } catch (IllegalArgumentException ex) {
            fallo = true;
        }
        check(fallo, "conectar un swap sin cola de disco se rechaza");
    }

    private static void vencimientoDuranteLaEscritura() {
        titulo("La victima vence mientras se escribe: se cancela y se devuelve el swap");
        Entorno e = new Entorno(30, 100, true);
        PCB a = e.conEs("A", 5, 20, 14);   // deadline corto
        e.hasta(a, ProcessState.BLOCKED, 30);
        PCB b = e.cpu("B", 20);
        e.tick();
        check(e.mts.getInFlightVictim() == a, "se pide la escritura de A");
        for (int i = 0; i < 100 && e.mts.getAbortedSuspensions() == 0; i++) {
            e.tick();
        }
        check(a.getState() == ProcessState.TERMINATED && a.isDeadlineMissed(), "A vencio por deadline antes de terminar de escribirse");
        check(e.mts.getAbortedSuspensions() == 1 && e.mts.getSuspensionCount() == 0, "la suspension se cancelo (no se cuenta como suspension)");
        check(e.swap.usedBlocks() == 0, "los bloques de swap se devolvieron (no quedan huerfanos)");
        check(e.pc.getBlockedSuspendedSnapshot().isEmpty(), "A no queda en Bloqueado/Suspendido");
        e.ticks(2);
        check(b.getState() != ProcessState.NEW, "B entro cuando A libero su RAM al vencer");
        check(e.pc.getRam().getUsedSize() == 20, "la RAM no se libero dos veces: solo B (20)");
        check(e.mts.getInFlightVictim() == null, "no queda suspension en curso");
    }

    private static void dosSuspensionesSeguidas() {
        titulo("Si hace falta mas de uno, se suspende de a uno");
        Entorno e = new Entorno(30, 100, true);
        PCB a = e.conEs("A", 3, 15, 5000);
        e.hasta(a, ProcessState.BLOCKED, 30);
        PCB c = e.conEs("C", 7, 15, 5000);
        e.hasta(c, ProcessState.BLOCKED, 30);
        PCB n = e.cpu("N", 30);
        int maxJuntas = 0;
        for (int i = 0; i < 200 && e.mts.getSuspensionCount() < 2; i++) {
            e.tick();
            int enCola = e.cola.size() + (e.cola.getInService() != null ? 1 : 0);
            maxJuntas = Math.max(maxJuntas, enCola);
        }
        check(e.mts.getSuspensionCount() == 2, "se completaron dos suspensiones");
        check(maxJuntas <= 1, "nunca hubo dos solicitudes de swap a la vez (maximo " + maxJuntas + ")");
        check(c.getState() == ProcessState.BLOCKED_SUSPENDED && a.getState() == ProcessState.BLOCKED_SUSPENDED,
                "A y C quedaron suspendidos");
        check(e.pc.getBlockedSuspendedSnapshot().get(0) == c, "se suspendio primero C (menor prioridad: numero 7)");
        e.ticks(2);
        check(n.getState() != ProcessState.NEW, "N (30) entra cuando las dos liberaron su RAM");
        check(e.swap.usedBlocks() == 30, "el swap guarda 30 bloques (15 + 15)");
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