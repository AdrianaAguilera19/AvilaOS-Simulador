/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.log;

import avilaos.datastructures.CustomQueue;
import java.util.concurrent.Semaphore;

/**
 * Registro global de eventos del sistema. Lo comparten el cluster y los computadores
 * (varios hilos escriben a la vez) y la interfaz lo lee mediante copias.
 * Guarda solo los últimos "capacity" eventos para no crecer sin límite.
 */
public class EventLogger {

    public static final int DEFAULT_CAPACITY = 1000;

    private final int capacity;
    private final CustomQueue<LogEvent> events = new CustomQueue<>();
    private final Semaphore lock = new Semaphore(1);   // protege events y el contador
    private volatile long totalLogged = 0;             // total histórico (no baja al descartar)

    public EventLogger() {
        this(DEFAULT_CAPACITY);
    }

    public EventLogger(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("La capacidad del log debe ser mayor a 0");
        }
        this.capacity = capacity;
    }

    /**
     * Registra un evento. Si el log está lleno, descarta el más antiguo.
     * @param cycle ciclo de reloj en que ocurrió
     * @param computerId computador donde ocurrió (LogEvent.SYSTEM si es global)
     * @param message descripción del evento (no puede estar vacía)
     */
    public void log(long cycle, int computerId, String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("El mensaje del evento no puede estar vacío");
        }
        lock.acquireUninterruptibly();
        try {
            events.enqueue(new LogEvent(cycle, computerId, message));
            totalLogged++;
            while (events.size() > capacity) {
                events.dequeue();
            }
        } finally {
            lock.release();
        }
    }

    /** Registra un evento global (no pertenece a un computador). */
    public void logSystem(long cycle, String message) {
        log(cycle, LogEvent.SYSTEM, message);
    }

    /** Copia de todos los eventos guardados, del más antiguo al más reciente. */
    public CustomQueue<LogEvent> getSnapshot() {
        lock.acquireUninterruptibly();
        try {
            return events.copy();
        } finally {
            lock.release();
        }
    }

    /** Copia de los últimos n eventos, del más antiguo al más reciente. */
    public CustomQueue<LogEvent> getLast(int n) {
        CustomQueue<LogEvent> all = getSnapshot();
        CustomQueue<LogEvent> result = new CustomQueue<>();
        for (int i = Math.max(0, all.size() - n); i < all.size(); i++) {
            result.enqueue(all.get(i));
        }
        return result;
    }

    /** Texto con los últimos n eventos, una línea por evento (para el área de texto de la pantalla). */
    public String getLastAsText(int n) {
        CustomQueue<LogEvent> last = getLast(n);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < last.size(); i++) {
            text.append(last.get(i)).append('\n');
        }
        return text.toString();
    }

    /** Total de eventos registrados desde el inicio; la pantalla lo usa para saber si hay algo nuevo. */
    public long getTotalLogged() { return totalLogged; }

    /** Eventos que se guardan ahora mismo. */
    public int size() {
        lock.acquireUninterruptibly();
        try {
            return events.size();
        } finally {
            lock.release();
        }
    }

    public int getCapacity() { return capacity; }
}
