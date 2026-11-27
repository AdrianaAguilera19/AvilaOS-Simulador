/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.datastructures.CustomQueue;
import avilaos.enums.ProcessState;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.hardware.RAM;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;
import avilaos.os.midterm.MediumTermScheduler;
import avilaos.os.midterm.SuspensionTrigger;
import avilaos.scheduler.FCFSPolicy;

/**
 * S05: el disparo de la suspension. Primero la regla sola (SuspensionTrigger) con casos limite y
 * despues el planificador de mediano plazo dentro de un computador con ticks manuales: se activa
 * cuando un proceso de Nuevos no cabe y hay bloqueados que liberarian lo necesario, no se activa
 * si suspender no serviria, se cuenta una sola vez y se apaga cuando deja de hacer falta.
 * Se ejecuta como programa (main); termina con "TODAS LAS PRUEBAS PASARON" o con codigo 1.
 */
public class PruebasTriggerSuspension {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        reglaSola();
        seActivaCuandoNoCabe();
        noSeActivaSiNoHayBloqueados();
        noSeActivaSiSuspenderNoAlcanza();
        seApagaCuandoYaNoHaceFalta();
        desactivadoNoDispara();

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
        } else {
            System.out.println("HAY " + fallas + " FALLAS de " + verificaciones + " verificaciones");
            System.exit(1);
        }
    }

    private static PCB p(String nombre, int memoria) {
        return ProcessFactory.createCpuBound(nombre, 1, memoria, 50, 500, 1, 0);
    }

    private static RAM ramConUsado(int total, int usado) {
        RAM r = new RAM(total);
        if (usado > 0) {
            r.allocate(usado);
        }
        return r;
    }

    private static void reglaSola() {
        titulo("La regla sola");
        RAM ram = ramConUsado(100, 80);   // 20 libres
        check(!SuspensionTrigger.mustSuspend(null, ram, 2, 40), "sin proceso esperando no hay que suspender");
        check(!SuspensionTrigger.mustSuspend(p("A", 10), ram, 2, 40), "si cabe en lo libre (10 de 20) no hay que suspender");
        check(!SuspensionTrigger.mustSuspend(p("A", 20), ram, 2, 40), "si cabe justo (20 de 20) no hay que suspender");
        check(SuspensionTrigger.mustSuspend(p("A", 21), ram, 2, 40), "no cabe (21 de 20) y los bloqueados liberan 40: hay que suspender");
        check(SuspensionTrigger.mustSuspend(p("A", 60), ram, 2, 40), "libres 20 + bloqueados 40 = 60 alcanza justo");
        check(!SuspensionTrigger.mustSuspend(p("A", 61), ram, 2, 40), "libres 20 + bloqueados 40 = 60 no alcanza para 61");
        check(!SuspensionTrigger.mustSuspend(p("A", 30), ram, 0, 0), "sin bloqueados no hay a quien suspender");
        check(!SuspensionTrigger.mustSuspend(p("A", 101), ram, 2, 40), "mas grande que toda la RAM: suspender no sirve");
        check(SuspensionTrigger.mustSuspend(p("A", 100), ram, 2, 80), "igual a toda la RAM y los bloqueados liberan lo suficiente");
        check(!SuspensionTrigger.mustSuspend(p("A", 30), null, 2, 40), "sin RAM se rechaza");
    }

    // ------------------------------------------------------------------ con un computador

    private static Cluster cluster(int ram) {
        Cluster c = new Cluster(10, 0);
        c.addComputer("PC1", new FCFSPolicy(), ram);
        return c;
    }

    /** Proceso que se bloquea 'espera' ciclos tras cada instruccion (ProcesoBloqueante, solo para pruebas). */
    private static PCB conEs(Cluster c, String nombre, int memoria, int espera, int instrucciones) {
        PCB x = ProcesoBloqueante.crear(c, 1, nombre, 1, memoria, instrucciones, 1, espera, 1000, c.getCurrentCycle());
        c.submit(x);
        return x;
    }

    private static PCB cpu(Cluster c, String nombre, int memoria, int ciclos) {
        PCB x = ProcessFactory.createCpuBound(nombre, 1, memoria, ciclos, 1000, 1, c.getCurrentCycle());
        c.submit(x);
        return x;
    }

    private static boolean hasta(Cluster c, PCB x, ProcessState s, int max) {
        for (int i = 0; i < max && x.getState() != s; i++) {
            c.tick();
        }
        return x.getState() == s;
    }

    private static void seActivaCuandoNoCabe() {
        titulo("Se activa cuando el nuevo no cabe y hay un bloqueado");
        Cluster c = cluster(30);
        Computer pc = c.getComputer(1);
        MediumTermScheduler m = pc.getMediumTermScheduler();
        PCB a = conEs(c, "A", 20, 200, 100);
        check(hasta(c, a, ProcessState.BLOCKED, 20), "A (20 de 30) se bloquea por E/S");
        check(!m.isTriggerPending(), "con A bloqueado y nadie esperando no hay disparo");
        PCB b = cpu(c, "B", 20, 5);
        c.tick();
        check(b.getState() == ProcessState.NEW, "B (20) no cabe: quedan 10 libres");
        check(m.isTriggerPending() && m.getTriggerProcess() == b, "el disparo se activa y senala a B");
        check(m.getTriggerCount() == 1, "se cuenta una vez");
        for (int i = 0; i < 5; i++) {
            c.tick();
        }
        check(m.isTriggerPending() && m.getTriggerCount() == 1, "sigue pendiente pero no se vuelve a contar");
        check(pc.getBlockedSuspendedSnapshot().isEmpty() && a.getState() == ProcessState.BLOCKED,
                "S05 solo dispara: todavia no suspende a nadie (eso es S06/S07)");
    }

    private static void noSeActivaSiNoHayBloqueados() {
        titulo("No se activa si no hay bloqueados");
        Cluster c = cluster(30);
        MediumTermScheduler m = c.getComputer(1).getMediumTermScheduler();
        PCB a = cpu(c, "A", 25, 500);
        PCB b = cpu(c, "B", 20, 5);
        for (int i = 0; i < 5; i++) {
            c.tick();
        }
        check(b.getState() == ProcessState.NEW, "B espera porque A ocupa 25 de 30");
        check(!m.isTriggerPending() && m.getTriggerCount() == 0,
                "A esta Listo/Ejecutando, no Bloqueado: no se suspende (solo se suspenden Bloqueados)");
    }

    private static void noSeActivaSiSuspenderNoAlcanza() {
        titulo("No se activa si suspender no alcanzaria");
        Cluster c = cluster(50);
        MediumTermScheduler m = c.getComputer(1).getMediumTermScheduler();
        PCB e = conEs(c, "E", 5, 300, 100);
        check(hasta(c, e, ProcessState.BLOCKED, 30), "E (5) se bloquea");
        PCB grande = cpu(c, "Grande", 40, 500);
        PCB nuevo = cpu(c, "Nuevo", 30, 5);
        for (int i = 0; i < 3; i++) {
            c.tick();
        }
        check(nuevo.getState() == ProcessState.NEW, "Nuevo (30) espera: hay 5 libres");
        check(!m.isTriggerPending() && m.getTriggerCount() == 0,
                "5 libres + 5 del bloqueado = 10 < 30: no se gasta E/S de disco en vano");
        check(grande.getState() != ProcessState.TERMINATED, "el proceso grande sigue vivo");
    }

    private static void seApagaCuandoYaNoHaceFalta() {
        titulo("Se apaga cuando deja de hacer falta");
        Cluster c = cluster(30);
        Computer pc = c.getComputer(1);
        MediumTermScheduler m = pc.getMediumTermScheduler();
        PCB a = conEs(c, "A", 20, 6, 2);
        check(hasta(c, a, ProcessState.BLOCKED, 20), "A se bloquea 6 ciclos");
        PCB b = cpu(c, "B", 20, 3);
        c.tick();
        check(m.isTriggerPending(), "B no cabe: disparo activo");
        for (int i = 0; i < 100 && b.getState() == ProcessState.NEW; i++) {
            c.tick();
        }
        check(b.getState() != ProcessState.NEW, "al terminar A y liberar su memoria, B entra");
        check(!m.isTriggerPending() && m.getTriggerProcess() == null, "el disparo se apaga");
        check(m.getTriggerCount() >= 1, "el conteo conserva que hubo un disparo");
    }

    private static void desactivadoNoDispara() {
        titulo("Desactivado no dispara");
        Cluster c = cluster(30);
        MediumTermScheduler m = c.getComputer(1).getMediumTermScheduler();
        PCB a = conEs(c, "A", 20, 200, 100);
        hasta(c, a, ProcessState.BLOCKED, 20);
        m.setEnabled(false);
        cpu(c, "B", 20, 5);
        for (int i = 0; i < 3; i++) {
            c.tick();
        }
        check(!m.isTriggerPending() && m.getTriggerCount() == 0, "con el planificador desactivado no hay disparo");
        m.setEnabled(true);
        c.tick();
        check(m.isTriggerPending() && m.getTriggerCount() == 1, "al reactivarlo el disparo se activa");
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