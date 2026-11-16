package avilaos.os.filesystem;

import avilaos.datastructures.BlockChain;
import avilaos.datastructures.DynamicArray;
import avilaos.enums.PathError;
import avilaos.enums.Permission;
import avilaos.model.hardware.BlockMap;

/**
 * Operaciones del sistema de archivos sobre el arbol (FileTree) y el disco (ChainAllocator + BlockMap):
 * crear directorio, leer, renombrar y eliminar (archivo o directorio completo).
 *
 * Esta clase solo sabe COMO se hace cada operacion. QUIEN puede hacerla (administrador o usuario, dueno,
 * archivo publico o privado) lo decide la clase de autorizacion (F10) antes de llamar aqui, y el semaforo
 * que protege el sistema (F13) envuelve estas llamadas. Los errores se devuelven en un FsResult.
 */
public class FileSystem {

    private final FileTree tree;
    private final ChainAllocator allocator;
    private final BlockMap map;

    public FileSystem(FileTree tree, ChainAllocator allocator, BlockMap map) {
        if (tree == null || allocator == null || map == null) {
            throw new IllegalArgumentException("El sistema de archivos necesita arbol, asignador y mapa de bloques");
        }
        this.tree = tree;
        this.allocator = allocator;
        this.map = map;
    }

    public FileTree getTree() { return tree; }

    public ChainAllocator getAllocator() { return allocator; }

    // ------------------------------------------------------------------ F04: crear directorio

    /**
     * Crea un directorio nuevo en la ruta indicada. El directorio padre debe existir.
     * @param path ruta absoluta del directorio nuevo (por ejemplo /usuarios/docs)
     * @param owner dueno del directorio
     * @param permission PUBLIC o PRIVATE
     */
    public FsResult createDirectory(String path, String owner, Permission permission) {
        if (path == null || path.trim().isEmpty()) {
            return FsResult.rejected("La ruta esta vacia");
        }
        String clean = path.trim();
        PathResult existing = tree.find(clean);
        if (existing.isFound()) {
            return FsResult.rejected("Ya existe «" + clean + "»");
        }
        if (existing.getError() != PathError.NOT_FOUND) {
            return FsResult.rejected(existing.getMessage());   // ruta mal formada o que pasa por un archivo
        }
        int slash = clean.lastIndexOf('/');
        String parentPath = slash == 0 ? "/" : clean.substring(0, slash);
        String name = clean.substring(slash + 1);

        PathResult parent = tree.find(parentPath);
        if (!parent.isFound()) {
            return FsResult.rejected(parent.getMessage());
        }
        if (!parent.getNode().isDirectory()) {
            return FsResult.rejected("«" + parentPath + "» es un archivo: no puede contener directorios");
        }
        try {
            FileNode created = FileNode.directory(name, owner, permission);
            tree.add(parent.getNode(), created);
            return FsResult.done("Directorio «" + clean + "» creado", created, null, 0);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return FsResult.rejected(e.getMessage());
        }
    }

    // ------------------------------------------------------------------ F06: leer y listar

    /**
     * Lee un archivo: recorre su cadena de bloques en el disco, del primero al ultimo.
     * @return en caso de exito, getChain() trae los bloques en el orden en que se leyeron
     */
    public FsResult read(String path) {
        PathResult found = tree.find(path);
        if (!found.isFound()) {
            return FsResult.rejected(found.getMessage());
        }
        FileNode node = found.getNode();
        if (node.isDirectory()) {
            return FsResult.rejected("«" + tree.pathOf(node) + "» es un directorio: no se puede leer como archivo");
        }
        BlockChain chain = allocator.chainOf(node.getFirstBlock());
        if (chain.size() != node.getBlockCount()) {
            return FsResult.rejected("La cadena de bloques de «" + tree.pathOf(node) + "» esta danada: tiene "
                    + chain.size() + " bloques y deberia tener " + node.getBlockCount());
        }
        return FsResult.done("Se leyeron " + chain.size() + " bloques de «" + tree.pathOf(node) + "»",
                node, chain, chain.size());
    }

    /**
     * Lista toda la estructura (todos los usuarios pueden verla): una linea por directorio o archivo.
     */
    public DynamicArray<String> list() {
        DynamicArray<FileNode> all = tree.allNodes();
        DynamicArray<String> lines = new DynamicArray<>();
        for (int i = 0; i < all.size(); i++) {
            FileNode n = all.get(i);
            String blocks = n.isFile() ? n.getBlockCount() + " bloques desde el " + n.getFirstBlock() : "-";
            lines.add(tree.pathOf(n) + "  " + n.getType() + "  dueno=" + n.getOwner()
                    + "  " + n.getPermission() + "  " + blocks);
        }
        return lines;
    }

    // ------------------------------------------------------------------ F07: renombrar

    /**
     * Cambia el nombre de un archivo o directorio. Si es un directorio, la ruta de todo su contenido
     * cambia sola (la ruta se calcula desde el arbol) y el dueno de sus bloques en el mapa se actualiza.
     */
    public FsResult rename(String path, String newName) {
        PathResult found = tree.find(path);
        if (!found.isFound()) {
            return FsResult.rejected(found.getMessage());
        }
        FileNode node = found.getNode();
        if (node == tree.getRoot()) {
            return FsResult.rejected("La raiz no se puede renombrar");
        }
        String oldPath = tree.pathOf(node);
        try {
            node.rename(newName);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return FsResult.rejected(e.getMessage());
        }
        refreshBlockOwners(node);
        return FsResult.done("«" + oldPath + "» ahora es «" + tree.pathOf(node) + "»", node, null, 0);
    }

    /** El mapa de bloques guarda la ruta como dueno: al renombrar hay que ponerle la ruta nueva. */
    private void refreshBlockOwners(FileNode node) {
        if (node.isFile()) {
            String path = tree.pathOf(node);
            BlockChain chain = allocator.chainOf(node.getFirstBlock());
            for (int i = 0; i < chain.size(); i++) {
                map.setOwner(chain.get(i), path);
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            refreshBlockOwners(node.getChild(i));
        }
    }

    // ------------------------------------------------------------------ F08 y F09: eliminar

    /**
     * Elimina un archivo (libera sus bloques) o un directorio completo (libera los bloques de todos los
     * archivos que contiene, en cualquier nivel). La raiz no se puede eliminar.
     */
    public FsResult delete(String path) {
        PathResult found = tree.find(path);
        if (!found.isFound()) {
            return FsResult.rejected(found.getMessage());
        }
        FileNode node = found.getNode();
        if (node == tree.getRoot()) {
            return FsResult.rejected("La raiz no se puede eliminar");
        }
        String oldPath = tree.pathOf(node);
        int[] counts = new int[3];                 // archivos, directorios, bloques liberados
        releaseSubtree(node, counts);
        node.getParent().removeChild(node);
        String detail = node.isFile()
                ? "archivo de " + counts[2] + " bloques"
                : counts[1] + " directorios, " + counts[0] + " archivos";
        return FsResult.done("«" + oldPath + "» eliminado (" + detail + "); " + counts[2] + " bloques liberados",
                node, null, counts[2]);
    }

    private void releaseSubtree(FileNode node, int[] counts) {
        if (node.isFile()) {
            counts[0]++;
            counts[2] += allocator.free(node.getFirstBlock());
            return;
        }
        counts[1]++;
        for (int i = 0; i < node.getChildCount(); i++) {
            releaseSubtree(node.getChild(i), counts);
        }
    }
}