/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.pruebas;

import avilaos.datastructures.DynamicArray;
import avilaos.enums.Permission;
import avilaos.model.hardware.BlockMap;
import avilaos.os.filesystem.ActiveUser;
import avilaos.os.filesystem.ChainAllocator;
import avilaos.os.filesystem.FileNode;
import avilaos.os.filesystem.FileSnapshot;
import avilaos.os.filesystem.FileSystem;
import avilaos.os.filesystem.FileTree;
import avilaos.os.filesystem.FsResult;
import avilaos.os.filesystem.PermissionMatrix;
import avilaos.os.filesystem.SecuredFileSystem;
import java.util.concurrent.Semaphore;

/**
 * Pruebas del semaforo del sistema de archivos (F13): varios hilos de simulacion y de la interfaz
 * usan el sistema a la vez sin corrupcion, cada operacion es atomica y la interfaz lee un snapshot.
 * Termina con "TODAS LAS PRUEBAS PASARON" o con la lista de fallas (codigo de salida 1).
 */
public class PruebasSemaforoArchivos {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) throws Exception {
        snapshotBasico();
        snapshotEsUnaCopia();
        creacionesConcurrentes();
        mismaRutaDesdeVariosHilos();
        crearYBorrarConcurrentes();
        lectoresDuranteEscrituras();
        elSemaforoSeLiberaTrasUnRechazo();

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

    private static class Sistema {
        final BlockMap mapa = new BlockMap(500);
        final FileSystem fs;
        final SecuredFileSystem seguro;

        Sistema() {
            FileTree arbol = new FileTree(ActiveUser.ADMIN_NAME);
            fs = new FileSystem(arbol, new ChainAllocator(mapa), mapa);
            seguro = new SecuredFileSystem(fs, new PermissionMatrix(arbol));
        }
    }

