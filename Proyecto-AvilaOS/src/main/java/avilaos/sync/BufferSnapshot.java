/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.sync;

/**
 * Foto de solo lectura de un buffer acotado: su contenido, su capacidad, el valor de cada
 * semáforo y los procesos que esperan en cada uno. La pantalla dibuja esta foto sin tocar
 * la lógica de la simulación (S09).
 *
 * Un semáforo con valor negativo indica cuántos procesos esperan en él.
 */
public final class BufferSnapshot {

    private final int id;
    private final int hostComputerId;
    private final int capacity;
    private final int[] contents;
    private final int mutexValue;
    private final int itemsValue;
    private final int spacesValue;
    private final String[] mutexWaiting;
    private final String[] itemsWaiting;
    private final String[] spacesWaiting;

    public BufferSnapshot(int id, int hostComputerId, int capacity, int[] contents,
            int mutexValue, int itemsValue, int spacesValue,
            String[] mutexWaiting, String[] itemsWaiting, String[] spacesWaiting) {
        this.id = id;
        this.hostComputerId = hostComputerId;
        this.capacity = capacity;
        this.contents = contents.clone();
        this.mutexValue = mutexValue;
        this.itemsValue = itemsValue;
        this.spacesValue = spacesValue;
        this.mutexWaiting = mutexWaiting.clone();
        this.itemsWaiting = itemsWaiting.clone();
        this.spacesWaiting = spacesWaiting.clone();
    }

    public int getId() { return id; }
    public int getHostComputerId() { return hostComputerId; }
    public int getCapacity() { return capacity; }
    /** Cantidad de elementos que hay en el buffer. */
    public int getSize() { return contents.length; }
    /** Copia de los elementos, del más antiguo al más reciente. */
    public int[] getContents() { return contents.clone(); }
    public int getMutexValue() { return mutexValue; }
    public int getItemsValue() { return itemsValue; }
    public int getSpacesValue() { return spacesValue; }
    /** Nombres de los procesos esperando en el semáforo mutex, en orden de llegada. */
    public String[] getMutexWaiting() { return mutexWaiting.clone(); }
    /** Nombres de los procesos esperando en el semáforo de elementos (buffer vacío). */
    public String[] getItemsWaiting() { return itemsWaiting.clone(); }
    /** Nombres de los procesos esperando en el semáforo de espacios (buffer lleno). */
    public String[] getSpacesWaiting() { return spacesWaiting.clone(); }
}
