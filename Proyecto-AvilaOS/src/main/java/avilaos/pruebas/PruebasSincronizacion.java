/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.datastructures.CustomQueue;
import avilaos.enums.BlockReason;
import avilaos.enums.ProcessState;
import avilaos.log.LogEvent;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;
import avilaos.scheduler.FCFSPolicy;
import avilaos.sync.BoundedBuffer;
import avilaos.sync.BufferMetrics;
import avilaos.sync.SemaforoAvila;

/**
 * I11: pruebas reproducibles de la sincronizacion local y remota (productor-consumidor).
 * Es una prueba que se queda en el repositorio: se ejecuta como programa (main) y termina
 * con "TODAS LAS PRUEBAS PASARON" o con la lista de fallas (codigo de salida 1).
 * Cubre: semaforos solos, buffer vacio, buffer lleno, mutex, varios productores y
 * consumidores, latencia remota, coherencia con el log de eventos y reproducibilidad.
 * Todo corre con ticks manuales del cluster (sin hilos ni esperas), asi que da siempre
 * el mismo resultado.
 */
public class PruebasSincronizacion {

    private static final int MAX_CICLOS = 1000;
    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        semaforos();
        bufferVacio();
        bufferLleno();
        mutex();
        variosProductoresConsumidores();
        latenciaRemota();
        coherenciaConElLog();
        reproducibilidad();

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

    /** Cluster de 2 computadores FCFS con la latencia de red indicada. */
    private static Cluster cluster(int latencia) {
        Cluster c = new Cluster(10, latencia);
        c.addComputer("PC1", new FCFSPolicy(), 200);
        c.addComputer("PC2", new FCFSPolicy(), 200);
        return c;
    }

    private static PCB productor(Cluster c, int pc, String nombre, BoundedBuffer b, int ciclosPorElemento, int elementos) {
        PCB p = ProcessFactory.createProducer(nombre, 1, 5, b.getId(), ciclosPorElemento, elementos,
                5000, pc, c.getCurrentCycle());
        entregar(c, pc, p);
        return p;
    }

    private static PCB consumidor(Cluster c, int pc, String nombre, BoundedBuffer b, int ciclosPorElemento, int elementos) {
        PCB p = ProcessFactory.createConsumer(nombre, 1, 5, b.getId(), ciclosPorElemento, elementos,
                5000, pc, c.getCurrentCycle());
        entregar(c, pc, p);
        return p;
    }

    /** Igual que ConfigLoader: primero el ejecutor, despues el proceso. */
    private static void entregar(Cluster c, int pc, PCB p) {
        c.getComputer(pc).registerRunner(c.getBufferManager().createRunner(p));
        c.submit(p);
    }

    private static PCB cpu(String nombre) {
        return ProcessFactory.createCpuBound(nombre, 1, 5, 10, 1000, 1, 0);
    }

    private static void ticks(Cluster c, int n) {
        for (int i = 0; i < n; i++) {
            c.tick();
        }
    }