    /** Corre n hilos con la misma tarea (recibe su numero) y espera a que todos terminen. */
    private static void correr(int n, final Tarea tarea) throws Exception {
        Thread[] hilos = new Thread[n];
        for (int i = 0; i < n; i++) {
            final int numero = i;
            hilos[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    tarea.hacer(numero);
                }
            });
        }
        for (Thread h : hilos) {
            h.start();
        }
        for (Thread h : hilos) {
            h.join(20000);
        }
    }

    private interface Tarea {
        void hacer(int hilo);
    }

    /** Revisa que el arbol y el disco cuadren: cada archivo tiene su cadena completa y los bloques suman. */
    private static boolean coherente(Sistema s) {
        DynamicArray<FileSnapshot> foto = s.seguro.snapshot();
        int bloques = 0;
        for (int i = 0; i < foto.size(); i++) {
            FileSnapshot f = foto.get(i);
            if (f.isFile()) {
                bloques += f.getBlockCount();
                FsResult leido = s.seguro.read(ActiveUser.admin(), f.getPath());
                if (!leido.isOk() || leido.getBlocks() != f.getBlockCount()) {
                    return false;
                }
            }
        }
        return bloques == s.mapa.usedCount() && s.mapa.usedCount() + s.mapa.freeCount() == s.mapa.size();
    }

    private static void snapshotBasico() {
        titulo("Snapshot del arbol");
        Sistema s = new Sistema();
        ActiveUser admin = ActiveUser.admin();
        s.seguro.createDirectory(admin, "/sistema", Permission.PUBLIC);
        s.seguro.createDirectory(admin, "/sistema/logs", Permission.PRIVATE);
        s.seguro.createFile(admin, "/sistema", "boot.bin", 3, Permission.PUBLIC);
        DynamicArray<FileSnapshot> foto = s.seguro.snapshot();
        check(foto.size() == 4, "raiz + 2 directorios + 1 archivo = 4 nodos");
        check(foto.get(0).getPath().equals("/") && foto.get(0).getDepth() == 0, "empieza en la raiz, nivel 0");
        check(foto.get(1).getPath().equals("/sistema") && foto.get(1).getDepth() == 1, "/sistema nivel 1");
        check(foto.get(2).getPath().equals("/sistema/logs") && foto.get(2).isDirectory() && foto.get(2).getDepth() == 2,
                "/sistema/logs nivel 2");
        FileSnapshot boot = foto.get(3);
        check(boot.getPath().equals("/sistema/boot.bin") && boot.isFile() && boot.getBlockCount() == 3, "el archivo con su ruta y bloques");
        check(boot.getOwner().equals("admin") && boot.getPermission() == Permission.PUBLIC, "dueno y permiso");
        check(boot.getFirstBlock() >= 0 && boot.getCreatorProcessId() == FileNode.NO_CREATOR, "primer bloque y sin proceso creador");
        check(boot.getColorHex().startsWith("#"), "color en hexadecimal");
        check(s.seguro.list().size() == 4, "list() sigue funcionando");
    }

    private static void snapshotEsUnaCopia() {
        titulo("El snapshot no cambia aunque el sistema cambie despues");
        Sistema s = new Sistema();
        ActiveUser admin = ActiveUser.admin();
        s.seguro.createFile(admin, "/", "a.txt", 2, Permission.PUBLIC);
        DynamicArray<FileSnapshot> antes = s.seguro.snapshot();
        s.seguro.rename(admin, "/a.txt", "b.txt");
        s.seguro.createFile(admin, "/", "c.txt", 1, Permission.PUBLIC);
        check(antes.size() == 2 && antes.get(1).getPath().equals("/a.txt") && antes.get(1).getName().equals("a.txt"),
                "la copia vieja conserva el nombre y la ruta de antes");
        DynamicArray<FileSnapshot> despues = s.seguro.snapshot();
        check(despues.size() == 3 && despues.get(1).getName().equals("b.txt"), "una copia nueva ve los cambios");
    }

    private static void creacionesConcurrentes() throws Exception {
        titulo("8 hilos crean archivos a la vez");
        final Sistema s = new Sistema();
        final ActiveUser admin = ActiveUser.admin();
        final boolean[] todosOk = {true};
        correr(8, new Tarea() {
            @Override
            public void hacer(int hilo) {
                FsResult dir = s.seguro.createDirectory(admin, "/h" + hilo, Permission.PUBLIC);
                if (!dir.isOk()) {
                    todosOk[0] = false;
                }
                for (int i = 0; i < 10; i++) {
                    if (!s.seguro.createFile(admin, "/h" + hilo, "f" + i, 2, Permission.PUBLIC).isOk()) {
                        todosOk[0] = false;
                    }
                }
            }
        });
        check(todosOk[0], "ninguna creacion fallo");
        DynamicArray<FileSnapshot> foto = s.seguro.snapshot();
        check(foto.size() == 1 + 8 + 80, "raiz + 8 directorios + 80 archivos");
        check(s.mapa.usedCount() == 160, "160 bloques ocupados (80 archivos x 2)");
        check(coherente(s), "el arbol y el disco cuadran");
    }

    private static void mismaRutaDesdeVariosHilos() throws Exception {
        titulo("Varios hilos crean el mismo archivo a la vez: solo uno lo logra");
        final Sistema s = new Sistema();
        final ActiveUser admin = ActiveUser.admin();
        final int[] logrados = {0};
        final Semaphore cuenta = new Semaphore(1);
        correr(12, new Tarea() {
            @Override
            public void hacer(int hilo) {
                if (s.seguro.createFile(admin, "/", "unico.dat", 5, Permission.PUBLIC).isOk()) {
                    cuenta.acquireUninterruptibly();
                    logrados[0]++;
                    cuenta.release();
                }
            }
        });
        check(logrados[0] == 1, "exactamente 1 de 12 lo crea (la comprobacion y la creacion son una sola operacion)");
        check(s.mapa.usedCount() == 5, "solo se reservaron 5 bloques, no 60");
        check(coherente(s), "queda coherente");
    }

    private static void crearYBorrarConcurrentes() throws Exception {
        titulo("Hilos que crean, renombran y borran los mismos archivos");
        final Sistema s = new Sistema();
        final ActiveUser admin = ActiveUser.admin();
        s.seguro.createDirectory(admin, "/zona", Permission.PUBLIC);
        correr(6, new Tarea() {
            @Override
            public void hacer(int hilo) {
                for (int i = 0; i < 60; i++) {
                    String nombre = "x" + (i % 5);
                    s.seguro.createFile(admin, "/zona", nombre, 1 + (i % 3), Permission.PUBLIC);
                    if (i % 4 == 0) {
                        s.seguro.rename(admin, "/zona/" + nombre, "y" + (i % 5));
                    }
                    if (i % 3 == 0) {
                        s.seguro.delete(admin, "/zona/" + nombre);
                        s.seguro.delete(admin, "/zona/y" + (i % 5));
                    }
                }
            }
        });
        check(coherente(s), "tras miles de operaciones cruzadas el arbol y el disco cuadran");
        s.seguro.delete(admin, "/zona");
        check(s.mapa.usedCount() == 0, "borrar el directorio libera todo: el mapa queda como al inicio");
    }

    private static void lectoresDuranteEscrituras() throws Exception {
        titulo("La interfaz lee snapshots mientras otros hilos escriben");
        final Sistema s = new Sistema();
        final ActiveUser admin = ActiveUser.admin();
        final boolean[] seguir = {true};
        final boolean[] foto = {true};
        final int[] lecturas = {0};
        Thread lector = new Thread(new Runnable() {
            @Override
            public void run() {
                while (seguir[0]) {
                    DynamicArray<FileSnapshot> f = s.seguro.snapshot();
                    lecturas[0]++;
                    for (int i = 1; i < f.size(); i++) {
                        String ruta = f.get(i).getPath();
                        String padre = ruta.substring(0, ruta.lastIndexOf('/'));
                        boolean padreAntes = false;
                        for (int j = 0; j < i; j++) {
                            if (f.get(j).getPath().equals(padre.isEmpty() ? "/" : padre)) {
                                padreAntes = true;
                            }
                        }
                        if (!padreAntes || f.get(i).getDepth() < 1) {
                            foto[0] = false;
                        }
                    }
                }
            }
        });
        lector.start();
        correr(4, new Tarea() {
            @Override
            public void hacer(int hilo) {
                for (int i = 0; i < 40; i++) {
                    s.seguro.createDirectory(admin, "/d" + hilo + "_" + i, Permission.PUBLIC);
                    s.seguro.createFile(admin, "/d" + hilo + "_" + i, "a", 1, Permission.PUBLIC);
                    if (i % 2 == 0) {
                        s.seguro.delete(admin, "/d" + hilo + "_" + i);
                    }
                }
            }
        });
        seguir[0] = false;
        lector.join(20000);
        check(lecturas[0] > 0, "el lector alcanzo a leer snapshots (" + lecturas[0] + ")");
        check(foto[0], "todos los snapshots eran consistentes: cada nodo con su padre antes");
        check(coherente(s), "al final el arbol y el disco cuadran");
    }

    private static void elSemaforoSeLiberaTrasUnRechazo() throws Exception {
        titulo("Un rechazo o un error no deja el semaforo tomado");
        final Sistema s = new Sistema();
        final ActiveUser admin = ActiveUser.admin();
        final ActiveUser ana = ActiveUser.user("ana");
        check(!s.seguro.createFile(admin, "/no-existe", "x", 1, Permission.PUBLIC).isOk(), "ruta inexistente: rechazado");
        check(!s.seguro.createDirectory(ana, "/privado", Permission.PUBLIC).isOk(), "usuario sin permiso: rechazado");
        check(!s.seguro.delete(admin, "/").isOk(), "borrar la raiz: rechazado");
        check(!s.seguro.createFile(null, "/", "x", 1, Permission.PUBLIC).isOk(), "usuario nulo: rechazado");
        final boolean[] termino = {false};
        Thread hilo = new Thread(new Runnable() {
            @Override
            public void run() {
                s.seguro.createFile(admin, "/", "despues.txt", 1, Permission.PUBLIC);
                s.seguro.snapshot();
                termino[0] = true;
            }
        });
        hilo.start();
        hilo.join(5000);
        check(termino[0], "despues de todo eso otro hilo todavia puede operar (no hay bloqueo)");
    }
}