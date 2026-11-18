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
import avilaos.interfaces.DiskPolicy;
import avilaos.model.process.PCB;
import avilaos.os.disk.DiskQueue;
import avilaos.os.disk.DiskRequest;
import avilaos.os.disk.DiskScheduler;
import avilaos.os.disk.policy.DiskPolicyFactory;
import avilaos.os.disk.policy.LookPolicy;
import avilaos.os.disk.policy.PolicyRun;
import avilaos.os.disk.policy.PolicySimulator;

/**
 * Pruebas de la politica extra LOOK (D14) con los casos del enunciado y del anexo.
 * Muestra que se agrego sin tocar DiskScheduler: el planificador la usa como a cualquier otra.
 * Termina con "TODAS LAS PRUEBAS PASARON" o con la lista de fallas (codigo de salida 1).
 */
public class PruebasPoliticaLook {

    private static final int[] P = {95, 180, 34, 119, 11, 123, 62, 64};
    private static final int[] A = {82, 17, 43, 140, 24, 16, 190};

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        registroEnElEnum();
        casoP();
        casoAnexo();
        diferenciaConScan();
        direccionHaciaAbajo();
        funcionaEnElPlanificador();
        cambioEnCaliente();
        entradasInvalidas();

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

    private static PCB proceso() {
        return new PCB("P", ProcessType.CPU_BOUND, 1, 8, 2, 50, 0, 0);
    }

    private static DynamicArray<DiskRequest> cola(int... bloques) {
        PCB p = proceso();
        DynamicArray<DiskRequest> c = new DynamicArray<>();
        for (int i = 0; i < bloques.length; i++) {
            c.add(new DiskRequest(bloques[i], IOOperationType.READ, p, i, 1));
        }
        return c;
    }

    private static PolicyRun correr(DiskSchedulingPolicy tipo, int cabezal, HeadDirection dir, int... bloques) {
        return PolicySimulator.run(DiskPolicyFactory.create(tipo), cola(bloques), cabezal, dir, 200);
    }

    private static void registroEnElEnum() {
        titulo("D14 - LOOK esta registrada y se crea desde la fabrica");
        DiskPolicy p = DiskPolicyFactory.create(DiskSchedulingPolicy.LOOK);
        check(p instanceof LookPolicy && p.getType() == DiskSchedulingPolicy.LOOK, "la fabrica crea LookPolicy");
        check(DiskSchedulingPolicy.LOOK.toString().equals("LOOK"), "el texto que ve el selector es «LOOK»");
        check(DiskSchedulingPolicy.desdeTexto("look") == DiskSchedulingPolicy.LOOK, "el JSON puede pedirla con «look»");
        boolean enSelector = false;
        for (DiskSchedulingPolicy d : DiskSchedulingPolicy.values()) {
            if (d == DiskSchedulingPolicy.LOOK) {
                enSelector = true;
            }
        }
        check(enSelector && DiskSchedulingPolicy.values().length == 5, "aparece en values(), que alimenta el selector (5 politicas)");
    }

    private static void casoP() {
        titulo("D14 - caso del enunciado: cabezal en 50 subiendo, solicitudes " + texto(P));
        PolicyRun r = correr(DiskSchedulingPolicy.LOOK, 50, HeadDirection.UP, P);
        int[] orden = {62, 64, 95, 119, 123, 180, 34, 11};
        check(igual(r.blocks(), orden), "orden 62, 64, 95, 119, 123, 180, 34, 11 (dio " + texto(r.blocks()) + ")");
        check(r.getTotalTravel() == 299, "recorrido 130 (hasta el 180) + 169 (de vuelta hasta el 11) = 299 (dio " + r.getTotalTravel() + ")");
        check(r.getFinalHead() == 11 && r.getFinalDirection() == HeadDirection.DOWN, "termina en el 11 bajando");
    }

    private static void casoAnexo() {
        titulo("D14 - anexo: cabezal en 50 subiendo, solicitudes " + texto(A));
        PolicyRun r = correr(DiskSchedulingPolicy.LOOK, 50, HeadDirection.UP, A);
        int[] orden = {82, 140, 190, 43, 24, 17, 16};
        check(igual(r.blocks(), orden), "orden 82, 140, 190, 43, 24, 17, 16 (dio " + texto(r.blocks()) + ")");
        check(r.getTotalTravel() == 314, "recorrido 140 (hasta el 190) + 174 (de vuelta hasta el 16) = 314 (dio " + r.getTotalTravel() + ")");
    }

