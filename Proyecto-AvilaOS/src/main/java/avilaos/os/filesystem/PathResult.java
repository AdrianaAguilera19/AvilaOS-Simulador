/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.filesystem;

import avilaos.enums.PathError;

/**
 * Resultado de buscar una ruta: el nodo encontrado o el motivo del error.
 * Permite rechazar una ruta mala con un mensaje claro, sin detener la simulacion.
 * @author Antonella Ruiz
 */
public class PathResult {
    private final FileNode node;
    private final PathError error;
    private final String message;

    private PathResult(FileNode node, PathError error, String message) {
        this.node = node;
        this.error = error;
        this.message = message;
    }

    static PathResult found(FileNode node) {
        return new PathResult(node, null, "");
    }

    static PathResult failed(PathError error, String message) {
        return new PathResult(null, error, message);
    }

    public boolean isFound() { return node != null; }

    /** El nodo encontrado, o null si la ruta fallo. */
    public FileNode getNode() { return node; }

    /** El motivo del fallo, o null si la ruta se encontro. */
    public PathError getError() { return error; }

    /** Texto para mostrar al usuario o escribir en el log; vacio si la ruta se encontro. */
    public String getMessage() { return message; }
}