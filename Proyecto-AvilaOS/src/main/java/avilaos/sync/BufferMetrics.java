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
 * Foto de solo lectura de las métricas de un buffer (S10): cuántos elementos se produjeron y
 * consumieron, y cuánto tiempo pasaron bloqueados los procesos en cada semáforo.
 *
 * El tiempo de bloqueo se cuenta en ciclos y solo incluye las esperas ya terminadas (el
 * proceso se despertó). Un proceso que sigue bloqueado todavía no entra en el promedio.
 */
public final class BufferMetrics {

    private final int bufferId;
    private final long produced;
    private final long consumed;
    private final long mutexWaits;
    private final long mutexWaitedCycles;
    private final long itemsWaits;
    private final long itemsWaitedCycles;
    private final long spacesWaits;
    private final long spacesWaitedCycles;

    public BufferMetrics(int bufferId, long produced, long consumed,
            long mutexWaits, long mutexWaitedCycles,
            long itemsWaits, long itemsWaitedCycles,
            long spacesWaits, long spacesWaitedCycles) {
        this.bufferId = bufferId;
        this.produced = produced;
        this.consumed = consumed;
        this.mutexWaits = mutexWaits;
        this.mutexWaitedCycles = mutexWaitedCycles;
        this.itemsWaits = itemsWaits;
        this.itemsWaitedCycles = itemsWaitedCycles;
        this.spacesWaits = spacesWaits;
        this.spacesWaitedCycles = spacesWaitedCycles;
    }

    public int getBufferId() { return bufferId; }
    public long getProduced() { return produced; }
    public long getConsumed() { return consumed; }

    /** Cuántas esperas terminadas hubo en cada semáforo. */
    public long getMutexWaits() { return mutexWaits; }
    public long getItemsWaits() { return itemsWaits; }
    public long getSpacesWaits() { return spacesWaits; }

    /** Suma de ciclos bloqueados en cada semáforo (para calcular o comprobar promedios). */
    public long getMutexWaitedCycles() { return mutexWaitedCycles; }
    public long getItemsWaitedCycles() { return itemsWaitedCycles; }
    public long getSpacesWaitedCycles() { return spacesWaitedCycles; }

    /** Promedio de ciclos bloqueado en cada semáforo; 0 si todavía no hubo esperas. */
    public double getMutexAverageWait() { return average(mutexWaitedCycles, mutexWaits); }
    public double getItemsAverageWait() { return average(itemsWaitedCycles, itemsWaits); }
    public double getSpacesAverageWait() { return average(spacesWaitedCycles, spacesWaits); }

    /** Promedio de ciclos bloqueado considerando los tres semáforos del buffer. */
    public double getAverageWait() {
        return average(mutexWaitedCycles + itemsWaitedCycles + spacesWaitedCycles,
                mutexWaits + itemsWaits + spacesWaits);
    }

    private static double average(long cycles, long waits) {
        return waits == 0 ? 0.0 : (double) cycles / waits;
    }
}
