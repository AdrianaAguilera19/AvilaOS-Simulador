/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.disk;

import avilaos.model.hardware.BlockMap;

/**
 * Limites del tamano del disco (entre 100 y 500 bloques) y validacion de su configuracion.
 * @author Antonella Ruiz
 */
public final class DiskLimits {
    public static final int MIN_BLOCKS = 100;
    public static final int MAX_BLOCKS = 500;

    private DiskLimits() { }

    /** Devuelve null si el tamano es valido; si no, el mensaje para mostrar al usuario. */
    public static String checkTotalBlocks(int totalBlocks) {
        if (totalBlocks < MIN_BLOCKS || totalBlocks > MAX_BLOCKS) {
            return "El disco debe tener entre " + MIN_BLOCKS + " y " + MAX_BLOCKS + " bloques (se pidieron " + totalBlocks + ")";
        }
        return null;
    }

    /** Crea el mapa de bloques de un disco de ese tamano, o avisa con IllegalArgumentException. */
    public static BlockMap createMap(int totalBlocks) {
        String error = checkTotalBlocks(totalBlocks);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        return new BlockMap(totalBlocks);
    }
}