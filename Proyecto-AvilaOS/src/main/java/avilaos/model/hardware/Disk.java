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
 * Disco como componente de hardware: guarda el mapa de bloques y el cabezal, y se suscribe al reloj
 * global igual que la CPU y la RAM. En cada tick avanza exactamente un ciclo de trabajo:
 * mover el cabezal hasta k posiciones, o transferir un bloque. Atiende una solicitud a la vez.
 * No contiene logica del sistema de archivos ni de planificacion: eso vive en la capa del SO.
 * @author Antonella Ruiz
 */
public class Disk implements Clockable {
    private final BlockMap map;
    private final DiskHead head;
    private final Semaphore mutex = new Semaphore(1);
    private long workCycles = 0;

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

    /** Cuantos ciclos de trabajo ha hecho el disco desde que se creo. */
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
    public synchronized boolean isBusy() { return current != null; }

    /** La solicitud en servicio, o null si esta libre. */
    public synchronized DiskRequest getCurrent() { return current; }

    /**
     * Empieza a atender una solicitud. El disco atiende una sola a la vez.
     * @param cycle ciclo actual del reloj; el tiempo de servicio se cuenta desde el siguiente tick
     */
    public synchronized void begin(DiskRequest request, long cycle) {
        if (request == null) {
            throw new IllegalArgumentException("Falta la solicitud");
        }
        if (current != null) {
            throw new IllegalStateException("El disco ya esta atendiendo una solicitud");
        }
        if (request.getTargetBlock() >= map.size()) {
            throw new IllegalArgumentException("El bloque " + request.getTargetBlock() + " esta fuera del disco");
        }
        current = request;
        startCycle = cycle;
        headBefore = head.getPosition();
        transferred = 0;
    }

    /** Cada tick del reloj es exactamente un ciclo de trabajo del disco. */
    @Override
    public void onTick(long cycle) {
        mutex.acquireUninterruptibly();
        try {
            workCycles++;
        } finally {
            mutex.release();
        }
        DiskRequest finished = null;
        long start = 0;
        int before = 0;
        synchronized (this) {
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
        }
        DiskListener l = listener;
        if (finished != null && l != null) {
            l.onServiceFinished(finished, start, cycle, before);
        }
    }
}