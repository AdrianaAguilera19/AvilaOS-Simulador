/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Enum.java to edit this template
 */
package avilaos.enums;

/**
 * Modo de usuario del sistema de archivos: el administrador puede operar en cualquier lugar,
 * el usuario solo en lo que le pertenece.
 * @author Antonella Ruiz
 */
public enum UserMode {
    ADMIN,
    USER;

    /** true si es el administrador. */
    public boolean isAdmin() {
        return this == ADMIN;
    }

    /** Convierte el texto («admin», " USER ") al modo. Devuelve null si no corresponde. */
    public static UserMode fromText(String text) {
        if (text == null) {
            return null;
        }
        String clean = text.trim().toUpperCase();
        for (UserMode m : values()) {
            if (m.name().equals(clean)) {
                return m;
            }
        }
        return null;
    }
}