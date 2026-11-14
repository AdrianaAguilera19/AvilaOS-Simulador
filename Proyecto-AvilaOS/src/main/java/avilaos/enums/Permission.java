/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.enums;

/**
 * Permiso de un archivo o directorio: PUBLIC lo puede leer cualquier usuario,
 * PRIVATE solo su dueno (y el administrador).
 * @author Antonella Ruiz
 */
public enum Permission {
    PUBLIC,
    PRIVATE;

    /** Convierte el texto del JSON ("public", " PRIVATE ") al permiso. Devuelve null si no corresponde. */
    public static Permission fromText(String text) {
        if (text == null) {
            return null;
        }
        String clean = text.trim().toUpperCase();
        for (Permission p : values()) {
            if (p.name().equals(clean)) {
                return p;
            }
        }
        return null;
    }
}
