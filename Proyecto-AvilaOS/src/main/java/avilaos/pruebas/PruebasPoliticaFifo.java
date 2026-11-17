/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.datastructures.DynamicArray;
import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.HeadDirection;
import avilaos.enums.IOOperationType;
import avilaos.enums.ProcessType;
import avilaos.model.process.PCB;
import avilaos.os.disk.DiskRequest;
import avilaos.os.disk.policy.FifoPolicy;
import avilaos.os.disk.policy.PolicyRun;
import avilaos.os.disk.policy.PolicySimulator;

/**
 * Pruebas de la politica FIFO (D10) con el caso P1 y el anexo del enunciado.
 * Termina con "TODAS LAS PRUEBAS PASARON" o con la lista de fallas (codigo de salida 1).
 */
public class PruebasPoliticaFifo {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        tipo();
        casoP1();
        anexo();
        ignoraElCabezal();
        mismoBloqueRepetido();
        colaVaciaYEntradasInvalidas();
        noModificaLaLista();

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
        } else {
            System.out.println("HAY " + fallas + " FALLAS de " + verificaciones + " verificaciones");
            System.exit(1);
        }
    }

    private static void check(boolean ok, String texto) {
        verificaciones++;
        System.out.println((ok ? "  OK    " : "  FALLA ") + texto);
        if (!ok) {
            fallas++;
        }
    }

    private static void titulo(String texto) {
        System.out.println();
        System.out.println("== " + texto);
    }

    private static boolean falla(Runnable accion) {
        try {
            accion.run();
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static boolean igual(int[] a, int[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    private static String texto(int[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            sb.append(i == 0 ? "" : ", ").append(a[i]);
        }
        return sb.toString();
    }

    private static DynamicArray<DiskRequest> cola(int... bloques) {
        PCB p = new PCB("P", ProcessType.CPU_BOUND, 1, 8, 2, 50, 0, 0);
        DynamicArray<DiskRequest> c = new DynamicArray<>();
        for (int i = 0; i < bloques.length; i++) {
            c.add(new DiskRequest(bloques[i], IOOperationType.READ, p, i, 1));
        }
        return c;
    }

    private static void tipo() {
        titulo("D10 - la politica se identifica como FIFO");
        check(new FifoPolicy().getType() == DiskSchedulingPolicy.FIFO, "getType() = FIFO");
    }

    private static void casoP1() {
        titulo("D10 - caso P1 del enunciado: cabezal en 50");
        PolicyRun r = PolicySimulator.run(new FifoPolicy(), cola(95, 180, 34, 119, 11, 123, 62, 64), 50, HeadDirection.UP, 200);
        int[] esperado = {95, 180, 34, 119, 11, 123, 62, 64};
        check(igual(r.blocks(), esperado), "orden 95 -> 180 -> 34 -> 119 -> 11 -> 123 -> 62 -> 64 (dio " + texto(r.blocks()) + ")");
        check(r.getTotalTravel() == 644, "desplazamiento total 644 bloques (dio " + r.getTotalTravel() + ")");
        check(r.getFinalHead() == 64, "el cabezal termina en el 64");
    }

    private static void anexo() {
        titulo("D10 - ejemplo del anexo: cabezal en 50, solicitudes 82, 17, 43, 140, 24, 16, 190");
        PolicyRun r = PolicySimulator.run(new FifoPolicy(), cola(82, 17, 43, 140, 24, 16, 190), 50, HeadDirection.UP, 200);
        check(igual(r.blocks(), new int[]{82, 17, 43, 140, 24, 16, 190}), "orden de llegada (dio " + texto(r.blocks()) + ")");
        check(r.getTotalTravel() == 518, "desplazamiento total 518 bloques (dio " + r.getTotalTravel() + ")");
    }

    private static void ignoraElCabezal() {
        titulo("D10 - no mira donde esta el cabezal ni hacia donde va");
        FifoPolicy f = new FifoPolicy();
        DynamicArray<DiskRequest> c = cola(10, 190, 100);
        check(f.selectNext(c, 0, HeadDirection.UP) == 0, "cabezal en 0: elige la primera");
        check(f.selectNext(c, 199, HeadDirection.DOWN) == 0, "cabezal en 199: elige la primera");
        check(f.selectNext(c, 100, HeadDirection.UP) == 0, "cabezal en 100: elige la primera aunque la ultima este encima");
    }

    private static void mismoBloqueRepetido() {
        titulo("D10 - dos solicitudes al mismo bloque se atienden en el orden de llegada");
        DynamicArray<DiskRequest> c = cola(30, 30, 30);
        PolicyRun r = PolicySimulator.run(new FifoPolicy(), c, 50, HeadDirection.UP, 200);
        check(r.get(0) == c.get(0) && r.get(1) == c.get(1) && r.get(2) == c.get(2), "salen en el mismo orden en que entraron");
        check(r.getTotalTravel() == 20, "50 -> 30 son 20 bloques y las otras dos no mueven el cabezal");
    }

    private static void colaVaciaYEntradasInvalidas() {
        titulo("D10 - cola vacia y entradas invalidas");
        FifoPolicy f = new FifoPolicy();
        check(f.selectNext(new DynamicArray<DiskRequest>(), 50, HeadDirection.UP) == -1, "sin solicitudes devuelve -1");
        PolicyRun vacio = PolicySimulator.run(f, new DynamicArray<DiskRequest>(), 50, HeadDirection.UP, 200);
        check(vacio.size() == 0 && vacio.getTotalTravel() == 0 && vacio.getFinalHead() == 50, "simular una cola vacia no mueve el cabezal");
        check(falla(() -> f.selectNext(null, 50, HeadDirection.UP)), "lista nula se rechaza");
        check(falla(() -> PolicySimulator.run(f, cola(10), 200, HeadDirection.UP, 200)), "cabezal fuera del disco se rechaza");
        check(falla(() -> PolicySimulator.run(f, cola(250), 50, HeadDirection.UP, 200)), "bloque fuera del disco se rechaza");
        check(falla(() -> PolicySimulator.run(null, cola(10), 50, HeadDirection.UP, 200)), "politica nula se rechaza");
    }

    private static void noModificaLaLista() {
        titulo("D10 - elegir no modifica la lista");
        DynamicArray<DiskRequest> c = cola(95, 180, 34);
        DiskRequest primera = c.get(0);
        new FifoPolicy().selectNext(c, 50, HeadDirection.UP);
        PolicySimulator.run(new FifoPolicy(), c, 50, HeadDirection.UP, 200);
        check(c.size() == 3 && c.get(0) == primera, "la lista sigue con sus 3 solicitudes en el mismo orden");
    }
}