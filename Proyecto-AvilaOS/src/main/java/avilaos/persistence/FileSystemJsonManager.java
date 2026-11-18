package avilaos.persistence;

import avilaos.datastructures.BlockChain;
import avilaos.datastructures.DynamicArray;
import avilaos.enums.BlockUse;
import avilaos.enums.NodeType;
import avilaos.enums.Permission;
import avilaos.model.hardware.BlockInfo;
import avilaos.model.hardware.BlockMap;
import avilaos.os.disk.DiskLimits;
import avilaos.os.filesystem.ChainAllocator;
import avilaos.os.filesystem.FileNode;
import avilaos.os.filesystem.FileTree;
import avilaos.os.filesystem.PathResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Carga y guarda el estado del sistema de archivos en formato JSON (J01, J02, J03 y J07).
 * Usa JsonObject/JsonArray de Gson y vuelca directamente a las estructuras propias del proyecto
 * sin emplear colecciones de java.util.
 */
public final class FileSystemJsonManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_ROOT_OWNER = "admin";

    private FileSystemJsonManager() { }

    // ------------------------------------------------------------------ J01 + J02: Carga

    /** Carga el JSON del sistema de archivos sin reservar area de swap adicional. */
    public static FileSystemData load(String path) throws ConfigException {
        return load(path, 0);
    }

    /**
     * Carga el JSON del sistema de archivos y reserva swapSizeBlocks adicionales al final
     * del BlockMap para los procesos suspendidos (0 si aun no se ha combinado con el JSON de procesos).
     */
    public static FileSystemData load(String path, int swapSizeBlocks) throws ConfigException {
        if (path == null || path.trim().isEmpty()) {
            throw new ConfigException("La ruta del archivo JSON esta vacia");
        }
        try (Reader reader = Files.newBufferedReader(Paths.get(path), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            return parseElement(root, swapSizeBlocks);
        } catch (IOException e) {
            throw new ConfigException("No se pudo leer el archivo del sistema de archivos: " + e.getMessage(), e);
        } catch (JsonParseException e) {
            throw new ConfigException("El archivo del sistema de archivos no es un JSON valido: " + e.getMessage(), e);
        }
    }

    /** Parsea un JSON directamente desde un String (util para pruebas y validacion previa). */
    public static FileSystemData parseJson(String jsonText) throws ConfigException {
        return parseJson(jsonText, 0);
    }

    public static FileSystemData parseJson(String jsonText, int swapSizeBlocks) throws ConfigException {
        if (jsonText == null || jsonText.trim().isEmpty()) {
            throw new ConfigException("El contenido JSON del sistema de archivos esta vacio");
        }
        try {
            JsonElement root = JsonParser.parseString(jsonText);
            return parseElement(root, swapSizeBlocks);
        } catch (JsonParseException e) {
            throw new ConfigException("El contenido no es un JSON valido: " + e.getMessage(), e);
        }
    }

    private static FileSystemData parseElement(JsonElement rootElement, int swapSizeBlocks) throws ConfigException {
        if (swapSizeBlocks < 0) {
            throw new ConfigException("El tamano del swap no puede ser negativo: " + swapSizeBlocks);
        }
        if (rootElement == null || rootElement.isJsonNull()) {
            throw new ConfigException("El archivo JSON del sistema de archivos esta vacio");
        }
        if (!rootElement.isJsonObject()) {
            throw new ConfigException("La raiz del JSON del sistema de archivos debe ser un objeto {}");
        }

        JsonObject root = rootElement.getAsJsonObject();

        // 1. Validar diskConfig
        JsonObject diskConfig = requireObject(root, "diskConfig", "la raiz del JSON");
        int totalBlocks = requireInt(diskConfig, "totalBlocks", "diskConfig");
        int initialHead = requireInt(diskConfig, "initialHead", "diskConfig");

        String blocksErr = DiskLimits.checkTotalBlocks(totalBlocks);
        if (blocksErr != null) {
            throw new ConfigException(blocksErr);
        }
        String headErr = DiskLimits.checkHeadStart(totalBlocks, initialHead);
        if (headErr != null) {
            throw new ConfigException(headErr);
        }

        // 2. Validar arreglo fileSystem
        JsonArray entries = requireArray(root, "fileSystem", "la raiz del JSON");

        BlockMap map = new BlockMap(totalBlocks + swapSizeBlocks);
        FileTree tree = new FileTree(DEFAULT_ROOT_OWNER);

        for (int i = 0; i < entries.size(); i++) {
            String ctx = "fileSystem[" + i + "]";
            JsonElement el = entries.get(i);
            if (el == null || !el.isJsonObject()) {
                throw new ConfigException("El elemento " + ctx + " debe ser un objeto JSON");
            }
            loadEntry(el.getAsJsonObject(), ctx, totalBlocks, map, tree);
        }

        return new FileSystemData(totalBlocks, initialHead, swapSizeBlocks, map, tree);
    }

    private static void loadEntry(JsonObject entry, String ctx, int totalBlocks,
            BlockMap map, FileTree tree) throws ConfigException {

        String rawPath = requireString(entry, "path", ctx);
        String[] parts = validateAndSplitPath(rawPath, ctx);
        String cleanPath = parts[0];
        String parentPath = parts[1];
        String name = parts[2];

        // Verificar que no exista ya ese nodo
        if (tree.find(cleanPath).isFound()) {
            throw new ConfigException("Ruta duplicada en " + ctx + ": '" + cleanPath + "'");
        }

        // Verificar que el directorio padre exista y sea directorio
        PathResult parentRes = tree.find(parentPath);
        if (!parentRes.isFound()) {
            throw new ConfigException("El directorio padre '" + parentPath + "' no existe para '" + cleanPath
                    + "' (" + ctx + ")");
        }
        FileNode parent = parentRes.getNode();
        if (!parent.isDirectory()) {
            throw new ConfigException("El padre '" + parentPath + "' no es un directorio (" + ctx + ")");
        }

        // Validar tipo, dueno y permisos con enums del proyecto
        String typeText = requireString(entry, "type", ctx);
        NodeType type = NodeType.fromText(typeText);
        if (type == null) {
            throw new ConfigException("Tipo de nodo desconocido en " + ctx + ": '" + typeText
                    + "' (use FILE o DIRECTORY)");
        }

        String owner = requireString(entry, "owner", ctx);

        String permText = requireString(entry, "permissions", ctx);
        Permission permission = Permission.fromText(permText);
        if (permission == null) {
            throw new ConfigException("Permiso desconocido en " + ctx + ": '" + permText
                    + "' (use public o private)");
        }

        int creatorProcessId = optionalCreator(entry, ctx);

        if (type == NodeType.DIRECTORY) {
            FileNode dir = FileNode.directory(name, owner, permission, creatorProcessId);
            tree.add(parent, dir);
            return;
        }

        // Tipo FILE: validar bloques y reconstruir la cadena enlazada (J02)
        int startBlock = requireInt(entry, "startBlock", ctx);
        int blocks = requireInt(entry, "blocks", ctx);

        if (blocks < 1) {
            throw new ConfigException("El archivo '" + cleanPath + "' debe ocupar al menos 1 bloque (" + ctx + ")");
        }
        if (startBlock < 0 || startBlock >= totalBlocks) {
            throw new ConfigException("startBlock fuera de rango (0.." + (totalBlocks - 1)
                    + ") en '" + cleanPath + "': " + startBlock);
        }

        int[] blockNumbers = resolveChainBlocks(entry, ctx, cleanPath, startBlock, blocks, totalBlocks);
        allocateChain(blockNumbers, cleanPath, map);

        FileNode file = FileNode.file(name, owner, permission, startBlock, blocks, creatorProcessId);
        tree.add(parent, file);
    }

    /**
     * J02: Determina los bloques que forman la cadena del archivo.
     * - Si el JSON trae el arreglo opcional "chain" (archivos fragmentados guardados con J03), lo valida y usa.
     * - Si no lo trae (formato del enunciado), reconstruye los bloques contiguos startBlock..startBlock+blocks-1.
     */
    private static int[] resolveChainBlocks(JsonObject entry, String ctx, String cleanPath,
            int startBlock, int blocks, int totalBlocks) throws ConfigException {

        if (entry.has("chain") && !entry.get("chain").isJsonNull()) {
            JsonArray chainArr = requireArray(entry, "chain", ctx);
            if (chainArr.size() != blocks) {
                throw new ConfigException("En '" + cleanPath + "', el arreglo 'chain' tiene " + chainArr.size()
                        + " elementos pero 'blocks' indica " + blocks);
            }
            int[] result = new int[blocks];
            for (int i = 0; i < blocks; i++) {
                int b = parseArrayInt(chainArr.get(i), ctx + ".chain[" + i + "]");
                if (b < 0 || b >= totalBlocks) {
                    throw new ConfigException("Bloque " + b + " fuera de rango (0.." + (totalBlocks - 1)
                            + ") en la cadena de '" + cleanPath + "'");
                }
                for (int j = 0; j < i; j++) {
                    if (result[j] == b) {
                        throw new ConfigException("Bloque repetido " + b + " en la cadena de '" + cleanPath + "'");
                    }
                }
                result[i] = b;
            }
            if (result[0] != startBlock) {
                throw new ConfigException("En '" + cleanPath + "', el primer bloque de 'chain' (" + result[0]
                        + ") no coincide con startBlock (" + startBlock + ")");
            }
            return result;
        }

        if (startBlock + blocks > totalBlocks) {
            throw new ConfigException("Los bloques de '" + cleanPath + "' (" + startBlock + ".."
                    + (startBlock + blocks - 1) + ") superan el limite del disco (" + totalBlocks + ")");
        }
        int[] result = new int[blocks];
        for (int i = 0; i < blocks; i++) {
            result[i] = startBlock + i;
        }
        return result;
    }

    /** Ocupa los bloques en BlockMap verificando que ninguno este solapado y los enlaza en cadena. */
    private static void allocateChain(int[] blockNumbers, String cleanPath, BlockMap map) throws ConfigException {
        for (int i = 0; i < blockNumbers.length; i++) {
            int b = blockNumbers[i];
            if (!map.isFree(b)) {
                BlockInfo current = map.get(b);
                throw new ConfigException("Solapamiento en el bloque " + b + " al cargar '" + cleanPath
                        + "': ya esta ocupado por '" + current.getOwner() + "'");
            }
        }
        for (int i = 0; i < blockNumbers.length; i++) {
            int b = blockNumbers[i];
            if (!map.occupy(b, BlockUse.FILE, cleanPath)) {
                throw new ConfigException("No se pudo reservar el bloque " + b + " para '" + cleanPath + "'");
            }
            if (i > 0) {
                map.setNext(blockNumbers[i - 1], b);
            }
        }
    }

    // ------------------------------------------------------------------ J03: Guardado

    /** Guarda en disco el estado actual del sistema de archivos con la posicion actual del cabezal. */
    public static void save(FileSystemData data, int currentHead, String path) throws ConfigException {
        if (data == null) {
            throw new ConfigException("No hay datos del sistema de archivos para guardar");
        }
        save(data.getFileTree(), data.getAllocator(), data.getTotalBlocks(), currentHead, path);
    }

    /** Guarda en archivo JSON el arbol, bloques, duenos, permisos y cabezal actual. */
    public static void save(FileTree tree, ChainAllocator allocator, int totalBlocks,
            int currentHead, String path) throws ConfigException {
        if (path == null || path.trim().isEmpty()) {
            throw new ConfigException("La ruta de destino para guardar el JSON esta vacia");
        }
        String json = toJson(tree, allocator, totalBlocks, currentHead);
        try (Writer writer = Files.newBufferedWriter(Paths.get(path), StandardCharsets.UTF_8)) {
            writer.write(json);
        } catch (IOException e) {
            throw new ConfigException("No se pudo guardar el sistema de archivos: " + e.getMessage(), e);
        }
    }

    /** Serializa el estado del sistema de archivos a texto JSON con el formato del enunciado. */
    public static String toJson(FileTree tree, ChainAllocator allocator, int totalBlocks,
            int currentHead) throws ConfigException {
        if (tree == null || allocator == null) {
            throw new ConfigException("Falta el arbol o el asignador de bloques para guardar el sistema de archivos");
        }
        String blocksErr = DiskLimits.checkTotalBlocks(totalBlocks);
        if (blocksErr != null) {
            throw new ConfigException(blocksErr);
        }
        String headErr = DiskLimits.checkHeadStart(totalBlocks, currentHead);
        if (headErr != null) {
            throw new ConfigException(headErr);
        }

        JsonObject root = new JsonObject();

        JsonObject diskConfig = new JsonObject();
        diskConfig.addProperty("totalBlocks", totalBlocks);
        diskConfig.addProperty("initialHead", currentHead);
        root.add("diskConfig", diskConfig);

        JsonArray fsArray = new JsonArray();
        // allNodes() recorre en preorden: cada directorio aparece antes que sus hijos
        DynamicArray<FileNode> nodes = tree.allNodes();
        for (int i = 0; i < nodes.size(); i++) {
            FileNode node = nodes.get(i);
            if (node.isRoot()) {
                continue;
            }
            JsonObject item = new JsonObject();
            item.addProperty("path", tree.pathOf(node));
            item.addProperty("type", node.getType().name());

            if (node.isFile()) {
                item.addProperty("startBlock", node.getFirstBlock());
                item.addProperty("blocks", node.getBlockCount());

                BlockChain chain = allocator.chainOf(node.getFirstBlock());
                if (!isContiguous(chain, node.getFirstBlock(), node.getBlockCount())) {
                    JsonArray chainArr = new JsonArray();
                    for (int k = 0; k < chain.size(); k++) {
                        chainArr.add(chain.get(k));
                    }
                    item.add("chain", chainArr);
                }
            }

            item.addProperty("owner", node.getOwner());
            item.addProperty("permissions", node.getPermission().name().toLowerCase());

            if (node.hasCreator()) {
                item.addProperty("creatorProcessId", node.getCreatorProcessId());
            }

            fsArray.add(item);
        }

        root.add("fileSystem", fsArray);
        return GSON.toJson(root);
    }

    private static boolean isContiguous(BlockChain chain, int startBlock, int expectedSize) {
        if (chain == null || chain.size() != expectedSize) {
            return false;
        }
        for (int i = 0; i < expectedSize; i++) {
            if (chain.get(i) != startBlock + i) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ Validadores auxiliares

    private static String[] validateAndSplitPath(String rawPath, String ctx) throws ConfigException {
        String clean = rawPath.trim();
        if (!clean.startsWith("/")) {
            throw new ConfigException("La ruta en " + ctx + " debe empezar con '/': '" + clean + "'");
        }
        if (clean.equals("/")) {
            throw new ConfigException("La raiz '/' se crea automaticamente y no debe listarse en " + ctx);
        }
        if (clean.endsWith("/") || clean.contains("//")) {
            throw new ConfigException("La ruta en " + ctx + " tiene segmentos vacios: '" + clean + "'");
        }
        int lastSlash = clean.lastIndexOf('/');
        String parentPath = (lastSlash == 0) ? "/" : clean.substring(0, lastSlash);
        String name = clean.substring(lastSlash + 1).trim();
        if (name.isEmpty()) {
            throw new ConfigException("El nombre del archivo o directorio esta vacio en " + ctx);
        }
        return new String[]{clean, parentPath, name};
    }

    private static JsonObject requireObject(JsonObject obj, String field, String ctx) throws ConfigException {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            throw new ConfigException("Falta el objeto '" + field + "' en " + ctx);
        }
        JsonElement el = obj.get(field);
        if (!el.isJsonObject()) {
            throw new ConfigException("El campo '" + field + "' en " + ctx + " debe ser un objeto JSON");
        }
        return el.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonObject obj, String field, String ctx) throws ConfigException {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            throw new ConfigException("Falta el arreglo '" + field + "' en " + ctx);
        }
        JsonElement el = obj.get(field);
        if (!el.isJsonArray()) {
            throw new ConfigException("El campo '" + field + "' en " + ctx + " debe ser un arreglo JSON");
        }
        return el.getAsJsonArray();
    }

    private static String requireString(JsonObject obj, String field, String ctx) throws ConfigException {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            throw new ConfigException("Falta el campo '" + field + "' en " + ctx);
        }
        JsonElement el = obj.get(field);
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
            throw new ConfigException("El campo '" + field + "' en " + ctx + " debe ser texto");
        }
        String value = el.getAsString().trim();
        if (value.isEmpty()) {
            throw new ConfigException("El campo '" + field + "' en " + ctx + " no puede estar vacio");
        }
        return value;
    }

    private static int requireInt(JsonObject obj, String field, String ctx) throws ConfigException {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            throw new ConfigException("Falta el campo '" + field + "' en " + ctx);
        }
        return parseArrayInt(obj.get(field), "'" + field + "' en " + ctx);
    }

    private static int parseArrayInt(JsonElement el, String label) throws ConfigException {
        if (el == null || !el.isJsonPrimitive()) {
            throw new ConfigException("El campo " + label + " debe ser un numero entero");
        }
        JsonPrimitive prim = el.getAsJsonPrimitive();
        if (!prim.isNumber()) {
            throw new ConfigException("El campo " + label + " debe ser un numero entero (no texto ni booleano)");
        }
        String raw = prim.getAsString().trim();
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new ConfigException("El campo " + label + " debe ser un numero entero valido: " + raw);
        }
    }

    private static int optionalCreator(JsonObject entry, String ctx) throws ConfigException {
        if (!entry.has("creatorProcessId") || entry.get("creatorProcessId").isJsonNull()) {
            return FileNode.NO_CREATOR;
        }
        int id = requireInt(entry, "creatorProcessId", ctx);
        if (id < FileNode.NO_CREATOR) {
            throw new ConfigException("creatorProcessId invalido en " + ctx + ": " + id);
        }
        return id;
    }
}