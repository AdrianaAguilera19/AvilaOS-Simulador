/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.sync;

import avilaos.datastructures.CustomQueue;
import avilaos.enums.BlockReason;
import avilaos.model.hardware.RAM;
import java.util.concurrent.Semaphore;
import avilaos.enums.AccessType;
import avilaos.model.process.PCB;

/**
 * Buffer acotado del productor-consumidor. Vive en la RAM de su computador
 * anfitrión y se sincroniza con tres semáforos (Stallings, fig. 5.13):
 *   mutex (s = 1), elementos (n = 0) y espacios (e = capacidad).
 *
 * El buffer NO ejecuta el algoritmo: expone los semáforos y las operaciones
 * insert/extract. Cada paso (semWait, insert, semSignal...) lo ejecuta el
 * proceso como una instrucción en modo SO, un paso por ciclo.
 */
public class BoundedBuffer {

    private final int id;
    private final int capacity;
    private final int hostComputerId;
    private final RAM hostRam;
    private final int reservedMemory;

    private final SemaforoAvila mutex;      // s = 1
    private final SemaforoAvila items;      // n = 0
    private final SemaforoAvila spaces;     // e = capacidad

    private final CustomQueue<Integer> data = new CustomQueue<>();
    private final Semaphore dataLock = new Semaphore(1); // protege data y contadores
    private long totalProduced = 0;
    private long totalConsumed = 0;

    /**
     * @param id             identificador del buffer
     * @param capacity       cantidad máxima de elementos (mayor que 0)
     * @param hostComputerId computador anfitrión
     * @param hostRam        RAM del anfitrión, donde se reserva el espacio
     * @param itemSize       memoria que ocupa cada elemento (mayor que 0)
     * @throws IllegalStateException si el buffer no cabe en la RAM del anfitrión
     */
    public BoundedBuffer(int id, int capacity, int hostComputerId, RAM hostRam, int itemSize) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("La capacidad debe ser mayor que 0");
        }
        if (itemSize <= 0) {
            throw new IllegalArgumentException("El tamaño de cada elemento debe ser mayor que 0");
        }
        if (hostRam == null) {
            throw new IllegalArgumentException("El buffer necesita la RAM de su anfitrión");
        }
        this.id = id;
        this.capacity = capacity;
        this.hostComputerId = hostComputerId;
        this.hostRam = hostRam;
        this.reservedMemory = capacity * itemSize;

        if (!hostRam.allocate(reservedMemory)) {
            throw new IllegalStateException("El buffer B" + id + " necesita " + reservedMemory
                    + " de memoria y no cabe en la RAM del computador " + hostComputerId);
        }

        this.mutex = new SemaforoAvila(1, BlockReason.MUTEX);
        this.items = new SemaforoAvila(0, BlockReason.EMPTY_SEMAPHORE);
        this.spaces = new SemaforoAvila(capacity, BlockReason.FULL_SEMAPHORE);
    }

    /** Añade un elemento. Solo debe llamarse dentro de la región crítica (tras semWait(spaces) y semWait(mutex)). */
    public void insert(int item) {
        dataLock.acquireUninterruptibly();
        try {
            if (data.size() >= capacity) {
                throw new IllegalStateException("Buffer B" + id + " lleno: error de sincronización");
            }
            data.enqueue(item);
            totalProduced++;
        } finally {
            dataLock.release();
        }
    }

    /** Extrae un elemento. Solo debe llamarse dentro de la región crítica (tras semWait(items) y semWait(mutex)). */
    public int extract() {
        dataLock.acquireUninterruptibly();
        try {
            if (data.isEmpty()) {
                throw new IllegalStateException("Buffer B" + id + " vacío: error de sincronización");
            }
            totalConsumed++;
            return data.dequeue();
        } finally {
            dataLock.release();
        }
    }

    /** Libera la memoria del anfitrión cuando el buffer deja de existir. */
    public void destroy() {
        hostRam.release(reservedMemory);
    }

    public int size() {
        dataLock.acquireUninterruptibly();
        try {
            return data.size();
        } finally {
            dataLock.release();
        }
    }

    public long getTotalProduced() {
        dataLock.acquireUninterruptibly();
        try {
            return totalProduced;
        } finally {
            dataLock.release();
        }
    }

    public long getTotalConsumed() {
        dataLock.acquireUninterruptibly();
        try {
            return totalConsumed;
        } finally {
            dataLock.release();
        }
    }
        /**
     * Foto de solo lectura del buffer: contenido, capacidad, valor de cada semáforo y procesos
     * que esperan en cada uno (S09). Cada parte se lee con su propio candado, así que la foto
     * nunca ve un semáforo o la cola de datos "a medio escribir".
     */
    public BufferSnapshot takeSnapshot() {
        return new BufferSnapshot(id, hostComputerId, capacity, getContents(),
                mutex.getValue(), items.getValue(), spaces.getValue(),
                waitingNames(mutex), waitingNames(items), waitingNames(spaces));
    }

    /** Copia de los elementos guardados, del más antiguo al más reciente. */
    public int[] getContents() {
        dataLock.acquireUninterruptibly();
        try {
            CustomQueue<Integer> copy = data.copy();
            int[] result = new int[copy.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = copy.get(i);
            }
            return result;
        } finally {
            dataLock.release();
        }
    }

    private static String[] waitingNames(SemaforoAvila sem) {
        CustomQueue<PCB> waiting = sem.getWaitingSnapshot();
        String[] names = new String[waiting.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = waiting.get(i).getName();
        }
        return names;
    }
    

        /** LOCAL si el proceso está en el computador anfitrión; REMOTE si está en otro. */
    public AccessType accessTypeFor(PCB pcb) {
        return pcb.getComputerId() == hostComputerId ? AccessType.LOCAL : AccessType.REMOTE;
    } 
    
    public int getId() { return id; }
    public int getCapacity() { return capacity; }
    public int getHostComputerId() { return hostComputerId; }
    public int getReservedMemory() { return reservedMemory; }
    public SemaforoAvila getMutex() { return mutex; }
    public SemaforoAvila getItems() { return items; }
    public SemaforoAvila getSpaces() { return spaces; }
}