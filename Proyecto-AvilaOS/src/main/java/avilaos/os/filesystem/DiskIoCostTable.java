package avilaos.os.filesystem;

import avilaos.enums.IOOperationType;
import avilaos.enums.NodeType;
import avilaos.enums.PathError;
import avilaos.interfaces.AccessControl;
import avilaos.model.hardware.BlockMap;
import avilaos.model.process.PCB;

/**
 * Tabla de costos y validacion previa de operaciones de E/S sobre el sistema de archivos (E08, E09 y E10).
 *
 * Bloque destino y bloques transferidos (E08):
 * - CREATE (archivo): primer bloque libre del disco; fileSizeBlocks bloques a transferir.
 * - CREATE (directorio): bloque 0 (metadatos); 1 bloque a transferir.
 * - READ: firstBlock del archivo; blockCount bloques a transferir.
 * - UPDATE: firstBlock del archivo (o primer bloque del subarbol / 0 si es directorio); 1 bloque.
 * - DELETE: firstBlock del archivo (o primer bloque del subarbol / 0 si es directorio);
 *           blockCount del archivo (o suma de bloques del subarbol, minimo 1).
 *
 * Reparto de instructions (E10):
 * - 1a instruccion: llamada al sistema de E/S en modo SO (ExecutionMode.S_O, suma 1 a kernelEntries).
 * - Restantes (instructions - 1): se ejecutan en CPU al despertar de la E/S.
 */
public final class DiskIoCostTable {

    /** Sufijo por defecto cuando un proceso UPDATE viene del JSON sin campo newName. */
    public static final String DEFAULT_UPDATE_SUFFIX = "_editado";

    private DiskIoCostTable() { }

    /** Resultado de validar y planificar una solicitud de E/S antes de encolarla en el disco. */
    public static final class IoPlan {
        private final boolean valid;
        private final boolean permissionDenied;
        private final int targetBlock;
        private final int blocksToTransfer;
        private final String resolvedNewName;
        private final String errorMessage;

        private IoPlan(boolean valid, boolean permissionDenied, int targetBlock,
                int blocksToTransfer, String resolvedNewName, String errorMessage) {
            this.valid = valid;
            this.permissionDenied = permissionDenied;
            this.targetBlock = targetBlock;
            this.blocksToTransfer = blocksToTransfer;
            this.resolvedNewName = resolvedNewName;
            this.errorMessage = errorMessage;
        }

        public static IoPlan ok(int targetBlock, int blocksToTransfer, String resolvedNewName) {
            return new IoPlan(true, false, targetBlock, blocksToTransfer, resolvedNewName, null);
        }

        public static IoPlan failed(String errorMessage) {
            return new IoPlan(false, false, -1, 0, null, errorMessage);
        }

        public static IoPlan denied(String reason) {
            return new IoPlan(false, true, -1, 0, null, "Permiso denegado: " + reason);
        }

        public boolean isValid() { return valid; }
        public boolean isPermissionDenied() { return permissionDenied; }
        public int getTargetBlock() { return targetBlock; }
        public int getBlocksToTransfer() { return blocksToTransfer; }
        public String getResolvedNewName() { return resolvedNewName; }
        public String getErrorMessage() { return errorMessage; }
    }

    /** Llamado desde SecuredFileSystem.planIo() bajo el semaforo del FS. */
    static IoPlan plan(PCB pcb, NodeType createNodeType, FileSystem fs,
            AccessControl access, BlockMap map) {
        if (pcb == null || !pcb.hasDiskOperation()) {
            return IoPlan.failed("El proceso no tiene una operacion de E/S configurada");
        }
        if (fs == null || access == null || map == null) {
            return IoPlan.failed("Falta el sistema de archivos, la autorizacion o el mapa de bloques");
        }

        ActiveUser who = resolveUser(pcb);
        FileTree tree = fs.getTree();
        ChainAllocator allocator = fs.getAllocator();
        String path = pcb.getTargetPath();
        IOOperationType op = pcb.getDiskOperation();

        switch (op) {
            case CREATE:
                return planCreate(pcb, who, createNodeType, tree, allocator, access, map);
            case READ:
                return planRead(who, path, tree, access);
            case UPDATE:
                return planUpdate(pcb, who, path, tree, access);
            case DELETE:
                return planDelete(who, path, tree, access);
            default:
                return IoPlan.failed("Operacion no soportada para proceso de archivo: " + op);
        }
    }

    public static ActiveUser resolveUser(PCB pcb) {
        if (pcb != null && pcb.hasUser()) {
            return new ActiveUser(pcb.getUserName(), pcb.getUserMode());
        }
        return ActiveUser.admin();
    }

