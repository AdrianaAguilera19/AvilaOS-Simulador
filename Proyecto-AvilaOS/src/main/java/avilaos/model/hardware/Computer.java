/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.model.hardware;

import avilaos.datastructures.CustomQueue;
import avilaos.enums.ExecutionMode;
import avilaos.enums.ProcessState;
import avilaos.interfaces.Clockable;
import avilaos.interfaces.SchedulingPolicy;
import avilaos.model.process.PCB;

/**
 * Un computador del cluster. Cada instancia tiene sus propias colas y su
 * propia politica de planificacion, y avanza un paso cada vez que el reloj
 * global le avisa (por eso implementa Clockable).
 * @author Antonella Ruiz
 */
public class Computer implements Clockable {

    // Identificacion del computador (no cambia)
    private final int id;
    private final String name;

    // Politica actual. Es volatile porque la GUI puede cambiarla mientras el reloj corre
    private volatile SchedulingPolicy policy;
    // true cuando hay que reordenar la cola de listos con la politica nueva
    private volatile boolean policyChanged = false;

    // La CPU de este computador
    private final CPU cpu = new CPU();

    // Colas propias de este computador
    private final CustomQueue<PCB> newQueue = new CustomQueue<>();      // procesos que acaban de llegar
    private final CustomQueue<PCB> readyQueue = new CustomQueue<>();    // listos para usar la CPU
    private final CustomQueue<PCB> blockedQueue = new CustomQueue<>();  // esperando E/S o un semaforo
    private final CustomQueue<PCB> finishedQueue = new CustomQueue<>(); // ya terminaron

    /**
     * Crea el computador con sus colas vacias y la CPU libre.
     * @param id identificador del computador
     * @param name nombre que se muestra en la interfaz (no puede estar vacio)
     * @param policy politica de planificacion inicial (no puede ser null)
     */
    public Computer(int id, String name, SchedulingPolicy policy) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del computador no puede estar vacio");
        }
        if (policy == null) {
            throw new IllegalArgumentException("El computador necesita una politica de planificacion");
        }
        this.id = id;
        this.name = name;
        this.policy = policy;
    }

    /**
     * Recibe un proceso nuevo. Siempre entra por la cola de nuevos; el paso a
     * listos lo hace onTick() desde el hilo del reloj, no quien llama a este metodo.
     * @param process proceso que llega al computador
     */
    public void admit(PCB process) {
        process.setState(ProcessState.NEW);
        newQueue.enqueue(process);
    }

    /**
     * Cambia la politica en caliente. La cola de listos se reordena con la politica
     * nueva en el siguiente ciclo (ver onTick).
     * @param newPolicy politica nueva (no puede ser null)
     */
    public void setPolicy(SchedulingPolicy newPolicy) {
        if (newPolicy == null) {
            throw new IllegalArgumentException("La politica no puede ser nula");
        }
        this.policy = newPolicy;
        this.policyChanged = true;   // onTick reordena la cola en el siguiente ciclo
    }

    /**
     * Un ciclo de este computador. Hace tres pasos en orden: admitir procesos
     * nuevos, ejecutar una instruccion en la CPU y elegir al siguiente si
     * la CPU quedo libre.
     * @param cycle numero de ciclo global actual
     */
    @Override
    public void onTick(long cycle) {
        // Si cambiaron la politica, reordeno la cola de listos con la nueva
        // (lo hace el hilo del reloj, que es el unico que toca la cola)
        if (policyChanged) {
            policyChanged = false;
            reorderReadyQueue(policy);
        }
        SchedulingPolicy currentPolicy = policy;   // la leo una sola vez por ciclo
        cpu.startCycle();                          // la CPU cuenta el ciclo

        // 1. Admision: paso los procesos nuevos a listos (A13 lo condicionara a la RAM)
        PCB p;
        while ((p = newQueue.dequeue()) != null) {
            p.setState(ProcessState.READY);
            currentPolicy.addToReady(readyQueue, p);
        }

        // 2. Si hay un proceso en la CPU, ejecuta una instruccion
        if (!cpu.isFree()) {
            cpu.executeUserInstruction();
            PCB running = cpu.getRunning();
            if (running.isFinished()) {
                // Termino todas sus instrucciones: va a la cola de terminados
                cpu.unload();
                running.setState(ProcessState.TERMINATED);
                finishedQueue.enqueue(running);
            } else if (currentPolicy.shouldPreempt(running, readyQueue, cpu.getCyclesInCpu())) {
                // La politica pide sacarlo de la CPU: vuelve a la cola de listos
                cpu.unload();
                running.setState(ProcessState.READY);
                currentPolicy.addToReady(readyQueue, running);
            }
        }

        // 3. Si la CPU quedo libre, la politica elige al siguiente
        if (cpu.isFree()) {
            PCB next = currentPolicy.selectNext(readyQueue);
            if (next != null) {
                cpu.load(next, cycle);   // la CPU marca el estado y el primer ciclo
            }
        }
    }

    /** Saca todos los procesos de la cola de listos y los vuelve a insertar con la politica dada. */
    private void reorderReadyQueue(SchedulingPolicy pol) {
        CustomQueue<PCB> temp = new CustomQueue<>();
        PCB p;
        while ((p = readyQueue.dequeue()) != null) {
            temp.enqueue(p);
        }
        while ((p = temp.dequeue()) != null) {
            pol.addToReady(readyQueue, p);
        }
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public SchedulingPolicy getPolicy() { return policy; }
    public CPU getCpu() { return cpu; }
    public PCB getRunning() { return cpu.getRunning(); }
    public ExecutionMode getMode() { return cpu.getMode(); }

    // Copias de las colas para la GUI: asi la interfaz mira sin tocar las colas reales
    public CustomQueue<PCB> getNewSnapshot() { return newQueue.copy(); }
    public CustomQueue<PCB> getReadySnapshot() { return readyQueue.copy(); }
    public CustomQueue<PCB> getBlockedSnapshot() { return blockedQueue.copy(); }
    public CustomQueue<PCB> getFinishedSnapshot() { return finishedQueue.copy(); }
}