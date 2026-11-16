package avilaos.pruebas;

import avilaos.datastructures.BlockChain;
import avilaos.datastructures.DynamicArray;
import avilaos.enums.Permission;
import avilaos.model.hardware.BlockInfo;
import avilaos.model.hardware.BlockMap;
import avilaos.os.disk.DiskLimits;
import avilaos.os.filesystem.ChainAllocator;
import avilaos.os.filesystem.FileNode;
import avilaos.os.filesystem.FileSystem;
import avilaos.os.filesystem.FileTree;
import avilaos.os.filesystem.FsResult;

/**
 * Pruebas de las operaciones del sistema de archivos (F04, F06, F07, F08 y F09).
 * Usa el sistema de archivos de ejemplo del enunciado (disco de 200 bloques). Se ejecuta como programa
 * (main) y termina con "TODAS LAS PRUEBAS PASARON" o con el numero de fallas (codigo de salida 1).
 */
public class PruebasOperacionesArchivos {

    private static int fallas = 0;
    private static int verificaciones = 0;

    public static void main(String[] args) {
        ejemploDelEnunciado();
        crearDirectorio();
        listarYLeer();
        renombrar();
        eliminarArchivo();
        eliminarDirectorio();
        consistenciaGeneral();

        System.out.println();
        if (fallas == 0) {
            System.out.println("TODAS LAS PRUEBAS PASARON (" + verificaciones + " verificaciones)");
        } else {
            System.out.println("HAY " + fallas + " FALLAS de " + verificaciones + " verificaciones");
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ utilidades

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

    /** El mapa de bloques del escenario en curso (para mirarlo y para danarlo a proposito). */
    private static BlockMap mapaActual;

    /** Disco vacio de 200 bloques con solo la raiz. */
    private static FileSystem nuevo() {
        BlockMap map = DiskLimits.createMap(200);
        mapaActual = map;
        return new FileSystem(new FileTree("admin"), new ChainAllocator(map), map);
    }

    /** Crea un archivo reservando bloques (hace de F05, que es de otra tarea) para preparar los escenarios. */
    private static FileNode archivo(FileSystem fs, String ruta, String dueno, Permission permiso, int bloques) {
        int barra = ruta.lastIndexOf('/');
        String padre = barra == 0 ? "/" : ruta.substring(0, barra);
        FileNode destino = fs.getTree().find(padre).getNode();
        BlockChain cadena = fs.getAllocator().request(bloques, ruta).getChain();
        FileNode nodo = FileNode.file(ruta.substring(barra + 1), dueno, permiso, cadena.first(), bloques);
        fs.getTree().add(destino, nodo);
        return nodo;
    }

    /** El sistema de archivos de ejemplo del enunciado: ocupa los 200 bloques. */
    private static FileSystem ejemplo() {
        FileSystem fs = nuevo();
        fs.createDirectory("/sistema", "admin", Permission.PUBLIC);
        fs.createDirectory("/usuarios", "admin", Permission.PUBLIC);
        archivo(fs, "/mbr.bin", "admin", Permission.PUBLIC, 11);
        archivo(fs, "/sistema/boot.bin", "admin", Permission.PUBLIC, 23);
        archivo(fs, "/usuarios/notas.txt", "user1", Permission.PRIVATE, 28);
        archivo(fs, "/sistema/script.py", "admin", Permission.PUBLIC, 2);
        archivo(fs, "/sistema/core.dll", "admin", Permission.PUBLIC, 31);
        archivo(fs, "/sistema/config.sys", "admin", Permission.PUBLIC, 24);
        archivo(fs, "/usuarios/img1.png", "user1", Permission.PRIVATE, 4);
        archivo(fs, "/usuarios/data.csv", "user2", Permission.PUBLIC, 57);
        archivo(fs, "/usuarios/video.mp4", "user1", Permission.PUBLIC, 20);
        return fs;
    }

    private static int libres(FileSystem fs) {
        return fs.getAllocator().freeBlocks();
    }

    /** El total de bloques que dicen los archivos del arbol debe coincidir con los ocupados del disco. */
    private static boolean coherente(FileSystem fs, BlockMap map) {
        return fs.getTree().usedBlocks() == map.usedCount();
    }

    // ------------------------------------------------------------------ pruebas

    private static void ejemploDelEnunciado() {
        titulo("El sistema de archivos de ejemplo del enunciado");
        BlockMap map = DiskLimits.createMap(200);
        mapaActual = map;
        FileSystem fs = new FileSystem(new FileTree("admin"), new ChainAllocator(map), map);
        fs.createDirectory("/sistema", "admin", Permission.PUBLIC);
        fs.createDirectory("/usuarios", "admin", Permission.PUBLIC);
        FileNode mbr = archivo(fs, "/mbr.bin", "admin", Permission.PUBLIC, 11);
        FileNode boot = archivo(fs, "/sistema/boot.bin", "admin", Permission.PUBLIC, 23);
        FileNode notas = archivo(fs, "/usuarios/notas.txt", "user1", Permission.PRIVATE, 28);
        FileNode script = archivo(fs, "/sistema/script.py", "admin", Permission.PUBLIC, 2);
        FileNode core = archivo(fs, "/sistema/core.dll", "admin", Permission.PUBLIC, 31);
        FileNode config = archivo(fs, "/sistema/config.sys", "admin", Permission.PUBLIC, 24);
        FileNode img = archivo(fs, "/usuarios/img1.png", "user1", Permission.PRIVATE, 4);
        FileNode data = archivo(fs, "/usuarios/data.csv", "user2", Permission.PUBLIC, 57);
        FileNode video = archivo(fs, "/usuarios/video.mp4", "user1", Permission.PUBLIC, 20);
        check(mbr.getFirstBlock() == 0 && boot.getFirstBlock() == 11 && notas.getFirstBlock() == 34
                && script.getFirstBlock() == 62 && core.getFirstBlock() == 64 && config.getFirstBlock() == 95
                && img.getFirstBlock() == 119 && data.getFirstBlock() == 123 && video.getFirstBlock() == 180,
                "los primeros bloques coinciden con el JSON del enunciado (0, 11, 34, 62, 64, 95, 119, 123, 180)");
        check(libres(fs) == 0 && coherente(fs, map), "los 9 archivos ocupan los 200 bloques y el arbol coincide con el disco");
        check(fs.getTree().countFiles() == 9 && fs.getTree().countDirectories() == 3,
                "9 archivos y 3 directorios (con la raiz)");
    }

    private static void crearDirectorio() {
        titulo("F04: crear directorio");
        FileSystem fs = ejemplo();
        FsResult ok = fs.createDirectory("/usuarios/docs", "user1", Permission.PRIVATE);
        check(ok.isOk() && fs.getTree().find("/usuarios/docs").isFound(), "crea /usuarios/docs");
        check(ok.getNode().getOwner().equals("user1") && ok.getNode().getPermission() == Permission.PRIVATE
                && ok.getNode().getBlockCount() == 0, "con su dueno y permiso, y sin bloques");
        FsResult hondo = fs.createDirectory("/usuarios/docs/2026", "user1", Permission.PUBLIC);
        check(hondo.isOk(), "se pueden anidar directorios");
        check(!fs.createDirectory("/usuarios/docs", "user1", Permission.PUBLIC).isOk(), "rechaza un directorio repetido");
        check(!fs.createDirectory("/sistema/boot.bin", "admin", Permission.PUBLIC).isOk(), "rechaza si ya existe un archivo con ese nombre");
        FsResult sinPadre = fs.createDirectory("/no/existe/nuevo", "admin", Permission.PUBLIC);
        check(!sinPadre.isOk() && !sinPadre.getMessage().isEmpty(), "rechaza si el directorio padre no existe, con mensaje");
        check(!fs.createDirectory("/sistema/core.dll/dentro", "admin", Permission.PUBLIC).isOk(), "rechaza crear dentro de un archivo");
        check(!fs.createDirectory("sin_barra", "admin", Permission.PUBLIC).isOk(), "rechaza una ruta que no empieza con /");
        check(!fs.createDirectory("   ", "admin", Permission.PUBLIC).isOk(), "rechaza una ruta vacia");
        check(!fs.createDirectory(null, "admin", Permission.PUBLIC).isOk(), "rechaza una ruta nula");
        check(!fs.createDirectory("/", "admin", Permission.PUBLIC).isOk(), "rechaza «/» (la raiz ya existe)");
        check(!fs.createDirectory("/usuarios//x", "admin", Permission.PUBLIC).isOk(), "rechaza una ruta con una parte vacia");
        check(!fs.createDirectory("/sistema/ ", "admin", Permission.PUBLIC).isOk(), "rechaza un nombre vacio");
        int antes = fs.getTree().countNodes();
        fs.createDirectory("/usuarios/docs", "x", Permission.PUBLIC);
        check(fs.getTree().countNodes() == antes, "un rechazo no deja nada a medias en el arbol");
    }

    private static void listarYLeer() {
        titulo("F06: listar y leer");
        FileSystem fs = ejemplo();
        DynamicArray<String> lineas = fs.list();
        check(lineas.size() == 12, "list() da una linea por cada nodo: raiz, 2 directorios y 9 archivos (" + lineas.size() + ")");
        boolean tieneNotas = false;
        for (int i = 0; i < lineas.size(); i++) {
            if (lineas.get(i).startsWith("/usuarios/notas.txt") && lineas.get(i).contains("user1")
                    && lineas.get(i).contains("PRIVATE") && lineas.get(i).contains("28 bloques desde el 34")) {
                tieneNotas = true;
            }
        }
        check(tieneNotas, "cada linea trae ruta, tipo, dueno, permiso y bloques");

        FsResult lectura = fs.read("/sistema/boot.bin");
        check(lectura.isOk() && lectura.getBlocks() == 23, "leer boot.bin recorre sus 23 bloques");
        BlockChain cadena = lectura.getChain();
        check(cadena.first() == 11 && cadena.get(22) == 33, "los lee en orden, del bloque 11 al 33");
        check(fs.read("/mbr.bin").getBlocks() == 11, "leer mbr.bin (en la raiz) recorre 11 bloques");
        check(!fs.read("/usuarios").isOk(), "no se puede leer un directorio como archivo");
        check(!fs.read("/usuarios/nada.txt").isOk(), "leer una ruta que no existe se rechaza con mensaje");
        check(!fs.read("usuarios/notas.txt").isOk(), "leer con una ruta relativa se rechaza");

        // se danha la cadena a proposito: un bloque de en medio queda libre
        mapaActual.release(20);
        FsResult danado = fs.read("/sistema/boot.bin");
        check(!danado.isOk() && danado.getMessage().contains("danada"), "una cadena rota se detecta y se rechaza, sin lanzar excepcion");
    }

    private static void renombrar() {
        titulo("F07: renombrar");
        BlockMap map = DiskLimits.createMap(200);
        mapaActual = map;
        FileSystem fs = new FileSystem(new FileTree("admin"), new ChainAllocator(map), map);
        fs.createDirectory("/sistema", "admin", Permission.PUBLIC);
        fs.createDirectory("/usuarios", "admin", Permission.PUBLIC);
        archivo(fs, "/usuarios/notas.txt", "user1", Permission.PRIVATE, 5);
        archivo(fs, "/usuarios/img1.png", "user1", Permission.PRIVATE, 4);
        fs.createDirectory("/usuarios/docs", "user1", Permission.PUBLIC);
        archivo(fs, "/usuarios/docs/tesis.pdf", "user1", Permission.PUBLIC, 3);

        FsResult r = fs.rename("/usuarios/img1.png", "foto.png");
        check(r.isOk() && fs.getTree().find("/usuarios/foto.png").isFound() && !fs.getTree().find("/usuarios/img1.png").isFound(),
                "renombrar un archivo cambia su ruta");
        int primero = fs.getTree().find("/usuarios/foto.png").getNode().getFirstBlock();
        check(map.get(primero).getOwner().equals("/usuarios/foto.png"),
                "el mapa de bloques muestra el nombre nuevo como dueno del archivo");
        check(fs.read("/usuarios/foto.png").getBlocks() == 4, "el archivo renombrado se sigue leyendo completo");
        check(!fs.rename("/usuarios/foto.png", "notas.txt").isOk(), "rechaza un nombre que ya existe en ese directorio");
        check(fs.getTree().find("/usuarios/foto.png").isFound(), "tras el rechazo el archivo conserva su nombre");
        check(!fs.rename("/usuarios/foto.png", "a/b").isOk(), "rechaza un nombre con /");
        check(!fs.rename("/usuarios/foto.png", "  ").isOk(), "rechaza un nombre vacio");
        check(!fs.rename("/", "raiz").isOk(), "la raiz no se renombra");
        check(!fs.rename("/no/existe", "x").isOk(), "rechaza una ruta que no existe");
        check(fs.rename("/usuarios/foto.png", "foto.png").isOk(), "renombrar con el mismo nombre no da error");

        FsResult dir = fs.rename("/usuarios", "users");
        check(dir.isOk(), "renombrar un directorio");
        check(fs.getTree().find("/users/notas.txt").isFound() && fs.getTree().find("/users/docs/tesis.pdf").isFound(),
                "la ruta de todo su contenido cambia, incluso en subdirectorios");
        check(!fs.getTree().find("/usuarios/notas.txt").isFound(), "las rutas viejas ya no existen");
        int bloqueTesis = fs.getTree().find("/users/docs/tesis.pdf").getNode().getFirstBlock();
        int bloqueNotas = fs.getTree().find("/users/notas.txt").getNode().getFirstBlock();
        check(map.get(bloqueTesis).getOwner().equals("/users/docs/tesis.pdf")
                && map.get(bloqueNotas).getOwner().equals("/users/notas.txt"),
                "el dueno de los bloques de todos los archivos de adentro tambien se actualiza");
        boolean todosCoinciden = true;
        BlockChain c = fs.getAllocator().chainOf(bloqueNotas);
        for (int i = 0; i < c.size(); i++) {
            todosCoinciden = todosCoinciden && map.get(c.get(i)).getOwner().equals("/users/notas.txt");
        }
        check(todosCoinciden, "y cada uno de los bloques de la cadena, no solo el primero");
        check(coherente(fs, map), "el arbol y el disco siguen coincidiendo");
    }

    private static void eliminarArchivo() {
        titulo("F08: eliminar archivo");
        BlockMap map = DiskLimits.createMap(200);
        mapaActual = map;
        FileSystem fs = new FileSystem(new FileTree("admin"), new ChainAllocator(map), map);
        fs.createDirectory("/usuarios", "admin", Permission.PUBLIC);
        archivo(fs, "/usuarios/a.txt", "user1", Permission.PUBLIC, 6);
        String antes = fotoMapa(map);
        archivo(fs, "/usuarios/nuevo.bin", "user1", Permission.PUBLIC, 9);
        check(map.usedCount() == 15, "con el archivo nuevo hay 15 bloques ocupados");
        FsResult r = fs.delete("/usuarios/nuevo.bin");
        check(r.isOk() && r.getBlocks() == 9, "eliminar libera sus 9 bloques");
        check(map.usedCount() == 6, "el disco vuelve a 6 bloques ocupados");
        check(fotoMapa(map).equals(antes), "el mapa de bloques queda exactamente como antes de crear el archivo");
        check(!fs.getTree().find("/usuarios/nuevo.bin").isFound(), "el archivo ya no esta en el arbol");
        check(fs.read("/usuarios/a.txt").getBlocks() == 6, "los demas archivos no se tocan");
        check(!fs.delete("/usuarios/nuevo.bin").isOk(), "eliminar dos veces: la segunda se rechaza");
        check(!fs.delete("/").isOk(), "la raiz no se elimina");
        check(!fs.delete("/no/existe").isOk(), "eliminar una ruta que no existe se rechaza con mensaje");
        archivo(fs, "/usuarios/otro.bin", "user1", Permission.PUBLIC, 9);
        check(fs.getTree().find("/usuarios/otro.bin").getNode().getFirstBlock() == 6,
                "los bloques liberados se pueden volver a usar");
        check(coherente(fs, map), "el arbol y el disco siguen coincidiendo");
    }

    private static String fotoMapa(BlockMap map) {
        BlockInfo[] todos = map.snapshot();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < todos.length; i++) {
            sb.append(todos[i].toString()).append('\n');
        }
        return sb.toString();
    }

    private static void eliminarDirectorio() {
        titulo("F09: eliminar directorio de forma recursiva");
        BlockMap map = DiskLimits.createMap(200);
        mapaActual = map;
        FileSystem fs = new FileSystem(new FileTree("admin"), new ChainAllocator(map), map);
        fs.createDirectory("/sistema", "admin", Permission.PUBLIC);
        fs.createDirectory("/usuarios", "admin", Permission.PUBLIC);
        archivo(fs, "/mbr.bin", "admin", Permission.PUBLIC, 11);
        archivo(fs, "/sistema/boot.bin", "admin", Permission.PUBLIC, 23);
        archivo(fs, "/usuarios/notas.txt", "user1", Permission.PRIVATE, 28);
        archivo(fs, "/sistema/script.py", "admin", Permission.PUBLIC, 2);
        archivo(fs, "/sistema/core.dll", "admin", Permission.PUBLIC, 31);
        archivo(fs, "/sistema/config.sys", "admin", Permission.PUBLIC, 24);
        archivo(fs, "/usuarios/img1.png", "user1", Permission.PRIVATE, 4);
        archivo(fs, "/usuarios/data.csv", "user2", Permission.PUBLIC, 57);
        archivo(fs, "/usuarios/video.mp4", "user1", Permission.PUBLIC, 20);
        check(map.freeCount() == 0, "empieza con los 200 bloques ocupados");

        FsResult r = fs.delete("/usuarios");
        check(r.isOk(), "eliminar /usuarios");
        check(r.getBlocks() == 109, "libera los bloques de notas.txt, img1.png, data.csv y video.mp4 (28 + 4 + 57 + 20 = 109)");
        check(map.freeCount() == 109 && map.usedCount() == 91, "quedan 109 bloques libres y 91 ocupados");
        check(!fs.getTree().find("/usuarios").isFound() && !fs.getTree().find("/usuarios/notas.txt").isFound(),
                "el directorio y todo su contenido salen del arbol");
        check(fs.getTree().find("/sistema/boot.bin").isFound() && fs.read("/sistema/core.dll").getBlocks() == 31,
                "lo que estaba fuera del directorio no se toca");
        boolean liberados = true;
        for (int b = 34; b < 62; b++) liberados = liberados && map.isFree(b);
        for (int b = 119; b < 123; b++) liberados = liberados && map.isFree(b);
        for (int b = 123; b < 180; b++) liberados = liberados && map.isFree(b);
        for (int b = 180; b < 200; b++) liberados = liberados && map.isFree(b);
        check(liberados, "los bloques 34-61, 119-122, 123-179 y 180-199 quedan libres");
        check(coherente(fs, map), "el arbol y el disco coinciden");

        // varios niveles
        fs.createDirectory("/sistema/a", "admin", Permission.PUBLIC);
        fs.createDirectory("/sistema/a/b", "admin", Permission.PUBLIC);
        archivo(fs, "/sistema/a/x.bin", "admin", Permission.PUBLIC, 5);
        archivo(fs, "/sistema/a/b/y.bin", "admin", Permission.PUBLIC, 7);
        int libresAntes = map.freeCount();
        FsResult hondo = fs.delete("/sistema/a");
        check(hondo.isOk() && hondo.getBlocks() == 12 && map.freeCount() == libresAntes + 12,
                "elimina subdirectorios anidados y libera los bloques de todos sus archivos (12)");
        FsResult vacio = fs.delete("/sistema/a");
        check(!vacio.isOk(), "un directorio ya eliminado se rechaza");
        fs.createDirectory("/vacio", "admin", Permission.PUBLIC);
        check(fs.delete("/vacio").isOk() && fs.delete("/vacio").getBlocks() == 0, "un directorio vacio se elimina sin liberar bloques");
        check(coherente(fs, map), "el arbol y el disco siguen coincidiendo");
    }

    private static void consistenciaGeneral() {
        titulo("Consistencia tras mezclar operaciones");
        BlockMap map = DiskLimits.createMap(100);
        mapaActual = map;
        FileSystem fs = new FileSystem(new FileTree("admin"), new ChainAllocator(map), map);
        boolean siempre = true;
        for (int ronda = 0; ronda < 20; ronda++) {
            fs.createDirectory("/d" + ronda, "admin", Permission.PUBLIC);
            archivo(fs, "/d" + ronda + "/f.bin", "u", Permission.PUBLIC, 3);
            siempre = siempre && coherente(fs, map);
            if (ronda % 3 == 0) {
                fs.rename("/d" + ronda, "r" + ronda);
                siempre = siempre && coherente(fs, map);
            }
            if (ronda % 2 == 1) {
                fs.delete("/d" + ronda);
                siempre = siempre && coherente(fs, map);
            }
        }
        check(siempre, "en 20 rondas de crear, renombrar y eliminar, el arbol y el disco siempre coinciden");
        boolean duenosOk = true;
        BlockInfo[] todos = map.snapshot();
        for (int b = 0; b < todos.length; b++) {
            if (!todos[b].isFree()) {
                String dueno = todos[b].getOwner();
                duenosOk = duenosOk && fs.getTree().find(dueno).isFound();
            }
        }
        check(duenosOk, "cada bloque ocupado tiene como dueno una ruta que existe en el arbol");
    }
}