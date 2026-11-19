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
 * Pruebas de pausar y reanudar el disco a mitad de una solicitud (D16):
 * en pausa no cambia nada y al reanudar continua exactamente donde estaba.
 * Termina con "TODAS LAS PRUEBAS PASARON" o con la lista de fallas (codigo de salida 1).
 */
public class PruebasPausaDisco {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) throws Exception {
        pausaMientrasSeMueveElCabezal();
        pausaMientrasTransfiere();
        pausaEnDireccionDescendente();
        discoLibreEnPausa();
        solicitudIniciadaEnPausa();
        pausaYReanudarRepetidas();
        conElRelojGlobal();
        pausaDesdeOtroHilo();

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

    private static PCB proceso(String nombre) {
        return new PCB(nombre, ProcessType.CPU_BOUND, 1, 8, 2, 50, 0, 0);
    }

    private static Disk disco(int inicio, int k, HeadDirection dir) {
        return new Disk(new BlockMap(200), new DiskHead(200, inicio, k, dir));
    }

    private static DiskRequest solicitud(int bloque, int bloques) {
        return new DiskRequest(bloque, IOOperationType.READ, proceso("P"), 0, bloques);
    }

    /** Guarda lo que el disco avisa al terminar. */
    private static class Aviso implements avilaos.interfaces.DiskListener {
        int veces = 0;
        long inicio = -1;
        long fin = -1;
        int cabezalAntes = -1;

        @Override
        public void onServiceFinished(DiskRequest request, long startCycle, long endCycle, int headBefore) {
            veces++;
            inicio = startCycle;
            fin = endCycle;
            cabezalAntes = headBefore;
        }
    }

    private static void ticks(Disk d, int n) {
        for (int i = 0; i < n; i++) {
            d.onTick(i + 1);
        }
    }

    private static void pausaMientrasSeMueveElCabezal() {
        titulo("Pausa en medio del movimiento del cabezal");
        Disk d = disco(0, 1, HeadDirection.UP);
        Aviso aviso = new Aviso();
        d.setListener(aviso);
        d.begin(solicitud(10, 3), 0);
        ticks(d, 4);
        check(d.getHead().getPosition() == 4, "tras 4 ticks el cabezal esta en 4");
        long trabajo = d.getWorkCycles();
        d.pause();
        check(d.isPaused(), "el disco queda en pausa");
        ticks(d, 50);
        check(d.getHead().getPosition() == 4, "50 ticks en pausa: el cabezal sigue en 4");
        check(d.getHead().getDirection() == HeadDirection.UP, "la direccion no cambia en pausa");
        check(d.getWorkCycles() == trabajo, "los ticks en pausa no cuentan como ciclos de trabajo");
        check(d.isBusy() && d.getCurrent().getTargetBlock() == 10, "la solicitud sigue en servicio");
        check(aviso.veces == 0, "no se avisa ninguna terminacion durante la pausa");
        d.resume();
        check(!d.isPaused(), "al reanudar deja de estar en pausa");
        ticks(d, 8);
        check(d.getHead().getPosition() == 10 && d.isBusy(), "tras 6 de movimiento y 2 de bloque va por el bloque 3");
        ticks(d, 1);
        check(!d.isBusy() && aviso.veces == 1, "termina y avisa una sola vez");
        check(aviso.inicio == 0 && aviso.cabezalAntes == 0, "el aviso conserva el inicio y el cabezal de antes de la pausa");
        check(d.getWorkCycles() == trabajo + 9, "en total 13 ciclos de trabajo: 4 + 9, igual que sin pausa");
    }

    private static void pausaMientrasTransfiere() {
        titulo("Pausa en medio de la transferencia de bloques");
        Disk d = disco(20, 1, HeadDirection.UP);
        Aviso aviso = new Aviso();
        d.setListener(aviso);
        d.begin(solicitud(22, 3), 5);
        ticks(d, 3);                       // 2 de movimiento + 1 bloque
        d.pause();
        ticks(d, 20);
        check(d.getHead().getPosition() == 22 && d.isBusy(), "en pausa el cabezal sigue en 22 y la solicitud pendiente");
        d.resume();
        ticks(d, 1);
        check(d.isBusy(), "con 2 de 3 bloques transferidos aun no termina");
        ticks(d, 1);
        check(!d.isBusy() && aviso.veces == 1, "al 3er bloque termina: la pausa no perdio ni repitio ninguno");
        check(aviso.inicio == 5, "conserva el ciclo en que empezo");
    }

