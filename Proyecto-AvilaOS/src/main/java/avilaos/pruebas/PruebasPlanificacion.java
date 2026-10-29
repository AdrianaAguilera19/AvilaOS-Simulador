package avilaos.pruebas;

import avilaos.enums.BlockReason;
import avilaos.enums.ProcessState;
import avilaos.enums.SchedulingAlgorithm;
import avilaos.interfaces.SchedulingPolicy;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;
import avilaos.scheduler.EDFPolicy;
import avilaos.scheduler.FCFSPolicy;
import avilaos.scheduler.PriorityPolicy;
import avilaos.scheduler.RoundRobinPolicy;

/**
 * I09: pruebas reproducibles de estados, planificacion y deadlines.
 * Se ejecuta como programa (main) y termina con "TODAS LAS PRUEBAS PASARON" o con la lista
 * de fallas (codigo de salida 1). Todo corre con ticks manuales del cluster, asi que da
 * siempre el mismo resultado.
 * Cubre: transiciones de estado, las cuatro politicas (FCFS, EDF, Prioridades apropiativas y
 * Round Robin), cambio de quantum y de politica en caliente, bloqueo por E/S de los procesos
 * IO_BOUND y vencimiento de deadline (en CPU, en listos, en bloqueados y en nuevos).
 */
