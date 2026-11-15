/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.filesystem;

import avilaos.datastructures.BlockChain;
import avilaos.enums.BlockUse;
import avilaos.model.hardware.BlockInfo;
import avilaos.model.hardware.BlockMap;

/**
 * Asignacion encadenada de bloques para archivos
 * reserva n bloques enlazados (cada bloque guarda cual es el siguiente) y devuelve la cadena; liberar recorre la cadena desde el primero.
 * Elige siempre los primeros bloques libres, de menor a mayor numero.
 * Solo usa los primeros fileBlocks bloques del mapa; el resto (el swap) no se toca.
 * @author Antonella Ruiz
 */
public class ChainAllocator {
    private final BlockMap map;
    private final int fileBlocks;

    public ChainAllocator(BlockMap map, int fileBlocks) {
        if (map == null) {
            throw new IllegalArgumentException("Falta el mapa de bloques");
        }
        if (fileBlocks < 1 || fileBlocks > map.size()) {
            throw new IllegalArgumentException("La zona de archivos debe estar entre 1 y " + map.size() + " bloques");
        }
        this.map = map;
        this.fileBlocks = fileBlocks;
    }

    public ChainAllocator(BlockMap map) {
        this(map, map.size());
    }

    /** Bloques libres dentro de la zona de archivos. */
    public int freeBlocks() {
        BlockInfo[] all = map.snapshot();
        int count = 0;
        for (int i = 0; i < fileBlocks; i++) {
            if (all[i].isFree()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Reserva count bloques enlazados para el dueno (la ruta del archivo).
     * Si no hay suficientes devuelve null y el disco queda igual que antes.
     */
    public BlockChain allocate(int count, String owner) {
        if (count < 1) {
            throw new IllegalArgumentException("Hay que reservar al menos 1 bloque");
        }
        BlockChain chain = new BlockChain();
        int previous = -1;
        int cursor = 0;
        while (chain.size() < count) {
            int block = map.firstFreeFrom(cursor);
            if (block < 0 || block >= fileBlocks) {
                rollback(chain);
                return null;
            }
            cursor = block + 1;
            if (!map.occupy(block, BlockUse.FILE, owner)) {
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

    /** Recorre la cadena que empieza en ese bloque y devuelve sus bloques en orden (vacia si esta libre). */
    public BlockChain chainOf(int firstBlock) {
        BlockChain chain = new BlockChain();
        int current = firstBlock;
        while (current != BlockInfo.NO_NEXT && chain.size() <= map.size()) {
            BlockInfo info = map.get(current);
            if (info.isFree()) {
                break;
            }
            chain.add(current);
            current = info.getNext();
        }
        return chain;
    }

    /** Libera toda la cadena que empieza en ese bloque. Devuelve cuantos bloques libero. */
    public int free(int firstBlock) {
        int freed = 0;
        int current = firstBlock;
        while (current != BlockInfo.NO_NEXT && freed <= map.size()) {
            BlockInfo info = map.get(current);
            if (info.isFree()) {
                break;
            }
            map.release(current);
            freed++;
            current = info.getNext();
        }
        return freed;
    }

    private void rollback(BlockChain chain) {
        for (int i = 0; i < chain.size(); i++) {
            map.release(chain.get(i));
        }
    }
}