    private static void diferenciaConScan() {
        titulo("D14 - LOOK da la vuelta en la ultima solicitud, SCAN en el extremo del disco");
        long scanP = correr(DiskSchedulingPolicy.SCAN, 50, HeadDirection.UP, P).getTotalTravel();
        long lookP = correr(DiskSchedulingPolicy.LOOK, 50, HeadDirection.UP, P).getTotalTravel();
        check(scanP == 337 && lookP == 299 && scanP - lookP == 38,
                "con P: SCAN 337, LOOK 299 (se ahorra el tramo 180 a 199 y vuelta: 2 x 19 = 38)");
        long scanA = correr(DiskSchedulingPolicy.SCAN, 50, HeadDirection.UP, A).getTotalTravel();
        long lookA = correr(DiskSchedulingPolicy.LOOK, 50, HeadDirection.UP, A).getTotalTravel();
        check(scanA == 332 && lookA == 314 && scanA - lookA == 18,
                "con el anexo: SCAN 332, LOOK 314 (2 x 9 = 18 menos)");
        PolicyRun sinInvertir = correr(DiskSchedulingPolicy.LOOK, 50, HeadDirection.UP, 60, 90, 120);
        PolicyRun scanSinInvertir = correr(DiskSchedulingPolicy.SCAN, 50, HeadDirection.UP, 60, 90, 120);
        check(sinInvertir.getTotalTravel() == 70 && scanSinInvertir.getTotalTravel() == 70,
                "si no hay que invertir el sentido, LOOK y SCAN recorren lo mismo (70)");
        check(igual(correr(DiskSchedulingPolicy.LOOK, 50, HeadDirection.UP, P).blocks(),
                correr(DiskSchedulingPolicy.SCAN, 50, HeadDirection.UP, P).blocks()),
                "el orden de atencion es el mismo que SCAN");
    }

    private static void direccionHaciaAbajo() {
        titulo("D14 - con el cabezal bajando");
        PolicyRun r = correr(DiskSchedulingPolicy.LOOK, 100, HeadDirection.DOWN, 30, 150, 60, 180, 10);
        int[] orden = {60, 30, 10, 150, 180};
        check(igual(r.blocks(), orden), "baja atendiendo 60, 30, 10 y luego sube a 150, 180 (dio " + texto(r.blocks()) + ")");
        check(r.getTotalTravel() == 90 + 170 && r.getFinalDirection() == HeadDirection.UP,
                "recorrido 90 + 170 = 260 y termina subiendo (dio " + r.getTotalTravel() + ")");
        check(correr(DiskSchedulingPolicy.LOOK, 50, HeadDirection.UP).size() == 0, "sin solicitudes no hay nada que atender");
    }

    private static void funcionaEnElPlanificador() {
        titulo("D14 - DiskScheduler usa LOOK sin saber que existe (solo conoce DiskPolicy)");
        DiskQueue q = new DiskQueue();
        DiskScheduler s = new DiskScheduler(q, DiskPolicyFactory.create(DiskSchedulingPolicy.LOOK), p -> { });
        PCB p = proceso();
        int[] bloques = {95, 34, 119, 62};
        for (int i = 0; i < bloques.length; i++) {
            q.add(new DiskRequest(bloques[i], IOOperationType.READ, p, i, 1));
        }
        int cabezal = 50;
        HeadDirection dir = HeadDirection.UP;
        StringBuilder atendidas = new StringBuilder();
        for (int i = 0; i < bloques.length; i++) {
            DiskRequest r = s.startNext(cabezal, dir);
            atendidas.append(i == 0 ? "" : ",").append(r.getTargetBlock());
            if (r.getTargetBlock() > cabezal) {
                dir = HeadDirection.UP;
            } else if (r.getTargetBlock() < cabezal) {
                dir = HeadDirection.DOWN;
            }
            cabezal = r.getTargetBlock();
            s.completeCurrent();
        }
        check(atendidas.toString().equals("62,95,119,34"), "atiende 62,95,119 subiendo y luego 34 (dio " + atendidas + ")");
        check(esLook(s.getPolicy()), "el planificador reporta LOOK como politica activa");
    }

    private static boolean esLook(DiskPolicy p) {
        return p.getType() == DiskSchedulingPolicy.LOOK;
    }

    private static void cambioEnCaliente() {
        titulo("D14 - cambio de politica en caliente hacia y desde LOOK");
        DiskQueue q = new DiskQueue();
        DiskScheduler s = new DiskScheduler(q, DiskPolicyFactory.create(DiskSchedulingPolicy.FIFO), p -> { });
        PCB p = proceso();
        for (int i = 0; i < 3; i++) {
            q.add(new DiskRequest(60 + i * 10, IOOperationType.READ, p, i, 1));
        }
        s.changePolicy(DiskSchedulingPolicy.LOOK);
        check(s.getPolicy().getType() == DiskSchedulingPolicy.LOOK && q.size() == 3, "cambia a LOOK sin perder solicitudes");
        s.changePolicy(DiskSchedulingPolicy.C_SCAN);
        check(s.getPolicy().getType() == DiskSchedulingPolicy.C_SCAN && q.size() == 3, "y vuelve a C-SCAN sin perder ninguna");
    }

    private static void entradasInvalidas() {
        titulo("D14 - entradas invalidas");
        LookPolicy l = new LookPolicy();
        check(falla(() -> l.selectNext(null, 50, HeadDirection.UP)), "lista nula se rechaza");
        check(falla(() -> l.selectNext(cola(10), 50, null)), "direccion nula se rechaza");
        check(l.selectNext(new DynamicArray<>(), 50, HeadDirection.UP) == -1, "cola vacia devuelve -1");
    }
}