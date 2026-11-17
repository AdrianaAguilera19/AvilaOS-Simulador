/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.datastructures.BlockChain;
import avilaos.enums.Permission;
import avilaos.enums.UserMode;
import avilaos.model.hardware.BlockMap;
import avilaos.os.filesystem.ChainAllocator;
import avilaos.os.filesystem.FileCreator;
import avilaos.os.filesystem.FileNode;
import avilaos.os.filesystem.FsResult;
import avilaos.os.filesystem.FileTree;

/**
 * Pruebas de crear archivos con tamano en bloques y permisos por modo de usuario (F05).
 * Termina con "TODAS LAS PRUEBAS PASARON" o con la lista de fallas (codigo de salida 1).
 */
public class PruebasCrearArchivo {

    private static int fallas = 0;
    private static int verificaciones = 0;

    /** Disco de 200 bloques, raiz de admin, /sistema (admin), /usuarios/ana (ana) y /usuarios/beto (beto). */
    private static class Escenario {
        BlockMap mapa = new BlockMap(200);
        FileTree arbol = new FileTree("admin");
        ChainAllocator asignador = new ChainAllocator(mapa);
        FileCreator creador = new FileCreator(arbol, asignador);

        Escenario() {
            FileNode sistema = FileNode.directory("sistema", "admin", Permission.PUBLIC);
            FileNode usuarios = FileNode.directory("usuarios", "admin", Permission.PUBLIC);
            FileNode ana = FileNode.directory("ana", "ana", Permission.PRIVATE);
            FileNode beto = FileNode.directory("beto", "beto", Permission.PRIVATE);
            arbol.add(arbol.getRoot(), sistema);
            arbol.add(arbol.getRoot(), usuarios);
            arbol.add(usuarios, ana);
            arbol.add(usuarios, beto);
        }
    }

