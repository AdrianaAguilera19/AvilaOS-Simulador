/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package avilaos.interfaces;

import avilaos.enums.BlockReason;
import avilaos.model.process.PCB;

/**
 * Quien lleva la cuenta de cuánto esperan los procesos en cada semáforo (S10).
 * Lo implementa el BufferManager; los computadores solo conocen esta interfaz y la usan
 * cuando un proceso bloqueado en un semáforo se despierta.
 */
public interface SemaphoreWaitRecorder {

    /**
     * Anota que un proceso estuvo bloqueado en un semáforo durante cierta cantidad de ciclos.
     * @param pcb proceso que se despertó (lleva el buffer asociado)
     * @param reason semáforo en el que esperó (MUTEX, EMPTY_SEMAPHORE o FULL_SEMAPHORE)
     * @param cycles ciclos que estuvo bloqueado
     */
    void recordSemaphoreWait(PCB pcb, BlockReason reason, long cycles);
}
