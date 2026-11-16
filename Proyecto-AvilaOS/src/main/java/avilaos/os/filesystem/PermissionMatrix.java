package avilaos.os.filesystem;

import avilaos.enums.Permission;
import avilaos.interfaces.AccessControl;

/**
 * La matriz de permisos del enunciado, en una sola clase (F10, F11):
 *
 *  Operacion            | ADMINISTRADOR          | USUARIO
 *  ---------------------+------------------------+-------------------------------------------------
 *  Leer archivo         | todos                  | los propios o los publicos
 *  Crear archivo        | en cualquier directorio| solo en directorios de los que es dueno
 *  Crear directorio     | si                     | no
 *  Actualizar (renombrar)| archivos y directorios| solo sus propios archivos, y nunca en /sistema
 *  Eliminar             | archivos y directorios | solo sus propios archivos, y nunca en /sistema
 *
 * "Propio" significa que el dueno del archivo se llama igual que el usuario activo (F12).
 * Los archivos de /sistema y los de otros usuarios nunca se modifican en modo usuario (F11).
 */
public class PermissionMatrix implements AccessControl {

    /** Directorio de los archivos del sistema: en modo usuario no se actualiza ni se elimina nada dentro. */
    public static final String SYSTEM_DIRECTORY = "/sistema";

    private final FileTree tree;

    public PermissionMatrix(FileTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("La matriz de permisos necesita el arbol de archivos");
        }
        this.tree = tree;
    }

    @Override
    public AccessDecision canRead(ActiveUser who, FileNode file) {
        AccessDecision invalid = check(who, file);
        if (invalid != null) {
            return invalid;
        }
        if (who.isAdmin() || file.isDirectory() || isOwner(who, file) || file.getPermission() == Permission.PUBLIC) {
            return AccessDecision.allow();
        }
        return AccessDecision.deny("«" + path(file) + "» es privado y pertenece a «" + file.getOwner()
                + "»; el usuario «" + who.getName() + "» solo puede leer archivos propios o publicos");
    }

    @Override
    public AccessDecision canCreateFile(ActiveUser who, FileNode directory) {
        AccessDecision invalid = check(who, directory);
        if (invalid != null) {
            return invalid;
        }
        if (!directory.isDirectory()) {
            return AccessDecision.deny("«" + path(directory) + "» es un archivo: no se puede crear nada dentro");
        }
        if (who.isAdmin() || isOwner(who, directory)) {
            return AccessDecision.allow();
        }
        return AccessDecision.deny("El usuario «" + who.getName() + "» solo puede crear archivos en sus propios directorios; «"
                + path(directory) + "» es de «" + directory.getOwner() + "»");
    }

    @Override
    public AccessDecision canCreateDirectory(ActiveUser who, FileNode parent) {
        AccessDecision invalid = check(who, parent);
        if (invalid != null) {
            return invalid;
        }
        if (who.isAdmin()) {
            return AccessDecision.allow();
        }
        return AccessDecision.deny("Solo el administrador puede crear directorios");
    }

    @Override
    public AccessDecision canUpdate(ActiveUser who, FileNode node) {
        return canModify(who, node, "renombrar");
    }

    @Override
    public AccessDecision canDelete(ActiveUser who, FileNode node) {
        return canModify(who, node, "eliminar");
    }

    // ------------------------------------------------------------------ reglas comunes

    /** Actualizar y eliminar tienen las mismas reglas para el usuario. */
    private AccessDecision canModify(ActiveUser who, FileNode node, String verb) {
        AccessDecision invalid = check(who, node);
        if (invalid != null) {
            return invalid;
        }
        if (who.isAdmin()) {
            return AccessDecision.allow();
        }
        if (isSystemPath(path(node))) {
            return AccessDecision.deny("«" + path(node) + "» es un archivo del sistema: en modo usuario no se puede " + verb);
        }
        if (node.isDirectory()) {
            return AccessDecision.deny("Solo el administrador puede " + verb + " directorios");
        }
        if (!isOwner(who, node)) {
            return AccessDecision.deny("«" + path(node) + "» pertenece a «" + node.getOwner()
                    + "»: el usuario «" + who.getName() + "» no puede " + verb + " archivos de otros");
        }
        return AccessDecision.allow();
    }

    private AccessDecision check(ActiveUser who, FileNode node) {
        if (who == null) {
            return AccessDecision.deny("No hay un usuario activo");
        }
        if (node == null || path(node) == null) {
            return AccessDecision.deny("El archivo o directorio no existe en el sistema");
        }
        return null;
    }

    private boolean isOwner(ActiveUser who, FileNode node) {
        return node.getOwner().equals(who.getName());
    }

    private String path(FileNode node) {
        return tree.pathOf(node);
    }

    static boolean isSystemPath(String path) {
        return path.equals(SYSTEM_DIRECTORY) || path.startsWith(SYSTEM_DIRECTORY + "/");
    }
}