/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Enum.java to edit this template
 */
package avilaos.enums;

/**
 *Tipos de proceso que el usuario puede crear 
 * @author Antonella Ruiz
 */
public enum ProcessType {
    CPU_BOUND, /** consume sobre todo el cpu*/
    IO_BOUND, /** cosume sobre todas las operaciones e/s*/
    PRODUCER, /** produce elementos en buffer acotado*/
    CONSUMER /** consume elemento de buffer acotado*/
}
