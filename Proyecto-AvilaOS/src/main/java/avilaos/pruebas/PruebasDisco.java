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
import avilaos.os.disk.policy.DiskPolicyFactory;
import avilaos.os.disk.policy.PolicyRun;
import avilaos.os.disk.policy.PolicySimulator;

/**
 * Pruebas del disco con los casos del enunciado (B11). Disco de 200 bloques (0 a 199), cabezal en el
 * bloque 50 subiendo.
 * Casos P1 a P4: solicitudes 95, 180, 34, 119, 11, 123, 62, 64 con FIFO, SSTF, SCAN y C-SCAN.
 * Casos A1 a A4 (anexo): solicitudes 82, 17, 43, 140, 24, 16, 190 con las mismas cuatro politicas.
 * Imprime el desplazamiento total de cada caso (644/236/337/183 y 518/208/332/192).
 * Termina con "TODAS LAS PRUEBAS PASARON" o con la lista de fallas (codigo de salida 1).
 */
public class PruebasDisco {

    private static final int TOTAL = 200;
    private static final int CABEZAL = 50;
    private static final int[] SOLICITUDES_P = {95, 180, 34, 119, 11, 123, 62, 64};
    private static final int[] SOLICITUDES_A = {82, 17, 43, 140, 24, 16, 190};

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        caso("P1", DiskSchedulingPolicy.FIFO, SOLICITUDES_P, new int[] {95, 180, 34, 119, 11, 123, 62, 64}, 644);
        caso("P2", DiskSchedulingPolicy.SSTF, SOLICITUDES_P, new int[] {62, 64, 34, 11, 95, 119, 123, 180}, 236);
        caso("P3", DiskSchedulingPolicy.SCAN, SOLICITUDES_P, new int[] {62, 64, 95, 119, 123, 180, 34, 11}, 337);
        caso("P4", DiskSchedulingPolicy.C_SCAN, SOLICITUDES_P, new int[] {62, 64, 95, 119, 123, 180, 11, 34}, 183);
        caso("A1", DiskSchedulingPolicy.FIFO, SOLICITUDES_A, new int[] {82, 17, 43, 140, 24, 16, 190}, 518);
        caso("A2", DiskSchedulingPolicy.SSTF, SOLICITUDES_A, new int[] {43, 24, 17, 16, 82, 140, 190}, 208);
        caso("A3", DiskSchedulingPolicy.SCAN, SOLICITUDES_A, new int[] {82, 140, 190, 43, 24, 17, 16}, 332);
        caso("A4", DiskSchedulingPolicy.C_SCAN, SOLICITUDES_A, new int[] {82, 140, 190, 16, 17, 24, 43}, 192);
        propiedades();

        System.out.println();
        System.out.println("Resumen (desplazamiento total, cabezal en 50):");
        System.out.println("  P1-P4: " + total(DiskSchedulingPolicy.FIFO, SOLICITUDES_P) + " / "
                + total(DiskSchedulingPolicy.SSTF, SOLICITUDES_P) + " / "
                + total(DiskSchedulingPolicy.SCAN, SOLICITUDES_P) + " / "
                + total(DiskSchedulingPolicy.C_SCAN, SOLICITUDES_P) + "   (FIFO/SSTF/SCAN/C-SCAN)");
        System.out.println("  A1-A4: " + total(DiskSchedulingPolicy.FIFO, SOLICITUDES_A) + " / "
                + total(DiskSchedulingPolicy.SSTF, SOLICITUDES_A) + " / "
                + total(DiskSchedulingPolicy.SCAN, SOLICITUDES_A) + " / "
                + total(DiskSchedulingPolicy.C_SCAN, SOLICITUDES_A) + "   (FIFO/SSTF/SCAN/C-SCAN)");
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

    private static DynamicArray<DiskRequest> cola(int[] bloques) {
        PCB p = new PCB("P", ProcessType.CPU_BOUND, 1, 8, 2, 50, 0, 0);
        DynamicArray<DiskRequest> c = new DynamicArray<>();
        for (int i = 0; i < bloques.length; i++) {
            c.add(new DiskRequest(bloques[i], IOOperationType.READ, p, i, 1));
        }
        return c;
    }

    private static PolicyRun correr(DiskSchedulingPolicy tipo, int[] solicitudes) {
        return PolicySimulator.run(DiskPolicyFactory.create(tipo), cola(solicitudes), CABEZAL, HeadDirection.UP, TOTAL);
    }

    private static long total(DiskSchedulingPolicy tipo, int[] solicitudes) {
        return correr(tipo, solicitudes).getTotalTravel();
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

    private static void caso(String nombre, DiskSchedulingPolicy tipo, int[] solicitudes, int[] orden, long esperado) {
        titulo(nombre + " - " + tipo + ", cabezal en " + CABEZAL + ", solicitudes " + texto(solicitudes));
        PolicyRun r = correr(tipo, solicitudes);
        check(igual(r.blocks(), orden), "orden de atencion " + texto(orden) + " (dio " + texto(r.blocks()) + ")");
        check(r.getTotalTravel() == esperado,
                "desplazamiento total " + esperado + " bloques (dio " + r.getTotalTravel() + ")");
        check(r.size() == solicitudes.length, "se atienden las " + solicitudes.length + " solicitudes, ninguna se pierde");
    }

    private static void propiedades() {
        titulo("Relaciones entre las politicas");
        check(total(DiskSchedulingPolicy.SSTF, SOLICITUDES_P) < total(DiskSchedulingPolicy.FIFO, SOLICITUDES_P),
                "P: SSTF recorre menos que FIFO");
        check(total(DiskSchedulingPolicy.C_SCAN, SOLICITUDES_P) < total(DiskSchedulingPolicy.SCAN, SOLICITUDES_P),
                "P: C-SCAN recorre menos que SCAN");
        check(total(DiskSchedulingPolicy.SSTF, SOLICITUDES_A) < total(DiskSchedulingPolicy.FIFO, SOLICITUDES_A),
                "A: SSTF recorre menos que FIFO");
        check(total(DiskSchedulingPolicy.C_SCAN, SOLICITUDES_A) < total(DiskSchedulingPolicy.SCAN, SOLICITUDES_A),
                "A: C-SCAN recorre menos que SCAN");
        DynamicArray<DiskRequest> original = cola(SOLICITUDES_P);
        PolicySimulator.run(DiskPolicyFactory.create(DiskSchedulingPolicy.SCAN), original, CABEZAL, HeadDirection.UP, TOTAL);
        check(original.size() == SOLICITUDES_P.length && original.get(0).getTargetBlock() == 95,
                "simular una politica no modifica la cola original");
    }
}