    public static void main(String[] args) {
        adminCreaEnCualquierDirectorio();
        usuarioEnSusDirectorios();
        usuarioFueraDeSusDirectorios();
        bloquesEncadenados();
        sinBloquesSuficientes();
        rutaYNombreInvalidos();
        nombreRepetido();
        datosFaltantes();

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

    private static void adminCreaEnCualquierDirectorio() {
        titulo("F05 - el administrador crea en cualquier directorio");
        Escenario e = new Escenario();
        FsResult r1 = e.creador.create("/sistema", "boot.bin", 4, "admin", UserMode.ADMIN, Permission.PUBLIC);
        check(r1.isOk() && r1.getNode().getName().equals("boot.bin"), "crea /sistema/boot.bin (4 bloques)");
        FsResult r2 = e.creador.create("/usuarios/ana", "notas.txt", 3, "admin", UserMode.ADMIN, Permission.PRIVATE);
        check(r2.isOk(), "crea dentro del directorio de ana");
        FsResult r3 = e.creador.create("/", "raiz.txt", 2, "admin", UserMode.ADMIN, Permission.PUBLIC);
        check(r3.isOk(), "crea en la raiz");
        check(e.arbol.find("/sistema/boot.bin").isFound(), "el archivo se encuentra por su ruta");
        check(e.arbol.find("/sistema/boot.bin").getNode().getBlockCount() == 4, "el nodo guarda 4 bloques");
        check(e.arbol.find("/sistema/boot.bin").getNode().getOwner().equals("admin"), "el dueno es quien lo creo");
        check(e.mapa.usedCount() == 9, "el disco tiene 9 bloques ocupados (4 + 3 + 2)");
    }

    private static void usuarioEnSusDirectorios() {
        titulo("F05 - el usuario crea en sus propios directorios");
        Escenario e = new Escenario();
        FsResult r = e.creador.create("/usuarios/ana", "tarea.doc", 5, "ana", UserMode.USER, Permission.PRIVATE);
        check(r.isOk(), "ana crea en /usuarios/ana");
        check(r.getNode().getOwner().equals("ana") && r.getNode().getPermission() == Permission.PRIVATE,
                "el archivo es de ana y es PRIVATE");
        check(e.arbol.find("/usuarios/ana/tarea.doc").isFound(), "aparece en el arbol");
    }

    private static void usuarioFueraDeSusDirectorios() {
        titulo("F05 - el usuario fuera de sus directorios es rechazado sin cambios");
        Escenario e = new Escenario();
        int nodos = e.arbol.countNodes();
        FsResult r1 = e.creador.create("/usuarios/beto", "intruso.txt", 2, "ana", UserMode.USER, Permission.PUBLIC);
        check(!r1.isOk(), "ana no puede crear en el directorio de beto");
        check(r1.getMessage().contains("propios directorios"), "mensaje: " + r1.getMessage());
        FsResult r2 = e.creador.create("/sistema", "x.bin", 1, "ana", UserMode.USER, Permission.PUBLIC);
        check(!r2.isOk(), "ana no puede crear en /sistema (es del admin)");
        FsResult r3 = e.creador.create("/", "y.txt", 1, "ana", UserMode.USER, Permission.PUBLIC);
        check(!r3.isOk(), "ana no puede crear en la raiz");
        check(e.mapa.usedCount() == 0, "el disco sigue sin bloques ocupados");
        check(e.arbol.countNodes() == nodos, "el arbol no cambio");
        check(e.creador.create("/usuarios/ana", "a.txt", 1, "admin", UserMode.USER, Permission.PUBLIC).isOk() == false,
                "el modo USER manda aunque el nombre sea «admin»: el directorio no es suyo");
    }

    private static void bloquesEncadenados() {
        titulo("F05 - se reservan bloques encadenados");
        Escenario e = new Escenario();
        e.creador.create("/sistema", "a.bin", 3, "admin", UserMode.ADMIN, Permission.PUBLIC);
        FsResult r = e.creador.create("/sistema", "b.bin", 4, "admin", UserMode.ADMIN, Permission.PUBLIC);
        FileNode b = r.getNode();
        check(b.getFirstBlock() == 3, "b.bin empieza en el bloque 3 (despues de a.bin)");
        BlockChain cadena = e.asignador.chainOf(b.getFirstBlock());
        check(cadena.size() == 4, "la cadena de b.bin tiene 4 bloques");
        check(cadena.get(0) == 3 && cadena.get(3) == 6, "bloques 3 a 6 enlazados");
        check(e.mapa.get(3).getOwner().equals("/sistema/b.bin"), "el dueno del bloque es la ruta «/sistema/b.bin»");
        check(e.mapa.usedCount() == 7, "7 bloques ocupados en total");
        FsResult raiz = e.creador.create("/", "r.txt", 1, "admin", UserMode.ADMIN, Permission.PUBLIC);
        check(e.mapa.get(raiz.getNode().getFirstBlock()).getOwner().equals("/r.txt"), "en la raiz la ruta es «/r.txt»");
    }

    private static void sinBloquesSuficientes() {
        titulo("F05 - sin bloques suficientes falla sin modificar nada");
        Escenario e = new Escenario();
        e.creador.create("/sistema", "grande.bin", 190, "admin", UserMode.ADMIN, Permission.PUBLIC);
        int usados = e.mapa.usedCount();
        int nodos = e.arbol.countNodes();
        FsResult r = e.creador.create("/sistema", "otro.bin", 11, "admin", UserMode.ADMIN, Permission.PUBLIC);
        check(!r.isOk(), "pedir 11 con 10 libres se rechaza");
        check(r.getMessage().contains("11") && r.getMessage().contains("10"), "mensaje: " + r.getMessage());
        check(e.mapa.usedCount() == usados, "el disco no cambio (" + usados + " ocupados)");
        check(e.arbol.countNodes() == nodos, "el arbol no cambio");
        check(e.arbol.find("/sistema/otro.bin").isFound() == false, "el archivo rechazado no existe");
        check(e.creador.create("/sistema", "justo.bin", 10, "admin", UserMode.ADMIN, Permission.PUBLIC).isOk(),
                "pedir exactamente los 10 libres si se puede");
        check(e.mapa.freeCount() == 0, "el disco queda lleno");
    }

    private static void rutaYNombreInvalidos() {
        titulo("F05 - ruta, nombre y tamano invalidos");
        Escenario e = new Escenario();
        check(!e.creador.create("/no/existe", "a.txt", 1, "admin", UserMode.ADMIN, Permission.PUBLIC).isOk(),
                "directorio inexistente se rechaza");
        check(!e.creador.create("sistema", "a.txt", 1, "admin", UserMode.ADMIN, Permission.PUBLIC).isOk(),
                "ruta sin «/» inicial se rechaza");
        e.creador.create("/sistema", "f.bin", 1, "admin", UserMode.ADMIN, Permission.PUBLIC);
        FsResult sobreArchivo = e.creador.create("/sistema/f.bin", "g.bin", 1, "admin", UserMode.ADMIN, Permission.PUBLIC);
        check(!sobreArchivo.isOk(), "crear dentro de un archivo se rechaza: " + sobreArchivo.getMessage());
        check(!e.creador.create("/sistema", "", 1, "admin", UserMode.ADMIN, Permission.PUBLIC).isOk(), "nombre vacio se rechaza");
        check(!e.creador.create("/sistema", "a/b", 1, "admin", UserMode.ADMIN, Permission.PUBLIC).isOk(), "nombre con «/» se rechaza");
        check(!e.creador.create("/sistema", "c.bin", 0, "admin", UserMode.ADMIN, Permission.PUBLIC).isOk(), "0 bloques se rechaza");
        check(!e.creador.create("/sistema", "d.bin", -3, "admin", UserMode.ADMIN, Permission.PUBLIC).isOk(), "bloques negativos se rechaza");
        check(e.mapa.usedCount() == 1, "solo f.bin ocupa disco (1 bloque)");
    }

    private static void nombreRepetido() {
        titulo("F05 - nombre repetido en el mismo directorio");
        Escenario e = new Escenario();
        e.creador.create("/sistema", "a.bin", 2, "admin", UserMode.ADMIN, Permission.PUBLIC);
        FsResult r = e.creador.create("/sistema", "a.bin", 2, "admin", UserMode.ADMIN, Permission.PUBLIC);
        check(!r.isOk(), "segundo «a.bin» en /sistema se rechaza: " + r.getMessage());
        check(e.mapa.usedCount() == 2, "no se reservaron bloques para el repetido");
        check(e.creador.create("/usuarios/ana", "a.bin", 2, "admin", UserMode.ADMIN, Permission.PUBLIC).isOk(),
                "el mismo nombre en otro directorio si se permite");
    }

    private static void datosFaltantes() {
        titulo("F05 - datos faltantes");
        Escenario e = new Escenario();
        check(!e.creador.create("/sistema", "a", 1, null, UserMode.ADMIN, Permission.PUBLIC).isOk(), "sin usuario se rechaza");
        check(!e.creador.create("/sistema", "a", 1, "admin", null, Permission.PUBLIC).isOk(), "sin modo se rechaza");
        check(!e.creador.create("/sistema", "a", 1, "admin", UserMode.ADMIN, null).isOk(), "sin permiso se rechaza");
        check(e.mapa.usedCount() == 0, "no se toco el disco");
        boolean lanza = false;
        try {
            new FileCreator(null, e.asignador);
        } catch (IllegalArgumentException ex) {
            lanza = true;
        }
        check(lanza, "el creador exige arbol y asignador");
    }
}