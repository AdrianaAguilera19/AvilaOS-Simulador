/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.midterm;

import avilaos.model.hardware.RAM;
import avilaos.model.process.PCB;

/**
 * S05: la regla que dice cuando hay que suspender. Es una decision pura (no cambia nada): se le
 * dan los datos y responde si, para que el proceso que espera en Nuevos pueda entrar, hace falta
 * sacar procesos Bloqueados de la RAM.
 *
 * Hay que suspender cuando se cumplen las cuatro cosas:
 *  1. hay un proceso esperando en la cola de Nuevos,
 *  2. ese proceso cabria en la RAM vacia (si es mas grande que toda la RAM, suspender no sirve),
 *  3. ahora no le alcanza la memoria libre,
 *  4. liberar la memoria de los Bloqueados le alcanzaria (si ni suspendiendo a todos cabe, no se
 *     gasta E/S de disco en vano).
 * Solo se suspenden Bloqueados: los Listos y el que esta en la CPU no se tocan.
 */
public final class SuspensionTrigger {

    private SuspensionTrigger() {
    }

    /**
     * @param waiting proceso que espera en Nuevos (el primero de la cola), o null si no hay
     * @param ram RAM del computador
     * @param blockedCount cuantos procesos Bloqueados hay en la RAM
     * @param blockedMemory memoria que ocupan en total esos Bloqueados
     * @return true si hay que suspender para que entre el proceso que espera
     */
    public static boolean mustSuspend(PCB waiting, RAM ram, int blockedCount, int blockedMemory) {
        if (waiting == null || ram == null || blockedCount <= 0) {
            return false;
        }
        int needed = waiting.getMemorySize();
        if (needed > ram.getTotalSize()) {
            return false;
        }
        int free = ram.getFreeSize();
        if (needed <= free) {
            return false;
        }
        return free + blockedMemory >= needed;
    }
}
