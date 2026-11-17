/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.enums.Permission;
import avilaos.enums.UserMode;
import avilaos.model.hardware.BlockMap;
import avilaos.os.filesystem.ChainAllocator;
import avilaos.os.filesystem.FileColorPalette;
import avilaos.os.filesystem.FileCreator;
import avilaos.os.filesystem.FileNode;
import avilaos.os.filesystem.FileTree;
import avilaos.os.filesystem.FsResult;

/**
 * Pruebas del proceso creador y del color de cada archivo (F14).
 * Termina con "TODAS LAS PRUEBAS PASARON" o con la lista de fallas (codigo de salida 1).
 */
public class PruebasCreadorColorArchivo {

    private static int fallas = 0;
    private static int verificaciones = 0;

    /** Disco de 200 bloques con /sistema (admin) y /usuarios/ana (ana). */
    private static class Escenario {
        BlockMap mapa = new BlockMap(200);
        FileTree arbol = new FileTree("admin");
        ChainAllocator asignador = new ChainAllocator(mapa);
        FileCreator creador = new FileCreator(arbol, asignador);

        Escenario() {
            FileNode sistema = FileNode.directory("sistema", "admin", Permission.PUBLIC);
            FileNode usuarios = FileNode.directory("usuarios", "admin", Permission.PUBLIC);
            FileNode ana = FileNode.directory("ana", "ana", Permission.PRIVATE);
            arbol.add(arbol.getRoot(), sistema);
            arbol.add(arbol.getRoot(), usuarios);
            arbol.add(usuarios, ana);
        }

        FileNode crear(String dir, String nombre, int bloques, int proceso) {
            FsResult r = creador.create(dir, nombre, bloques, "admin", UserMode.ADMIN, Permission.PUBLIC, proceso);
            return r.getNode();
        }
    }

