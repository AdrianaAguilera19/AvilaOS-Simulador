/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.model.hardware;

import avilaos.enums.BlockUse;

/**
 * Foto inmutable de un bloque del disco, para que la GUI lo lea sin tocar el mapa.
 * @author Antonella Ruiz
 */
public final class BlockInfo {
    public static final int NO_NEXT = -1;

    private final int index;
    private final BlockUse use;
    private final String owner;
    private final int next;

    public BlockInfo(int index, BlockUse use, String owner, int next) {
        this.index = index;
        this.use = use;
        this.owner = owner;
        this.next = next;
    }

    public int getIndex() { return index; }

    public BlockUse getUse() { return use; }

    /** Ruta del archivo o id del proceso suspendido; null si el bloque esta libre. */
    public String getOwner() { return owner; }

    /** Bloque que sigue en la cadena, o NO_NEXT. */
    public int getNext() { return next; }

    public boolean isFree() { return use == BlockUse.FREE; }

    @Override
    public String toString() {
        return isFree() ? index + ": libre" : index + ": " + use + " " + owner + " -> " + next;
    }
}