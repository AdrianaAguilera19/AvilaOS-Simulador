/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.filesystem;

/**
 * Resultado de una operacion sobre el sistema de archivos: el nodo afectado o el motivo del rechazo.
 * Permite rechazar con un mensaje claro, sin lanzar error ni detener la simulacion.
 * @author Antonella Ruiz
 */
public final class FileOpResult {
    private final FileNode node;
    private final String message;

    private FileOpResult(FileNode node, String message) {
        this.node = node;
        this.message = message;
    }

    public static FileOpResult done(FileNode node) {
        return new FileOpResult(node, "");
    }

    public static FileOpResult rejected(String message) {
        return new FileOpResult(null, message);
    }

    public boolean isDone() { return node != null; }

    /** El nodo creado o afectado, o null si se rechazo. */
    public FileNode getNode() { return node; }

    /** El motivo del rechazo; vacio si la operacion se hizo. */
    public String getMessage() { return message; }
}