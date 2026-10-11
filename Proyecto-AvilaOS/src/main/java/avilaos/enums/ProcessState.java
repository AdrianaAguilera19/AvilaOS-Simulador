/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Enum.java to edit this template
 */
package avilaos.enums;

/**
 *Estados Posibles de un proceso
 * @author Antonella Ruiz
 */
public enum ProcessState {
    NEW,/** Creado esperando que haya suficiente memoria para su admision*/
    READY, /** en RAM y preparado, esperando a que el planificador le asigne el CPU*/
    RUNNING, /** usando la CPU*/
    BLOCKED, /** esperando un evento*/
    TERMINATED /** termino el trabajo o vencio su deadline,memoria liberada*/
}
