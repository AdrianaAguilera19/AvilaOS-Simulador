/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.enums;



/**
 * Tipos de operacion de E/S que se pueden pedir al disco.
 * CREATE, READ, UPDATE y DELETE son las operaciones sobre archivos;
 * SUSPEND y REACTIVATE son las del swap del mediano plazo.
 * @author Antonella Ruiz
 */
public enum IOOperationType {
    CREATE,
    READ,
    UPDATE,
    DELETE,
    SUSPEND,
    REACTIVATE;

    /** true si la operacion es sobre un archivo del sistema de archivos (CRUD). */
    public boolean isFileOperation() {
        return this == CREATE || this == READ || this == UPDATE || this == DELETE;
    }

    /** true si la operacion mueve un proceso entre RAM y swap. */
    public boolean isSwapOperation() {
        return this == SUSPEND || this == REACTIVATE;
    }

    /**
     * Convierte el texto del JSON ("create", " READ ", etc.) a la operacion.
     * Devuelve null si el texto no corresponde a ninguna.
     */
    public static IOOperationType fromText(String text) {
        if (text == null) {
            return null;
        }
        String clean = text.trim().toUpperCase();
        for (IOOperationType op : values()) {
            if (op.name().equals(clean)) {
                return op;
            }
        }
        return null;
    }
}
