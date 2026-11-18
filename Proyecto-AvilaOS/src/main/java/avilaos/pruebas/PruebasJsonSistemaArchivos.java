package avilaos.pruebas;

import avilaos.datastructures.BlockChain;
import avilaos.enums.Permission;
import avilaos.enums.UserMode;
import avilaos.model.hardware.BlockInfo;
import avilaos.model.hardware.BlockMap;
import avilaos.os.filesystem.FileCreator;
import avilaos.os.filesystem.FileNode;
import avilaos.os.filesystem.FileTree;
import avilaos.persistence.ConfigException;
import avilaos.persistence.FileSystemData;
import avilaos.persistence.FileSystemJsonManager;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pruebas de persistencia JSON del sistema de archivos (J01, J02, J03 y validaciones J07).
 * Se ejecuta como programa (main) y termina con "TODAS LAS PRUEBAS PASARON" o codigo 1 si falla.
 */
public class PruebasJsonSistemaArchivos {

    private static int fallas = 0;
    private static int verificaciones = 0;

    private static final String JSON_ENUNCIADO = """
            {
              "diskConfig": {
                "totalBlocks": 200,
                "initialHead": 50
              },
              "fileSystem": [
                { "path": "/mbr.bin", "type": "FILE", "startBlock": 0, "blocks": 11, "owner": "admin", "permissions": "public" },
                { "path": "/sistema", "type": "DIRECTORY", "owner": "admin", "permissions": "public" },
                { "path": "/usuarios", "type": "DIRECTORY", "owner": "admin", "permissions": "public" },
                { "path": "/sistema/boot.bin", "type": "FILE", "startBlock": 11, "blocks": 23, "owner": "admin", "permissions": "public" },
                { "path": "/usuarios/notas.txt", "type": "FILE", "startBlock": 34, "blocks": 28, "owner": "user1", "permissions": "private" },
                { "path": "/sistema/script.py", "type": "FILE", "startBlock": 62, "blocks": 2, "owner": "admin", "permissions": "public" },
                { "path": "/sistema/core.dll", "type": "FILE", "startBlock": 64, "blocks": 31, "owner": "admin", "permissions": "public" },
                { "path": "/sistema/config.sys", "type": "FILE", "startBlock": 95, "blocks": 24, "owner": "admin", "permissions": "public" },
                { "path": "/usuarios/img1.png", "type": "FILE", "startBlock": 119, "blocks": 4, "owner": "user1", "permissions": "private" },
                { "path": "/usuarios/data.csv", "type": "FILE", "startBlock": 123, "blocks": 57, "owner": "user2", "permissions": "public" },
                { "path": "/usuarios/video.mp4", "type": "FILE", "startBlock": 180, "blocks": 20, "owner": "user1", "permissions": "public" }
              ]
            }
            """;

