/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.model.hardware;

import avilaos.interfaces.Clockable;
import avilaos.interfaces.DiskListener;
import avilaos.os.disk.DiskRequest;
import java.util.concurrent.Semaphore;

/**
 * Disco como componente de hardware
 * @author Antonella Ruiz
 */
public class Disk implements Clockable {
    private final BlockMap map;
    private final DiskHead head;
    private final Semaphore mutex = new Semaphore(1);
    private long workCycles = 0;
    private boolean paused = false;

    private DiskRequest current;
    private long startCycle;
    private int headBefore;
    private int transferred;
    private volatile DiskListener listener;

    public Disk(BlockMap map, DiskHead head) {
        if (map == null) {
            throw new IllegalArgumentException("El disco necesita su mapa de bloques");
        }
        if (head == null) {
            throw new IllegalArgumentException("El disco necesita su cabezal");
        }
        this.map = map;
        this.head = head;
    }

    /** Disco con el cabezal en el bloque 0 y k por defecto. */
    public Disk(BlockMap map) {
        this(map, new DiskHead(map == null ? 1 : map.size(), 0));
    }

    public BlockMap getMap() { return map; }

    public DiskHead getHead() { return head; }

    /** Cantidad de bloques del disco. */
    public int totalBlocks() { return map.size(); }

    /** Cuantos ciclos de trabajo ha hecho el disco desde que se creo (los ticks en pausa no cuentan). */
    public long getWorkCycles() {
        mutex.acquireUninterruptibly();
        try {
            return workCycles;
        } finally {
            mutex.release();
        }
    }

    public void setListener(DiskListener listener) { this.listener = listener; }

    /** true si el disco esta atendiendo una solicitud. */
    public boolean isBusy() { return getCurrent() != null; }

    /** La solicitud en servicio, o null si esta libre. */
    public DiskRequest getCurrent() {
        mutex.acquireUninterruptibly();
        try {
            return current;
        } finally {
            mutex.release();
        }
    }

    /**
     * Pausa el disco. Cuando este metodo regresa ningun tick en curso puede cambiar el estado:
     * los siguientes se ignoran hasta llamar a {@link #resume()}. Pausar dos veces no hace nada.
     */
    public void pause() { setPaused(true); }

    /** Reanuda el disco: el siguiente tick continua exactamente donde se quedo. */
    public void resume() { setPaused(false); }

    /** true si el disco esta en pausa. */
    public boolean isPaused() {
        mutex.acquireUninterruptibly();
        try {
            return paused;
        } finally {
            mutex.release();
        }
    }

    private void setPaused(boolean value) {
        mutex.acquireUninterruptibly();
        try {
            paused = value;
        } finally {
            mutex.release();
        }
    }

    /**
     * Empieza a atender una solicitud. El disco atiende una sola a la vez.
     * Tambien se puede llamar en pausa: la solicitud queda lista y avanza al reanudar.
     * @param cycle ciclo actual del reloj; el tiempo de servicio se cuenta desde el siguiente tick
     */
    public void begin(DiskRequest request, long cycle) {
        if (request == null) {
            throw new IllegalArgumentException("Falta la solicitud");
        }
        if (request.getTargetBlock() >= map.size()) {
            throw new IllegalArgumentException("El bloque " + request.getTargetBlock() + " esta fuera del disco");
        }
        mutex.acquireUninterruptibly();
        try {
            if (current != null) {
                throw new IllegalStateException("El disco ya esta atendiendo una solicitud");
            }
            current = request;
            startCycle = cycle;
            headBefore = head.getPosition();
            transferred = 0;
        } finally {
            mutex.release();
        }
    }

    /**
     * Cada tick del reloj es exactamente un ciclo de trabajo del disco, salvo en pausa, donde no pasa nada.
     * El aviso al listener se hace fuera del semaforo para que este pueda pedirle otra solicitud al disco.
     */
    @Override
    public void onTick(long cycle) {
        DiskRequest finished = null;
        long start = 0;
        int before = 0;
        mutex.acquireUninterruptibly();
        try {
            if (paused) {
                return;
            }
            workCycles++;
            if (current == null) {
                return;
            }
            if (head.getPosition() != current.getTargetBlock()) {
                head.moveToward(current.getTargetBlock());
            } else {
                transferred++;
                if (transferred >= current.getBlocksToTransfer()) {
                    finished = current;
                    start = startCycle;
                    before = headBefore;
                    current = null;
                }
            }
        } finally {
            mutex.release();
        }
        DiskListener l = listener;
        if (finished != null && l != null) {
            l.onServiceFinished(finished, start, cycle, before);
        }
    }
}