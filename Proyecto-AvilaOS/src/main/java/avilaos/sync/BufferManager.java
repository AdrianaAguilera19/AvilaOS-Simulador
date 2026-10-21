/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.sync;

import avilaos.datastructures.CustomLinkedList;
import avilaos.model.hardware.Cluster;
import avilaos.model.hardware.Computer;
import avilaos.model.process.PCB;
import java.util.concurrent.Semaphore;

/**
 * Crea y registra los buffers del sistema distribuido. Cada buffer reserva su
 * espacio en la RAM de su computador anfitrión. Los IDs son únicos en todo el cluster.
 */
public class BufferManager {

    /** Memoria por elemento cuando no se indica otra (valor por acordar en el equipo). */
    public static final int DEFAULT_ITEM_SIZE = 1;

    private final Cluster cluster;
    private final CustomLinkedList<BoundedBuffer> buffers = new CustomLinkedList<>();
    private final Semaphore lock = new Semaphore(1);
    private int nextId = 1;

    public BufferManager(Cluster cluster) {
        if (cluster == null) {
            throw new IllegalArgumentException("El gestor de buffers necesita un cluster");
        }
        this.cluster = cluster;
    }

    public BoundedBuffer createBuffer(int capacity, int hostComputerId) {
        return createBuffer(capacity, hostComputerId, DEFAULT_ITEM_SIZE);
    }

    /**
     * @throws IllegalArgumentException si el computador anfitrión no existe o los datos son inválidos
     * @throws IllegalStateException si el buffer no cabe en la RAM del anfitrión
     */
    public BoundedBuffer createBuffer(int capacity, int hostComputerId, int itemSize) {
        Computer host = cluster.getComputer(hostComputerId);
        if (host == null) {
            throw new IllegalArgumentException("No existe el computador " + hostComputerId);
        }
        lock.acquireUninterruptibly();
        try {
            BoundedBuffer buffer = new BoundedBuffer(nextId, capacity, hostComputerId,
                    host.getRam(), itemSize);
            nextId++;                       // el ID solo se gasta si el buffer se creó
            buffers.addLast(buffer);
            return buffer;
        } finally {
            lock.release();
        }
    }

    /** @return el buffer con ese ID, o null si no existe */
    public BoundedBuffer getBuffer(int id) {
        lock.acquireUninterruptibly();
        try {
            for (int i = 0; i < buffers.size(); i++) {
                if (buffers.get(i).getId() == id) {
                    return buffers.get(i);
                }
            }
            return null;
        } finally {
            lock.release();
        }
    }

    public boolean exists(int id) {
        return getBuffer(id) != null;
    }

    /** Elimina el buffer y libera su memoria. No debe borrarse un buffer que aún usen procesos. */
    public boolean removeBuffer(int id) {
        BoundedBuffer buffer = getBuffer(id);
        if (buffer == null) {
            return false;
        }
        lock.acquireUninterruptibly();
        try {
            if (!buffers.remove(buffer)) {
                return false;
            }
        } finally {
            lock.release();
        }
        buffer.destroy();
        return true;
    }

    /** Copia de la lista de buffers para que la pantalla la dibuje. */
    public CustomLinkedList<BoundedBuffer> getSnapshot() {
        lock.acquireUninterruptibly();
        try {
            return buffers.copy();
        } finally {
            lock.release();
        }
    }

    /** Arma el ejecutor de un productor o consumidor con el buffer que declaró. */
    public BufferProcessRunner createRunner(PCB pcb) {
        BoundedBuffer buffer = getBuffer(pcb.getAssociatedBufferId());
        if (buffer == null) {
            throw new IllegalArgumentException("El buffer B" + pcb.getAssociatedBufferId()
                    + " no existe");
        }
                return new BufferProcessRunner(pcb, buffer, cluster::getNetworkLatency);
    }
}
