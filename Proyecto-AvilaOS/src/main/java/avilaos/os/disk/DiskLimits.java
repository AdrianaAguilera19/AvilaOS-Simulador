/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.disk;

import avilaos.enums.HeadDirection;
import avilaos.model.hardware.BlockMap;
import avilaos.model.hardware.Disk;
import avilaos.model.hardware.DiskHead;

/**
 * Limites del tamano del disco (entre 100 y 500 bloques) y validacion de su configuracion:
 * tamano, posicion inicial del cabezal y movimiento por ciclo (k). Cada validacion devuelve el
 * mensaje para el usuario (o null si es valida) en vez de lanzar un error, asi la GUI y el JSON
 * pueden rechazar un valor malo sin detener la simulacion.
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

    /**
     * Devuelve null si la posicion inicial del cabezal esta dentro del disco (0 a totalBlocks - 1);
     * si no, el mensaje para mostrar al usuario.
     */
    public static String checkHeadStart(int totalBlocks, int startPosition) {
        if (startPosition < 0 || startPosition >= totalBlocks) {
            return "La posicion inicial del cabezal debe estar entre 0 y " + (totalBlocks - 1)
                    + " (se pidio " + startPosition + ")";
        }
        return null;
    }

    /** Devuelve null si el movimiento por ciclo (k) es mayor que 0; si no, el mensaje para el usuario. */
    public static String checkSpeed(int speed) {
        if (speed < 1) {
            return "El movimiento por ciclo (k) debe ser mayor que 0 (se pidio " + speed + ")";
        }
        return null;
    }

    /**
     * Revisa la configuracion completa del cabezal de un disco: el tamano del disco, la posicion
     * inicial y k. Devuelve el primer problema que encuentra, o null si todo es valido.
     */
    public static String checkHead(int totalBlocks, int startPosition, int speed) {
        String error = checkTotalBlocks(totalBlocks);
        if (error == null) {
            error = checkHeadStart(totalBlocks, startPosition);
        }
        if (error == null) {
            error = checkSpeed(speed);
        }
        return error;
    }

    /**
     * Cambia k del cabezal con la simulacion en marcha. Si el valor no es valido no se cambia nada
     * ni se lanza error: se devuelve el mensaje para mostrarlo.
     * @return null si se cambio; si no, el mensaje de por que no
     */
    public static String trySetSpeed(DiskHead head, int speed) {
        if (head == null) {
            return "Falta el cabezal";
        }
        String error = checkSpeed(speed);
        if (error == null) {
            head.setSpeed(speed);
        }
        return error;
    }

    /**
     * Crea un disco completo (mapa de bloques y cabezal) con una configuracion ya validada, o avisa
     * con IllegalArgumentException y el mismo mensaje que devuelven las validaciones.
     */
    public static Disk createDisk(int totalBlocks, int startPosition, int speed, HeadDirection direction) {
        String error = checkHead(totalBlocks, startPosition, speed);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        if (direction == null) {
            throw new IllegalArgumentException("Falta la direccion inicial del cabezal");
        }
        return new Disk(new BlockMap(totalBlocks), new DiskHead(totalBlocks, startPosition, speed, direction));
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