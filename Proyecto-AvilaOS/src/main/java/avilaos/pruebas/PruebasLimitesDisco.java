/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.model.hardware.BlockInfo;
import avilaos.model.hardware.BlockMap;
import avilaos.os.disk.DiskLimits;
import avilaos.os.filesystem.AllocationResult;
import avilaos.os.filesystem.ChainAllocator;

/**
 * Pruebas de la validacion del tamano del disco y del rechazo por falta de bloques (D06).
 * Termina con "TODAS LAS PRUEBAS PASARON" o con la lista de fallas (codigo de salida 1).
 */
public class PruebasLimitesDisco {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        tamanoDelDisco();
        crearDiscoValidado();
        archivoMayorQueElEspacio();
        casoDelEnunciado();
        tamanoInvalidoDeArchivo();
        rechazoConSwap();

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

    /** Imprime lo que el sistema le diria al usuario, como evidencia de cada rechazo. */
    private static void evidencia(String texto) {
        System.out.println("          -> " + texto);
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

    private static void tamanoDelDisco() {
        titulo("D06 - rango del disco 100 a 500");
        check(DiskLimits.checkTotalBlocks(100) == null, "100 se acepta");
        check(DiskLimits.checkTotalBlocks(200) == null, "200 (el del JSON de ejemplo) se acepta");
        check(DiskLimits.checkTotalBlocks(500) == null, "500 se acepta");
        check(DiskLimits.checkTotalBlocks(99) != null, "99 se rechaza");
        check(DiskLimits.checkTotalBlocks(50) != null, "50 se rechaza");
        check(DiskLimits.checkTotalBlocks(501) != null, "501 se rechaza");
        check(DiskLimits.checkTotalBlocks(600) != null, "600 se rechaza");
        check(DiskLimits.checkTotalBlocks(0) != null && DiskLimits.checkTotalBlocks(-5) != null, "0 y negativos se rechazan");
        String mensaje = DiskLimits.checkTotalBlocks(600);
        check(mensaje.contains("100") && mensaje.contains("500") && mensaje.contains("600"), "el mensaje dice el rango y el valor pedido");
        evidencia("disco de 50 bloques: " + DiskLimits.checkTotalBlocks(50));
        evidencia("disco de 600 bloques: " + mensaje);
    }

    private static void crearDiscoValidado() {
        titulo("D06 - crear el disco con el tamano validado");
        check(DiskLimits.createMap(100).size() == 100, "disco de 100 bloques");
        check(DiskLimits.createMap(500).size() == 500, "disco de 500 bloques");
        check(falla(() -> DiskLimits.createMap(50)), "disco de 50 se rechaza");
        check(falla(() -> DiskLimits.createMap(600)), "disco de 600 se rechaza");
        try {
            DiskLimits.createMap(600);
        } catch (IllegalArgumentException e) {
            evidencia("createMap(600) lanza: " + e.getMessage());
        }
    }

    private static void archivoMayorQueElEspacio() {
        titulo("D06 - crear archivo sin bloques suficientes (caso T05)");
        BlockMap mapa = DiskLimits.createMap(100);
        ChainAllocator asignador = new ChainAllocator(mapa);
        check(asignador.request(95, "/lleno").isGranted(), "se ocupan 95 de 100 bloques");
        BlockInfo[] antes = mapa.snapshot();
        AllocationResult r = asignador.request(10, "/nuevo.txt");
        check(!r.isGranted() && r.getChain() == null, "crear archivo de 10 con 5 libres se rechaza");
        check(r.getMessage().contains("10") && r.getMessage().contains("5"), "el mensaje dice cuantos se piden y cuantos hay");
        evidencia("libres antes del rechazo: 5");
        evidencia("crear /nuevo.txt de 10 bloques: RECHAZADO - " + r.getMessage());
        BlockInfo[] despues = mapa.snapshot();
        boolean igual = antes.length == despues.length;
        for (int i = 0; i < antes.length && igual; i++) {
            if (antes[i].getUse() != despues[i].getUse() || antes[i].getNext() != despues[i].getNext()) {
                igual = false;
            }
        }
        check(igual && mapa.freeCount() == 5, "el disco no cambia");
        evidencia("libres despues del rechazo: " + mapa.freeCount() + " (el disco no cambio)");
        AllocationResult justo = asignador.request(5, "/justo");
        check(justo.isGranted() && justo.getChain().size() == 5 && justo.getMessage() == null, "pedir justo lo que queda se concede");
        AllocationResult lleno = asignador.request(1, "/mas");
        check(!lleno.isGranted(), "con el disco lleno se rechaza");
        evidencia("crear de 1 bloque con el disco lleno: RECHAZADO - " + lleno.getMessage());
    }

    private static void casoDelEnunciado() {
        titulo("D06 - disco de 200 bloques del JSON de ejemplo");
        BlockMap mapa = DiskLimits.createMap(200);
        ChainAllocator asignador = new ChainAllocator(mapa);
        check(asignador.request(200, "/todo").isGranted(), "caben 200 bloques en un disco de 200");
        check(!asignador.request(1, "/uno").isGranted(), "ya no cabe ni uno");
    }

    private static void tamanoInvalidoDeArchivo() {
        titulo("D06 - archivo de 0 o menos bloques");
        BlockMap mapa = DiskLimits.createMap(100);
        ChainAllocator asignador = new ChainAllocator(mapa);
        AllocationResult cero = asignador.request(0, "/a");
        check(!cero.isGranted() && cero.getMessage() != null, "0 bloques se rechaza con mensaje");
        evidencia("crear de 0 bloques: RECHAZADO - " + cero.getMessage());
        check(!asignador.request(-3, "/a").isGranted(), "bloques negativos se rechazan");
        check(mapa.freeCount() == 100, "los rechazos no ocupan nada");
    }

    private static void rechazoConSwap() {
        titulo("D06 - el espacio libre cuenta solo la zona de archivos");
        BlockMap mapa = new BlockMap(300);
        ChainAllocator asignador = new ChainAllocator(mapa, 200);
        check(asignador.request(150, "/a").isGranted(), "150 caben");
        AllocationResult r = asignador.request(100, "/b");
        check(!r.isGranted() && r.getMessage().contains("50"), "100 no caben aunque el swap este libre: hay 50 en la zona de archivos");
        evidencia("crear de 100 bloques: RECHAZADO - " + r.getMessage());
        check(mapa.freeCount() == 150, "el disco no cambia");
    }
}