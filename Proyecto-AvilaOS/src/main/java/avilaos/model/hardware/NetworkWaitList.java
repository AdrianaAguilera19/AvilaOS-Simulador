/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.model.hardware;

import avilaos.datastructures.CustomLinkedList;
import avilaos.datastructures.CustomQueue;
import avilaos.model.process.PCB;

/**
 * Procesos de un computador que esperan la latencia de red de un acceso remoto.
 * Solo la toca el hilo del reloj de su computador; los contadores son volatile
 * para que la GUI y las métricas los lean desde otro hilo.
 */
public class NetworkWaitList {

    private static class Wait {
        final PCB pcb;
        int remaining;

        Wait(PCB pcb, int remaining) {
            this.pcb = pcb;
            this.remaining = remaining;
        }
    }

    private final CustomLinkedList<Wait> waits = new CustomLinkedList<>();
    private volatile long totalAccesses = 0;       // accesos remotos realizados
    private volatile long totalWaitedCycles = 0;   // ciclos-proceso esperando por la red

    /** Empieza a contar los ciclos de espera de un proceso. */
    public void add(PCB pcb, int cycles) {
        if (cycles <= 0) {
            throw new IllegalArgumentException("La espera de red debe ser mayor a 0");
        }
        waits.addLast(new Wait(pcb, cycles));
        totalAccesses++;
    }

    /** Avanza un ciclo y devuelve los procesos cuya espera ya terminó. */
    public CustomQueue<PCB> tick() {
        CustomQueue<PCB> done = new CustomQueue<>();
        CustomLinkedList<Wait> snapshot = waits.copy();
        for (int i = 0; i < snapshot.size(); i++) {
            Wait w = snapshot.get(i);
            totalWaitedCycles++;
            w.remaining--;
            if (w.remaining <= 0) {
                waits.remove(w);
                done.enqueue(w.pcb);
            }
        }
        return done;
    }

    public int size() { return waits.size(); }
    public long getTotalAccesses() { return totalAccesses; }
    public long getTotalWaitedCycles() { return totalWaitedCycles; }
}