    private static boolean terminado(PCB... procesos) {
        for (PCB p : procesos) {
            if (p.getState() != ProcessState.TERMINATED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Avanza ciclo a ciclo hasta que todos terminen (o se pasen los ciclos maximos), revisando
     * en cada ciclo que el buffer nunca pase de su capacidad ni quede con valores imposibles.
     * @return ciclos que tardo, o -1 si no terminaron (posible interbloqueo)
     */
    private static int correrHastaTerminar(Cluster c, BoundedBuffer b, PCB... procesos) {
        for (int i = 1; i <= MAX_CICLOS; i++) {
            c.tick();
            if (b.size() > b.getCapacity() || b.size() < 0 || b.getMutex().getValue() > 1
                    || b.getItems().getValue() > b.getCapacity() || b.getSpaces().getValue() > b.getCapacity()) {
                check(false, "invariante del buffer roto en el ciclo " + i);
                return -1;
            }
            if (terminado(procesos)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean semaforosEnReposo(BoundedBuffer b) {
        return b.getMutex().getValue() == 1 && b.getItems().getValue() == 0
                && b.getSpaces().getValue() == b.getCapacity();
    }

    private static int contarEnLog(Cluster c, String texto) {
        CustomQueue<LogEvent> eventos = c.getLogger().getSnapshot();
        int n = 0;
        for (int i = 0; i < eventos.size(); i++) {
            if (eventos.get(i).getMessage().contains(texto)) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ pruebas

    /** Semaforos solos: bloqueo, despertar, orden de llegada y valores. */
    private static void semaforos() {
        titulo("Semaforos");
        SemaforoAvila s = new SemaforoAvila(1, BlockReason.MUTEX);
        PCB a = cpu("A"), b = cpu("B"), c = cpu("C");
        check(!s.semWait(a) && s.getValue() == 0, "semWait con valor 1: no bloquea y baja a 0");
        check(s.semWait(b) && s.getValue() == -1, "semWait con valor 0: bloquea y baja a -1");
        check(b.getState() == ProcessState.BLOCKED && b.getBlockReason() == BlockReason.MUTEX,
                "el proceso bloqueado queda BLOCKED con motivo MUTEX");
        check(s.semWait(c), "un segundo proceso tambien se bloquea");
        check(s.getWaitingSnapshot().size() == 2, "hay 2 procesos esperando");
        PCB despertado = s.semSignal();
        check(despertado == b, "semSignal despierta al primero que llego (orden FIFO)");
        check(s.semSignal() == c, "el siguiente semSignal despierta al segundo");
        check(s.semSignal() == null && s.getValue() == 1,
                "semSignal sin nadie esperando devuelve null y sube el valor a 1");
        check(s.getWaitingSnapshot().size() == 0, "la cola de espera quedo vacia");
        try {
            new SemaforoAvila(-1, BlockReason.MUTEX);
            check(false, "valor inicial negativo");
        } catch (IllegalArgumentException e) {
            check(true, "un valor inicial negativo se rechaza");
        }
    }

    /** Un consumidor sin nada que consumir se bloquea hasta que llega un productor. */
    private static void bufferVacio() {
        titulo("Buffer vacio");
        Cluster c = cluster(0);
        BoundedBuffer b = c.getBufferManager().createBuffer(2, 1);
        PCB cons = consumidor(c, 1, "C1", b, 1, 1);
        ticks(c, 12);
        check(cons.getState() == ProcessState.BLOCKED && cons.getBlockReason() == BlockReason.EMPTY_SEMAPHORE,
                "el consumidor se bloquea por buffer vacio (EMPTY_SEMAPHORE)");
        check(b.getItems().getValue() == -1 && b.getItems().getWaitingSnapshot().size() == 1,
                "el semaforo de elementos esta en -1 con 1 proceso esperando");
        check(b.getTotalConsumed() == 0 && b.size() == 0, "no consumio nada");
        ticks(c, 10);
        check(cons.getState() == ProcessState.BLOCKED, "sigue bloqueado mientras nadie produce");
        PCB prod = productor(c, 1, "P1", b, 1, 1);
        int ciclos = correrHastaTerminar(c, b, prod, cons);
        check(ciclos > 0, "al llegar el productor, los dos terminan (" + ciclos + " ciclos)");
        check(b.getTotalProduced() == 1 && b.getTotalConsumed() == 1, "se produjo 1 y se consumio 1");
        check(semaforosEnReposo(b), "los semaforos quedan en reposo (1/0/" + b.getCapacity() + ")");
        check(b.takeMetrics().getItemsWaits() == 1, "la metrica registra 1 espera en el semaforo de elementos");
    }

    /** Un productor con el buffer lleno se bloquea hasta que alguien consume. */
    private static void bufferLleno() {
        titulo("Buffer lleno");
        Cluster c = cluster(0);
        BoundedBuffer b = c.getBufferManager().createBuffer(1, 1);
        PCB prod = productor(c, 1, "P1", b, 1, 3);
        ticks(c, 30);
        check(prod.getState() == ProcessState.BLOCKED && prod.getBlockReason() == BlockReason.FULL_SEMAPHORE,
                "el productor se bloquea por buffer lleno (FULL_SEMAPHORE)");
        check(b.size() == 1 && b.getTotalProduced() == 1, "el buffer tiene 1 elemento (su capacidad) y solo se produjo 1");
        check(b.getSpaces().getValue() == -1, "el semaforo de espacios esta en -1");
        PCB cons = consumidor(c, 1, "C1", b, 1, 3);
        int ciclos = correrHastaTerminar(c, b, prod, cons);
        check(ciclos > 0, "al empezar a consumir, ambos terminan (" + ciclos + " ciclos)");
        check(b.getTotalProduced() == 3 && b.getTotalConsumed() == 3, "se produjeron y consumieron 3");
        check(b.size() == 0 && semaforosEnReposo(b), "buffer vacio y semaforos en reposo");
        check(b.takeMetrics().getSpacesWaits() >= 1, "la metrica registra esperas en el semaforo de espacios");
    }

    /**
     * Exclusion mutua: dos productores de computadores distintos llegan juntos al semWait(mutex)
     * en el mismo ciclo (sin latencia de red): uno entra y el otro debe bloquearse en el mutex.
     */
    private static void mutex() {
        titulo("Mutex");
        Cluster c = cluster(0);
        BoundedBuffer b = c.getBufferManager().createBuffer(4, 1);
        PCB p1 = productor(c, 1, "P1", b, 1, 3);
        PCB p2 = productor(c, 2, "P2", b, 1, 3);
        PCB c1 = consumidor(c, 2, "C1", b, 1, 6);
        boolean vioMutex = false;
        int minMutex = 1;
        int ciclos = -1;
        for (int i = 1; i <= MAX_CICLOS && ciclos < 0; i++) {
            c.tick();
            minMutex = Math.min(minMutex, b.getMutex().getValue());
            for (int pc = 1; pc <= 2; pc++) {
                CustomQueue<PCB> bloqueados = c.getComputer(pc).getBlockedSnapshot();
                for (int k = 0; k < bloqueados.size(); k++) {
                    if (bloqueados.get(k).getBlockReason() == BlockReason.MUTEX) {
                        vioMutex = true;
                    }
                }
            }
            if (b.size() > b.getCapacity()) {
                check(false, "el buffer se paso de su capacidad en el ciclo " + i);
                return;
            }
            if (terminado(p1, p2, c1)) {
                ciclos = i;
            }
        }
        check(ciclos > 0, "los 3 procesos terminan (" + ciclos + " ciclos)");
        check(vioMutex, "un proceso se bloqueo esperando el mutex (hubo competencia real)");
        check(minMutex < 0, "el mutex llego a valores negativos (proceso en su cola de espera)");
        check(b.getTotalProduced() == 6 && b.getTotalConsumed() == 6, "se produjeron y consumieron 6 (no se perdio ninguno)");
        check(semaforosEnReposo(b), "el mutex vuelve a 1 y los demas semaforos a su valor inicial");
        check(b.takeMetrics().getMutexWaits() > 0, "la metrica registra esperas en el mutex");
    }

    /** Varios productores y consumidores, locales y remotos, con distintos ritmos. */
    private static void variosProductoresConsumidores() {
        titulo("Varios productores y consumidores");
        Cluster c = cluster(2);
        BoundedBuffer b = c.getBufferManager().createBuffer(3, 1);
        PCB p1 = productor(c, 1, "P1", b, 1, 4);
        PCB p2 = productor(c, 2, "P2", b, 2, 3);
        PCB p3 = productor(c, 2, "P3", b, 1, 3);
        PCB c1 = consumidor(c, 1, "C1", b, 2, 6);
        PCB c2 = consumidor(c, 2, "C2", b, 1, 4);
        int ciclos = correrHastaTerminar(c, b, p1, p2, p3, c1, c2);
        check(ciclos > 0, "los 5 procesos terminan sin interbloqueo (" + ciclos + " ciclos)");
        check(b.getTotalProduced() == 10 && b.getTotalConsumed() == 10, "se produjeron 10 y se consumieron 10");
        check(b.size() == 0, "el buffer queda vacio");
        check(semaforosEnReposo(b), "semaforos mutex/elementos/espacios en 1/0/3");
        check(c.getComputer(1).getBlockedSnapshot().size() == 0 && c.getComputer(2).getBlockedSnapshot().size() == 0,
                "no queda ningun proceso bloqueado");
        check(c.getComputer(1).getNetworkAccesses() == 0 && c.getComputer(2).getNetworkAccesses() == 10,
                "PC1 (anfitrion) no usa la red; PC2 hace 10 accesos remotos (3+3+4)");
    }

    /** La latencia solo afecta a los accesos remotos y se paga una vez por elemento. */
    private static void latenciaRemota() {
        titulo("Latencia remota");
        int latencia = 3, elementos = 3;
        Cluster cl = cluster(latencia);
        BoundedBuffer bl = cl.getBufferManager().createBuffer(5, 1);
        PCB local = productor(cl, 1, "PL", bl, 1, elementos);
        int tLocal = correrHastaTerminar(cl, bl, local);

        Cluster cr = cluster(latencia);
        BoundedBuffer br = cr.getBufferManager().createBuffer(5, 1);
        PCB remoto = productor(cr, 2, "PR", br, 1, elementos);
        boolean vioRed = false;
        int tRemoto = -1;
        for (int i = 1; i <= MAX_CICLOS && tRemoto < 0; i++) {
            cr.tick();
            if (remoto.getState() == ProcessState.BLOCKED && remoto.getBlockReason() == BlockReason.NETWORK_LATENCY) {
                vioRed = true;
            }
            if (terminado(remoto)) {
                tRemoto = i;
            }
        }
        check(tLocal > 0 && tRemoto > 0, "el productor local y el remoto terminan");
        check(cl.getComputer(1).getNetworkAccesses() == 0 && cl.getComputer(1).getNetworkWaitedCycles() == 0,
                "acceso local: 0 accesos de red y 0 ciclos de espera");
        check(cr.getComputer(2).getNetworkAccesses() == elementos,
                "acceso remoto: " + elementos + " accesos de red (uno por elemento)");
        check(cr.getComputer(2).getNetworkWaitedCycles() == (long) elementos * latencia,
                "acceso remoto: " + (elementos * latencia) + " ciclos de espera de red (" + elementos + " x " + latencia + ")");
        check(vioRed, "durante la latencia el proceso esta BLOCKED con motivo NETWORK_LATENCY");
        check(tRemoto >= tLocal + elementos * latencia,
                "el remoto tarda al menos " + (elementos * latencia) + " ciclos mas (local " + tLocal + ", remoto " + tRemoto + ")");
        check(br.getTotalProduced() == elementos && br.size() == elementos && br.getMutex().getValue() == 1,
                "el remoto tambien produce todo (" + elementos + " elementos en el buffer) y libera el mutex");

        // latencia 0: el acceso remoto no espera
        Cluster c0 = cluster(0);
        BoundedBuffer b0 = c0.getBufferManager().createBuffer(5, 1);
        PCB r0 = productor(c0, 2, "P0", b0, 1, elementos);
        int t0 = correrHastaTerminar(c0, b0, r0);
        check(t0 > 0 && c0.getComputer(2).getNetworkWaitedCycles() == 0, "con latencia 0 el acceso remoto no espera");

        // la latencia se puede cambiar en caliente: se usa en los accesos siguientes
        Cluster cc = cluster(1);
        BoundedBuffer bc = cc.getBufferManager().createBuffer(5, 1);
        PCB rc = productor(cc, 2, "PC", bc, 1, 4);
        while (cc.getComputer(2).getNetworkAccesses() < 2 && cc.getCurrentCycle() < MAX_CICLOS) {
            cc.tick();
        }
        cc.setNetworkLatency(4);
        int t = correrHastaTerminar(cc, bc, rc);
        long esperado = 2L * 1 + 2L * 4;   // 2 accesos con latencia 1 y 2 con latencia 4
        check(t > 0 && cc.getComputer(2).getNetworkWaitedCycles() >= esperado,
                "cambiar la latencia en caliente afecta a los accesos siguientes (espera total "
                + cc.getComputer(2).getNetworkWaitedCycles() + " >= " + esperado + ")");
    }

    /** Cada bloqueo por semaforo tiene su desbloqueo en el log y coincide con las metricas. */
    private static void coherenciaConElLog() {
        titulo("Coherencia con el log y las metricas");
        Cluster c = cluster(2);
        BoundedBuffer b = c.getBufferManager().createBuffer(2, 1);
        PCB p1 = productor(c, 1, "P1", b, 2, 3);
        PCB p2 = productor(c, 2, "P2", b, 1, 3);
        PCB c1 = consumidor(c, 1, "C1", b, 2, 3);
        PCB c2 = consumidor(c, 2, "C2", b, 1, 3);
        int ciclos = correrHastaTerminar(c, b, p1, p2, c1, c2);
        check(ciclos > 0, "el escenario termina (" + ciclos + " ciclos)");
        check(c.getLogger().getTotalLogged() <= c.getLogger().getCapacity(),
                "el log no descarto eventos (" + c.getLogger().getTotalLogged() + " de " + c.getLogger().getCapacity() + ")");
        int bloqMutex = contarEnLog(c, "se bloquea (MUTEX)");
        int bloqItems = contarEnLog(c, "se bloquea (EMPTY_SEMAPHORE)");
        int bloqSpaces = contarEnLog(c, "se bloquea (FULL_SEMAPHORE)");
        int despMutex = contarEnLog(c, "se desbloquea (MUTEX)");
        int despItems = contarEnLog(c, "se desbloquea (EMPTY_SEMAPHORE)");
        int despSpaces = contarEnLog(c, "se desbloquea (FULL_SEMAPHORE)");
        System.out.println("        log: mutex " + bloqMutex + "/" + despMutex + ", elementos " + bloqItems + "/" + despItems
                + ", espacios " + bloqSpaces + "/" + despSpaces + " (bloqueos/desbloqueos)");
        check(bloqMutex == despMutex && bloqItems == despItems && bloqSpaces == despSpaces,
                "cada bloqueo por semaforo tiene su desbloqueo");
        BufferMetrics m = b.takeMetrics();
        check(m.getMutexWaits() == despMutex && m.getItemsWaits() == despItems && m.getSpacesWaits() == despSpaces,
                "las esperas de las metricas coinciden con los desbloqueos del log");
        check(contarEnLog(c, "inserta en el buffer B1") == 6 && contarEnLog(c, "extrae") == 6,
                "el log tiene 6 inserciones y 6 extracciones");
    }

    /** El mismo escenario dos veces da exactamente el mismo resultado. */
    private static void reproducibilidad() {
        titulo("Reproducibilidad");
        long[] a = escenarioCompleto();
        long[] b = escenarioCompleto();
        boolean iguales = a.length == b.length;
        for (int i = 0; i < a.length && iguales; i++) {
            iguales = a[i] == b[i];
        }
        System.out.println("        corrida 1: ciclos=" + a[0] + " esperas(m/e/s)=" + a[1] + "/" + a[2] + "/" + a[3]
                + " ciclosEsperados(m/e/s)=" + a[4] + "/" + a[5] + "/" + a[6] + " red=" + a[7]);
        check(a[0] > 0, "el escenario termina");
        check(iguales, "dos corridas dan los mismos ciclos, esperas y accesos de red");
    }

    private static long[] escenarioCompleto() {
        Cluster c = cluster(2);
        BoundedBuffer b = c.getBufferManager().createBuffer(2, 1);
        PCB p1 = productor(c, 1, "P1", b, 2, 4);
        PCB p2 = productor(c, 2, "P2", b, 1, 4);
        PCB c1 = consumidor(c, 1, "C1", b, 1, 4);
        PCB c2 = consumidor(c, 2, "C2", b, 2, 4);
        int ciclos = correrHastaTerminar(c, b, p1, p2, c1, c2);
        BufferMetrics m = b.takeMetrics();
        return new long[] {ciclos, m.getMutexWaits(), m.getItemsWaits(), m.getSpacesWaits(),
            Math.round(m.getMutexWaits() * m.getMutexAverageWait()),
            Math.round(m.getItemsWaits() * m.getItemsAverageWait()),
            Math.round(m.getSpacesWaits() * m.getSpacesAverageWait()),
            c.getComputer(2).getNetworkWaitedCycles()};
    }
}
