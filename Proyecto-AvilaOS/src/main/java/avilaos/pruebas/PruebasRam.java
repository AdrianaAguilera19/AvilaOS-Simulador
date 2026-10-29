/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.datastructures.CustomLinkedList;
import avilaos.datastructures.CustomQueue;
import avilaos.enums.ProcessState;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.hardware.RAM;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;
import avilaos.scheduler.FCFSPolicy;
import avilaos.sync.BoundedBuffer;

/**
 * I10: pruebas reproducibles de la RAM y de la admision de procesos.
 * Es una prueba que se queda en el repositorio: se ejecuta como programa (main) y termina
 * con "TODAS LAS PRUEBAS PASARON" o con la lista de fallas (codigo de salida 1).
 * Cubre: la clase RAM sola, falta de memoria, admision posterior, liberacion al terminar o
 * al vencer el deadline, revision de la cola de nuevos (orden de llegada), memoria de los
 * buffers y de los procesos bloqueados, y que la cuenta de memoria siempre cuadre.
 * Todo corre con ticks manuales del cluster, asi que da siempre el mismo resultado.
 */
public class PruebasRam {

    private static final int MAX_CICLOS = 1000;
    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) throws Exception {
        ramSola();
        ramConcurrente();
        admisionNormal();
        faltaDeMemoria();
        admisionPosterior();
        colaDeNuevosEnOrden();
        liberacionPorDeadline();
        deadlineEnLaColaDeNuevos();
        procesoMasGrandeQueLaRam();
        memoriaDeLosBuffers();
        procesoBloqueadoConservaSuMemoria();
        cuentaDeMemoriaSiempreCuadra();

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

    /** Cluster de 2 computadores FCFS; PC1 tiene la RAM indicada. */
    private static Cluster cluster(int ramPc1) {
        Cluster c = new Cluster(10, 0);
        c.addComputer("PC1", new FCFSPolicy(), ramPc1);
        c.addComputer("PC2", new FCFSPolicy(), 200);
        return c;
    }

    /** Proceso CPU-bound de PC1 que llega en el ciclo actual y se entrega al computador. */
    private static PCB proceso(Cluster c, String nombre, int memoria, int ciclos, int deadline) {
        PCB p = ProcessFactory.createCpuBound(nombre, 1, memoria, ciclos, deadline, 1, c.getCurrentCycle());
        c.submit(p);
        return p;
    }

    private static void ticks(Cluster c, int n) {
        for (int i = 0; i < n; i++) {
            c.tick();
        }
    }

    /** Avanza hasta que el proceso termine; devuelve true si lo logro. */
    private static boolean hastaTerminar(Cluster c, PCB p) {
        for (int i = 0; i < MAX_CICLOS; i++) {
            if (p.getState() == ProcessState.TERMINATED) {
                return true;
            }
            c.tick();
        }
        return p.getState() == ProcessState.TERMINATED;
    }

    private static int enNuevos(Computer pc) {
        return pc.getNewSnapshot().size();
    }

    /** Suma de la memoria de los procesos que tienen RAM reservada (CPU, listos y bloqueados). */
    private static int memoriaDeProcesos(Computer pc) {
        int suma = 0;
        PCB corriendo = pc.getRunning();
        if (corriendo != null) {
            suma += corriendo.getMemorySize();
        }
        CustomQueue<PCB> listos = pc.getReadySnapshot();
        for (int i = 0; i < listos.size(); i++) {
            suma += listos.get(i).getMemorySize();
        }
        CustomQueue<PCB> bloqueados = pc.getBlockedSnapshot();
        for (int i = 0; i < bloqueados.size(); i++) {
            suma += bloqueados.get(i).getMemorySize();
        }
        return suma;
    }

    /** Memoria reservada por los buffers que viven en ese computador. */
    private static int memoriaDeBuffers(Cluster c, Computer pc) {
        int suma = 0;
        CustomLinkedList<BoundedBuffer> buffers = c.getBufferManager().getSnapshot();
        for (int i = 0; i < buffers.size(); i++) {
            if (buffers.get(i).getHostComputerId() == pc.getId()) {
                suma += buffers.get(i).getReservedMemory();
            }
        }
        return suma;
    }

    // ------------------------------------------------------------------ pruebas

    /** La clase RAM sola: reservar, liberar y rechazar. */
    private static void ramSola() {
        titulo("RAM sola");
        RAM ram = new RAM(100);
        check(ram.getTotalSize() == 100 && ram.getUsedSize() == 0 && ram.getFreeSize() == 100,
                "una RAM nueva esta toda libre");
        check(ram.allocate(40) && ram.getUsedSize() == 40 && ram.getFreeSize() == 60,
                "reservar 40: queda 40 usado y 60 libre");
        check(ram.allocate(60) && ram.getUsedSize() == 100 && ram.getFreeSize() == 0,
                "reservar justo lo que queda llena la RAM (100 de 100)");
        check(!ram.allocate(1) && ram.getUsedSize() == 100, "con la RAM llena, reservar 1 falla y no cambia nada");
        ram.release(30);
        check(ram.getUsedSize() == 70 && ram.getFreeSize() == 30, "liberar 30: quedan 70 usados");
        check(!ram.allocate(31) && ram.getUsedSize() == 70, "reservar mas de lo libre (31 de 30) falla sin cambiar nada");
        check(ram.allocate(30) && ram.getUsedSize() == 100, "reservar lo que se libero funciona");
        try {
            ram.release(101);
            check(false, "liberar mas de lo usado");
        } catch (IllegalStateException e) {
            check(true, "liberar mas de lo usado se rechaza");
        }
        try {
            ram.allocate(0);
            check(false, "reservar 0");
        } catch (IllegalArgumentException e) {
            check(true, "reservar 0 se rechaza");
        }
        try {
            ram.release(-5);
            check(false, "liberar negativo");
        } catch (IllegalArgumentException e) {
            check(true, "liberar un valor negativo se rechaza");
        }
        try {
            new RAM(0);
            check(false, "RAM de tamano 0");
        } catch (IllegalArgumentException e) {
            check(true, "una RAM de tamano 0 se rechaza");
        }
    }

    /** Varios hilos reservando y liberando a la vez: la RAM nunca se pasa ni se desajusta. */
    private static void ramConcurrente() throws Exception {
        titulo("RAM con varios hilos");
        final RAM ram = new RAM(4);
        final int hilos = 8, vueltas = 2000;
        final int[] maximo = {0};
        final Object cerrojo = new Object();
        final int[] dentro = {0};
        final boolean[] excedida = {false};
        Thread[] ts = new Thread[hilos];
        for (int h = 0; h < hilos; h++) {
            ts[h] = new Thread(() -> {
                for (int i = 0; i < vueltas; i++) {
                    if (ram.allocate(1)) {
                        synchronized (cerrojo) {
                            dentro[0]++;
                            maximo[0] = Math.max(maximo[0], dentro[0]);
                            if (dentro[0] > 4) {
                                excedida[0] = true;
                            }
                        }
                        synchronized (cerrojo) {
                            dentro[0]--;
                        }
                        ram.release(1);
                    }
                }
            });
        }
        for (Thread t : ts) {
            t.start();
        }
        for (Thread t : ts) {
            t.join();
        }
        check(!excedida[0], "nunca hubo mas de 4 reservas a la vez (RAM de 4)");
        check(ram.getUsedSize() == 0, "al final no queda memoria usada (" + ram.getUsedSize() + ")");
    }

    /** Un proceso que cabe pasa de nuevo a listo en el primer ciclo y ocupa su memoria. */
    private static void admisionNormal() {
        titulo("Admision normal");
        Cluster c = cluster(100);
        Computer pc = c.getComputer(1);
        PCB a = proceso(c, "A", 30, 4, 500);
        check(a.getState() == ProcessState.NEW && enNuevos(pc) == 1 && pc.getRam().getUsedSize() == 0,
                "al llegar queda NEW en la cola de nuevos y todavia no ocupa RAM");
        c.tick();
        check(a.getState() != ProcessState.NEW && enNuevos(pc) == 0, "en el primer ciclo sale de la cola de nuevos");
        check(pc.getRam().getUsedSize() == 30, "ocupa su memoria (30 de 100)");
        check(c.getComputer(2).getRam().getUsedSize() == 0, "la RAM del otro computador no cambia");
        check(hastaTerminar(c, a), "el proceso termina");
        check(pc.getRam().getUsedSize() == 0, "al terminar libera toda su memoria");
    }

    /** Sin memoria suficiente el proceso espera en la cola de nuevos. */
    private static void faltaDeMemoria() {
        titulo("Falta de memoria");
        Cluster c = cluster(30);
        Computer pc = c.getComputer(1);
        PCB a = proceso(c, "A", 20, 10, 500);
        PCB b = proceso(c, "B", 20, 3, 500);
        ticks(c, 3);
        check(a.getState() != ProcessState.NEW, "A (20) entra: cabe en la RAM de 30");
        check(b.getState() == ProcessState.NEW && enNuevos(pc) == 1, "B (20) se queda en NEW: solo quedan 10 libres");
        check(pc.getRam().getUsedSize() == 20, "la RAM usada sigue en 20 (B no reservo nada)");
        ticks(c, 3);
        check(b.getState() == ProcessState.NEW, "B sigue esperando mientras A siga vivo");
    }

    /** Cuando un proceso termina y libera memoria, el que esperaba entra en ese mismo ciclo. */
    private static void admisionPosterior() {
        titulo("Admision posterior");
        Cluster c = cluster(30);
        Computer pc = c.getComputer(1);
        PCB a = proceso(c, "A", 20, 5, 500);
        PCB b = proceso(c, "B", 20, 3, 500);
        boolean aTermino = false;
        for (int i = 0; i < MAX_CICLOS && !aTermino; i++) {
            c.tick();
            aTermino = a.getState() == ProcessState.TERMINATED;
        }
        check(aTermino, "A termina");
        check(b.getState() != ProcessState.NEW && enNuevos(pc) == 0,
                "B entra en el mismo ciclo en que A libera su memoria");
        check(pc.getRam().getUsedSize() == 20, "la RAM tiene a B solamente (20 de 30)");
        check(hastaTerminar(c, b), "B termina");
        check(pc.getRam().getUsedSize() == 0, "al final no queda memoria usada");
    }

    /** La cola de nuevos respeta el orden de llegada: si el primero no cabe, los demas esperan. */
    private static void colaDeNuevosEnOrden() {
        titulo("Cola de nuevos: orden de llegada");
        Cluster c = cluster(30);
        Computer pc = c.getComputer(1);
        PCB a = proceso(c, "A", 25, 8, 500);   // entra y ocupa casi todo
        PCB b = proceso(c, "B", 20, 2, 500);   // no cabe junto a A
        PCB d = proceso(c, "D", 5, 2, 500);    // si cabria, pero llego despues de B
        ticks(c, 3);
        check(b.getState() == ProcessState.NEW && d.getState() == ProcessState.NEW,
                "B no cabe y D (que si cabria) no le pasa por delante: los dos esperan");
        check(enNuevos(pc) == 2 && pc.getNewSnapshot().get(0) == b, "la cola de nuevos tiene a B primero y a D despues");
        check(pc.getRam().getUsedSize() == 25, "solo A tiene memoria reservada");
        check(hastaTerminar(c, a), "A termina");
        check(b.getState() != ProcessState.NEW && d.getState() != ProcessState.NEW,
                "al liberarse la memoria entran B y D (20 + 5 caben en 30)");
        check(pc.getRam().getUsedSize() == 25, "ahora B y D ocupan 25");
        check(hastaTerminar(c, b) && hastaTerminar(c, d), "B y D terminan");
        check(pc.getRam().getUsedSize() == 0, "no queda memoria usada");
    }

    /** Un proceso terminado por deadline tambien libera su memoria y deja entrar al que esperaba. */
    private static void liberacionPorDeadline() {
        titulo("Liberacion por vencimiento de deadline");
        Cluster c = cluster(30);
        Computer pc = c.getComputer(1);
        PCB a = proceso(c, "A", 25, 1000, 6);    // necesita 1000 ciclos pero solo tiene 6
        PCB b = proceso(c, "B", 20, 3, 500);
        ticks(c, 3);
        check(b.getState() == ProcessState.NEW, "B espera porque A ocupa 25 de 30");
        boolean vencio = false;
        for (int i = 0; i < 20 && !vencio; i++) {
            c.tick();
            vencio = a.getState() == ProcessState.TERMINATED;
        }
        check(vencio && a.isDeadlineMissed(), "A termina por deadline vencido");
        check(b.getState() != ProcessState.NEW && enNuevos(pc) == 0,
                "al liberarse la memoria de A, B entra de inmediato");
        check(pc.getRam().getUsedSize() == 20, "la RAM refleja solo a B (20)");
        check(hastaTerminar(c, b), "B termina bien");
        check(pc.getRam().getUsedSize() == 0, "no queda memoria usada");
    }

    /** Un proceso que vence mientras esta en la cola de nuevos no tenia RAM: no se libera nada. */
    private static void deadlineEnLaColaDeNuevos() {
        titulo("Deadline vencido en la cola de nuevos");
        Cluster c = cluster(30);
        Computer pc = c.getComputer(1);
        PCB a = proceso(c, "A", 25, 1000, 500);
        PCB b = proceso(c, "B", 20, 3, 4);       // espera, y su deadline es de 4 ciclos
        ticks(c, 8);
        check(b.getState() == ProcessState.TERMINATED && b.isDeadlineMissed(),
                "B vence esperando en la cola de nuevos");
        check(enNuevos(pc) == 0, "ya no esta en la cola de nuevos");
        check(pc.getRam().getUsedSize() == 25, "la RAM sigue en 25: B no tenia memoria que liberar");
        check(a.getState() != ProcessState.TERMINATED, "A no se ve afectado");
    }

    /** Un proceso mas grande que toda la RAM se rechaza; uno del tamano exacto entra. */
    private static void procesoMasGrandeQueLaRam() {
        titulo("Proceso mas grande que la RAM");
        Cluster c = cluster(50);
        Computer pc = c.getComputer(1);
        PCB grande = ProcessFactory.createCpuBound("G", 1, 51, 3, 500, 1, 0);
        try {
            c.submit(grande);
            check(false, "proceso de 51 en una RAM de 50");
        } catch (IllegalArgumentException e) {
            check(true, "un proceso de 51 en una RAM de 50 se rechaza al llegar");
        }
        check(enNuevos(pc) == 0 && pc.getRam().getUsedSize() == 0, "el rechazo no deja nada en la cola ni en la RAM");
        PCB justo = proceso(c, "J", 50, 3, 500);
        c.tick();
        check(justo.getState() != ProcessState.NEW && pc.getRam().getUsedSize() == 50,
                "un proceso de exactamente 50 entra y llena la RAM");
        check(hastaTerminar(c, justo) && pc.getRam().getUsedSize() == 0, "termina y libera todo");
    }

    /** Los buffers reservan memoria en su anfitrion y compiten con los procesos. */
    private static void memoriaDeLosBuffers() {
        titulo("Memoria de los buffers");
        Cluster c = cluster(30);
        Computer pc = c.getComputer(1);
        BoundedBuffer b = c.getBufferManager().createBuffer(20, 1);
        check(pc.getRam().getUsedSize() == 20 && b.getReservedMemory() == 20,
                "un buffer de capacidad 20 reserva 20 de la RAM de su anfitrion");
        check(c.getComputer(2).getRam().getUsedSize() == 0, "la RAM del otro computador no se toca");
        PCB m = proceso(c, "M", 15, 2, 500);
        ticks(c, 4);
        check(m.getState() == ProcessState.NEW, "un proceso de 15 espera: el buffer deja solo 10 libres");
        try {
            c.getBufferManager().createBuffer(11, 1);
            check(false, "buffer que no cabe");
        } catch (IllegalStateException e) {
            check(true, "un buffer que no cabe en la RAM libre se rechaza");
        }
        check(pc.getRam().getUsedSize() == 20 && c.getBufferManager().getSnapshot().size() == 1,
                "el buffer rechazado no deja memoria reservada ni queda registrado");
        check(c.getBufferManager().removeBuffer(b.getId()) && pc.getRam().getUsedSize() == 0,
                "al eliminar el buffer se libera su memoria");
        c.tick();
        check(m.getState() != ProcessState.NEW && pc.getRam().getUsedSize() == 15,
                "al liberarse la memoria del buffer, el proceso en espera entra");
    }

    /** Un proceso bloqueado sigue ocupando su RAM (no se libera hasta que termina). */
    private static void procesoBloqueadoConservaSuMemoria() {
        titulo("Un proceso bloqueado conserva su memoria");
        Cluster c = cluster(50);
        Computer pc = c.getComputer(1);
        BoundedBuffer b = c.getBufferManager().createBuffer(5, 1);   // reserva 5
        PCB cons = ProcessFactory.createConsumer("C", 1, 15, b.getId(), 1, 1, 5000, 1, 0);
        pc.registerRunner(c.getBufferManager().createRunner(cons));
        c.submit(cons);
        ticks(c, 12);
        check(cons.getState() == ProcessState.BLOCKED, "el consumidor queda bloqueado (buffer vacio)");
        check(pc.getRam().getUsedSize() == 20, "sigue ocupando sus 15 (mas 5 del buffer = 20)");
        PCB grande = proceso(c, "G", 40, 2, 500);
        ticks(c, 3);
        check(grande.getState() == ProcessState.NEW, "un proceso de 40 no entra: el bloqueado aun tiene su memoria");
        PCB prod = ProcessFactory.createProducer("P", 1, 10, b.getId(), 1, 1, 5000, 1, c.getCurrentCycle());
        pc.registerRunner(c.getBufferManager().createRunner(prod));
        c.submit(prod);
        check(hastaTerminar(c, cons) && hastaTerminar(c, prod), "al llegar un productor, los dos terminan");
        check(hastaTerminar(c, grande), "el proceso grande entra y termina cuando se libera la memoria");
        check(pc.getRam().getUsedSize() == 5, "al final solo queda la memoria del buffer (5)");
    }

    /** En cada ciclo de un escenario mixto, la RAM usada = procesos con memoria + buffers. */
    private static void cuentaDeMemoriaSiempreCuadra() {
        titulo("La cuenta de memoria cuadra en todos los ciclos");
        Cluster c = cluster(60);
        Computer pc1 = c.getComputer(1);
        Computer pc2 = c.getComputer(2);
        BoundedBuffer b = c.getBufferManager().createBuffer(10, 1);   // 10 en PC1
        PCB[] todos = new PCB[8];
        todos[0] = proceso(c, "A", 20, 6, 500);
        todos[1] = proceso(c, "B", 25, 4, 500);
        todos[2] = proceso(c, "C", 15, 3, 500);
        todos[3] = proceso(c, "D", 30, 5, 12);       // algunos vencen
        PCB prod = ProcessFactory.createProducer("P", 1, 10, b.getId(), 1, 4, 5000, 1, 0);
        PCB cons = ProcessFactory.createConsumer("K", 1, 10, b.getId(), 1, 4, 5000, 2, 0);
        pc1.registerRunner(c.getBufferManager().createRunner(prod));
        c.submit(prod);
        pc2.registerRunner(c.getBufferManager().createRunner(cons));
        c.submit(cons);
        todos[4] = prod;
        todos[5] = cons;
        todos[6] = proceso(c, "E", 10, 2, 500);
        todos[7] = proceso(c, "F", 55, 2, 500);      // casi toda la RAM: tendra que esperar mucho
        boolean cuadro = true;
        boolean nuncaSePaso = true;
        int cicloMalo = -1;
        boolean todosTerminaron = false;
        for (int i = 1; i <= MAX_CICLOS && !todosTerminaron; i++) {
            c.tick();
            int esperado1 = memoriaDeProcesos(pc1) + memoriaDeBuffers(c, pc1);
            int esperado2 = memoriaDeProcesos(pc2) + memoriaDeBuffers(c, pc2);
            if (pc1.getRam().getUsedSize() != esperado1 || pc2.getRam().getUsedSize() != esperado2) {
                cuadro = false;
                if (cicloMalo < 0) {
                    cicloMalo = i;
                }
            }
            if (pc1.getRam().getUsedSize() > 60) {
                nuncaSePaso = false;
            }
            todosTerminaron = true;
            for (PCB p : todos) {
                if (p.getState() != ProcessState.TERMINATED) {
                    todosTerminaron = false;
                }
            }
        }
        check(todosTerminaron, "los 8 procesos terminan (unos bien y otros por deadline)");
        check(cuadro, "en todos los ciclos: RAM usada = memoria de los procesos + de los buffers"
                + (cicloMalo > 0 ? " (fallo en el ciclo " + cicloMalo + ")" : ""));
        check(nuncaSePaso, "la RAM usada nunca supero el total");
        check(pc1.getRam().getUsedSize() == 10 && pc2.getRam().getUsedSize() == 0,
                "al final solo queda la memoria del buffer en PC1 (10) y PC2 esta libre");
        boolean algunoVencio = false;
        for (PCB p : todos) {
            if (p.isDeadlineMissed()) {
                algunoVencio = true;
            }
        }
        check(algunoVencio, "el escenario incluye procesos terminados por deadline");
    }
}
