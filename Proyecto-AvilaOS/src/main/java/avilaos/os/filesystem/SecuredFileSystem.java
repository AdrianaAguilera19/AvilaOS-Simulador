package avilaos.os.filesystem;

import avilaos.datastructures.DynamicArray;
import avilaos.enums.Permission;
import avilaos.interfaces.AccessControl;

/**
 * El sistema de archivos con autorizacion: cada operacion pregunta primero a AccessControl y solo si esta
 * permitida se la pasa a FileSystem. Si no, devuelve un rechazo con el motivo, sin tocar nada y sin lanzar
 * excepciones. Es la puerta que usan los procesos de E/S (nunca la interfaz grafica de forma directa).
 */
public class SecuredFileSystem {

    private final FileSystem fs;
    private final AccessControl access;
    private final FileCreator creator;

    public SecuredFileSystem(FileSystem fs, AccessControl access) {
        if (fs == null || access == null) {
            throw new IllegalArgumentException("Falta el sistema de archivos o la autorizacion");
        }
        this.fs = fs;
        this.access = access;
        this.creator = new FileCreator(fs.getTree(), fs.getAllocator());
    }

    public FileSystem getFileSystem() { return fs; }

    /** Ver la estructura es libre para todos los usuarios. */
    public DynamicArray<String> list() {
        return fs.list();
    }

    public FsResult createDirectory(ActiveUser who, String path, Permission permission) {
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
    }

        public FsResult createFile(ActiveUser who, String directoryPath, String name, int sizeBlocks, Permission permission) {
        PathResult dir = fs.getTree().find(directoryPath);
        if (!dir.isFound()) {
            return FsResult.rejected(dir.getMessage());
        }
        AccessDecision d = access.canCreateFile(who, dir.getNode());
        if (!d.isAllowed()) {
            return denied(d);
        }
        return creator.create(directoryPath, name, sizeBlocks, who.getName(), who.getMode(), permission);
    }

    public FsResult read(ActiveUser who, String path) {
        PathResult found = fs.getTree().find(path);
        if (found.isFound()) {
            AccessDecision d = access.canRead(who, found.getNode());
            if (!d.isAllowed()) {
                return denied(d);
            }
        }
        return fs.read(path);
    }

    public FsResult rename(ActiveUser who, String path, String newName) {
        PathResult found = fs.getTree().find(path);
        if (found.isFound()) {
            AccessDecision d = access.canUpdate(who, found.getNode());
            if (!d.isAllowed()) {
                return denied(d);
            }
        }
        return fs.rename(path, newName);
    }

    public FsResult delete(ActiveUser who, String path) {
        PathResult found = fs.getTree().find(path);
        if (found.isFound()) {
            AccessDecision d = access.canDelete(who, found.getNode());
            if (!d.isAllowed()) {
                return denied(d);
            }
        }
        return fs.delete(path);
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
