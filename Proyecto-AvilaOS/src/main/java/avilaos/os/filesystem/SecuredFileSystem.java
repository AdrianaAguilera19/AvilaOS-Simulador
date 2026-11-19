package avilaos.os.filesystem;

import avilaos.datastructures.DynamicArray;
import avilaos.enums.Permission;
import avilaos.interfaces.AccessControl;
import java.util.concurrent.Semaphore;

/**
 * El sistema de archivos con autorizacion
 */
public class SecuredFileSystem {

    private final FileSystem fs;
    private final AccessControl access;
    private final FileCreator creator;
    private final Semaphore lock = new Semaphore(1);   // protege el arbol y los bloques del sistema de archivos

    public SecuredFileSystem(FileSystem fs, AccessControl access) {
        if (fs == null || access == null) {
            throw new IllegalArgumentException("Falta el sistema de archivos o la autorizacion");
        }
        this.fs = fs;
        this.access = access;
        this.creator = new FileCreator(fs.getTree(), fs.getAllocator());
    }

    /**
     * El sistema de archivos sin proteccion: solo para armarlo al inicio y para pruebas.
     * Una vez arrancada la simulacion, todo acceso debe pasar por los metodos de esta clase.
     */
    public FileSystem getFileSystem() { return fs; }

    /** Ver la estructura es libre para todos los usuarios. */
    public DynamicArray<String> list() {
        lock.acquireUninterruptibly();
        try {
            return fs.list();
        } finally {
            lock.release();
        }
    }

    /**
     * Copia inmutable de todo el arbol, en preorden (cada directorio antes que su contenido), tomada
     * de forma consistente: nunca muestra una operacion a medias. Es lo unico que lee la interfaz.
     */
    public DynamicArray<FileSnapshot> snapshot() {
        DynamicArray<FileSnapshot> result = new DynamicArray<>();
        lock.acquireUninterruptibly();
        try {
            collect(fs.getTree().getRoot(), FileNode.ROOT_NAME, 0, result);
        } finally {
            lock.release();
        }
        return result;
    }

    private static void collect(FileNode node, String path, int depth, DynamicArray<FileSnapshot> out) {
        out.add(new FileSnapshot(path, depth, node));
        String prefix = FileNode.ROOT_NAME.equals(path) ? "" : path;
        for (int i = 0; i < node.getChildCount(); i++) {
            FileNode child = node.getChild(i);
            collect(child, prefix + "/" + child.getName(), depth + 1, out);
        }
    }

    public FsResult createDirectory(ActiveUser who, String path, Permission permission) {
        lock.acquireUninterruptibly();
        try {
            FileNode parent = parentOf(path);
            if (parent != null) {
                AccessDecision d = access.canCreateDirectory(who, parent);
                if (!d.isAllowed()) {
                    return denied(d);
                }
            } else if (who != null && !who.isAdmin()) {
                return FsResult.rejected("Permiso denegado: Solo el administrador puede crear directorios");
            }
            return fs.createDirectory(path, who == null ? null : who.getName(), permission);
        } finally {
            lock.release();
        }
    }

    public FsResult createFile(ActiveUser who, String directoryPath, String name, int sizeBlocks, Permission permission) {
        lock.acquireUninterruptibly();
        try {
            PathResult dir = fs.getTree().find(directoryPath);
            if (!dir.isFound()) {
                return FsResult.rejected(dir.getMessage());
            }
            AccessDecision d = access.canCreateFile(who, dir.getNode());
            if (!d.isAllowed()) {
                return denied(d);
            }
            return creator.create(directoryPath, name, sizeBlocks, who.getName(), who.getMode(), permission);
        } finally {
            lock.release();
        }
    }

    public FsResult read(ActiveUser who, String path) {
        lock.acquireUninterruptibly();
        try {
            PathResult found = fs.getTree().find(path);
            if (found.isFound()) {
                AccessDecision d = access.canRead(who, found.getNode());
                if (!d.isAllowed()) {
                    return denied(d);
                }
            }
            return fs.read(path);
        } finally {
            lock.release();
        }
    }

    public FsResult rename(ActiveUser who, String path, String newName) {
        lock.acquireUninterruptibly();
        try {
            PathResult found = fs.getTree().find(path);
            if (found.isFound()) {
                AccessDecision d = access.canUpdate(who, found.getNode());
                if (!d.isAllowed()) {
                    return denied(d);
                }
            }
            return fs.rename(path, newName);
        } finally {
            lock.release();
        }
    }

    public FsResult delete(ActiveUser who, String path) {
        lock.acquireUninterruptibly();
        try {
            PathResult found = fs.getTree().find(path);
            if (found.isFound()) {
                AccessDecision d = access.canDelete(who, found.getNode());
                if (!d.isAllowed()) {
                    return denied(d);
                }
            }
            return fs.delete(path);
        } finally {
            lock.release();
        }
    }

    private static FsResult denied(AccessDecision d) {
        return FsResult.rejected("Permiso denegado: " + d.getReason());
    }

    /** El directorio donde viviria la ruta indicada, o null si no se puede determinar (error de ruta). */
    private FileNode parentOf(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        String clean = path.trim();
        int slash = clean.lastIndexOf('/');
        if (slash < 0) {
            return null;
        }
        PathResult parent = fs.getTree().find(slash == 0 ? "/" : clean.substring(0, slash));
        return parent.isFound() ? parent.getNode() : null;
    }
}