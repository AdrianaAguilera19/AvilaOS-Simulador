/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.log;

/** Un evento del log: qué pasó, cuándo (ciclo de reloj) y en qué computador. */
public final class LogEvent {

    /** Identificador para eventos globales que no pertenecen a un computador. */
    public static final int SYSTEM = 0;

    private final long cycle;
    private final int computerId;
    private final String message;

    public LogEvent(long cycle, int computerId, String message) {
        this.cycle = cycle;
        this.computerId = computerId;
        this.message = message;
    }

    public long getCycle() { return cycle; }
    public int getComputerId() { return computerId; }
    public String getMessage() { return message; }

    /** Texto listo para mostrar, por ejemplo: [Ciclo 87] Computador 1: P3 pasa a ejecución. */
    @Override
    public String toString() {
        String who = computerId == SYSTEM ? "Sistema" : "Computador " + computerId;
        return "[Ciclo " + cycle + "] " + who + ": " + message;
    }
}