    public static void main(String[] args) throws Exception {
        cargaEnunciadoYCadena();
        cargaConAreaDeSwap();
        guardarCargarGuardarIdentico();
        guardarYRecargarDiscoFragmentado();
        jsonInvalidosYDanados();

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

    private static boolean fallaConConfigException(String json, String etiqueta) {
        try {
            FileSystemJsonManager.parseJson(json);
            return false;
        } catch (ConfigException e) {
            System.out.println("          -> [" + etiqueta + "] " + e.getMessage());
            return true;
        }
    }

    private static void cargaEnunciadoYCadena() throws Exception {
        titulo("J01 + J02 - Carga del JSON del enunciado y reconstruccion de cadenas");
        FileSystemData data = FileSystemJsonManager.parseJson(JSON_ENUNCIADO);
        FileTree tree = data.getFileTree();
        BlockMap map = data.getBlockMap();

        check(data.getTotalBlocks() == 200, "totalBlocks = 200");
        check(data.getInitialHead() == 50, "initialHead = 50");
        check(tree.countFiles() == 9, "se cargaron los 9 archivos del enunciado");
        check(tree.countDirectories() == 3, "3 directorios contando la raiz (/ , /sistema, /usuarios)");
        check(map.usedCount() == 200 && map.freeCount() == 0, "todos los 200 bloques quedan ocupados");

        // Verificar cadena enlazada de /usuarios/notas.txt (34..61, 28 bloques)
        FileNode notas = tree.find("/usuarios/notas.txt").getNode();
        check(notas != null && notas.getOwner().equals("user1") && notas.getPermission() == Permission.PRIVATE,
                "notas.txt conserva dueno user1 y permiso PRIVATE");

        BlockChain cadenaNotas = data.getAllocator().chainOf(notas.getFirstBlock());
        check(cadenaNotas.size() == 28 && cadenaNotas.first() == 34 && cadenaNotas.get(27) == 61,
                "cadena de notas.txt va de 34 a 61 (28 bloques)");
        check(map.get(34).getNext() == 35 && map.get(61).getNext() == BlockInfo.NO_NEXT,
                "enlaces next correctos en inicio y fin de cadena");
        check("/usuarios/notas.txt".equals(map.get(40).getOwner()),
                "bloque intermedio guarda la ruta del archivo como dueno");
    }

    private static void cargaConAreaDeSwap() throws Exception {
        titulo("J01 - Carga con swap adicional (200 bloques FS + 100 swap)");
        FileSystemData data = FileSystemJsonManager.parseJson(JSON_ENUNCIADO, 100);
        check(data.getBlockMap().size() == 300, "BlockMap tiene 300 bloques totales");
        check(data.getAllocator().freeBlocks() == 0, "zona de archivos (0..199) llena");
        check(data.getSwapArea() != null && data.getSwapArea().getFirstBlock() == 200
                && data.getSwapArea().freeBlocks() == 100,
                "swap reservado en bloques 200..299 con 100 libres");
    }

    private static void guardarCargarGuardarIdentico() throws Exception {
        titulo("J03 - Guardar -> cargar -> guardar produce el mismo contenido");
        Path archivo1 = Files.createTempFile("avilaos_fs_1_", ".json");
        Path archivo2 = Files.createTempFile("avilaos_fs_2_", ".json");
        try {
            FileSystemData inicial = FileSystemJsonManager.parseJson(JSON_ENUNCIADO);
            FileSystemJsonManager.save(inicial, 50, archivo1.toString());

            FileSystemData recargado = FileSystemJsonManager.load(archivo1.toString());
            FileSystemJsonManager.save(recargado, recargado.getInitialHead(), archivo2.toString());

            String texto1 = Files.readString(archivo1);
            String texto2 = Files.readString(archivo2);
            check(texto1.equals(texto2), "primer guardado y segundo guardado son identicos");
            check(recargado.getFileTree().countFiles() == 9 && recargado.getBlockMap().usedCount() == 200,
                    "el estado recargado conserva los 9 archivos y 200 bloques");
        } finally {
            Files.deleteIfExists(archivo1);
            Files.deleteIfExists(archivo2);
        }
    }

    private static void guardarYRecargarDiscoFragmentado() throws Exception {
        titulo("J02 + J03 - Preservar cadenas no contiguas tras operaciones CRUD");
        FileSystemData data = FileSystemJsonManager.parseJson(JSON_ENUNCIADO);

        // Borramos dos archivos separados: script.py (62..63) e img1.png (119..122) -> 6 bloques libres en 2 huecos
        check(data.getFileSystem().delete("/sistema/script.py").isOk(), "elimina /sistema/script.py (bloques 62..63)");
        check(data.getFileSystem().delete("/usuarios/img1.png").isOk(), "elimina /usuarios/img1.png (bloques 119..122)");

        FileCreator creator = new FileCreator(data.getFileTree(), data.getAllocator());
        check(creator.create("/usuarios", "mezcla.bin", 6, "admin", UserMode.ADMIN, Permission.PUBLIC, 7).isOk(),
                "crea /usuarios/mezcla.bin de 6 bloques ocupando ambos huecos");

        String guardado1 = FileSystemJsonManager.toJson(data.getFileTree(), data.getAllocator(),
                data.getTotalBlocks(), 88);
        FileSystemData recargado = FileSystemJsonManager.parseJson(guardado1);
        String guardado2 = FileSystemJsonManager.toJson(recargado.getFileTree(), recargado.getAllocator(),
                recargado.getTotalBlocks(), recargado.getInitialHead());

        FileNode mezcla = recargado.getFileTree().find("/usuarios/mezcla.bin").getNode();
        BlockChain cadena = recargado.getAllocator().chainOf(mezcla.getFirstBlock());
        check(cadena.size() == 6 && cadena.get(0) == 62 && cadena.get(1) == 63
                && cadena.get(2) == 119 && cadena.get(5) == 122,
                "cadena fragmentada 62->63->119->120->121->122 reconstruida exacta");
        check(mezcla.getCreatorProcessId() == 7, "conserva el creatorProcessId al recargar");
        check(recargado.getInitialHead() == 88, "conserva la posicion del cabezal (88)");
        check(guardado1.equals(guardado2), "guardar -> cargar -> guardar identico incluso fragmentado");
    }

    private static void jsonInvalidosYDanados() {
        titulo("J01 + J07 - Rechazo controlado de JSON malformados o inconsistentes");

        check(fallaConConfigException("{ esto no es json", "sintaxis rota"), "rechaza JSON roto");
        check(fallaConConfigException("", "vacio"), "rechaza archivo vacio");
        check(fallaConConfigException("{ \"fileSystem\": [] }", "sin diskConfig"), "rechaza falta de diskConfig");
        check(fallaConConfigException("""
                { "diskConfig": { "totalBlocks": 50, "initialHead": 10 }, "fileSystem": [] }
                """, "totalBlocks fuera de rango"), "rechaza disco menor a 100 bloques");
        check(fallaConConfigException("""
                { "diskConfig": { "totalBlocks": 200, "initialHead": 200 }, "fileSystem": [] }
                """, "cabezal fuera de rango"), "rechaza cabezal igual a totalBlocks");
        check(fallaConConfigException("""
                {
                  "diskConfig": { "totalBlocks": 200, "initialHead": 50 },
                  "fileSystem": [
                    { "path": "/fantasma/a.txt", "type": "FILE", "startBlock": 0, "blocks": 5, "owner": "admin", "permissions": "public" }
                  ]
                }
                """, "padre inexistente"), "rechaza archivo cuyo directorio padre no existe");
        check(fallaConConfigException("""
                {
                  "diskConfig": { "totalBlocks": 200, "initialHead": 50 },
                  "fileSystem": [
                    { "path": "/a.bin", "type": "FILE", "startBlock": 0, "blocks": 10, "owner": "admin", "permissions": "public" },
                    { "path": "/b.bin", "type": "FILE", "startBlock": 5, "blocks": 10, "owner": "admin", "permissions": "public" }
                  ]
                }
                """, "bloques solapados"), "rechaza solapamiento de bloques entre archivos");
        check(fallaConConfigException("""
                {
                  "diskConfig": { "totalBlocks": "doscientos", "initialHead": 50 },
                  "fileSystem": []
                }
                """, "tipo invalido"), "rechaza texto donde se espera entero");
    }
}