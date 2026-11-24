/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.datastructures.CustomQueue;
import avilaos.enums.BlockReason;
import avilaos.enums.ProcessState;
import avilaos.interfaces.VictimPolicy;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;
import avilaos.os.midterm.MediumTermScheduler;
import avilaos.os.midterm.VictimSelector;
import avilaos.scheduler.FCFSPolicy;

/**
 * S06: a quien se suspende. Comprueba el criterio (menor prioridad = numero mas grande; en empate,
 * el que lleva mas tiempo bloqueado), que no se elige a quien espera al disco, y que el planificador
 * de mediano plazo senala a la victima mientras el disparo esta activo.
 * Se ejecuta como programa (main); termina con "TODAS LAS PRUEBAS PASARON" o con codigo 1.
 */
public class PruebasVictimaSuspension {

    private static final VictimSelector SEL = new VictimSelector();
    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        elegibles();
        porPrioridad();
        empateElMasAntiguo();
        sinCandidatos();
        noModificaLaCola();
        enElPlanificador();
        criterioIntercambiable();

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
        } else {
            System.out.println("HAY " + fallas + " FALLAS de " + verificaciones + " verificaciones");
            System.exit(1);
        }
    }

    /** Proceso Bloqueado por el motivo dado, con la prioridad dada (sin pasar por un computador). */
    private static PCB bloqueado(String nombre, int prioridad, BlockReason motivo) {
        PCB p = ProcessFactory.createCpuBound(nombre, prioridad, 10, 50, 500, 1, 0);
        p.setState(ProcessState.READY);
        p.setState(ProcessState.RUNNING);
        p.setState(ProcessState.BLOCKED);
        p.setBlockReason(motivo);
        return p;
    }

    private static CustomQueue<PCB> cola(PCB... ps) {
        CustomQueue<PCB> q = new CustomQueue<>();
        for (int i = 0; i < ps.length; i++) {
            q.enqueue(ps[i]);
        }
        return q;
    }

    private static void elegibles() {
        titulo("Quien es elegible");
        check(VictimSelector.isEligible(bloqueado("A", 1, BlockReason.IO)), "un Bloqueado por E/S simulada es elegible");
        check(VictimSelector.isEligible(bloqueado("A", 1, BlockReason.MUTEX)), "un Bloqueado en un semaforo es elegible");
        check(VictimSelector.isEligible(bloqueado("A", 1, BlockReason.NETWORK_LATENCY)), "un Bloqueado por red es elegible");
        check(!VictimSelector.isEligible(bloqueado("A", 1, BlockReason.DISK_IO)), "uno que espera al disco no es elegible");
        PCB listo = ProcessFactory.createCpuBound("L", 1, 10, 50, 500, 1, 0);
        listo.setState(ProcessState.READY);
        check(!VictimSelector.isEligible(listo), "un Listo no es elegible");
        check(!VictimSelector.isEligible(null), "null no es elegible");
    }

    private static void porPrioridad() {
        titulo("Se elige el de menor prioridad (numero mas grande)");
        PCB alta = bloqueado("Alta", 1, BlockReason.IO);
        PCB media = bloqueado("Media", 3, BlockReason.IO);
        PCB baja = bloqueado("Baja", 5, BlockReason.IO);
        check(SEL.choose(cola(alta, media, baja)) == baja, "gana Baja (5) aunque sea la ultima");
        check(SEL.choose(cola(baja, media, alta)) == baja, "gana Baja (5) aunque sea la primera");
        check(SEL.choose(cola(media, baja, alta)) == baja, "gana Baja (5) estando en medio");
        check(SEL.choose(cola(alta)) == alta, "con un solo candidato, ese");
    }

    private static void empateElMasAntiguo() {
        titulo("En empate, el que lleva mas tiempo bloqueado");
        PCB primero = bloqueado("Primero", 4, BlockReason.IO);   // se bloqueo antes: esta primero en la cola
        PCB segundo = bloqueado("Segundo", 4, BlockReason.IO);
        PCB tercero = bloqueado("Tercero", 4, BlockReason.IO);
        check(SEL.choose(cola(primero, segundo, tercero)) == primero, "empate de 3: el primero en bloquearse");
        PCB otro = bloqueado("Otro", 2, BlockReason.IO);
        check(SEL.choose(cola(otro, segundo, tercero)) == segundo,
                "empate entre Segundo y Tercero: el mas antiguo, sin importar que haya otro mejor antes");
        PCB masBaja = bloqueado("MasBaja", 9, BlockReason.IO);
        check(SEL.choose(cola(primero, segundo, masBaja)) == masBaja,
                "la prioridad manda sobre la antiguedad");
    }

    private static void sinCandidatos() {
        titulo("Sin candidatos");
        check(SEL.choose(null) == null, "cola null: no hay victima");
        check(SEL.choose(new CustomQueue<PCB>()) == null, "cola vacia: no hay victima");
        PCB d1 = bloqueado("D1", 5, BlockReason.DISK_IO);
        PCB d2 = bloqueado("D2", 9, BlockReason.DISK_IO);
        check(SEL.choose(cola(d1, d2)) == null, "si todos esperan al disco, no hay victima");
        PCB ok = bloqueado("Ok", 1, BlockReason.IO);
        check(SEL.choose(cola(d2, ok)) == ok, "se salta al que espera al disco aunque tenga peor prioridad");
    }

    private static void noModificaLaCola() {
        titulo("No modifica la cola");
        PCB a = bloqueado("A", 2, BlockReason.IO);
        PCB b = bloqueado("B", 7, BlockReason.IO);
        CustomQueue<PCB> q = cola(a, b);
        SEL.choose(q);
        check(q.size() == 2 && q.get(0) == a && q.get(1) == b, "la cola queda igual");
        check(a.getState() == ProcessState.BLOCKED && b.getState() == ProcessState.BLOCKED, "los estados no cambian");
    }

    // ------------------------------------------------------------------ con un computador

    private static PCB conEs(Cluster c, String nombre, int prioridad, int memoria) {
        PCB x = ProcessFactory.createIoBound(nombre, prioridad, memoria, 100, 1, 300, 1000, 1, c.getCurrentCycle());
        c.submit(x);
        return x;
    }

    private static boolean hasta(Cluster c, PCB x, ProcessState s) {
        for (int i = 0; i < 60 && x.getState() != s; i++) {
            c.tick();
        }
        return x.getState() == s;
    }

    private static void enElPlanificador() {
        titulo("El planificador senala a la victima");
        Cluster c = new Cluster(10, 0);
        c.addComputer("PC1", new FCFSPolicy(), 60);
        Computer pc = c.getComputer(1);
        MediumTermScheduler m = pc.getMediumTermScheduler();
        PCB buena = conEs(c, "Buena", 1, 20);
        check(hasta(c, buena, ProcessState.BLOCKED), "Buena (prioridad 1) se bloquea");
        PCB mala = conEs(c, "Mala", 6, 20);
        check(hasta(c, mala, ProcessState.BLOCKED), "Mala (prioridad 6) se bloquea");
        check(m.getVictim() == null, "sin proceso esperando no hay victima");
        PCB nuevo = ProcessFactory.createCpuBound("Nuevo", 1, 30, 5, 1000, 1, c.getCurrentCycle());
        c.submit(nuevo);
        c.tick();
        check(nuevo.getState() == ProcessState.NEW && m.isTriggerPending(), "Nuevo (30) no cabe: hay disparo");
        check(m.getVictim() == mala, "la victima es Mala, la de menor prioridad");
        check(buena.getState() == ProcessState.BLOCKED && mala.getState() == ProcessState.BLOCKED,
                "S06 solo elige: nadie se suspende todavia");
        m.setEnabled(false);
        check(m.getVictim() == null, "al desactivar el planificador no queda victima");
    }

    private static void criterioIntercambiable() {
        titulo("El criterio es intercambiable");
        Cluster c = new Cluster(10, 0);
        c.addComputer("PC1", new FCFSPolicy(), 60);
        MediumTermScheduler m = c.getComputer(1).getMediumTermScheduler();
        check(m.getVictimPolicy() instanceof VictimSelector, "por defecto usa VictimSelector");
        check(m.getVictimPolicy().getName() != null && !m.getVictimPolicy().getName().isEmpty(), "el criterio tiene nombre");
        PCB buena = conEs(c, "Buena", 1, 20);
        hasta(c, buena, ProcessState.BLOCKED);
        PCB mala = conEs(c, "Mala", 6, 20);
        hasta(c, mala, ProcessState.BLOCKED);
        c.submit(ProcessFactory.createCpuBound("Nuevo", 1, 30, 5, 1000, 1, c.getCurrentCycle()));
        c.tick();
        check(m.getVictim() == mala, "con el criterio por defecto la victima es Mala");
        // otro criterio: el primero elegible en llegar (ignora la prioridad)
        m.setVictimPolicy(new VictimPolicy() {
            @Override
            public String getName() {
                return "Primero en bloquearse";
            }

            @Override
            public PCB choose(CustomQueue<PCB> blocked) {
                for (int i = 0; i < blocked.size(); i++) {
                    if (VictimSelector.isEligible(blocked.get(i))) {
                        return blocked.get(i);
                    }
                }
                return null;
            }
        });
        c.tick();
        check(m.getVictimPolicy().getName().equals("Primero en bloquearse"), "el criterio cambio");
        check(m.getVictim() == buena, "con el nuevo criterio la victima es Buena (la primera en bloquearse)");
        try {
            m.setVictimPolicy(null);
            check(false, "un criterio nulo");
        } catch (IllegalArgumentException e) {
            check(true, "un criterio nulo se rechaza");
        }
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