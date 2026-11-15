/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.datastructures.BlockChain;
import avilaos.enums.BlockUse;
import avilaos.model.hardware.BlockInfo;
import avilaos.model.hardware.BlockMap;
import avilaos.os.filesystem.ChainAllocator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pruebas de la asignacion encadenada de bloques (D05).
 * Termina con "TODAS LAS PRUEBAS PASARON" o con la lista de fallas (codigo de salida 1).
 */
public class PruebasAsignacionEncadenada {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) throws Exception {
        reservaEnlazada();
        liberarDejaElMapaIgual();
        archivoQueNoCabe();
        huecosEnElDisco();
        zonaDeArchivos();
        entradasInvalidas();
        recorrerCadena();
        hilosReservando();

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

    private static boolean mapaVacio(BlockMap mapa) {
        return mapa.freeCount() == mapa.size();
    }

    private static void reservaEnlazada() {
        titulo("D05 - reservar n bloques enlazados");
        BlockMap mapa = new BlockMap(200);
        ChainAllocator asignador = new ChainAllocator(mapa);
        BlockChain cadena = asignador.allocate(5, "/usuarios/notas.txt");
        check(cadena != null && cadena.size() == 5, "reserva 5 bloques");
        check(cadena.first() == 0, "el primero es el bloque 0 (primer libre)");
        check(mapa.usedCount() == 5, "quedan 5 bloques ocupados");
        boolean enlazados = true;
        for (int i = 0; i < 4; i++) {
            if (mapa.get(cadena.get(i)).getNext() != cadena.get(i + 1)) {
                enlazados = false;
            }
        }
        check(enlazados, "cada bloque apunta al siguiente");
        check(mapa.get(cadena.get(4)).getNext() == BlockInfo.NO_NEXT, "el ultimo no tiene siguiente");
        boolean dueno = true;
        for (int i = 0; i < 5; i++) {
            BlockInfo info = mapa.get(cadena.get(i));
            if (info.getUse() != BlockUse.FILE || !"/usuarios/notas.txt".equals(info.getOwner())) {
                dueno = false;
            }
        }
        check(dueno, "todos son del archivo y llevan su ruta como dueno");
        BlockChain otra = asignador.allocate(3, "/b");
        check(otra.first() == 5, "el segundo archivo sigue donde termino el primero");
        check(asignador.allocate(1, "/c").size() == 1, "un archivo de 1 bloque tambien");
    }

    private static void liberarDejaElMapaIgual() {
        titulo("D05 - liberar recorriendo la cadena");
        BlockMap mapa = new BlockMap(200);
        ChainAllocator asignador = new ChainAllocator(mapa);
        BlockChain cadena = asignador.allocate(5, "/a");
        check(asignador.free(cadena.first()) == 5, "libera los 5 bloques");
        check(mapaVacio(mapa), "el mapa queda identico al inicial");
        boolean limpio = true;
        for (int i = 0; i < 5; i++) {
            BlockInfo info = mapa.get(i);
            if (!info.isFree() || info.getOwner() != null || info.getNext() != BlockInfo.NO_NEXT) {
                limpio = false;
            }
        }
        check(limpio, "sin dueno ni siguiente en los bloques liberados");
        check(asignador.free(0) == 0, "liberar una cadena ya libre no hace nada");
        BlockChain a = asignador.allocate(3, "/a");
        BlockChain b = asignador.allocate(3, "/b");
        asignador.free(a.first());
        check(mapa.usedCount() == 3 && !mapa.isFree(b.first()), "liberar un archivo no toca al otro");
    }

    private static void archivoQueNoCabe() {
        titulo("D05 - sin bloques suficientes no cambia nada");
        BlockMap mapa = new BlockMap(100);
        ChainAllocator asignador = new ChainAllocator(mapa);
        asignador.allocate(95, "/grande");
        BlockInfo[] antes = mapa.snapshot();
        check(asignador.allocate(10, "/otro") == null, "pedir 10 con 5 libres devuelve null");
        BlockInfo[] despues = mapa.snapshot();
        boolean igual = true;
        for (int i = 0; i < antes.length; i++) {
            if (antes[i].getUse() != despues[i].getUse() || antes[i].getNext() != despues[i].getNext()) {
                igual = false;
            }
        }
        check(igual, "el disco queda exactamente igual");
        check(asignador.allocate(5, "/justo") != null && mapa.freeCount() == 0, "pedir justo lo que queda funciona");
        check(asignador.allocate(1, "/mas") == null, "disco lleno rechaza");
    }

