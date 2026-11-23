/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Enum.java to edit this template
 */
package avilaos.enums;

/**
 * Estados posibles de un proceso: el modelo de siete estados.
 * @author Antonella Ruiz
 */
public enum ProcessState {
    NEW,/** Creado esperando que haya suficiente memoria para su admision*/
    READY, /** en RAM y preparado, esperando a que el planificador le asigne el CPU*/
    RUNNING, /** usando la CPU*/
        BLOCKED, /** en RAM, esperando un evento (semaforo, E/S, red)*/
    READY_SUSPENDED, /** en el swap, listo para volver a RAM cuando haya espacio*/
    BLOCKED_SUSPENDED, /** en el swap, esperando todavia un evento; al ocurrir pasa a READY_SUSPENDED*/
    TERMINATED; /** termino el trabajo o vencio su deadline,memoria liberada*/

    /**
     * @return true si el proceso esta suspendido, es decir, su imagen esta en el swap y no en la RAM
     */
    public boolean isSuspended() {
        return this == READY_SUSPENDED || this == BLOCKED_SUSPENDED;
    }
}
