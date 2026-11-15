/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.enums.IOOperationType;
import avilaos.model.process.PCB;
import avilaos.model.process.ProcessFactory;

/**
 * Pruebas de la fabrica de procesos de E/S (E02).
 * Termina con "TODAS LAS PRUEBAS PASARON" o con la lista de fallas (codigo de salida 1).
 */
public class PruebasFabricaEs {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        procesosDelEnunciado();
        textoDeLaOperacion();
        operacionInvalida();
        rutaInvalida();
        tamanoYNombre();
        datosDelProceso();

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

    private static PCB crear(String operacion, String ruta, int bloques, String nombreNuevo) {
        return ProcessFactory.createDiskIo("P1", 1, 16, 2, 40, 0, 0, operacion, ruta, bloques, nombreNuevo);
    }

    private static void procesosDelEnunciado() {
        titulo("E02 - procesos con la forma del JSON del enunciado");
        PCB leer = crear("READ", "/usuarios/notas.txt", 0, null);
        check(leer.getDiskOperation() == IOOperationType.READ && leer.getTargetPath().equals("/usuarios/notas.txt"), "READ de una ruta");
        PCB crear = crear("CREATE", "/usuarios/nuevo.txt", 8, null);
        check(crear.getDiskOperation() == IOOperationType.CREATE && crear.getFileSizeBlocks() == 8, "CREATE con 8 bloques");
        PCB actualizar = crear("UPDATE", "/usuarios/notas.txt", 0, null);
        check(actualizar.getDiskOperation() == IOOperationType.UPDATE && actualizar.getNewName() == null, "UPDATE sin nombre nuevo (como el JSON)");
        PCB borrar = crear("DELETE", "/usuarios", 0, null);
        check(borrar.getDiskOperation() == IOOperationType.DELETE, "DELETE de un directorio");
        check(leer.hasDiskOperation() && crear.hasDiskOperation(), "todos quedan con operacion de E/S");
    }

    private static void textoDeLaOperacion() {
        titulo("E02 - la operacion viene como texto");
        check(crear("read", "/a.txt", 0, null).getDiskOperation() == IOOperationType.READ, "minusculas");
        check(crear("  Delete ", "/a.txt", 0, null).getDiskOperation() == IOOperationType.DELETE, "espacios y mezcla");
    }

    private static void operacionInvalida() {
        titulo("E02 - operacion invalida");
        check(falla(() -> crear("COPY", "/a.txt", 0, null)), "operacion desconocida");
        check(falla(() -> crear(null, "/a.txt", 0, null)), "operacion null");
        check(falla(() -> crear("", "/a.txt", 0, null)), "operacion vacia");
        check(falla(() -> crear("SUSPEND", "/a.txt", 0, null)), "SUSPEND no se pide por aqui");
        check(falla(() -> crear("REACTIVATE", "/a.txt", 0, null)), "REACTIVATE no se pide por aqui");
    }

    private static void rutaInvalida() {
        titulo("E02 - ruta invalida");
        check(falla(() -> crear("READ", null, 0, null)), "ruta null");
        check(falla(() -> crear("READ", "  ", 0, null)), "ruta en blanco");
        check(falla(() -> crear("READ", "usuarios/notas.txt", 0, null)), "ruta relativa");
        check(falla(() -> crear("READ", "/", 0, null)), "la raiz no se puede operar");
        check(falla(() -> crear("READ", "/usuarios/", 0, null)), "termina en /");
        check(falla(() -> crear("READ", "/usuarios//notas.txt", 0, null)), "parte vacia");
        try {
            crear("READ", "usuarios", 0, null);
        } catch (IllegalArgumentException e) {
            check(e.getMessage().contains("empezar con /"), "el mensaje explica el problema");
        }
    }

    private static void tamanoYNombre() {
        titulo("E02 - tamano y nombre nuevo");
        check(falla(() -> crear("CREATE", "/a.txt", 0, null)), "CREATE con tamano 0");
        check(falla(() -> crear("CREATE", "/a.txt", -4, null)), "CREATE con tamano negativo");
        check(falla(() -> crear("READ", "/a.txt", -1, null)), "tamano negativo en READ");
        check(falla(() -> crear("READ", "/a.txt", 0, "otro.txt")), "nombre nuevo solo en UPDATE");
        check("b.txt".equals(crear("UPDATE", "/a.txt", 0, "b.txt").getNewName()), "UPDATE con nombre nuevo");
    }

    private static void datosDelProceso() {
        titulo("E02 - validaciones del proceso (memoria, deadline, instrucciones)");
        check(falla(() -> ProcessFactory.createDiskIo("P1", 1, 0, 2, 40, 0, 0, "READ", "/a", 0, null)), "memoria 0");
        check(falla(() -> ProcessFactory.createDiskIo("P1", 1, 16, 0, 40, 0, 0, "READ", "/a", 0, null)), "instrucciones 0");
        check(falla(() -> ProcessFactory.createDiskIo("P1", 1, 16, 2, 0, 0, 0, "READ", "/a", 0, null)), "deadline 0");
        check(falla(() -> ProcessFactory.createDiskIo(" ", 1, 16, 2, 40, 0, 0, "READ", "/a", 0, null)), "nombre vacio");
        PCB p = ProcessFactory.createDiskIo("P9", 3, 32, 5, 90, 0, 7, "READ", "/a", 0, null);
        check(p.getName().equals("P9") && p.getPriority() == 3 && p.getMemorySize() == 32, "nombre, prioridad y memoria");
        check(p.getTotalInstructions() == 5 && p.getDeadline() == 90 && p.getArrivalCycle() == 7, "instrucciones, deadline y llegada");
    }
}