    private static void huecosEnElDisco() {
        titulo("D05 - usa los huecos y queda encadenado");
        BlockMap mapa = new BlockMap(20);
        ChainAllocator asignador = new ChainAllocator(mapa);
        BlockChain a = asignador.allocate(4, "/a");
        BlockChain b = asignador.allocate(4, "/b");
        BlockChain c = asignador.allocate(4, "/c");
        asignador.free(a.first());
        asignador.free(c.first());
        BlockChain d = asignador.allocate(6, "/d");
        check(d.get(0) == 0 && d.get(3) == 3 && d.get(4) == 8 && d.get(5) == 9, "usa el hueco 0-3 y sigue en 8, 9");
        check(mapa.get(3).getNext() == 8, "el enlace salta de 3 a 8");
        check(asignador.chainOf(d.first()).size() == 6, "recorrer desde el primero encuentra los 6");
        check(asignador.chainOf(b.first()).size() == 4, "el archivo del medio sigue entero");
    }

    private static void zonaDeArchivos() {
        titulo("D05 - la zona de archivos no invade el swap");
        BlockMap mapa = new BlockMap(300);
        ChainAllocator asignador = new ChainAllocator(mapa, 200);
        check(asignador.freeBlocks() == 200, "200 bloques libres para archivos");
        check(asignador.allocate(200, "/todo") != null, "se pueden llenar los 200");
        check(asignador.allocate(1, "/mas") == null, "no pasa a la zona de swap");
        check(mapa.isFree(200) && mapa.isFree(299), "los bloques 200 a 299 siguen libres");
        check(asignador.freeBlocks() == 0, "freeBlocks cuenta solo la zona de archivos");
    }

    private static void entradasInvalidas() {
        titulo("D05 - entradas invalidas");
        BlockMap mapa = new BlockMap(10);
        ChainAllocator asignador = new ChainAllocator(mapa);
        check(falla(() -> asignador.allocate(0, "/a")), "0 bloques");
        check(falla(() -> asignador.allocate(-2, "/a")), "bloques negativos");
        check(falla(() -> asignador.allocate(2, " ")), "dueno vacio");
        check(mapaVacio(mapa), "los rechazos no ocupan nada");
        check(falla(() -> new ChainAllocator(null)), "sin mapa");
        check(falla(() -> new ChainAllocator(mapa, 0)), "zona de 0 bloques");
        check(falla(() -> new ChainAllocator(mapa, 11)), "zona mayor que el disco");
    }

    private static void recorrerCadena() {
        titulo("D05 - recorrer una cadena");
        BlockMap mapa = new BlockMap(50);
        ChainAllocator asignador = new ChainAllocator(mapa);
        BlockChain cadena = asignador.allocate(7, "/a");
        BlockChain leida = asignador.chainOf(cadena.first());
        check(leida.size() == 7 && leida.get(0) == 0 && leida.get(6) == 6, "chainOf devuelve los 7 bloques en orden");
        check(asignador.chainOf(30).isEmpty(), "chainOf de un bloque libre es vacia");
    }

    private static void hilosReservando() throws Exception {
        titulo("D05 - varios hilos reservando a la vez");
        final BlockMap mapa = new BlockMap(200);
        final ChainAllocator asignador = new ChainAllocator(mapa);
        final AtomicInteger concedidos = new AtomicInteger();
        final BlockChain[] cadenas = new BlockChain[40];
        Thread[] hilos = new Thread[4];
        for (int h = 0; h < hilos.length; h++) {
            final int numero = h;
            hilos[h] = new Thread(() -> {
                for (int i = 0; i < 10; i++) {
                    BlockChain c = asignador.allocate(5, "/h" + numero + "/f" + i);
                    if (c != null) {
                        cadenas[numero * 10 + i] = c;
                        concedidos.incrementAndGet();
                    }
                }
            });
        }
        for (Thread t : hilos) {
            t.start();
        }
        for (Thread t : hilos) {
            t.join();
        }
        check(concedidos.get() == 40, "los 40 archivos de 5 bloques caben en 200 y se conceden todos");
        check(mapa.usedCount() == 200, "el disco queda lleno, sin bloques repetidos");
        boolean duenos = true;
        for (int i = 0; i < 40; i++) {
            for (int j = 0; j < cadenas[i].size(); j++) {
                String esperado = "/h" + (i / 10) + "/f" + (i % 10);
                if (!esperado.equals(mapa.get(cadenas[i].get(j)).getOwner())) {
                    duenos = false;
                }
            }
        }
        check(duenos, "cada bloque pertenece al archivo que lo reservo");
    }
}