    public static void main(String[] args) {
        guardaElProcesoCreador();
        sinProcesoCreadorConocido();
        procesoCreadorInvalido();
        colorEstable();
        coloresDistintos();
        formatoDelColor();
        directoriosSinColor();

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

    private static int distancia(int c1, int c2) {
        return Math.abs(((c1 >> 16) & 255) - ((c2 >> 16) & 255))
                + Math.abs(((c1 >> 8) & 255) - ((c2 >> 8) & 255))
                + Math.abs((c1 & 255) - (c2 & 255));
    }

    private static void guardaElProcesoCreador() {
        titulo("F14 - cada archivo guarda el ID del proceso que lo creo");
        Escenario e = new Escenario();
        FileNode a = e.crear("/sistema", "boot.bin", 4, 7);
        FileNode b = e.crear("/usuarios/ana", "notas.txt", 2, 12);
        check(a.getCreatorProcessId() == 7 && a.hasCreator(), "boot.bin lo creo el proceso 7");
        check(b.getCreatorProcessId() == 12, "notas.txt lo creo el proceso 12");
        check(e.arbol.find("/sistema/boot.bin").getNode().getCreatorProcessId() == 7, "el dato se conserva al buscarlo por su ruta");
        a.rename("kernel.bin");
        check(a.getCreatorProcessId() == 7, "renombrar el archivo no cambia el proceso creador");
        check(FileNode.file("x", "admin", Permission.PUBLIC, 5, 1, 0).getCreatorProcessId() == 0, "el ID 0 tambien es un proceso valido");
    }

    private static void sinProcesoCreadorConocido() {
        titulo("F14 - sin proceso creador (carga inicial o version anterior de create)");
        Escenario e = new Escenario();
        FsResult r = e.creador.create("/sistema", "a.bin", 1, "admin", UserMode.ADMIN, Permission.PUBLIC);
        check(r.isOk() && !r.getNode().hasCreator() && r.getNode().getCreatorProcessId() == FileNode.NO_CREATOR,
                "queda con NO_CREATOR");
        check(r.getNode().getColor() != FileColorPalette.NO_COLOR, "pero tiene color igual");
        check(!FileNode.file("y", "admin", Permission.PUBLIC, 3, 2).hasCreator(), "FileNode.file de 5 datos tambien");
        check(!FileNode.root("admin").hasCreator(), "la raiz no tiene proceso creador");
    }

    private static void procesoCreadorInvalido() {
        titulo("F14 - un ID de proceso invalido se rechaza sin modificar nada");
        Escenario e = new Escenario();
        int nodos = e.arbol.countNodes();
        FsResult r = e.creador.create("/sistema", "a.bin", 3, "admin", UserMode.ADMIN, Permission.PUBLIC, -5);
        check(!r.isOk(), "el ID -5 se rechaza: " + r.getMessage());
        check(e.mapa.usedCount() == 0, "no se reservaron bloques");
        check(e.arbol.countNodes() == nodos, "el arbol no cambio");
        check(falla(() -> FileNode.file("z", "admin", Permission.PUBLIC, 3, 2, -2)), "FileNode.file tambien lo rechaza");
        check(falla(() -> FileNode.directory("d", "admin", Permission.PUBLIC, -9)), "y FileNode.directory");
    }

    private static void colorEstable() {
        titulo("F14 - el color es estable");
        Escenario e = new Escenario();
        FileNode a = e.crear("/sistema", "a.bin", 3, 1);
        FileNode b = e.crear("/sistema", "b.bin", 3, 2);
        int colorA = a.getColor();
        check(a.getColor() == colorA && a.getColor() == colorA, "pedir el color dos veces da lo mismo");
        check(colorA == FileColorPalette.colorFor(a.getFirstBlock()), "sale del primer bloque del archivo");
        a.rename("otro.bin");
        a.setPermission(Permission.PRIVATE);
        check(a.getColor() == colorA, "renombrar o cambiar el permiso no cambia el color");
        Escenario otro = new Escenario();
        FileNode a2 = otro.crear("/sistema", "a.bin", 3, 1);
        FileNode b2 = otro.crear("/sistema", "b.bin", 3, 2);
        check(a2.getColor() == a.getColor() && b2.getColor() == b.getColor(),
                "recrear el mismo disco da los mismos colores (por eso recupera sus colores al cargar)");
        check(FileNode.file("zzz", "ana", Permission.PUBLIC, 3, 9).getColor() == FileColorPalette.colorFor(3),
                "el color no depende del nombre, del dueno ni del tamano");
    }

    private static void coloresDistintos() {
        titulo("F14 - archivos distintos tienen colores distintos");
        Escenario e = new Escenario();
        FileNode[] archivos = new FileNode[40];
        for (int i = 0; i < archivos.length; i++) {
            archivos[i] = e.crear("/sistema", "f" + i + ".bin", 2, i + 1);
        }
        boolean repetido = false;
        int minima = Integer.MAX_VALUE;
        for (int i = 0; i < archivos.length; i++) {
            for (int j = i + 1; j < archivos.length; j++) {
                int d = distancia(archivos[i].getColor(), archivos[j].getColor());
                minima = Math.min(minima, d);
                if (archivos[i].getColor() == archivos[j].getColor()) {
                    repetido = true;
                }
            }
        }
        check(!repetido, "40 archivos en el mismo disco: ningun color repetido");
        check(minima >= 25, "y los mas parecidos se distinguen (distancia minima " + minima + " de 765)");
        boolean repetidoMax = false;
        for (int i = 0; i < 500 && !repetidoMax; i++) {
            for (int j = i + 1; j < 500; j++) {
                if (FileColorPalette.colorFor(i) == FileColorPalette.colorFor(j)) {
                    repetidoMax = true;
                    break;
                }
            }
        }
        check(!repetidoMax, "con el disco maximo (500 bloques) ningun primer bloque repite color");
        int[] saltos = {1, 2, 3, 4, 5, 8, 10, 16};
        boolean todosSeDistinguen = true;
        for (int salto : saltos) {
            int minimaSalto = Integer.MAX_VALUE;
            for (int i = 0; i < 30; i++) {
                for (int j = i + 1; j < 30; j++) {
                    minimaSalto = Math.min(minimaSalto,
                            distancia(FileColorPalette.colorFor(i * salto), FileColorPalette.colorFor(j * salto)));
                }
            }
            if (minimaSalto < 20) {
                todosSeDistinguen = false;
            }
        }
        check(todosSeDistinguen, "archivos del mismo tamano (primeros bloques separados 1, 2, 3, 4, 5, 8, 10 o 16) tambien se distinguen");
        int vecinos = Integer.MAX_VALUE;
        for (int i = 0; i < 499; i++) {
            vecinos = Math.min(vecinos, distancia(FileColorPalette.colorFor(i), FileColorPalette.colorFor(i + 1)));
        }
        check(vecinos >= 100, "archivos con bloques iniciales vecinos tienen colores muy distintos (minimo " + vecinos + ")");
    }

    private static void formatoDelColor() {
        titulo("F14 - formato del color para el mapa y la tabla");
        boolean enRango = true;
        boolean claro = true;
        for (int i = 0; i < 500; i++) {
            int c = FileColorPalette.colorFor(i);
            if (c < 0 || c > 0xFFFFFF) {
                enRango = false;
            }
            int mayor = Math.max((c >> 16) & 255, Math.max((c >> 8) & 255, c & 255));
            if (mayor < 150) {
                claro = false;
            }
        }
        check(enRango, "todos son 0xRRGGBB validos (entre 0 y 0xFFFFFF)");
        check(claro, "ninguno es tan oscuro que no se lea sobre el fondo");
        String hex = FileColorPalette.toHex(FileColorPalette.colorFor(0));
        check(hex.length() == 7 && hex.charAt(0) == '#' && hex.equals(hex.toUpperCase()), "el texto es «#RRGGBB»: " + hex);
        check(FileColorPalette.toHex(0x00000A).equals("#00000A"), "rellena con ceros: #00000A");
        FileNode f = FileNode.file("f", "admin", Permission.PUBLIC, 0, 1);
        check(f.getColorHex().equals(FileColorPalette.toHex(f.getColor())), "getColorHex() del nodo coincide");
        check(falla(() -> FileColorPalette.colorFor(-1)), "un primer bloque negativo se rechaza");
    }

    private static void directoriosSinColor() {
        titulo("F14 - los directorios no tienen color");
        Escenario e = new Escenario();
        FileNode d = e.arbol.find("/sistema").getNode();
        check(d.getColor() == FileColorPalette.NO_COLOR && d.getColorHex().isEmpty(), "/sistema no tiene color");
        check(e.arbol.getRoot().getColor() == FileColorPalette.NO_COLOR, "la raiz tampoco");
        FileNode creado = FileNode.directory("nuevo", "admin", Permission.PUBLIC, 4);
        check(creado.getCreatorProcessId() == 4 && creado.getColor() == FileColorPalette.NO_COLOR,
                "un directorio puede guardar su proceso creador, pero sigue sin color");
    }
}