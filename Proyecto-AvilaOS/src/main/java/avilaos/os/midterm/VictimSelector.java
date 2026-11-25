/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.os.midterm;

import avilaos.datastructures.CustomQueue;
import avilaos.enums.ProcessState;
import avilaos.interfaces.VictimPolicy;
import avilaos.model.process.PCB;

/**
 * S06: criterio por defecto para elegir a quien suspender entre los procesos Bloqueados que estan
 * en la RAM. Es una decision pura: recibe la cola de bloqueados y devuelve el elegido sin modificar nada.
 *
 * Criterio:
 *  1. Se elige el de MENOR prioridad. En este sistema un numero menor significa mayor prioridad,
 *     asi que el de menor prioridad es el que tiene el numero MAS GRANDE.
 *  2. Si hay empate de prioridad, el que lleva MAS TIEMPO bloqueado. La cola de bloqueados guarda
 *     a los procesos en el orden en que se bloquearon, asi que es el que esta mas adelante.
 *
 * Justificacion:
 *  - El de menor prioridad es el que menos importa al sistema: suspenderlo es el menor dano.
 *  - Un proceso Bloqueado no usa la CPU, asi que sacarlo de la RAM no retrasa su trabajo hasta que
 *    ocurra su evento; en cambio sacar a un Listo si lo retrasaria.
 *  - En empate, el que lleva mas tiempo bloqueado es el que probablemente tardara mas en despertar,
 *    asi que su imagen estara menos tiempo "fuera de lugar" en el swap.
 *  - Desempatar por la posicion en la cola es determinista y evita inanicion de los recien bloqueados.
 *
 * Se puede suspender a cualquier Bloqueado, tambien al que espera una operacion de disco (DISK_IO): su
 * solicitud sigue en la cola del disco y, cuando termina, el proceso pasa a Listo/Suspendido (S08).
 */
public class VictimSelector implements VictimPolicy {

    @Override
    public String getName() {
        return "Menor prioridad, luego mas tiempo bloqueado";
    }

    /**
     * @param p proceso a revisar
     * @return true si el proceso se puede suspender: esta Bloqueado en la RAM
     */
    public static boolean isEligible(PCB p) {
        return p != null && p.getState() == ProcessState.BLOCKED;
    }

    @Override
    public PCB choose(CustomQueue<PCB> blocked) {
        if (blocked == null) {
            return null;
        }
        PCB victim = null;
        for (int i = 0; i < blocked.size(); i++) {
            PCB p = blocked.get(i);
            if (!isEligible(p)) {
                continue;
            }
            // '>' estricto: en un empate se queda el que ya estaba, que lleva mas tiempo bloqueado
            if (victim == null || p.getPriority() > victim.getPriority()) {
                victim = p;
            }
        }
        return victim;
    }
}