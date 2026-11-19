/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.log;

import avilaos.datastructures.CustomQueue;
import avilaos.enums.LogCategory;
import java.util.concurrent.Semaphore;

/**
 * Registro global de eventos del sistema. Lo comparten el cluster y los computadores
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

    /** Registra un evento de la categoría GENERAL (los del P1). */
    public void log(long cycle, int computerId, String message) {
        log(cycle, computerId, LogCategory.GENERAL, message);
    }

    /**
     * Registra un evento. Si el log está lleno, descarta el más antiguo.
     * @param cycle ciclo de reloj en que ocurrió
     * @param computerId computador donde ocurrió (LogEvent.SYSTEM si es global)
     * @param category categoría del evento (no puede ser null)
     * @param message descripción del evento (no puede estar vacía)
     */
    public void log(long cycle, int computerId, LogCategory category, String message) {
        if (category == null) {
            throw new IllegalArgumentException("Falta la categoría del evento");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("El mensaje del evento no puede estar vacío");
        }
        lock.acquireUninterruptibly();
        try {
            events.enqueue(new LogEvent(cycle, computerId, category, message));
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
        log(cycle, LogEvent.SYSTEM, LogCategory.GENERAL, message);
    }

    /** Registra un evento global de una categoría (disco, sistema de archivos, suspensión, permisos). */
    public void logSystem(long cycle, LogCategory category, String message) {
        log(cycle, LogEvent.SYSTEM, category, message);
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
        return getLast(n, null);
    }

    /**
     * Copia de los últimos n eventos de una categoría, del más antiguo al más reciente.
     * @param filter categoría a mostrar; null para mostrar todas
     */
    public CustomQueue<LogEvent> getLast(int n, LogCategory filter) {
        CustomQueue<LogEvent> all = getSnapshot();
        int matching = all.size();
        if (filter != null) {
            matching = 0;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).getCategory() == filter) {
                    matching++;
                }
            }
        }
        int skip = Math.max(0, matching - n);   // cuantos de los que cumplen se descartan por antiguos
        CustomQueue<LogEvent> result = new CustomQueue<>();
        for (int i = 0; i < all.size(); i++) {
            LogEvent e = all.get(i);
            if (filter != null && e.getCategory() != filter) {
                continue;
            }
            if (skip > 0) {
                skip--;
            } else {
                result.enqueue(e);
            }
        }
        return result;
    }

    /** Texto con los últimos n eventos, una línea por evento (para el área de texto de la pantalla). */
    public String getLastAsText(int n) {
        return getLastAsText(n, null);
    }

    /** Igual que {@link #getLastAsText(int)} pero solo de una categoría (null = todas). */
    public String getLastAsText(int n, LogCategory filter) {
        CustomQueue<LogEvent> last = getLast(n, filter);
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