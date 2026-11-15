/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.model.hardware;

import avilaos.enums.BlockUse;
import java.util.concurrent.Semaphore;

/**
 * Mapa de bloques del disco:
 * @author Antonella Ruiz
 */
public class BlockMap {
    private final BlockUse[] uses;
    private final String[] owners;
    private final int[] nexts;
    private final Semaphore lock = new Semaphore(1);

    public BlockMap(int totalBlocks) {
        if (totalBlocks <= 0) {
            throw new IllegalArgumentException("El disco necesita al menos 1 bloque");
        }
        uses = new BlockUse[totalBlocks];
        owners = new String[totalBlocks];
        nexts = new int[totalBlocks];
        for (int i = 0; i < totalBlocks; i++) {
            uses[i] = BlockUse.FREE;
            nexts[i] = BlockInfo.NO_NEXT;
        }
    }

    public int size() {
        return uses.length;
    }

    public int freeCount() {
        lock.acquireUninterruptibly();
        try {
            int count = 0;
            for (int i = 0; i < uses.length; i++) {
                if (uses[i] == BlockUse.FREE) {
                    count++;
                }
            }
            return count;
        } finally {
            lock.release();
        }
    }

    public int usedCount() {
        return size() - freeCount();
    }

    public boolean isFree(int block) {
        checkRange(block);
        lock.acquireUninterruptibly();
        try {
            return uses[block] == BlockUse.FREE;
        } finally {
            lock.release();
        }
    }

    /** Ocupa el bloque. Devuelve false si ya estaba ocupado (no cambia nada). */
    public boolean occupy(int block, BlockUse use, String owner) {
        checkRange(block);
        if (use == null || use == BlockUse.FREE) {
            throw new IllegalArgumentException("Para ocupar un bloque se necesita un uso distinto de FREE");
        }
        if (owner == null || owner.trim().isEmpty()) {
            throw new IllegalArgumentException("El bloque necesita un dueno");
        }
        lock.acquireUninterruptibly();
        try {
            if (uses[block] != BlockUse.FREE) {
                return false;
            }
            uses[block] = use;
            owners[block] = owner.trim();
            nexts[block] = BlockInfo.NO_NEXT;
            return true;
        } finally {
            lock.release();
        }
    }

    /** Dice cual es el bloque siguiente en la cadena. Falla si el bloque esta libre. */
    public void setNext(int block, int next) {
        checkRange(block);
        if (next != BlockInfo.NO_NEXT) {
            checkRange(next);
        }
        lock.acquireUninterruptibly();
        try {
            if (uses[block] == BlockUse.FREE) {
                throw new IllegalStateException("El bloque " + block + " esta libre");
            }
            nexts[block] = next;
        } finally {
            lock.release();
        }
    }

    /** Libera el bloque. Devuelve false si ya estaba libre. */
    public boolean release(int block) {
        checkRange(block);
        lock.acquireUninterruptibly();
        try {
            if (uses[block] == BlockUse.FREE) {
                return false;
            }
            uses[block] = BlockUse.FREE;
            owners[block] = null;
            nexts[block] = BlockInfo.NO_NEXT;
            return true;
        } finally {
            lock.release();
        }
    }

    /** Primer bloque libre a partir de "from" (inclusive), o -1 si no hay. */
    public int firstFreeFrom(int from) {
        lock.acquireUninterruptibly();
        try {
            for (int i = Math.max(from, 0); i < uses.length; i++) {
                if (uses[i] == BlockUse.FREE) {
                    return i;
                }
            }
            return -1;
        } finally {
            lock.release();
        }
    }

    public BlockInfo get(int block) {
        checkRange(block);
        lock.acquireUninterruptibly();
        try {
            return info(block);
        } finally {
            lock.release();
        }
    }

    /** Copia consistente de todos los bloques (para la GUI). */
    public BlockInfo[] snapshot() {
        lock.acquireUninterruptibly();
        try {
            BlockInfo[] copy = new BlockInfo[uses.length];
            for (int i = 0; i < uses.length; i++) {
                copy[i] = info(i);
            }
            return copy;
        } finally {
            lock.release();
        }
    }

    private BlockInfo info(int block) {
        return new BlockInfo(block, uses[block], owners[block], nexts[block]);
    }

    private void checkRange(int block) {
        if (block < 0 || block >= uses.length) {
            throw new IndexOutOfBoundsException("Bloque " + block + " fuera de 0.." + (uses.length - 1));
        }
    }
}