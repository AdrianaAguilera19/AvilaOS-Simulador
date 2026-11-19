/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.log;

import avilaos.enums.LogCategory;

/** Un evento del log: qué pasó, cuándo (ciclo de reloj), en qué computador y de qué categoría. */
public final class LogEvent {

    /** Identificador para eventos globales que no pertenecen a un computador. */
    public static final int SYSTEM = 0;

    private final long cycle;
    private final int computerId;
    private final LogCategory category;
    private final String message;

    public LogEvent(long cycle, int computerId, LogCategory category, String message) {
        if (category == null) {
            throw new IllegalArgumentException("Falta la categoría del evento");
        }
        this.cycle = cycle;
        this.computerId = computerId;
        this.category = category;
        this.message = message;
    }

    /** Evento de la categoría GENERAL (los del P1). */
    public LogEvent(long cycle, int computerId, String message) {
        this(cycle, computerId, LogCategory.GENERAL, message);
    }

    public long getCycle() { return cycle; }
    public int getComputerId() { return computerId; }
    public LogCategory getCategory() { return category; }
    public String getMessage() { return message; }

    /**
     * Texto listo para mostrar. Los del P1 no cambian: [Ciclo 87] Computador 1: P3 pasa a ejecución.
     * Los nuevos llevan la categoría: [Ciclo 87] [DISCO] Sistema: El disco atiende la solicitud de P4.
     */
    @Override
    public String toString() {
        String who = computerId == SYSTEM ? "Sistema" : "Computador " + computerId;
        return "[Ciclo " + cycle + "] " + category.tag() + who + ": " + message;
    }
}