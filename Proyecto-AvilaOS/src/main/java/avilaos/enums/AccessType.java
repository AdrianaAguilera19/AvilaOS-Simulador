/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Enum.java to edit this template
 */
package avilaos.enums;

/** Tipo de acceso de un proceso a un buffer. */
public enum AccessType {
    LOCAL,   // el proceso está en el mismo computador que el buffer
    REMOTE   // el proceso está en otro computador (paga latencia de red)
}
