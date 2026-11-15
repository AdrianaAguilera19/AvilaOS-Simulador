/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.disk;

import avilaos.datastructures.BlockChain;
import avilaos.enums.BlockUse;
import avilaos.model.hardware.BlockInfo;
import avilaos.model.hardware.BlockMap;

/**
 * Area de intercambio (swap): una region del mapa de bloques, aparte de la zona de archivos,
 * donde se guardan los procesos suspendidos
 * @author Antonella Ruiz
 */
public class SwapArea {
    private final BlockMap map;
    private final int firstBlock;
    private final int size;

    /**
     * @param map mapa de bloques de todo el disco
     * @param firstBlock primer bloque del swap (justo despues de la zona de archivos)
     * @param size cantidad de bloques del swap (configurable)
     */
    public SwapArea(BlockMap map, int firstBlock, int size) {
        if (map == null) {
            throw new IllegalArgumentException("Falta el mapa de bloques");
        }
        if (size < 1) {
            throw new IllegalArgumentException("El swap necesita al menos 1 bloque");
        }
        if (firstBlock < 0 || firstBlock + size > map.size()) {
            throw new IllegalArgumentException("El swap no cabe en el disco: bloques " + firstBlock + " a " + (firstBlock + size - 1));
        }
        this.map = map;
        this.firstBlock = firstBlock;
        this.size = size;
    }

    public int getFirstBlock() { return firstBlock; }

    public int size() { return size; }

    public boolean contains(int block) {
        return block >= firstBlock && block < firstBlock + size;
    }

    public int freeBlocks() {
        return size - usedBlocks();
    }

    public int usedBlocks() {
        BlockInfo[] all = map.snapshot();
        int count = 0;
        for (int i = firstBlock; i < firstBlock + size; i++) {
            if (!all[i].isFree()) {
                count++;
            }
        }
        return count;
    }

    /** Cuantos bloques de swap ocupa ese proceso. */
    public int blocksOf(String processId) {
        BlockInfo[] all = map.snapshot();
        int count = 0;
        for (int i = firstBlock; i < firstBlock + size; i++) {
            if (isOwnedBy(all[i], processId)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Reserva blocks bloques de swap para un proceso que se suspende.
     * Si no hay espacio devuelve null y el swap queda igual. Un proceso no puede reservar dos veces.
     */
    public BlockChain reserve(String processId, int blocks) {
        if (blocks < 1) {
            throw new IllegalArgumentException("Hay que reservar al menos 1 bloque");
        }
        if (blocksOf(processId) > 0) {
            throw new IllegalStateException("El proceso " + processId + " ya tiene bloques en el swap");
        }
        BlockChain chain = new BlockChain();
        int previous = -1;
        int cursor = firstBlock;
        while (chain.size() < blocks) {
            int block = map.firstFreeFrom(cursor);
            if (block < 0 || !contains(block)) {
                for (int i = 0; i < chain.size(); i++) {
                    map.release(chain.get(i));
                }
                return null;
            }
            cursor = block + 1;
            if (!map.occupy(block, BlockUse.SUSPENDED_PROCESS, processId)) {
                continue;
            }
            if (previous >= 0) {
                map.setNext(previous, block);
            }
            chain.add(block);
            previous = block;
        }
        return chain;
    }

    /** Libera todos los bloques de swap de ese proceso. Devuelve cuantos libero. */
    public int release(String processId) {
        int freed = 0;
        BlockInfo[] all = map.snapshot();
        for (int i = firstBlock; i < firstBlock + size; i++) {
            if (isOwnedBy(all[i], processId) && map.release(i)) {
                freed++;
            }
        }
        return freed;
    }

    private boolean isOwnedBy(BlockInfo info, String processId) {
        return info.getUse() == BlockUse.SUSPENDED_PROCESS && info.getOwner() != null && info.getOwner().equals(processId);
    }
}