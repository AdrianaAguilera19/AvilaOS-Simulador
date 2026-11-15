/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.enums.BlockUse;
import avilaos.model.hardware.BlockInfo;
import avilaos.model.hardware.BlockMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pruebas del mapa de bloques del disco (D04).
 * Termina con "TODAS LAS PRUEBAS PASARON" o con la lista de fallas (codigo de salida 1).
 */
public class PruebasMapaBloques {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) throws Exception {
        estadoInicial();
        ocuparYLiberar();
        cadenaDeBloques();
        duenos();
        errores();
        snapshotIndependiente();
        accesoConcurrente();

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

    private static void estadoInicial() {
        titulo("D04 - disco nuevo");
        BlockMap mapa = new BlockMap(200);
        check(mapa.size() == 200, "tiene 200 bloques");
        check(mapa.freeCount() == 200 && mapa.usedCount() == 0, "todos libres");
        check(mapa.isFree(0) && mapa.isFree(199), "primero y ultimo libres");
        check(mapa.get(50).getUse() == BlockUse.FREE && mapa.get(50).getOwner() == null, "bloque libre sin dueno");
        check(mapa.get(50).getNext() == BlockInfo.NO_NEXT, "bloque libre sin siguiente");
    }

    private static void ocuparYLiberar() {
        titulo("D04 - ocupar y liberar");
        BlockMap mapa = new BlockMap(200);
        check(mapa.occupy(11, BlockUse.FILE, "/sistema/boot.bin"), "ocupa un bloque libre");
        check(!mapa.isFree(11), "queda ocupado");
        check(mapa.usedCount() == 1 && mapa.freeCount() == 199, "los contadores cambian");
        check(!mapa.occupy(11, BlockUse.FILE, "/otro.txt"), "no se puede ocupar uno ocupado");
        check("/sistema/boot.bin".equals(mapa.get(11).getOwner()), "el dueno no cambia por el intento");
        check(mapa.release(11), "libera el bloque");
        check(mapa.isFree(11) && mapa.get(11).getOwner() == null, "queda libre y sin dueno");
        check(!mapa.release(11), "liberar uno libre devuelve false");
        check(mapa.firstFreeFrom(0) == 0, "primer libre es el 0");
        mapa.occupy(0, BlockUse.FILE, "a");
        mapa.occupy(1, BlockUse.FILE, "a");
        check(mapa.firstFreeFrom(0) == 2, "primer libre salta los ocupados");
        check(mapa.firstFreeFrom(150) == 150, "busca desde una posicion");
        BlockMap chico = new BlockMap(2);
        chico.occupy(0, BlockUse.FILE, "a");
        chico.occupy(1, BlockUse.FILE, "a");
        check(chico.firstFreeFrom(0) == -1, "disco lleno devuelve -1");
    }

    private static void cadenaDeBloques() {
        titulo("D04 - cada bloque guarda el siguiente");
        BlockMap mapa = new BlockMap(200);
        mapa.occupy(34, BlockUse.FILE, "/usuarios/notas.txt");
        mapa.occupy(40, BlockUse.FILE, "/usuarios/notas.txt");
        mapa.occupy(41, BlockUse.FILE, "/usuarios/notas.txt");
        mapa.setNext(34, 40);
        mapa.setNext(40, 41);
        check(mapa.get(34).getNext() == 40, "34 apunta a 40");
        check(mapa.get(40).getNext() == 41, "40 apunta a 41");
        check(mapa.get(41).getNext() == BlockInfo.NO_NEXT, "el ultimo no tiene siguiente");
        mapa.release(40);
        check(mapa.get(40).getNext() == BlockInfo.NO_NEXT, "al liberar se borra el siguiente");
        mapa.occupy(40, BlockUse.FILE, "otro");
        check(mapa.get(40).getNext() == BlockInfo.NO_NEXT, "al ocupar de nuevo empieza sin siguiente");
    }

