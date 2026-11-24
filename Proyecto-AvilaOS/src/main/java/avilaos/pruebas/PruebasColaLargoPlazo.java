/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.enums.ProcessState;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;
import avilaos.scheduler.FCFSPolicy;

/**
 * S02: la cola de largo plazo (la cola de Nuevos). Un proceso que no cabe en la RAM queda esperando
 * en Nuevos sin ocupar memoria, y se revisa en cada ciclo para admitirlo apenas se libere espacio
 * (al terminar otro proceso o al vencerse su deadline). La admision respeta el orden de llegada.
 * Se ejecuta como programa (main); termina con "TODAS LAS PRUEBAS PASARON" o con codigo 1.
 */
public class PruebasColaLargoPlazo {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        noOcupaRam();
        seAdmiteAlTerminarOtro();
        seAdmiteAlVencerOtro();
        ordenDeLlegada();
        variosEntranAlMismoTiempo();
        masGrandeQueLaRam();
        venceEnNuevos();

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
        } else {
            System.out.println("HAY " + fallas + " FALLAS de " + verificaciones + " verificaciones");
            System.exit(1);
        }
    }

    private static Cluster cluster(int ram) {
        Cluster c = new Cluster(10, 0);
        c.addComputer("PC1", new FCFSPolicy(), ram);
        return c;
    }

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

    private static void hastaQueSalgaDeNuevos(Cluster c, PCB p) {
        for (int i = 0; i < 200 && p.getState() == ProcessState.NEW; i++) {
            c.tick();
        }
    }

    private static void noOcupaRam() {
        titulo("Un Nuevo que no cabe espera sin ocupar RAM");
        Cluster c = cluster(30);
        Computer pc = c.getComputer(1);
        PCB a = proceso(c, "A", 25, 1000, 5000);
        PCB b = proceso(c, "B", 20, 5, 5000);
        ticks(c, 5);
        check(a.getState() != ProcessState.NEW, "A (25 de 30) entra");
        check(b.getState() == ProcessState.NEW, "B (20) no cabe y queda en Nuevos");
        check(pc.getRam().getUsedSize() == 25, "la RAM refleja solo a A (25), no a B (" + pc.getRam().getUsedSize() + ")");
        check(pc.getNewSnapshot().size() == 1 && pc.getNewSnapshot().get(0) == b, "B esta en la cola de Nuevos");
    }

    private static void seAdmiteAlTerminarOtro() {
        titulo("Se admite cuando otro termina y libera memoria");
        Cluster c = cluster(30);
        Computer pc = c.getComputer(1);
        PCB a = proceso(c, "A", 25, 4, 5000);
        PCB b = proceso(c, "B", 20, 3, 5000);
        hastaQueSalgaDeNuevos(c, b);
        check(a.getState() == ProcessState.TERMINATED, "B recien entra cuando A ya termino");
        check(b.getState() != ProcessState.NEW, "B sale de Nuevos");
        check(pc.getRam().getUsedSize() == 20, "la RAM refleja solo a B (20)");
    }

    private static void seAdmiteAlVencerOtro() {
        titulo("Se admite cuando otro vence su deadline");
        Cluster c = cluster(30);
        Computer pc = c.getComputer(1);
        PCB a = proceso(c, "A", 25, 1000, 6);
        PCB b = proceso(c, "B", 20, 3, 5000);
        hastaQueSalgaDeNuevos(c, b);
        check(a.getState() == ProcessState.TERMINATED && a.isDeadlineMissed(), "A termino por deadline");
        check(b.getState() != ProcessState.NEW, "al liberarse la memoria de A, B entra");
        check(pc.getRam().getUsedSize() == 20, "la RAM refleja solo a B (20)");
    }

    private static void ordenDeLlegada() {
        titulo("Se respeta el orden de llegada");
        Cluster c = cluster(30);
        proceso(c, "A", 25, 1000, 5000);
        PCB b = proceso(c, "B", 20, 5, 5000);
        PCB chico = proceso(c, "C", 3, 5, 5000);
        ticks(c, 5);
        check(b.getState() == ProcessState.NEW, "B espera");
        check(chico.getState() == ProcessState.NEW, "C (3) cabria en los 5 libres pero no se cuela delante de B");
    }

    private static void variosEntranAlMismoTiempo() {
        titulo("Al liberarse mucha memoria entran varios");
        Cluster c = cluster(30);
        Computer pc = c.getComputer(1);
        PCB a = proceso(c, "A", 28, 3, 5000);
        PCB b = proceso(c, "B", 10, 50, 5000);
        PCB d = proceso(c, "D", 10, 50, 5000);
        hastaQueSalgaDeNuevos(c, b);
        check(a.getState() == ProcessState.TERMINATED, "A termino");
        check(b.getState() != ProcessState.NEW && d.getState() != ProcessState.NEW, "B y D entran juntos");
        check(pc.getRam().getUsedSize() == 20 && pc.getNewSnapshot().isEmpty(), "RAM en 20 y Nuevos vacia");
    }

    private static void masGrandeQueLaRam() {
        titulo("Un proceso mas grande que toda la RAM se rechaza");
        Cluster c = cluster(30);
        try {
            proceso(c, "Enorme", 31, 5, 5000);
            check(false, "un proceso de 31 en una RAM de 30");
        } catch (IllegalArgumentException e) {
            check(true, "se rechaza al llegar: nunca podria entrar");
        }
        check(c.getComputer(1).getNewSnapshot().isEmpty(), "no queda esperando para siempre en Nuevos");
    }

    private static void venceEnNuevos() {
        titulo("Un Nuevo que vence esperando no toca la RAM");
        Cluster c = cluster(30);
        Computer pc = c.getComputer(1);
        proceso(c, "A", 25, 1000, 5000);
        PCB b = proceso(c, "B", 20, 5, 3);
        ticks(c, 10);
        check(b.getState() == ProcessState.TERMINATED && b.isDeadlineMissed(), "B vence esperando en Nuevos");
        check(pc.getRam().getUsedSize() == 25, "la RAM sigue en 25: no libero memoria que no tenia");
        check(pc.getNewSnapshot().isEmpty(), "ya no esta en Nuevos");
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

