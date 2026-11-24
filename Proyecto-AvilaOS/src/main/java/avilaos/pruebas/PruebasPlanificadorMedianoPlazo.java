/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;
import avilaos.os.midterm.MediumTermScheduler;
import avilaos.scheduler.FCFSPolicy;

/**
 * S04: el planificador de mediano plazo como componente del reloj. Comprueba que cada computador
 * tiene el suyo, que atiende un ciclo por tick, que se puede desactivar y reactivar y que no
 * altera el funcionamiento normal del computador.
 * Se ejecuta como programa (main); termina con "TODAS LAS PRUEBAS PASARON" o con codigo 1.
 */
public class PruebasPlanificadorMedianoPlazo {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        existeYEstaActivo();
        atiendeUnCicloPorTick();
        sePuedeDesactivar();
        noAlteraElComputador();
        entradaInvalida();

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
        } else {
            System.out.println("HAY " + fallas + " FALLAS de " + verificaciones + " verificaciones");
            System.exit(1);
        }
    }

    private static Computer computador(Cluster c) {
        c.addComputer("PC1", new FCFSPolicy(), 100);
        return c.getComputer(1);
    }

    private static void existeYEstaActivo() {
        titulo("Existe y esta activo");
        Computer pc = computador(new Cluster(10, 0));
        MediumTermScheduler m = pc.getMediumTermScheduler();
        check(m != null, "el computador tiene planificador de mediano plazo");
        check(m.getComputer() == pc, "esta ligado a su computador");
        check(m.isEnabled(), "empieza activo");
        check(m.getTicksSeen() == 0 && m.getLastCycle() == -1, "todavia no atendio ningun ciclo");
        check(pc.getMediumTermScheduler() == m, "siempre es el mismo objeto");
    }

    private static void atiendeUnCicloPorTick() {
        titulo("Un ciclo por tick del reloj");
        Cluster c = new Cluster(10, 0);
        Computer pc = computador(c);
        MediumTermScheduler m = pc.getMediumTermScheduler();
        for (int i = 0; i < 5; i++) {
            c.tick();
        }
        check(m.getTicksSeen() == 5, "tras 5 ticks atendio 5 ciclos (" + m.getTicksSeen() + ")");
        check(m.getLastCycle() == c.getCurrentCycle(), "su ultimo ciclo es el actual del reloj ("
                + m.getLastCycle() + ")");
    }

    private static void sePuedeDesactivar() {
        titulo("Activar y desactivar");
        Cluster c = new Cluster(10, 0);
        Computer pc = computador(c);
        MediumTermScheduler m = pc.getMediumTermScheduler();
        c.tick();
        m.setEnabled(false);
        c.tick();
        c.tick();
        check(!m.isEnabled() && m.getTicksSeen() == 1, "desactivado no atiende ciclos (sigue en 1)");
        m.setEnabled(true);
        c.tick();
        check(m.getTicksSeen() == 2 && m.getLastCycle() == c.getCurrentCycle(),
                "al reactivarlo vuelve a atender");
    }

    private static void noAlteraElComputador() {
        titulo("No altera el funcionamiento normal");
        Cluster c = new Cluster(10, 0);
        Computer pc = computador(c);
        PCB p = ProcessFactory.createCpuBound("A", 1, 10, 3, 500, 1, c.getCurrentCycle());
        c.submit(p);
        for (int i = 0; i < 20 && p.getState() != avilaos.enums.ProcessState.TERMINATED; i++) {
            c.tick();
        }
        check(p.getState() == avilaos.enums.ProcessState.TERMINATED && !p.isDeadlineMissed(),
                "un proceso normal sigue terminando bien");
        check(pc.getReadySuspendedSnapshot().isEmpty() && pc.getBlockedSuspendedSnapshot().isEmpty(),
                "no suspende nada por ahora");
        check(pc.getRam().getUsedSize() == 0, "la RAM queda libre");
    }

    private static void entradaInvalida() {
        titulo("Entrada invalida");
        try {
            new MediumTermScheduler(null);
            check(false, "un planificador sin computador");
        } catch (IllegalArgumentException e) {
            check(true, "un planificador sin computador se rechaza");
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