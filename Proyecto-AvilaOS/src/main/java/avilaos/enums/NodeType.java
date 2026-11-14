/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.enums;


/**
 * Tipo de nodo del sistema de archivos.
 * @author Antonella Ruiz
 */
public enum NodeType {
    FILE,
    DIRECTORY;

    /** Convierte el texto del JSON ("file", " DIRECTORY ") al tipo. Devuelve null si no corresponde. */
    public static NodeType fromText(String text) {
        if (text == null) {
            return null;
        }
        String clean = text.trim().toUpperCase();
        for (NodeType t : values()) {
            if (t.name().equals(clean)) {
                return t;
            }
        }
        return null;
    }
}