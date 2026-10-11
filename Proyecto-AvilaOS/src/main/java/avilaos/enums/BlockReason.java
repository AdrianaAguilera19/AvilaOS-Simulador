/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Enum.java to edit this template
 */
package avilaos.enums;

/**
 *Motivos por el que un proceso entra en bloqueado
 * @author Antonella Ruiz
 */
public enum BlockReason {
    NONE, /** proceso no bloqueado*/
    IO, /** espera a que termine una operacion de e/s (proceso IO_BOUND) */
    MUTEX, /** espera en la cola del semaforo mutex de un buffer */
    EMPTY_SEMAPHORE, /** espera en el semaf de elementos de un buffer vacio (consumer)*/
    FULL_SEMAPHORE, /** espera en el semaf de espacios de un buffer lleno (producer)*/
    NETWORK_LATENCY /** espera los ciclos de latencia de red al acceder a un buffer de otro computador*/
}
