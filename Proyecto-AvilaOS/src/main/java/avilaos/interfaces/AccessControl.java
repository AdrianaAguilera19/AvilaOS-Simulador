package avilaos.interfaces;

import avilaos.os.filesystem.AccessDecision;
import avilaos.os.filesystem.ActiveUser;
import avilaos.os.filesystem.FileNode;

/**
 * Autorizacion de las operaciones sobre el sistema de archivos (F10). Cada operacion pregunta aqui
 * antes de hacerse. Es una interfaz para poder cambiar las reglas sin tocar el sistema de archivos.
 * Toda respuesta trae permitido o rechazado y, si es rechazo, el motivo en texto.
 */
public interface AccessControl {

    /** Leer un archivo (ver la estructura es libre para todos y no pasa por aqui). */
    AccessDecision canRead(ActiveUser who, FileNode file);

    /** Crear un archivo dentro de un directorio. */
    AccessDecision canCreateFile(ActiveUser who, FileNode directory);

    /** Crear un directorio dentro de otro. */
    AccessDecision canCreateDirectory(ActiveUser who, FileNode parent);

    /** Actualizar (renombrar) un archivo o directorio. */
    AccessDecision canUpdate(ActiveUser who, FileNode node);

    /** Eliminar un archivo o directorio. */
    AccessDecision canDelete(ActiveUser who, FileNode node);
}