    private static void pausaEnDireccionDescendente() {
        titulo("Pausa con el cabezal bajando");
        Disk d = disco(100, 4, HeadDirection.UP);
        d.begin(solicitud(60, 1), 0);
        ticks(d, 3);
        check(d.getHead().getPosition() == 88 && d.getHead().getDirection() == HeadDirection.DOWN, "baja de 4 en 4: 96, 92, 88");
        d.pause();
        ticks(d, 10);
        d.resume();
        check(d.getHead().getPosition() == 88, "al reanudar sigue en 88");
        ticks(d, 7);
        check(d.getHead().getPosition() == 60 && d.isBusy(), "llega al 60 (28/4 = 7 ciclos de movimiento) y falta el bloque");
        ticks(d, 1);
        check(!d.isBusy(), "termina con el mismo tiempo de servicio que sin pausa");
    }

    private static void discoLibreEnPausa() {
        titulo("Disco libre en pausa");
        Disk d = disco(0, 1, HeadDirection.UP);
        ticks(d, 3);
        d.pause();
        ticks(d, 10);
        check(d.getWorkCycles() == 3, "en pausa, libre, no suma ciclos de trabajo");
        d.resume();
        ticks(d, 2);
        check(d.getWorkCycles() == 5, "al reanudar vuelve a contar");
    }

    private static void solicitudIniciadaEnPausa() {
        titulo("Solicitud iniciada mientras esta en pausa");
        Disk d = disco(0, 1, HeadDirection.UP);
        d.pause();
        d.begin(solicitud(5, 1), 0);
        ticks(d, 10);
        check(d.isBusy() && d.getHead().getPosition() == 0, "queda lista pero no avanza hasta reanudar");
        d.resume();
        ticks(d, 6);
        check(!d.isBusy() && d.getHead().getPosition() == 5, "al reanudar se atiende completa (5 + 1 ciclos)");
    }

    private static void pausaYReanudarRepetidas() {
        titulo("Pausar y reanudar varias veces");
        Disk d = disco(0, 1, HeadDirection.UP);
        Aviso aviso = new Aviso();
        d.setListener(aviso);
        d.begin(solicitud(6, 2), 0);
        d.pause();
        d.pause();
        check(d.isPaused(), "pausar dos veces deja una sola pausa");
        d.resume();
        d.resume();
        check(!d.isPaused(), "reanudar dos veces no rompe nada");
        for (int i = 0; i < 8; i++) {
            ticks(d, 1);
            d.pause();
            ticks(d, 3);
            d.resume();
        }
        check(!d.isBusy() && aviso.veces == 1, "pausando entre cada tick igual termina una sola vez");
        check(d.getWorkCycles() == 8, "con 6 + 2 = 8 ciclos de trabajo exactos");
    }

    private static void conElRelojGlobal() {
        titulo("Con el reloj global");
        GlobalClock reloj = new GlobalClock(1000);
        Disk d = disco(0, 1, HeadDirection.UP);
        reloj.register(d);
        d.begin(solicitud(10, 1), 0);
        for (int i = 0; i < 5; i++) {
            reloj.tick();
        }
        d.pause();
        for (int i = 0; i < 5; i++) {
            reloj.tick();
        }
        check(reloj.getCurrentCycle() == 10, "el reloj sigue contando");
        check(d.getWorkCycles() == 5 && d.getHead().getPosition() == 5, "el disco quedo congelado en el ciclo 5");
        d.resume();
        for (int i = 0; i < 6; i++) {
            reloj.tick();
        }
        check(!d.isBusy() && d.getWorkCycles() == 11, "al reanudar termina: 10 de movimiento + 1 de bloque");
    }

    private static void pausaDesdeOtroHilo() throws Exception {
        titulo("Pausa desde otro hilo mientras el reloj sigue dando ticks");
        final Disk d = disco(0, 1, HeadDirection.UP);
        d.begin(solicitud(199, 5), 0);
        final boolean[] seguir = {true};
        Thread hilo = new Thread(new Runnable() {
            @Override
            public void run() {
                long ciclo = 0;
                while (seguir[0]) {
                    d.onTick(++ciclo);
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        });
        hilo.start();
        Thread.sleep(40);
        d.pause();
        long trabajo = d.getWorkCycles();
        int posicion = d.getHead().getPosition();
        Thread.sleep(80);
        check(d.getWorkCycles() == trabajo && d.getHead().getPosition() == posicion,
                "tras pause() ningun tick cambia el estado, aunque el otro hilo siga");
        d.resume();
        Thread.sleep(40);
        check(d.getWorkCycles() > trabajo, "al reanudar el disco vuelve a avanzar");
        seguir[0] = false;
        hilo.join();
    }
}