    private static IoPlan planCreate(PCB pcb, ActiveUser who, NodeType createNodeType,
            FileTree tree, ChainAllocator allocator, AccessControl access, BlockMap map) {
        String clean = pcb.getTargetPath().trim();
        PathResult existing = tree.find(clean);
        if (existing.isFound()) {
            return IoPlan.failed("Ya existe «" + clean + "»");
        }
        if (existing.getError() != PathError.NOT_FOUND) {
            return IoPlan.failed(existing.getMessage());
        }

        int slash = clean.lastIndexOf('/');
        String parentPath = slash == 0 ? "/" : clean.substring(0, slash);
        String name = clean.substring(slash + 1).trim();
        if (name.isEmpty()) {
            return IoPlan.failed("El nombre del elemento a crear esta vacio");
        }

        PathResult parentRes = tree.find(parentPath);
        if (!parentRes.isFound()) {
            return IoPlan.failed(parentRes.getMessage());
        }
        FileNode parent = parentRes.getNode();
        if (!parent.isDirectory()) {
            return IoPlan.failed("«" + parentPath + "» es un archivo, no un directorio");
        }

        if (createNodeType == NodeType.DIRECTORY) {
            AccessDecision d = access.canCreateDirectory(who, parent);
            if (!d.isAllowed()) {
                return IoPlan.denied(d.getReason());
            }
            return IoPlan.ok(0, 1, null);
        }

        AccessDecision d = access.canCreateFile(who, parent);
        if (!d.isAllowed()) {
            return IoPlan.denied(d.getReason());
        }

        int needed = pcb.getFileSizeBlocks();
        if (needed < 1) {
            return IoPlan.failed("El archivo debe ocupar al menos 1 bloque");
        }
        int free = allocator.freeBlocks();
        if (needed > free) {
            return IoPlan.failed("No hay bloques libres suficientes: se piden " + needed + " y hay " + free);
        }
        int firstFree = map.firstFreeFrom(0);
        if (firstFree < 0) {
            return IoPlan.failed("No se encontro un bloque inicial libre en el disco");
        }
        return IoPlan.ok(firstFree, needed, null);
    }

    private static IoPlan planRead(ActiveUser who, String path, FileTree tree, AccessControl access) {
        PathResult found = tree.find(path);
        if (!found.isFound()) {
            return IoPlan.failed(found.getMessage());
        }
        FileNode node = found.getNode();
        AccessDecision d = access.canRead(who, node);
        if (!d.isAllowed()) {
            return IoPlan.denied(d.getReason());
        }
        if (node.isDirectory()) {
            return IoPlan.failed("«" + path + "» es un directorio: no se puede leer como archivo");
        }
        return IoPlan.ok(node.getFirstBlock(), node.getBlockCount(), null);
    }

    private static IoPlan planUpdate(PCB pcb, ActiveUser who, String path, FileTree tree, AccessControl access) {
        PathResult found = tree.find(path);
        if (!found.isFound()) {
            return IoPlan.failed(found.getMessage());
        }
        FileNode node = found.getNode();
        if (node.isRoot()) {
            return IoPlan.failed("La raiz no se puede renombrar");
        }
        AccessDecision d = access.canUpdate(who, node);
        if (!d.isAllowed()) {
            return IoPlan.denied(d.getReason());
        }

        String targetNewName = resolveUpdateName(node, pcb.getNewName());
        if (targetNewName.indexOf('/') >= 0) {
            return IoPlan.failed("El nombre nuevo no puede contener «/»");
        }
        FileNode parent = node.getParent();
        if (parent != null) {
            FileNode collision = parent.findChild(targetNewName);
            if (collision != null && collision != node) {
                return IoPlan.failed("Ya existe «" + targetNewName + "» en ese directorio");
            }
        }

        int targetBlock = node.isFile() ? node.getFirstBlock() : firstBlockInSubtreeOrZero(node);
        return IoPlan.ok(targetBlock, 1, targetNewName);
    }

    private static IoPlan planDelete(ActiveUser who, String path, FileTree tree, AccessControl access) {
        PathResult found = tree.find(path);
        if (!found.isFound()) {
            return IoPlan.failed(found.getMessage());
        }
        FileNode node = found.getNode();
        if (node.isRoot()) {
            return IoPlan.failed("La raiz no se puede eliminar");
        }
        AccessDecision d = access.canDelete(who, node);
        if (!d.isAllowed()) {
            return IoPlan.denied(d.getReason());
        }

        int targetBlock = node.isFile() ? node.getFirstBlock() : firstBlockInSubtreeOrZero(node);
        int blocks = node.isFile() ? node.getBlockCount() : Math.max(1, countBlocksInSubtree(node));
        return IoPlan.ok(targetBlock, blocks, null);
    }

    /** Si newName viene null (como en el JSON del enunciado), agrega "_editado" antes de la extension. */
    public static String resolveUpdateName(FileNode node, String explicitNewName) {
        if (explicitNewName != null && !explicitNewName.trim().isEmpty()) {
            return explicitNewName.trim();
        }
        String current = node.getName();
        int dot = current.lastIndexOf('.');
        String base = dot > 0 ? current.substring(0, dot) : current;
        String ext = dot > 0 ? current.substring(dot) : "";
        String candidate = base + DEFAULT_UPDATE_SUFFIX + ext;

        FileNode parent = node.getParent();
        if (parent == null || parent.findChild(candidate) == null) {
            return candidate;
        }
        int attempt = 2;
        while (parent.findChild(base + DEFAULT_UPDATE_SUFFIX + "_" + attempt + ext) != null) {
            attempt++;
        }
        return base + DEFAULT_UPDATE_SUFFIX + "_" + attempt + ext;
    }

    private static int firstBlockInSubtreeOrZero(FileNode node) {
        if (node.isFile()) {
            return node.getFirstBlock();
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            int found = firstBlockInSubtreeOrZero(node.getChild(i));
            if (found >= 0) {
                return found;
            }
        }
        return 0;
    }

    private static int countBlocksInSubtree(FileNode node) {
        if (node.isFile()) {
            return node.getBlockCount();
        }
        int total = 0;
        for (int i = 0; i < node.getChildCount(); i++) {
            total += countBlocksInSubtree(node.getChild(i));
        }
        return total;
    }
}