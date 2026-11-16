/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.model.hardware;

import avilaos.enums.HeadDirection;

/**
 * Cabezal del disco: posicion, direccion y velocidad (k posiciones por ciclo).
 * Tiempo de servicio de una solicitud = ceil(distancia / k) + bloques transferidos:
 * cada ciclo el cabezal se mueve hasta k posiciones, y cada bloque leido o escrito cuesta 1 ciclo.
 * @author Antonella Ruiz
 */
public class DiskHead {
    /** Valor por defecto de k (movimiento por ciclo), pendiente de confirmar en DEC-13. */
    public static final int DEFAULT_SPEED = 1;

    private final int totalBlocks;
    private int position;
    private HeadDirection direction;
    private int speed;

    public DiskHead(int totalBlocks, int startPosition, int speed, HeadDirection direction) {
        if (totalBlocks < 1) {
            throw new IllegalArgumentException("El disco debe tener al menos 1 bloque");
        }
        if (startPosition < 0 || startPosition >= totalBlocks) {
            throw new IllegalArgumentException("La posicion inicial debe estar entre 0 y " + (totalBlocks - 1));
        }
        if (direction == null) {
            throw new IllegalArgumentException("Falta la direccion del cabezal");
        }
        this.totalBlocks = totalBlocks;
        this.position = startPosition;
        this.direction = direction;
        setSpeed(speed);
    }

    public DiskHead(int totalBlocks, int startPosition) {
        this(totalBlocks, startPosition, DEFAULT_SPEED, HeadDirection.UP);
    }

    public int getPosition() { return position; }

    public HeadDirection getDirection() { return direction; }

    public int getSpeed() { return speed; }

    public void setSpeed(int speed) {
        if (speed < 1) {
            throw new IllegalArgumentException("El movimiento por ciclo (k) debe ser mayor a 0");
        }
        this.speed = speed;
    }

    /** Distancia en bloques desde la posicion actual hasta el bloque destino. */
    public int distanceTo(int target) {
        return Math.abs(target - position);
    }

    /** Ciclos de movimiento para llegar al bloque destino: ceil(distancia / k). */
    public int moveCycles(int target) {
        int distance = distanceTo(target);
        return (distance + speed - 1) / speed;
    }

    /** Tiempo de servicio total: ciclos de movimiento + 1 ciclo por bloque transferido. */
    public int serviceTime(int target, int blocksToTransfer) {
        if (blocksToTransfer < 1) {
            throw new IllegalArgumentException("Hay que transferir al menos 1 bloque");
        }
        return moveCycles(target) + blocksToTransfer;
    }

    /**
     * Mueve el cabezal un ciclo hacia el bloque destino: hasta k posiciones, sin pasarse.
     * Actualiza la direccion segun hacia donde se mueve (no cambia si ya esta en el destino).
     * @return cuantas posiciones se movio
     */
    public int moveToward(int target) {
        if (target < 0 || target >= totalBlocks) {
            throw new IllegalArgumentException("El bloque " + target + " esta fuera del disco");
        }
        int distance = distanceTo(target);
        if (distance == 0) {
            return 0;
        }
        int step = Math.min(speed, distance);
        if (target > position) {
            position += step;
            direction = HeadDirection.UP;
        } else {
            position -= step;
            direction = HeadDirection.DOWN;
        }
        return step;
    }
}