    private static void duenos() {
        titulo("D04 - dueno archivo o proceso suspendido");
        BlockMap mapa = new BlockMap(200);
        mapa.occupy(5, BlockUse.FILE, "/sistema/boot.bin");
        mapa.occupy(6, BlockUse.SUSPENDED_PROCESS, "P7");
        check(mapa.get(5).getUse() == BlockUse.FILE, "bloque de archivo");
        check(mapa.get(6).getUse() == BlockUse.SUSPENDED_PROCESS, "bloque de proceso suspendido");
        check("P7".equals(mapa.get(6).getOwner()), "dueno del suspendido es P7");
        check(mapa.get(6).toString().contains("P7"), "toString muestra el dueno");
        check(mapa.get(7).toString().contains("libre"), "toString de libre");
    }

    private static void errores() {
        titulo("D04 - entradas invalidas");
        BlockMap mapa = new BlockMap(10);
        check(falla(() -> new BlockMap(0)), "disco de 0 bloques se rechaza");
        check(falla(() -> mapa.isFree(10)), "bloque fuera de rango (alto)");
        check(falla(() -> mapa.isFree(-1)), "bloque fuera de rango (negativo)");
        check(falla(() -> mapa.occupy(1, BlockUse.FREE, "a")), "ocupar con uso FREE se rechaza");
        check(falla(() -> mapa.occupy(1, null, "a")), "ocupar sin uso se rechaza");
        check(falla(() -> mapa.occupy(1, BlockUse.FILE, " ")), "ocupar sin dueno se rechaza");
        check(falla(() -> mapa.setNext(3, 4)), "siguiente de un bloque libre se rechaza");
        mapa.occupy(3, BlockUse.FILE, "a");
        check(falla(() -> mapa.setNext(3, 99)), "siguiente fuera de rango se rechaza");
        check(mapa.usedCount() == 1, "los rechazos no cambian el disco");
    }

    private static void snapshotIndependiente() {
        titulo("D04 - snapshot para la GUI");
        BlockMap mapa = new BlockMap(5);
        mapa.occupy(2, BlockUse.FILE, "a");
        BlockInfo[] foto = mapa.snapshot();
        check(foto.length == 5, "la foto tiene todos los bloques");
        check(!foto[2].isFree() && foto[1].isFree(), "la foto refleja el estado");
        mapa.release(2);
        mapa.occupy(4, BlockUse.FILE, "b");
        check(!foto[2].isFree() && foto[4].isFree(), "la foto no cambia despues");
        check(mapa.snapshot()[4].getOwner().equals("b"), "una foto nueva si ve el cambio");
    }

    private static void accesoConcurrente() throws Exception {
        titulo("D04 - varios hilos a la vez");
        final BlockMap mapa = new BlockMap(500);
        final int hilos = 8;
        final AtomicInteger ganados = new AtomicInteger();
        Thread[] grupo = new Thread[hilos];
        for (int h = 0; h < hilos; h++) {
            final String dueno = "hilo" + h;
            grupo[h] = new Thread(() -> {
                for (int b = 0; b < 500; b++) {
                    if (mapa.occupy(b, BlockUse.FILE, dueno)) {
                        ganados.incrementAndGet();
                    }
                }
            });
        }
        for (Thread t : grupo) {
            t.start();
        }
        for (Thread t : grupo) {
            t.join();
        }
        check(ganados.get() == 500, "cada bloque lo gana un solo hilo (500 en total)");
        check(mapa.usedCount() == 500 && mapa.freeCount() == 0, "el disco queda lleno sin perder cuenta");

        Thread[] liberadores = new Thread[hilos];
        final AtomicInteger liberados = new AtomicInteger();
        for (int h = 0; h < hilos; h++) {
            liberadores[h] = new Thread(() -> {
                for (int b = 0; b < 500; b++) {
                    if (mapa.release(b)) {
                        liberados.incrementAndGet();
                    }
                }
            });
        }
        for (Thread t : liberadores) {
            t.start();
        }
        for (Thread t : liberadores) {
            t.join();
        }
        check(liberados.get() == 500, "cada bloque lo libera un solo hilo");
        check(mapa.freeCount() == 500, "el disco queda vacio");
    }
}
