/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.enums.HeadDirection;
import avilaos.enums.IOOperationType;
import avilaos.enums.ProcessType;
import avilaos.model.hardware.BlockMap;
import avilaos.model.hardware.Disk;
import avilaos.model.hardware.DiskHead;
import avilaos.model.hardware.GlobalClock;
import avilaos.model.process.PCB;
import avilaos.os.disk.DiskRequest;

/**
 * Pruebas del cabezal del disco y del tiempo de servicio (D07):
 * tiempo = ceil(distancia / k) + bloques transferidos.
 * Termina con "TODAS LAS PRUEBAS PASARON" o con la lista de fallas (codigo de salida 1).
 */
public class PruebasCabezalDisco {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        cabezalBasico();
        formulaDelTiempo();
        movimientoPorCiclo();
        direccionSeActualiza();
        discoAtiendeUnaSolicitud();
        discoConVariasVelocidades();
        casoP1ConElReloj();
        discoOcupado();
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

    private static PCB proceso(String nombre) {
        return new PCB(nombre, ProcessType.CPU_BOUND, 1, 8, 2, 50, 0, 0);
    }

    /** Pone en marcha la solicitud y da ticks hasta que el disco termina; devuelve los ciclos que tardo. */
    private static int atender(Disk disco, DiskRequest r) {
        disco.begin(r, 0);
        int ciclos = 0;
        while (disco.isBusy()) {
            disco.onTick(++ciclos);
        }
        return ciclos;
    }

    private static void cabezalBasico() {
        titulo("D07 - cabezal: posicion, direccion y k");
        DiskHead h = new DiskHead(200, 50);
        check(h.getPosition() == 50, "posicion inicial 50");
        check(h.getDirection() == HeadDirection.UP, "direccion inicial UP");
        check(h.getSpeed() == DiskHead.DEFAULT_SPEED, "k por defecto = " + DiskHead.DEFAULT_SPEED);
        DiskHead h2 = new DiskHead(200, 0, 4, HeadDirection.DOWN);
        check(h2.getSpeed() == 4 && h2.getDirection() == HeadDirection.DOWN, "k=4 y direccion DOWN configurables");
        h2.setSpeed(7);
        check(h2.getSpeed() == 7, "k se puede cambiar");
    }

    private static void formulaDelTiempo() {
        titulo("D07 - tiempo de servicio = ceil(distancia / k) + bloques");
        DiskHead k1 = new DiskHead(200, 50, 1, HeadDirection.UP);
        check(k1.distanceTo(95) == 45 && k1.distanceTo(10) == 40, "distancias 45 y 40");
        check(k1.serviceTime(95, 1) == 46, "k=1, 50->95, 1 bloque: 45 + 1 = 46");
        check(k1.serviceTime(50, 3) == 3, "ya en el bloque: 0 + 3 bloques = 3");
        DiskHead k4 = new DiskHead(200, 50, 4, HeadDirection.UP);
        check(k4.moveCycles(60) == 3, "k=4, distancia 10: ceil(10/4) = 3 ciclos");
        check(k4.moveCycles(58) == 2, "k=4, distancia 8: exactamente 2 ciclos");
        check(k4.moveCycles(51) == 1, "k=4, distancia 1: 1 ciclo");
        check(k4.serviceTime(60, 2) == 5, "k=4, distancia 10, 2 bloques: 3 + 2 = 5");
        check(k4.serviceTime(40, 1) == 4, "hacia atras tambien cuenta: distancia 10 -> 3 + 1 = 4");
        DiskHead k10 = new DiskHead(200, 0, 10, HeadDirection.UP);
        check(k10.moveCycles(199) == 20, "k=10, distancia 199: ceil(19,9) = 20");
    }

    private static void movimientoPorCiclo() {
        titulo("D07 - el cabezal se mueve hasta k posiciones por ciclo sin pasarse");
        DiskHead h = new DiskHead(200, 50, 4, HeadDirection.UP);
        check(h.moveToward(60) == 4 && h.getPosition() == 54, "ciclo 1: 50 -> 54");
        check(h.moveToward(60) == 4 && h.getPosition() == 58, "ciclo 2: 54 -> 58");
        check(h.moveToward(60) == 2 && h.getPosition() == 60, "ciclo 3: 58 -> 60 (solo 2, no se pasa)");
        check(h.moveToward(60) == 0 && h.getPosition() == 60, "ya llego: no se mueve");
        check(h.moveToward(0) == 4 && h.getPosition() == 56, "hacia el 0: 60 -> 56");
    }

    private static void direccionSeActualiza() {
        titulo("D07 - la direccion se actualiza al moverse");
        DiskHead h = new DiskHead(200, 100, 1, HeadDirection.UP);
        h.moveToward(50);
        check(h.getDirection() == HeadDirection.DOWN, "al ir hacia un bloque menor pasa a DOWN");
        h.moveToward(150);
        check(h.getDirection() == HeadDirection.UP, "al ir hacia un bloque mayor pasa a UP");
        DiskHead quieto = new DiskHead(200, 30, 1, HeadDirection.DOWN);
        quieto.moveToward(30);
        check(quieto.getDirection() == HeadDirection.DOWN, "si ya esta en el destino la direccion no cambia");
    }

