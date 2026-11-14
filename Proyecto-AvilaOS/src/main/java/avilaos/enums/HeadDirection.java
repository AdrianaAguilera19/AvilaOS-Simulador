/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.enums;

/**
 * Direccion de movimiento del cabezal del disco (hacia bloques mayores o menores).
 * @author Antonella Ruiz
 */
public enum HeadDirection {
    UP,
    DOWN;

    /** La direccion contraria (SCAN y C-SCAN la invierten al llegar al extremo). */
    public HeadDirection opposite() {
        return this == UP ? DOWN : UP;
    }

    /** Convierte el texto ("up", " DOWN ") a la direccion. Devuelve null si no corresponde. */
    public static HeadDirection fromText(String text) {
        if (text == null) {
            return null;
        }
        String clean = text.trim().toUpperCase();
        for (HeadDirection d : values()) {
            if (d.name().equals(clean)) {
                return d;
            }
        }
        return null;
    }
}