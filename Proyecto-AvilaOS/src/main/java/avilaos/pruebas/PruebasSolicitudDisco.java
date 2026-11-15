/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.enums.DiskSchedulingPolicy;
import avilaos.enums.IOOperationType;
import avilaos.enums.ProcessType;
import avilaos.model.process.PCB;
import avilaos.os.disk.DiskRequest;

/**
 * Pruebas del modelo de solicitud de disco (D02).
 * Termina con "TODAS LAS PRUEBAS PASARON" o con la lista de fallas (codigo de salida 1).
 */
public class PruebasSolicitudDisco {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        solicitudValida();
        solicitudInvalida();
        politicaDeAtencion();

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

    private static PCB proceso(String nombre) {
        return new PCB(nombre, ProcessType.CPU_BOUND, 1, 8, 2, 50, 0, 0);
    }

    // ------------------------------------------------------------------ D02

    private static void solicitudValida() {
        titulo("D02 - solicitud con todos sus datos");
        PCB p4 = proceso("P4");
        DiskRequest r = new DiskRequest(87, IOOperationType.CREATE, p4, 12, 5);
        check(r.getTargetBlock() == 87, "bloque destino 87");
        check(r.getOperation() == IOOperationType.CREATE, "operacion CREATE");
        check(r.getRequester() == p4, "proceso solicitante");
        check(r.getArrivalCycle() == 12, "ciclo de llegada 12");
        check(r.getBlocksToTransfer() == 5, "5 bloques a transferir");
        check(!r.isServed() && r.getServedWith() == null, "al crearla no tiene politica");
        check(r.toString().contains("P4") && r.toString().contains("87"), "toString legible");
        check(new DiskRequest(0, IOOperationType.SUSPEND, p4, 0, 16).getBlocksToTransfer() == 16, "acepta SUSPEND y bloque 0");
    }

    private static void solicitudInvalida() {
        titulo("D02 - datos invalidos se rechazan");
        PCB p = proceso("P1");
        check(falla(() -> new DiskRequest(-1, IOOperationType.READ, p, 0, 1)), "bloque negativo");
        check(falla(() -> new DiskRequest(5, null, p, 0, 1)), "sin operacion");
        check(falla(() -> new DiskRequest(5, IOOperationType.READ, null, 0, 1)), "sin proceso");
        check(falla(() -> new DiskRequest(5, IOOperationType.READ, p, -1, 1)), "llegada negativa");
        check(falla(() -> new DiskRequest(5, IOOperationType.READ, p, 0, 0)), "0 bloques a transferir");
        check(falla(() -> new DiskRequest(5, IOOperationType.READ, p, 0, -3)), "bloques negativos");
    }

    private static void politicaDeAtencion() {
        titulo("D02 - politica con la que se atendio");
        DiskRequest r = new DiskRequest(10, IOOperationType.READ, proceso("P2"), 0, 1);
        r.markServed(DiskSchedulingPolicy.SSTF);
        check(r.isServed() && r.getServedWith() == DiskSchedulingPolicy.SSTF, "queda anotada SSTF");
        check(falla(() -> r.markServed(DiskSchedulingPolicy.FIFO)), "no se puede anotar dos veces");
        check(r.getServedWith() == DiskSchedulingPolicy.SSTF, "la politica no cambia por el intento");
        DiskRequest otra = new DiskRequest(11, IOOperationType.READ, proceso("P3"), 0, 1);
        check(falla(() -> otra.markServed(null)), "politica vacia se rechaza");
        check(!otra.isServed(), "sigue sin atender tras el rechazo");
    }
}