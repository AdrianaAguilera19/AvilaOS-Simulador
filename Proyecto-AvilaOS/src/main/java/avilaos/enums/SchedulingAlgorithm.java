/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Enum.java to edit this template
 */
package avilaos.enums;

/**
 *Algoritmos de planificacion de procesos
 * @author Antonella Ruiz
 */
public enum SchedulingAlgorithm {
    FCFS, /** primero que llega primero en ejecutarse */
    EDF, /** se ejecuta el proceso con el deadline mas cercano*/ 
    PRIORITY, /** proceso de mayor prioridad desaloja al que esta en la CPU */
    ROUND_ROBIN, /**turnos de duracion fija (quantum) */
}