    private static void discoAtiendeUnaSolicitud() {
        titulo("D07 - el disco gasta exactamente el tiempo de servicio");
        Disk disco = new Disk(new BlockMap(200), new DiskHead(200, 50, 1, HeadDirection.UP));
        int ciclos = atender(disco, new DiskRequest(95, IOOperationType.READ, proceso("P1"), 0, 1));
        check(ciclos == 46, "50 -> 95 con 1 bloque tarda 46 ciclos (tardo " + ciclos + ")");
        check(disco.getHead().getPosition() == 95, "el cabezal quedo en el 95");
        check(disco.getHead().getDirection() == HeadDirection.UP, "direccion UP");
        check(!disco.isBusy() && disco.getCurrent() == null, "el disco quedo libre");
        int sinMover = atender(disco, new DiskRequest(95, IOOperationType.UPDATE, proceso("P2"), 0, 4));
        check(sinMover == 4, "mismo bloque, 4 bloques a transferir: 4 ciclos");
        int atras = atender(disco, new DiskRequest(80, IOOperationType.DELETE, proceso("P3"), 0, 1));
        check(atras == 16 && disco.getHead().getDirection() == HeadDirection.DOWN, "95 -> 80: 15 + 1 = 16 ciclos y direccion DOWN");
    }

    private static void discoConVariasVelocidades() {
        titulo("D07 - con k distinto el mismo viaje tarda distinto");
        int[] ks = {1, 2, 5, 10, 50};
        int[] esperado = {101, 51, 21, 11, 3};
        for (int i = 0; i < ks.length; i++) {
            Disk disco = new Disk(new BlockMap(200), new DiskHead(200, 0, ks[i], HeadDirection.UP));
            int ciclos = atender(disco, new DiskRequest(100, IOOperationType.READ, proceso("P"), 0, 1));
            check(ciclos == esperado[i], "k=" + ks[i] + ": 0 -> 100 + 1 bloque = " + esperado[i] + " ciclos (tardo " + ciclos + ")");
        }
    }

    private static void casoP1ConElReloj() {
        titulo("D07 - caso P1 del enunciado (FIFO, cabezal en 50) con el reloj global");
        int[] bloques = {95, 180, 34, 119, 11, 123, 62, 64};
        BlockMap mapa = new BlockMap(200);
        Disk disco = new Disk(mapa, new DiskHead(200, 50, 1, HeadDirection.UP));
        GlobalClock reloj = new GlobalClock(10);
        reloj.register(disco);
        long[] fin = new long[8];
        int[] antes = new int[8];
        int[] cuenta = {0};
        disco.setListener((r, inicio, f, previo) -> {
            fin[cuenta[0]] = f;
            antes[cuenta[0]] = previo;
            cuenta[0]++;
        });
        PCB p = proceso("P1");
        int siguiente = 0;
        disco.begin(new DiskRequest(bloques[siguiente++], IOOperationType.READ, p, 0, 1), reloj.getCurrentCycle());
        int limite = 2000;
        while ((disco.isBusy() || siguiente < bloques.length) && limite-- > 0) {
            reloj.tick();
            if (!disco.isBusy() && siguiente < bloques.length) {
                disco.begin(new DiskRequest(bloques[siguiente++], IOOperationType.READ, p, 0, 1), reloj.getCurrentCycle());
            }
        }
        int desplazamiento = 0;
        for (int i = 0; i < 8; i++) {
            desplazamiento += Math.abs(bloques[i] - antes[i]);
        }
        check(cuenta[0] == 8, "se atendieron las 8 solicitudes");
        check(desplazamiento == 644, "desplazamiento total 644 bloques (dio " + desplazamiento + ")");
        check(fin[7] == 644 + 8, "el reloj marco 652 ciclos: 644 de movimiento + 8 de transferencia (dio " + fin[7] + ")");
        check(disco.getWorkCycles() == reloj.getCurrentCycle(), "el disco hizo un ciclo de trabajo por cada tick del reloj");
        check(disco.getHead().getPosition() == 64, "el cabezal termino en el 64");
    }

    private static void discoOcupado() {
        titulo("D07 - el disco atiende una sola solicitud a la vez");
        Disk disco = new Disk(new BlockMap(200));
        disco.begin(new DiskRequest(10, IOOperationType.READ, proceso("P1"), 0, 1), 0);
        check(disco.isBusy() && disco.getCurrent().getTargetBlock() == 10, "queda ocupado con la solicitud al bloque 10");
        check(falla(() -> disco.begin(new DiskRequest(20, IOOperationType.READ, proceso("P2"), 0, 1), 0)),
                "una segunda solicitud con el disco ocupado se rechaza");
        Disk libre = new Disk(new BlockMap(200));
        for (int i = 1; i <= 5; i++) {
            libre.onTick(i);
        }
        check(libre.getWorkCycles() == 5 && libre.getHead().getPosition() == 0, "ocioso: cuenta los ciclos y el cabezal no se mueve");
    }

    private static void entradasInvalidas() {
        titulo("D07 - entradas invalidas");
        check(falla(() -> new DiskHead(200, 200)), "posicion inicial fuera del disco (200 en un disco de 200)");
        check(falla(() -> new DiskHead(200, -1)), "posicion inicial negativa");
        check(falla(() -> new DiskHead(200, 10, 0, HeadDirection.UP)), "k = 0");
        check(falla(() -> new DiskHead(200, 10, -2, HeadDirection.UP)), "k negativo");
        check(falla(() -> new DiskHead(200, 10, 1, null)), "sin direccion");
        check(falla(() -> new DiskHead(200, 10).moveToward(200)), "mover hacia un bloque fuera del disco");
        check(falla(() -> new DiskHead(200, 10).serviceTime(20, 0)), "0 bloques a transferir");
        check(falla(() -> new Disk(new BlockMap(100)).begin(new DiskRequest(150, IOOperationType.READ, proceso("P"), 0, 1), 0)),
                "solicitud a un bloque fuera de este disco");
        check(falla(() -> new Disk(new BlockMap(100), null)), "disco sin cabezal");
    }
}