public class PruebasPlanificacion {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        transicionesDeEstado();
        fcfsAtiendeEnOrdenDeLlegada();
        edfElijeElDeadlineMasCercano();
        prioridadesDesalojan();
        roundRobinRota();
        quantumSeCambiaEnCaliente();
        politicaSeCambiaEnCaliente();
        procesoIoBoundSeBloqueaPorEs();
        deadlineEnLaCpu();
        deadlineEnListos();
        deadlineEnBloqueados();
        deadlineEnNuevos();
        terminarNormalmenteNoEsVencer();

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
        } else {
            System.out.println("HAY " + fallas + " FALLAS de " + verificaciones + " verificaciones");
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ utilidades

    private static void check(boolean ok, String texto) {
        verificaciones++;
        System.out.println((ok ? "  OK    " : "  FALLA ") + texto);
        if (!ok) {
            fallas++;
        }
    }

    private static void titulo(String texto) {
        System.out.println("--- " + texto + " ---");
    }

    /** Cluster de 2 computadores; el primero usa la politica indicada y la RAM indicada. */
    private static Cluster cluster(SchedulingPolicy politica, int ram) {
        Cluster cl = new Cluster(1, 0);
        cl.addComputer("PC1", politica, ram);
        cl.addComputer("PC2", new FCFSPolicy(), 100);
        return cl;
    }

    private static PCB cpu(String nombre, int prioridad, int instrucciones, int deadline) {
        return ProcessFactory.createCpuBound(nombre, prioridad, 10, instrucciones, deadline, 1, 0);
    }

    private static void ticks(Cluster cl, int n) {
        for (int i = 0; i < n; i++) {
            cl.tick();
        }
    }

    private static String enCpu(Computer c) {
        return c.getRunning() == null ? "-" : c.getRunning().getName();
    }

    private static String cola(avilaos.datastructures.CustomQueue<PCB> q) {
        if (q.isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < q.size(); i++) {
            sb.append(q.get(i).getName()).append(i < q.size() - 1 ? "," : "");
        }
        return sb.toString();
    }

    /** Nombre del proceso en CPU despues de cada uno de los n ciclos (por ejemplo "A,A,B,B"). */
    private static String secuencia(Cluster cl, Computer c, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            cl.tick();
            sb.append(enCpu(c)).append(i < n - 1 ? "," : "");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ pruebas

    private static void transicionesDeEstado() {
        titulo("Transiciones: Nuevo -> Listo -> Ejecucion -> Terminado");
        Cluster cl = cluster(new FCFSPolicy(), 100);
        Computer pc = cl.getComputer(1);
        PCB a = cpu("A", 1, 3, 100);
        PCB b = cpu("B", 1, 3, 100);
        cl.submit(a);
        cl.submit(b);
        check(a.getState() == ProcessState.NEW, "al crearse, A esta en Nuevo");
        cl.tick();
        check(a.getState() == ProcessState.RUNNING, "tras el primer ciclo A pasa por Listo y queda en Ejecucion");
        check(b.getState() == ProcessState.READY, "B espera en Listo");
        check(pc.getRam().getUsedSize() == 20, "la RAM reserva la memoria de los dos procesos admitidos");
        ticks(cl, 3);
        check(a.getState() == ProcessState.TERMINATED, "A termina al ejecutar sus 3 instrucciones");
        check(b.getState() == ProcessState.RUNNING, "al terminar A, B toma la CPU");
        check(pc.getRam().getUsedSize() == 10, "al terminar A se libera su memoria");
        ticks(cl, 3);
        check(b.getState() == ProcessState.TERMINATED && pc.getRam().getUsedSize() == 0,
                "B termina y la RAM queda libre");
        check(!a.isDeadlineMissed() && !b.isDeadlineMissed(), "terminar normalmente no marca deadline vencido");
    }

    private static void fcfsAtiendeEnOrdenDeLlegada() {
        titulo("FCFS");
        Cluster cl = cluster(new FCFSPolicy(), 100);
        Computer pc = cl.getComputer(1);
        cl.submit(cpu("A", 3, 3, 500));
        cl.submit(cpu("B", 1, 3, 100));
        cl.submit(cpu("C", 2, 3, 300));
        cl.tick();
        check(enCpu(pc).equals("A") && cola(pc.getReadySnapshot()).equals("B,C"),
                "ejecuta A y deja B,C en orden de llegada, sin mirar prioridad ni deadline");
        ticks(cl, 10);
        check(cola(pc.getFinishedSnapshot()).equals("A,B,C"), "terminan en orden de llegada: A,B,C");
    }

    private static void edfElijeElDeadlineMasCercano() {
        titulo("EDF");
        Cluster cl = cluster(new EDFPolicy(), 100);
        Computer pc = cl.getComputer(1);
        cl.submit(cpu("A", 1, 3, 300));
        cl.submit(cpu("B", 1, 3, 100));
        cl.submit(cpu("C", 1, 3, 200));
        cl.tick();
        check(enCpu(pc).equals("B"), "elige primero el de deadline mas cercano (B)");
        check(cola(pc.getReadySnapshot()).equals("C,A"), "la cola de listos queda ordenada por deadline: C,A");
        ticks(cl, 9);
        check(cola(pc.getFinishedSnapshot()).equals("B,C,A"), "terminan por deadline creciente: B,C,A");
    }

    private static void prioridadesDesalojan() {
        titulo("Prioridades apropiativas");
        Cluster cl = cluster(new PriorityPolicy(), 100);
        Computer pc = cl.getComputer(1);
        PCB a = cpu("A", 5, 20, 500);
        cl.submit(a);
        ticks(cl, 3);
        check(enCpu(pc).equals("A"), "A (prioridad 5) corre sola");
        PCB b = cpu("B", 1, 5, 500);
        cl.submit(b);
        cl.tick();
        check(enCpu(pc).equals("B"), "B (prioridad 1, mejor) desaloja a A en cuanto llega");
        check(a.getState() == ProcessState.READY, "A vuelve a Listo");
        PCB c = cpu("C", 3, 5, 500);
        cl.submit(c);
        cl.tick();
        check(enCpu(pc).equals("B"), "C (prioridad 3) no desaloja a B (prioridad 1)");
        check(cola(pc.getReadySnapshot()).equals("C,A"), "listos ordenados por prioridad: C,A");
    }

    private static void roundRobinRota() {
        titulo("Round Robin");
        Cluster cl = cluster(new RoundRobinPolicy(2), 100);
        Computer pc = cl.getComputer(1);
        cl.submit(cpu("A", 1, 20, 500));
        cl.submit(cpu("B", 1, 20, 500));
        check(secuencia(cl, pc, 8).equals("A,A,B,B,A,A,B,B"),
                "con quantum 2 se alternan cada 2 ciclos: A,A,B,B,A,A,B,B");
        Cluster solo = cluster(new RoundRobinPolicy(2), 100);
        solo.submit(cpu("S", 1, 20, 500));
        check(secuencia(solo, solo.getComputer(1), 5).equals("S,S,S,S,S"),
                "un proceso solo conserva la CPU aunque agote su quantum");
    }

    private static void quantumSeCambiaEnCaliente() {
        titulo("Quantum en caliente");
        Cluster cl = cluster(new RoundRobinPolicy(2), 100);
        Computer pc = cl.getComputer(1);
        cl.submit(cpu("A", 1, 30, 500));
        cl.submit(cpu("B", 1, 30, 500));
        ticks(cl, 2);
        cl.setQuantum(1, 4);
        check(pc.getQuantum() == 4, "el computador guarda el quantum nuevo");
        ticks(cl, 2);   // termina el turno de A en curso (ya usa el quantum 4)
        String seq = secuencia(cl, pc, 8);
        check(seq.equals("B,B,B,B,A,A,A,A"), "con quantum 4 los turnos duran 4 ciclos (vi: " + seq + ")");
        try {
            cl.setQuantum(1, 0);
            check(false, "quantum 0 debe rechazarse");
        } catch (IllegalArgumentException e) {
            check(true, "quantum 0 se rechaza sin romper nada");
        }
    }

    private static void politicaSeCambiaEnCaliente() {
        titulo("Cambio de politica en caliente");
        Cluster cl = cluster(new FCFSPolicy(), 100);
        Computer pc = cl.getComputer(1);
        cl.submit(cpu("A", 3, 30, 500));
        cl.submit(cpu("B", 2, 30, 500));
        cl.submit(cpu("C", 1, 30, 500));
        ticks(cl, 2);
        check(enCpu(pc).equals("A"), "en FCFS corre A");
        cl.setPolicy(1, SchedulingAlgorithm.PRIORITY);
        cl.tick();
        check(enCpu(pc).equals("C") && cola(pc.getReadySnapshot()).equals("B,A"),
                "al pasar a Prioridades, C desaloja a A y la cola se reordena: B,A");
        check(cl.getComputer(2).getAlgorithm() == SchedulingAlgorithm.FCFS, "el otro computador no cambia");
    }

    private static void procesoIoBoundSeBloqueaPorEs() {
        titulo("Proceso IO_BOUND: se bloquea por E/S y libera la CPU");
        Cluster cl = cluster(new FCFSPolicy(), 100);
        Computer pc = cl.getComputer(1);
        // 6 instrucciones; cada 2 pide E/S y espera 3 ciclos
        PCB io = ProcessFactory.createIoBound("IO", 1, 10, 6, 2, 3, 500, 1, 0);
        PCB cpuBound = cpu("CPU", 1, 4, 500);
        cl.submit(io);
        cl.submit(cpuBound);
        ticks(cl, 3);     // ciclo 1 carga IO; ciclos 2 y 3 ejecutan sus 2 primeras instrucciones
        check(io.getState() == ProcessState.BLOCKED && io.getBlockReason() == BlockReason.IO,
                "tras 2 instrucciones, IO pide E/S y queda Bloqueado por E/S");
        check(cola(pc.getBlockedSnapshot()).equals("IO"), "aparece en la cola de bloqueados");
        check(enCpu(pc).equals("CPU"), "la CPU queda libre para el otro proceso (CPU)");
        ticks(cl, 3);
        check(io.getState() != ProcessState.BLOCKED, "pasados los 3 ciclos de E/S, IO se desbloquea");
        check(io.getBlockReason() == BlockReason.NONE, "y ya no tiene motivo de bloqueo");
        ticks(cl, 30);
        check(cola(pc.getFinishedSnapshot()).contains("IO") && cola(pc.getFinishedSnapshot()).contains("CPU"),
                "ambos terminan");
        check(!io.isDeadlineMissed() && pc.getRam().getUsedSize() == 0, "sin deadline vencido y con la RAM liberada");
    }

    private static void deadlineEnLaCpu() {
        titulo("Deadline: proceso en la CPU");
        Cluster cl = cluster(new FCFSPolicy(), 100);
        Computer pc = cl.getComputer(1);
        PCB a = cpu("A", 1, 50, 4);
        cl.submit(a);
        ticks(cl, 3);
        check(a.getState() == ProcessState.RUNNING, "antes de vencer sigue en ejecucion");
        cl.tick();
        check(a.getState() == ProcessState.TERMINATED && a.isDeadlineMissed(), "al llegar el deadline a 0 se termina");
        check(pc.getRunning() == null || pc.getRunning() != a, "libera la CPU");
        check(pc.getRam().getUsedSize() == 0, "libera su memoria");
        check(cola(pc.getFinishedSnapshot()).equals("A"), "queda en la cola de terminados");
    }

    private static void deadlineEnListos() {
        titulo("Deadline: proceso esperando en Listos");
        Cluster cl = cluster(new FCFSPolicy(), 100);
        Computer pc = cl.getComputer(1);
        cl.submit(cpu("A", 1, 50, 500));
        PCB b = cpu("B", 1, 5, 3);
        cl.submit(b);
        ticks(cl, 3);
        check(b.getState() == ProcessState.TERMINATED && b.isDeadlineMissed(),
                "B vence esperando la CPU y se termina");
        check(pc.getReadySnapshot().isEmpty(), "sale de la cola de listos");
        check(pc.getRam().getUsedSize() == 10, "solo queda reservada la memoria de A");
    }

    private static void deadlineEnBloqueados() {
        titulo("Deadline: proceso Bloqueado");
        Cluster cl = cluster(new FCFSPolicy(), 100);
        Computer pc = cl.getComputer(1);
        // pide E/S despues de 1 instruccion y espera 50 ciclos, pero su deadline es 6
        PCB io = ProcessFactory.createIoBound("IO", 1, 10, 4, 1, 50, 6, 1, 0);
        cl.submit(io);
        ticks(cl, 3);
        check(io.getState() == ProcessState.BLOCKED, "IO esta bloqueado esperando E/S");
        ticks(cl, 4);
        check(io.getState() == ProcessState.TERMINATED && io.isDeadlineMissed(),
                "vence mientras esta bloqueado y se termina");
        check(pc.getBlockedSnapshot().isEmpty(), "sale de la cola de bloqueados");
        check(pc.getRam().getUsedSize() == 0, "libera su memoria");
        ticks(cl, 60);
        check(io.getState() == ProcessState.TERMINATED, "cuando termina la E/S no resucita");
    }

    private static void deadlineEnNuevos() {
        titulo("Deadline: proceso esperando RAM en Nuevos");
        Cluster cl = cluster(new FCFSPolicy(), 10);   // solo cabe un proceso de 10
        Computer pc = cl.getComputer(1);
        cl.submit(cpu("A", 1, 50, 500));
        PCB b = cpu("B", 1, 5, 3);
        cl.submit(b);
        cl.tick();
        check(b.getState() == ProcessState.NEW && cola(pc.getNewSnapshot()).equals("B"),
                "B espera en Nuevos porque no cabe en la RAM");
        ticks(cl, 2);
        check(b.getState() == ProcessState.TERMINATED && b.isDeadlineMissed(),
                "vence en Nuevos y se termina");
        check(pc.getRam().getUsedSize() == 10, "no libera memoria que nunca tuvo");
    }

    private static void terminarNormalmenteNoEsVencer() {
        titulo("Un proceso que termina justo a tiempo no cuenta como vencido");
        Cluster cl = cluster(new FCFSPolicy(), 100);
        PCB a = cpu("A", 1, 3, 100);
        cl.submit(a);
        ticks(cl, 20);
        check(a.getState() == ProcessState.TERMINATED && !a.isDeadlineMissed(),
                "A termino sus instrucciones y no se marca como vencido aunque el reloj siga");
    }
}
